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
package com.github.luluvise.droid_utils.content.bitmap;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.github.luluvise.droid_utils.cache.CacheMemoizer;
import com.github.luluvise.droid_utils.cache.bitmap.BitmapDiskCache;
import com.github.luluvise.droid_utils.cache.bitmap.BitmapLruCache;
import com.github.luluvise.droid_utils.cache.keys.CacheUrlKey;
import com.github.luluvise.droid_utils.content.AbstractContentProxy;
import com.github.luluvise.droid_utils.lib.DroidUtils;
import com.github.luluvise.droid_utils.logging.LoggedThreadFactory;
import com.google.common.annotations.Beta;

/**
 * Abstract base class for a {@link Bitmap} content proxy. Every
 * {@link BitmapProxy} subclass owns one or more memory caches (in order to be
 * able to fine-tune the size of each of them) and a disk cache, which are
 * managed separately.
 * 
 * Every {@link BitmapProxy} shares the same executors, one for querying the
 * cache for a Bitmap (memory and disk I/O can be required) and another for
 * executing image download requests without preventing the other cache requests
 * to block. The executor maximum thread pool size varies depending on the
 * number of CPU cores in the device.
 * 
 * The only {@link ActionType}s allowed for Bitmap content proxies are:<br>
 * {@link ActionType#NORMAL}, {@link ActionType#PRE_FETCH}
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@Immutable
public abstract class BitmapProxy extends AbstractContentProxy {

	static {
		// here we query memory and disk caches and set bitmaps into views
		final int executorSize = DroidUtils.getCpuBoundPoolSize() + 1;
		// here we either execute a network request or we wait for it
		final int dwExecutorSize = DroidUtils.getIOBoundPoolSize();

		BITMAP_EXECUTOR = Executors.unconfigurableExecutorService(Executors.newFixedThreadPool(
				executorSize, new LoggedThreadFactory("BitmapProxy executor thread")));
		DOWNLOADER_EXECUTOR = Executors.unconfigurableExecutorService(Executors.newFixedThreadPool(
				dwExecutorSize, new LoggedThreadFactory("BitmapProxy downloader executor thread")));
		DOWNLOAD_FUTURES = new CacheMemoizer<String, Bitmap>(dwExecutorSize);
	}

	private static final ExecutorService BITMAP_EXECUTOR;

	private static final ExecutorService DOWNLOADER_EXECUTOR;

	/**
	 * Gets the {@link Executor} to be used by subclasses.
	 */
	@Nonnull
	protected static final ExecutorService getExecutor() {
		return BITMAP_EXECUTOR;
	}

	/**
	 * Gets the Executor to be used for downloading bitmaps
	 */
	@Nonnull
	public static final ExecutorService getDownloaderExecutor() {
		return DOWNLOADER_EXECUTOR;
	}

	/**
	 * {@link CacheMemoizer} used for loading Bitmaps from the cache.
	 */
	protected static final CacheMemoizer<String, Bitmap> DOWNLOAD_FUTURES;

	/**
	 * Method to be called by subclasses to get a bitmap content from any
	 * BitmapProxy by passing the main request parameters and type of actions.
	 * 
	 * <b>This needs to be called from the UI thread</b>, as the image setting
	 * is asynchronous except in the case we already have the image available in
	 * the memory cache.
	 * 
	 * @param cache
	 *            The {@link BitmapLruCache} memory cache to use
	 * @param diskCache
	 *            The {@link BitmapDiskCache} to use
	 * @param url
	 *            The {@link CacheUrlKey} of the image to retrieve
	 * @param action
	 *            The {@link ActionType} to perform, one of
	 *            {@link ActionType#NORMAL} or {@link ActionType#PRE_FETCH}
	 * @param callback
	 *            The {@link BitmapAsyncSetter} to set the bitmap in an
	 *            {@link ImageView}. Can be null with
	 *            {@link ActionType#PRE_FETCH}
	 * @param placeholder
	 *            An (optional) {@link Drawable} temporary placeholder, only set
	 *            if the bitmap is not in the memory cache
	 * @return The {@link Future} that holds the Bitmap loading
	 */
	@CheckForNull
	protected Future<Bitmap> getBitmap(@Nonnull BitmapLruCache<String> cache,
			@Nullable BitmapDiskCache diskCache, @Nonnull CacheUrlKey url,
			@Nullable ActionType action, BitmapAsyncSetter callback,
			@CheckForNull Drawable placeholder) {
		final boolean preFetch = (action == ActionType.PRE_FETCH);
		Bitmap bitmap;

		if ((bitmap = cache.get(url.hash())) != null) {
			// cache hit at the very first attempt, no other actions needed
			if (!preFetch) { // set Bitmap if we are not just pre-fetching
				/*
				 * This is supposed to be called from the UI thread and be
				 * synchronous.
				 */
				callback.setBitmapSync(bitmap);
			}
			return null; // no Future to return
		} else {
			if (!preFetch) {
				// set temporary placeholder if we are not just pre-fetching
				if (placeholder != null) {
					callback.setPlaceholderSync(placeholder);
				}
			} else {
				callback = null; // make sure there's no callback
			}
			return BITMAP_EXECUTOR.submit(new BitmapLoader(DOWNLOAD_FUTURES, cache, diskCache, url,
					callback));
		}
	}

	/**
	 * See
	 * {@link #getBitmap(BitmapLruCache, BitmapDiskCache, CacheUrlKey, ActionType, BitmapAsyncSetter, Drawable)}
	 * with null placeholder.
	 */
	@CheckForNull
	protected Future<Bitmap> getBitmap(BitmapLruCache<String> cache, BitmapDiskCache diskCache,
			CacheUrlKey url, ActionType action, BitmapAsyncSetter callback) {
		return getBitmap(cache, diskCache, url, action, callback, null);
	}

}
