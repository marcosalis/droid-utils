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
package com.github.luluvise.droid_utils.cache.bitmap;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.concurrent.ThreadSafe;

import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.support.v4.util.LruCache;
import android.util.Log;

import com.github.luluvise.droid_utils.DroidConfig;
import com.github.luluvise.droid_utils.cache.CacheMemoizer;
import com.github.luluvise.droid_utils.cache.ContentCache;
import com.github.luluvise.droid_utils.lib.BitmapUtils;
import com.google.common.annotations.Beta;
import com.google.common.cache.Cache;

/**
 * Abstract class that exposes basic functionalities implemented in any
 * hard-referenced Bitmap cache. The size of the cache is strictly limited by
 * the memory occupation of the contained Bitmaps to avoid OutOfMemoryError.
 * 
 * The actual cache is implemented on top of an {@code LruCache<K, Bitmap>}
 * instance.
 * 
 * TODO: use Guava's {@link Cache} instead? It's really concurrent (backed by a
 * ConcurrentHashMap implementation, whereas LruCache operations block on the
 * whole structure) and automatically handles blocking load with Future's get().
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@ThreadSafe
public class BitmapLruCache<K> extends LruCache<K, Bitmap> implements ContentCache<K, Bitmap> {

	private static final String TAG = BitmapLruCache.class.getSimpleName();

	private final String mName;
	private final ConcurrentMap<K, Future<Bitmap>> mDownloadsCache;

	/**
	 * Constructor for a BitmapCache.<br>
	 * Call {@link ActivityManager#getMemoryClass()} to properly size this cache
	 * depending on the maximum available application memory heap.
	 * 
	 * @param cacheName
	 *            The (optional) name of the cache (for logging purposes)
	 * @param maxSize
	 *            The max memory occupation, in bytes, that the cache will ever
	 *            occupy when full.
	 * @param downloadsCache
	 *            An (optional) {@link ConcurrentMap} that uses the same keys as
	 *            this {@link BitmapLruCache} to allow Bitmap entries to be
	 *            removed when using a {@link CacheMemoizer} to populate the
	 *            cache.
	 */
	public BitmapLruCache(@Nullable String cacheName, @Nonnegative int maxSize,
			@Nullable ConcurrentMap<K, Future<Bitmap>> downloadsCache) {
		super(maxSize);
		mName = cacheName;
		mDownloadsCache = downloadsCache;
		if (DroidConfig.DEBUG) {
			Log.i(TAG, mName + ": max cache size is set to " + maxSize + " bytes");
		}
	}

	/**
	 * The cache items size is measured in terms of the Bitmap's size in bytes
	 * (see {@link BitmapUtils#getSize(Bitmap)}.
	 * 
	 * {@inheritDoc}
	 */
	@Override
	protected final int sizeOf(K key, @Nonnull Bitmap bitmap) {
		return BitmapUtils.getSize(bitmap);
	}

	/**
	 * If the specified key is not already associated with a value, associate it
	 * with the given value.
	 * 
	 * <b>Note:</b> this method doesn't respect the LRU policy, as an already
	 * inserted element isn't put at the top of the queue as the
	 * {@link LruCache#put(Object, Object)} method would do.
	 * 
	 * @param key
	 *            key with which the specified value is to be associated
	 * @param value
	 *            value to be associated with the specified key
	 * @return the previous value associated with the specified key, or null if
	 *         there was no mapping for the key. (A null return can also
	 *         indicate that the map previously associated null with the key, if
	 *         the implementation supports null values.)
	 */
	public synchronized Bitmap putIfAbsent(K key, Bitmap value) {
		Bitmap old = null;
		if ((old = get(key)) == null) {
			return put(key, value);
		} else {
			return old;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		if (DroidConfig.DEBUG) {
			Log.i(TAG, mName + " session stats: hits " + hitCount() + ", miss " + missCount());
		}
		evictAll();
	}

	/**
	 * Do NOT EVER attempt to recycle a Bitmap here, it still could be actively
	 * used in layouts or drawables even if evicted from the cache.
	 * 
	 * {@inheritDoc}
	 */
	@Override
	@OverridingMethodsMustInvokeSuper
	protected void entryRemoved(boolean evicted, K key, Bitmap oldValue, Bitmap newValue) {
		super.entryRemoved(evicted, key, oldValue, newValue);
		// remove evicted Bitmap task from the downloads cache if exists
		if (mDownloadsCache != null) {
			mDownloadsCache.remove(key);
		}

		if (DroidConfig.DEBUG) {
			if (oldValue != null && newValue != null) {
				Log.w(TAG, mName + ": item " + key + " replaced: this should never happen!");
			}
			Log.v(TAG, mName + ": item removed, cache size is now " + size() + " bytes");
		}
	}

}