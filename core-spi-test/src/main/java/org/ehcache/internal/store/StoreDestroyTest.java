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

package org.ehcache.internal.store;

import org.ehcache.config.Eviction;
import org.ehcache.exceptions.CacheAccessException;
import org.ehcache.spi.cache.Store;
import org.ehcache.spi.test.After;
import org.ehcache.spi.test.Before;
import org.ehcache.spi.test.SPITest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test the {@link org.ehcache.spi.cache.Store#destroy()} contract of the
 * {@link org.ehcache.spi.cache.Store Store} interface.
 * <p/>
 *
 * @author Aurelien Broszniowski
 */

public class StoreDestroyTest<K, V> extends SPIStoreTester<K, V> {

  public StoreDestroyTest(final StoreFactory<K, V> factory) {
    super(factory);
  }

  protected Store<K, V> kvStore;

  @Before
  public void setUp() {
    kvStore = factory.newStore(factory.newConfiguration(factory.getKeyType(), factory.getValueType(), null, Eviction
        .all(), null));
  }

  @After
  public void tearDown() {
    if (kvStore != null) {
      kvStore.close();
      kvStore.close();
    }
  }

  @SPITest
  public void noDataCanBeRecoveredFromDestroyedStore()
      throws IllegalAccessException, InstantiationException, CacheAccessException {
    K key = factory.getKeyType().newInstance();
    V value = factory.getValueType().newInstance();

    kvStore.put(key, value);

    try {
      kvStore.destroy();
    } catch (CacheAccessException e) {
      System.err.println("Warning, an exception is thrown due to the SPI test");
      e.printStackTrace();
    }

    try {
      assertThat(kvStore.containsKey(key), is(false));
    } catch (Exception e) {
      System.err.println("An exception is thrown, This might happen since the store is destroyed.");
      e.printStackTrace();
    }
  }
}
