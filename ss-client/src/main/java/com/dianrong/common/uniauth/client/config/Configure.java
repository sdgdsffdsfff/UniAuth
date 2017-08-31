package com.dianrong.common.uniauth.client.config;

/**
 * 手动构造bean对象.
 *
 * @author wanglin
 */
public interface Configure<T> {

  /**
   * Create a new T.
   *
   * @param args创建传入的参数.
   * @return new T
   * @throws UniauthInvalidParamterException 如果传入的参数不符合要求.
   */
  T create(Object... args);

  /**
   * Judge whether the class type is supported.
   *
   * @param cls the type
   * @return true if the class is supported
   */
  boolean isSupport(Class<?> cls);
}
