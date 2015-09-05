/**
 * Copyright (C) 2015 opendevz (opendevz@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nibeans;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.nibeans.internal.BeanProvider;
import org.nibeans.internal.BeanProviderRegistry;
import org.nibeans.internal.BeanProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager bean implementations and creates instances of them, sparing interaction with the underlying
 * implementation classes.
 * 
 * @author Tareq Sharafy (tareq.sharafy@sap.com)
 *
 */
public final class BeanFactory {
	private static final Logger LOG = LoggerFactory.getLogger(BeanFactory.class);
	private static BeanFactory instance;
	// All the registered providers
	private final Map<Class<?>, BeanProvider<?>> providers = new HashMap<>();

	/**
	 * Get the only instance of this class.
	 */
	public static BeanFactory getInstance() {
		if (instance == null) {
			instance = new BeanFactory();
		}
		return instance;
	}

	/**
	 * Create an instance of the default bean implementation for the given interface.
	 * 
	 * @param beanInterface
	 *            The interface for which to create an implementation instance.
	 * @return An instance of the default bean implementation, or null if no provider was registered for the
	 *         given class definition.
	 */
	@SuppressWarnings("unchecked")
	public <T> T createBean(Class<T> beanInterface) {
		BeanProvider<?> provider = providers.get(beanInterface);
		if (provider != null) {
			return (T) provider.createInstance();
		}
		return null;
	}

	/**
	 * Get a list of all the registered bean providers.
	 */
	public List<BeanProvider<?>> getAllBeanProviders() {
		return new ArrayList<>(providers.values());
	}

	private BeanFactory() {
		BeanProviderRegistry registry = new BeanProviderRegistry() {
			@Override
			public <T> void register(BeanProvider<T> provider) {
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
