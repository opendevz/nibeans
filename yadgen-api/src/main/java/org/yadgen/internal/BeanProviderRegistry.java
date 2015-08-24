package org.yadgen.internal;

public interface BeanProviderRegistry {

	public <T> void register(BeanProvider<T> provider);

}
