package com.dianrong.common.uniauth.server.datafilter.impl;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dianrong.common.uniauth.common.bean.InfoName;
import com.dianrong.common.uniauth.server.data.mapper.PermissionMapper;
import com.dianrong.common.uniauth.server.datafilter.FieldType;
import com.dianrong.common.uniauth.server.exp.AppException;
import com.dianrong.common.uniauth.server.util.TypeParseUtil;
import com.dianrong.common.uniauth.server.util.UniBundle;

/**.
 * 权限的数据过滤处理实现.
 * @author wanglin
 */
@Service("permissionDataFilter")
public class PermissionDataFilter extends CurrentAbastracDataFIleter{
	
	@Autowired
	private PermissionMapper permissionMapper;
	
	/**.
	 * 标示处理的表名
	 */
	private String processTalbeName = "权限数据";
	
	/**.
	 * 处理过滤status=0的情况
	 * @param filterMap 过滤条件字段
	 */
	@Override
	public void filterStatusEqual0(Map<FieldType, Object> filterMap){
		Set<Entry<FieldType, Object>> entrySet = filterMap.entrySet();
		//遍历
		for(Entry<FieldType, Object> kv : entrySet){
			if(kv.getKey() == FieldType.FIELD_TYPE_ID){
				int countById = permissionMapper.countPermissionByIdWithStatusEffective(TypeParseUtil.paraseToLongFromObject(kv.getValue()));
				//有数据  就要报错
				if(countById > 0){
					throw new AppException(InfoName.INTERNAL_ERROR, UniBundle.getMsg("datafilter.data.exsit.error", processTalbeName , "id" , TypeParseUtil.paraseToLongFromObject(kv.getValue())));
				}
			}
		}
	}
	
	/**.
	 * 处理过滤不能出现status=0的情况
	 * @param filterMap 入参数据
	 */
	@Override
	public void filterNoStatusEqual0(Map<FieldType, Object> filterMap){
		Set<Entry<FieldType, Object>> entrySet = filterMap.entrySet();
		//遍历
		for(Entry<FieldType, Object> kv : entrySet){
			if(kv.getKey() == FieldType.FIELD_TYPE_ID){
				int countById = permissionMapper.countPermissionByIdWithStatusEffective(TypeParseUtil.paraseToLongFromObject(kv.getValue()));
				//有数据  就要报错
				if(countById <= 0){
					throw new AppException(InfoName.INTERNAL_ERROR, UniBundle.getMsg("datafilter.data.notexsit.error", processTalbeName , "id" , TypeParseUtil.paraseToLongFromObject(kv.getValue())));
				}
				break;
			}
		}
	}

	@Override
	protected void doFileterFieldValueIsExsist(FieldType type, Integer id, Object fieldValue) {
	}
}
