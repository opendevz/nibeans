package org.yadgen.internal;

public interface BeanProviderRegistry {

	public <T> void register(Class<T> ibean, BeanProvider<T> provider);

}
