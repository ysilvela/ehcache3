/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehcache.config;

import org.ehcache.Cache;
import org.ehcache.Ehcache;
import org.ehcache.internal.HeapCache;
import org.ehcache.spi.ServiceLocator;
import org.ehcache.spi.cache.Store;
import org.ehcache.spi.service.Service;
import org.ehcache.spi.service.ServiceConfiguration;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;

import static org.ehcache.config.CacheConfigurationBuilder.newCacheConfigurationBuilder;
import static org.ehcache.internal.util.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Alex Snaps
 */
@SuppressWarnings("deprecation")
public class CachingTierConfigurationBuilderTest {

  @Test
  public void testNothing() throws Exception {

    final ServiceLocator serviceLocator = new ServiceLocator();
    serviceLocator.startAllServices(new HashMap<Service, ServiceConfiguration<?>>());
    
    final CacheConfiguration<String, String> config = newCacheConfigurationBuilder().buildConfig(String.class, String.class);
    final Store.Provider service = serviceLocator.findService(Store.Provider.class);
    Collection<ServiceConfiguration<?>> serviceConfigs = config.getServiceConfigurations();
    ServiceConfiguration<?>[] serviceConfigArray = serviceConfigs.toArray(new ServiceConfiguration[serviceConfigs.size()]);
    final Store<String, String> store = service.createStore(new StoreConfigurationImpl<String, String>(config), serviceConfigArray);
    
    
    @SuppressWarnings("unchecked")
    final Cache<String, String> cache = new HeapCache<String, String>(mock(CacheConfiguration.class), store, LoggerFactory.getLogger(Ehcache.class + "-" + "CachingTierConfigurationBuilderTest"));

    assertThat(cache, not(hasKey("key")));
    cache.put("key", "value");
    assertThat(cache, hasKey("key"));
  }
}
