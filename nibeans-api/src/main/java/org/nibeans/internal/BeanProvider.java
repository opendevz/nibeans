package org.nibeans.internal;

public interface BeanProvider<T> {

	public Class<T> getBeanInterface();

	public T createInstance();

}
