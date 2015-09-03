package org.nibeans.internal;

public interface BeanProviderRegistry {

	public <T> void register(BeanProvider<T> provider);

}
