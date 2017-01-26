package com.dianrong.common.uniauth.server.support.apicontrl;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import com.dianrong.common.uniauth.common.apicontrol.HeaderKey;
import com.dianrong.common.uniauth.common.apicontrol.LoginRequestLoadHeaderOperator;
import com.dianrong.common.uniauth.common.apicontrol.LoginResponseLoadHeaderOperator;
import com.dianrong.common.uniauth.common.apicontrol.PtHeaderOperator;
import com.dianrong.common.uniauth.common.apicontrol.RequestVerifiedType;
import com.dianrong.common.uniauth.common.apicontrol.ResponseVerifiedType;
import com.dianrong.common.uniauth.common.apicontrol.StringHeaderValueOperator;
import com.dianrong.common.uniauth.common.apicontrol.exp.InsufficientPrivilegesException;
import com.dianrong.common.uniauth.common.apicontrol.exp.InvalidTokenException;
import com.dianrong.common.uniauth.common.apicontrol.exp.LoadCredentialFailedException;
import com.dianrong.common.uniauth.common.apicontrol.exp.TokenCreateFailedException;
import com.dianrong.common.uniauth.common.apicontrol.exp.TokenExpiredException;
import com.dianrong.common.uniauth.common.apicontrol.model.LoginRequestLoad;
import com.dianrong.common.uniauth.common.apicontrol.model.LoginResponseLoad;
import com.dianrong.common.uniauth.common.apicontrol.server.CallerCredential;
import com.dianrong.common.uniauth.server.support.apicontrl.security.JWTProcessor;

import lombok.extern.slf4j.Slf4j;

/**
 * uniauth server的接口访问控制filter
 * 
 * @author wanglin
 */
@Component("apiControlFilter")
@Slf4j
public class ApiControlFilter extends GenericFilterBean {
    // ANONYMOUS domain
    public static final String ANONYMOUS_DOMAIN_NAME = "ANONYMOUS";

    @Autowired
    private ApiCallerLoader callerLoader;

    @Autowired
    private UniauthServerPermissionJudger permissionJudger;

    @Autowired
    private ServerPermissionCacher serverPermissionCacher;

    @Autowired
    private JWTProcessor jwtProcessor;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest && response instanceof HttpServletResponse)) {
            // 不处理
            chain.doFilter(request, response);
        } else {
            final HttpServletRequest httpRequest = (HttpServletRequest) request;
            final HttpServletResponse httpResponse = (HttpServletResponse) response;
            StringHeaderValueOperator headerOperator = new UniauthServerHeaderOperator(httpRequest, httpResponse);
            try {
                RequestVerifiedType requestType = null;
                try {
                    requestType = RequestVerifiedType.translateStrToReqType(headerOperator.getHeader(HeaderKey.REQUEST_TYPE));
                    requestType = requestType == null ? RequestVerifiedType.ANONYMOUS : requestType;
                } catch (IllegalArgumentException ex) {
                    log.warn("failed to translate api control request type string : " + headerOperator.getHeader(HeaderKey.REQUEST_TYPE), ex);
                    throw new InvalidTokenException();
                }
                // load caller info 
                CallerCredential<ApiCtlPermission> apiCaller = loadApiCallerInfo(headerOperator, requestType);
                // judge permission
                if (permissionJudger.judge(apiCaller, httpRequest)) {
                    // set request caller name
                    CallerAccountHolder.set(apiCaller.getAccount());
                    try {
                        // operate header before response write body
                        afterSuccessInvokeApi(headerOperator, requestType, apiCaller);
                        chain.doFilter(request, response);
                    } finally {
                        // clear
                        CallerAccountHolder.remove();
                    }
                } else {
                    // 权限不足
                    throw new InsufficientPrivilegesException();
                }
            } catch (LoadCredentialFailedException e) {
               log.info("load credential failed ", e); 
               headerOperator.setHeader(HeaderKey.RESPONSE_TYPE, ResponseVerifiedType.AUTENTICATION_FAILED.toString());
            } catch (InsufficientPrivilegesException e) {
                log.info("request insufficent privileges  " + e);
                headerOperator.setHeader(HeaderKey.RESPONSE_TYPE, ResponseVerifiedType.INSUFFICIENT_PRIVILEGES.toString());
            } catch (InvalidTokenException e) {
                log.info("request content is invalid " + e);
                headerOperator.setHeader(HeaderKey.RESPONSE_TYPE, ResponseVerifiedType.TOKEN_INVALID.toString());
            } catch (TokenExpiredException e) {
                log.info("request token is expired " + e);
                headerOperator.setHeader(HeaderKey.RESPONSE_TYPE, ResponseVerifiedType.TOKEN_EXPIRED.toString());
            }
        }
    }

    // 成功访问之后，设置返回结果
    private void afterSuccessInvokeApi(StringHeaderValueOperator headerOperator, RequestVerifiedType requestType,  CallerCredential<ApiCtlPermission> apiCaller) {
        switch(requestType) {
            case LOGIN:
                try {
                    headerOperator.setHeader(HeaderKey.RESPONSE_TYPE, ResponseVerifiedType.LOGIN_SUCCESS.toString());
                    PtHeaderOperator<LoginResponseLoad> responseLoginHeaderOperator = new LoginResponseLoadHeaderOperator(headerOperator);
                    responseLoginHeaderOperator.setHeader(HeaderKey.RESPONSE_REULST, new LoginResponseLoad(jwtProcessor.marshal(apiCaller), apiCaller.getExpiredTime()));
                } catch (TokenCreateFailedException e) {
                    log.error("failed create token + " + apiCaller, e);
                    // client 端重新登陆
                }
                break;
            case TOKEN:
                headerOperator.setHeader(HeaderKey.RESPONSE_TYPE, ResponseVerifiedType.TOKEN_AVAILABLE.toString());
                break;
            case ANONYMOUS:
                // do nothing
                break;
        }
    }
    
    /**
     * get api caller info
     * 
     * @param headerOperator process header
     * @param requestType requestType
     * @return api caller info
     * @throws TokenExpiredException if token is expired
     * @throws InvalidTokenException if token is invalid
     * @throws LoadCredentialFailedException  load credential failed
     */
    private CallerCredential<ApiCtlPermission> loadApiCallerInfo(StringHeaderValueOperator headerOperator, RequestVerifiedType requestType) throws InvalidTokenException, TokenExpiredException, LoadCredentialFailedException {
        switch (requestType) {
            case ANONYMOUS:
                return loadAnonymousCaller();
            case TOKEN:
               return loadTokenCaller(headerOperator.getHeader(HeaderKey.REQUEST_CONTENT));
            case LOGIN:
                PtHeaderOperator<LoginRequestLoad> requestLoginHeaderOperator = new LoginRequestLoadHeaderOperator(headerOperator);
                LoginRequestLoad loginUser = null;
                try {
                    loginUser = requestLoginHeaderOperator.getHeader(HeaderKey.REQUEST_CONTENT);
                } catch (Throwable t) {
                    log.warn("invalid login request user info", t);
                    throw new InvalidTokenException();
                }
                return callerLoader.loadCredential(loginUser.getAccount(), loginUser.getPassword());
        }
        throw new InvalidTokenException();
    }

    // construct a anonymous ApiCaller
    private ApiCaller loadAnonymousCaller() {
        return new ApiCaller(ANONYMOUS_DOMAIN_NAME, ANONYMOUS_DOMAIN_NAME, serverPermissionCacher.getPublicPermissions());
    }

    // construct a token ApiCaller
    private CallerCredential<ApiCtlPermission> loadTokenCaller(String token) throws InvalidTokenException, TokenExpiredException {
        return jwtProcessor.unMarshal(token);
    }
}
