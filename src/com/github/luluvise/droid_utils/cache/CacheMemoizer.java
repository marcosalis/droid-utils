/*
 * Copyright 2013 Luluvise Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.luluvise.droid_utils.cache;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import com.google.common.annotations.Beta;
import com.google.common.cache.LoadingCache;

/**
 * General purpose implementation of the Memoizer pattern to avoid repeated
 * "computation" (or retrieval) of a cache entry if a content resource is
 * requested more then once and the first attempt is not completed yet.
 * 
 * Taken and edited from the book "Concurrency in practice", Brian Goetz.
 * 
 * TODO: this pattern is already used by the {@link LoadingCache} class in
 * Guava. Use its methods for a more realiable loading cache WHEN the Guava 13
 * libraries will be backported for Java 5.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@ThreadSafe
public class CacheMemoizer<K, V> {

	protected static final int INIT_CACHE_SIZE = 16;

	/**
	 * This map contains already executed, or in execution tasks from where the
	 * memoizer will try to retrieve, concurrently, the cache item before
	 * running a new {@link Callable}.
	 */
	private final ConcurrentMap<K, Future<V>> mTaskCache;

	/**
	 * Create a new {@link CacheMemoizer}
	 * 
	 * @param concurrencyLevel
	 *            The expected concurrency level of the underlying cache
	 */
	public CacheMemoizer(@Nonnegative int concurrencyLevel) {
		mTaskCache = new ConcurrentHashMap<K, Future<V>>(INIT_CACHE_SIZE, 0.75f, concurrencyLevel);
	}

	/**
	 * Calls {@link ConcurrentMap#remove(Object)} on the internal cache, for
	 * example to remove an item if it gets evicted from an LRU cache.
	 * 
	 * @param key
	 *            The cache key
	 * @return The {@link Future} associated with the key, if any
	 */
	@CheckForNull
	public final Future<V> remove(@Nonnull K key) {
		return mTaskCache.remove(key);
	}

	/**
	 * Clears the memoizer's internal futures cache.
	 */
	public final void clear() {
		mTaskCache.clear();
	}

	/**
	 * Retrieves an item, reliably checking if the item associated with the
	 * {@code key} is being retrieved from another task. If so, it waits for the
	 * existing task to be completed, otherwise it starts the passed task.
	 * 
	 * @param key
	 *            The item key
	 * @param task
	 *            The {@link Callable} to be used if no other computations are
	 *            already in progress
	 * @return The computed/retrieved object, or null if it could not be
	 *         retrieved
	 * @throws Exception
	 *             if the {@link Callable} threw an exception
	 */
	@CheckForNull
	public V execute(@Nonnull final K key, @Nonnull Callable<V> task) throws Exception {
		// we try to retrieve item from our task cache
		Future<V> future = mTaskCache.get(key);
		if (future == null) { // no task found
			FutureTask<V> newFutureTask = new FutureTask<V>(task);
			future = mTaskCache.putIfAbsent(key, newFutureTask);
			if (future == null) {
				// no tasks inserted in the meantime, execute it
				future = newFutureTask;
				newFutureTask.run();
			}
		}
		try {
			// wait for the task execution completion
			return future.get();
		} catch (CancellationException e) {
			// remove canceled item from the cache
			mTaskCache.remove(key, future);
		} catch (ExecutionException e) {
			// we don't want the cache to be poisoned with failed attempts
			mTaskCache.remove(key, future);
			throw launderThrowable(e.getCause());
		}
		return null;
	}

	/**
	 * Coerce an unchecked Throwable to a RuntimeException
	 * 
	 * If the Throwable is an Error, throw it; if it is a RuntimeException
	 * return it, otherwise throw IllegalStateException
	 * 
	 * @author Brian Goetz and Tim Peierls
	 */
	@Nonnull
	public static Exception launderThrowable(@Nonnull Throwable t) {
		if (t instanceof RuntimeException) {
			return (RuntimeException) t;
		} else if (t instanceof Exception) {
			return (Exception) t;
		} else if (t instanceof Error) {
			throw (Error) t;
		} else {
			// TODO: check this
			throw new IllegalStateException("Not unchecked", t);
		}
	}

}