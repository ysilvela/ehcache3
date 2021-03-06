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
package org.ehcache.internal.store.disk;

import org.ehcache.config.StoreConfigurationImpl;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.ehcache.function.Function;
import org.ehcache.internal.TimeSource;
import org.ehcache.internal.serialization.JavaSerializationProvider;
import org.ehcache.spi.cache.Store;
import org.ehcache.spi.cache.tiering.CachingTier;
import org.ehcache.spi.serialization.Serializer;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ludovic Orban
 */
public class DiskStoreTest {

  private TestTimeSource timeSource;
  private DiskStore<Number, CharSequence> diskStore;
  private long capacityConstraint;

  @Before
  public void setUp() throws Exception {
    timeSource = new TestTimeSource();
    capacityConstraint = 5L;
    StoreConfigurationImpl<Number, CharSequence> config = new StoreConfigurationImpl<Number, CharSequence>(Number.class, CharSequence.class, capacityConstraint,
        null, null, ClassLoader.getSystemClassLoader(), Expirations.timeToLiveExpiration(new Duration(10, TimeUnit.MILLISECONDS)));
    JavaSerializationProvider serializationProvider = new JavaSerializationProvider();
    Serializer<DiskStorageFactory.Element> elementSerializer = serializationProvider.createSerializer(DiskStorageFactory.Element.class, config.getClassLoader());
    Serializer<Object> objectSerializer = serializationProvider.createSerializer(Object.class, config.getClassLoader());

    diskStore = new DiskStore<Number, CharSequence>(config, "DiskStoreTest", timeSource, elementSerializer, objectSerializer);
    diskStore.destroy();
    diskStore.create();
    diskStore.init();
  }

  @After
  public void tearDown() throws Exception {
    diskStore.close();
    diskStore.destroy();
    diskStore = null;
  }

  @Test
  public void testPutThenFaultedWithComputeElementDoesNotExpire() throws Exception {
    diskStore.put(1, "put-one");

    Store.ValueHolder<CharSequence> valueHolder = diskStore.computeIfAbsentAndFault(1, new Function<Number, CharSequence>() {
      @Override
      public CharSequence apply(Number number) {
        return "computed-one";
      }
    });

    assertThat(valueHolder, is(nullValue())); // not computed
    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("put-one"));

    timeSource.advanceTime(10);

    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("put-one"));
  }

  @Test
  public void testPutThenFaultedWithGetElementDoesNotExpire() throws Exception {
    diskStore.put(1, "put-one");

    Store.ValueHolder<CharSequence> valueHolder = diskStore.getAndFault(1);

    assertThat(valueHolder.value(), Matchers.<CharSequence>equalTo("put-one"));
    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("put-one"));

    timeSource.advanceTime(10);

    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("put-one"));
  }

  @Test
  public void testComputedFaultedElementDoesNotExpire() throws Exception {
    Store.ValueHolder<CharSequence> valueHolder = diskStore.computeIfAbsentAndFault(1, new Function<Number, CharSequence>() {
      @Override
      public CharSequence apply(Number number) {
        return "computed-one";
      }
    });

    assertThat(valueHolder.value(), Matchers.<CharSequence>equalTo("computed-one")); // computed
    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("computed-one"));

    timeSource.advanceTime(10);

    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("computed-one"));
  }

  @Test
  public void testPutThenFaultedWithComputeElementCannotBeEvicted() throws Exception {
    diskStore.put(1, "put-one");

    Store.ValueHolder<CharSequence> valueHolder = diskStore.computeIfAbsentAndFault(1, new Function<Number, CharSequence>() {
      @Override
      public CharSequence apply(Number number) {
        return "computed-one";
      }
    });

    assertThat(valueHolder, is(nullValue())); // not computed
    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("put-one"));

    // DiskStore does not evict elements pending flush, hence we flush before requesting an eviction
    diskStore.flushToDisk();
    // fill the store enough to provoke an eviction
    for (int i = 0; i < capacityConstraint; i++) {
      diskStore.put(-i, "-" + i);
    }

    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("put-one"));
  }

  @Test
  public void testPutThenFaultedWithGetElementCannotBeEvicted() throws Exception {
    diskStore.put(1, "put-one");

    Store.ValueHolder<CharSequence> valueHolder = diskStore.getAndFault(1);

    assertThat(valueHolder.value(), Matchers.<CharSequence>equalTo("put-one"));
    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("put-one"));

    // DiskStore does not evict elements pending flush, hence we flush before requesting an eviction
    diskStore.flushToDisk();
    // fill the store enough to provoke an eviction
    for (int i = 0; i < capacityConstraint; i++) {
      diskStore.put(-i, "-" + i);
    }

    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("put-one"));
  }

  @Test
  public void testComputedFaultedElementCannotBeEvicted() throws Exception {
    Store.ValueHolder<CharSequence> valueHolder = diskStore.computeIfAbsentAndFault(1, new Function<Number, CharSequence>() {
      @Override
      public CharSequence apply(Number number) {
        return "computed-one";
      }
    });

    assertThat(valueHolder.value(), Matchers.<CharSequence>equalTo("computed-one")); // computed
    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("computed-one"));

    // DiskStore does not evict elements pending flush, hence we flush before requesting an eviction
    diskStore.flushToDisk();
    // fill the store enough to provoke an eviction
    for (int i = 0; i < capacityConstraint; i++) {
      diskStore.put(-i, "-" + i);
    }

    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("computed-one"));
  }

  @Test
  public void testFlushingMakesMappingEvictableAgain() throws Exception {
    Store.ValueHolder<CharSequence> valueHolder = diskStore.computeIfAbsentAndFault(1, new Function<Number, CharSequence>() {
      @Override
      public CharSequence apply(Number number) {
        return "computed-one";
      }
    });
    assertThat(valueHolder.value(), Matchers.<CharSequence>equalTo("computed-one"));

    Store.ValueHolder<CharSequence> heapValueHolder = mock(Store.ValueHolder.class);
    when(heapValueHolder.value()).thenReturn("computed-one");
    CachingTier<Number, CharSequence> cachingTier = mock(CachingTier.class);
    when(cachingTier.getExpireTimeMillis(same(heapValueHolder))).thenReturn(5L);

    assertThat(diskStore.flush(1, heapValueHolder, cachingTier), is(true));

    assertThat(diskStore.get(1), is(notNullValue()));

    // DiskStore does not evict elements pending flush, hence we flush before requesting an eviction
    diskStore.flushToDisk();
    // fill the store enough to provoke an eviction
    for (int i = 0; i < capacityConstraint; i++) {
      diskStore.put(-i, "-" + i);
    }

    assertThat(diskStore.get(1), is(nullValue()));
  }

  @Test
  public void testFlushingOfExpiredElementRemovesIt() throws Exception {
    Store.ValueHolder<CharSequence> valueHolder = diskStore.computeIfAbsentAndFault(1, new Function<Number, CharSequence>() {
      @Override
      public CharSequence apply(Number number) {
        return "computed-one";
      }
    });
    assertThat(valueHolder.value(), Matchers.<CharSequence>equalTo("computed-one"));

    Store.ValueHolder<CharSequence> heapValueHolder = mock(Store.ValueHolder.class);
    when(heapValueHolder.value()).thenReturn("computed-one");
    CachingTier<Number, CharSequence> cachingTier = mock(CachingTier.class);
    when(cachingTier.isExpired(same(heapValueHolder))).thenReturn(true);
    when(cachingTier.getExpireTimeMillis(same(heapValueHolder))).thenReturn(5L);

    timeSource.advanceTime(5L);

    assertThat(diskStore.flush(1, heapValueHolder, cachingTier), is(true));
    assertThat(diskStore.get(1), is(nullValue()));
  }

  @Test
  public void testFlushingOfModifiedMappingHasNoEffect() throws Exception {
    Store.ValueHolder<CharSequence> valueHolder = diskStore.computeIfAbsentAndFault(1, new Function<Number, CharSequence>() {
      @Override
      public CharSequence apply(Number number) {
        return "computed-one";
      }
    });
    assertThat(valueHolder.value(), Matchers.<CharSequence>equalTo("computed-one"));

    CachingTier<Number, CharSequence> cachingTier = mock(CachingTier.class);
    Store.ValueHolder<CharSequence> heapValueHolder = mock(Store.ValueHolder.class);
    when(cachingTier.getExpireTimeMillis(same(heapValueHolder))).thenReturn(5L);

    diskStore.put(1, "put-one");

    assertThat(diskStore.flush(1, heapValueHolder, cachingTier), is(false));
    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("put-one"));
  }

  @Test
  public void testFlushingUpdatesExpirationTime() throws Exception {
    Store.ValueHolder<CharSequence> valueHolder = diskStore.computeIfAbsentAndFault(1, new Function<Number, CharSequence>() {
      @Override
      public CharSequence apply(Number number) {
        return "computed-one";
      }
    });
    assertThat(valueHolder.value(), Matchers.<CharSequence>equalTo("computed-one"));

    CachingTier<Number, CharSequence> cachingTier = mock(CachingTier.class);
    Store.ValueHolder<CharSequence> heapValueHolder = mock(Store.ValueHolder.class);
    when(heapValueHolder.value()).thenReturn("computed-one");
    when(cachingTier.getExpireTimeMillis(same(heapValueHolder))).thenReturn(15L);

    timeSource.advanceTime(10);

    diskStore.flushToDisk();

    assertThat(diskStore.flush(1, heapValueHolder, cachingTier), is(true));
    assertThat(diskStore.get(1).value(), Matchers.<CharSequence>equalTo("computed-one"));
  }

  private static class TestTimeSource implements TimeSource {

    private long time = 1;

    @Override
    public long getTimeMillis() {
      return time;
    }

    private void advanceTime(long delta) {
      this.time += delta;
    }
  }
}
