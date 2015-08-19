package org.yadgen;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yadgen.internal.BeanProvider;
import org.yadgen.internal.BeanProviderRegistry;
import org.yadgen.internal.BeanProviderService;

/**
 * Manager bean implementations and creates instances of them, sparing interaction with the underlying
 * implementation classes.
 * 
 * @author Tareq Sharafy (tareq.sharafy@sap.com)
 *
 */
public final class BeanFactory {
	private static final Logger LOG = LoggerFactory.getLogger(BeanFactory.class);
	// All the registered providers
	private static final Map<Class<?>, BeanProvider<?>> providers = new HashMap<>();

	/**
	 * Create an instance of the default bean implementation for the given interface.
	 * 
	 * @param beanInterface
	 *            The interface for which to create an implementation instance.
	 * @return An instance of the default bean implementation, or null if no provider was registered for the
	 *         given class definition.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T createBean(Class<T> beanInterface) {
		BeanProvider<?> provider = providers.get(beanInterface);
		if (provider != null) {
			return (T) provider.createInstance();
		}
		return null;
	}

	static {
		BeanProviderRegistry registry = new BeanProviderRegistry() {
			@Override
			public <T> void register(Class<T> ibean, BeanProvider<T> provider) {
				Class<?> beanInterface = provider.getBeanInterface();
				if (beanInterface != null) {
					BeanProvider<?> existingProvider = providers.get(beanInterface);
					if (existingProvider == null) {
						providers.put(beanInterface, provider);
					} else {
						LOG.warn("Ignoring provider {} of bean interface {} because it is already provided by {}",
								provider, beanInterface, existingProvider);
					}
				} else {
					LOG.warn("Provider {} has a null bean interface", provider);
				}
			}
		};
		// Collect all providers by visiting the service providers of BeanProviderService
		for (BeanProviderService providerService : ServiceLoader.load(BeanProviderService.class)) {
			providerService.registerProviders(registry);
		}
	}

}
