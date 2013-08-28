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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.util.Log;
import android.widget.ImageView;

import com.github.luluvise.droid_utils.DroidConfig;
import com.github.luluvise.droid_utils.annotations.NotForUIThread;
import com.github.luluvise.droid_utils.cache.CacheMemoizer;
import com.github.luluvise.droid_utils.cache.ContentCache.OnEntryRemovedListener;
import com.github.luluvise.droid_utils.cache.bitmap.BitmapDiskCache;
import com.github.luluvise.droid_utils.cache.bitmap.BitmapLruCache;
import com.github.luluvise.droid_utils.cache.keys.CacheUrlKey;
import com.github.luluvise.droid_utils.concurrent.PriorityThreadFactory;
import com.github.luluvise.droid_utils.concurrent.ReorderingThreadPoolExecutor;
import com.github.luluvise.droid_utils.content.AbstractContentProxy;
import com.github.luluvise.droid_utils.lib.DroidUtils;
import com.google.common.annotations.Beta;

/**
 * Abstract base class for a {@link Bitmap} content proxy. Every
 * {@link BitmapProxy} subclass can hold one or more (in order to be able to
 * fine-tune the size of each of them) memory caches and a disk cache, which are
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
public abstract class BitmapProxy extends AbstractContentProxy implements
		OnEntryRemovedListener<String, Bitmap> {

	static {
		// here we query memory and disk caches and set bitmaps into views
		final int executorSize = DroidUtils.getCpuBoundPoolSize() + 1;
		// here we either execute a network request or we wait for it
		final int dwExecutorSize = DroidUtils.getIOBoundPoolSize();

		// prepare bitmaps main executor
		final LinkedBlockingQueue<Runnable> executorQueue = new LinkedBlockingQueue<Runnable>();
		final PriorityThreadFactory executorFactory = new PriorityThreadFactory(
				"BitmapProxy executor thread", Process.THREAD_PRIORITY_BACKGROUND);
		final ThreadPoolExecutor bitmapExecutor = new ThreadPoolExecutor(executorSize,
				executorSize, 0L, TimeUnit.MILLISECONDS, executorQueue, executorFactory);
		BITMAP_EXECUTOR = bitmapExecutor;

		// prepare bitmaps downloader executor
		final BlockingQueue<Runnable> downloaderQueue = ReorderingThreadPoolExecutor
				.createBlockingQueue();
		final PriorityThreadFactory downloaderFactory = new PriorityThreadFactory(
				"BitmapProxy downloader executor thread", Process.THREAD_PRIORITY_DEFAULT);
		final ReorderingThreadPoolExecutor<String> downloaderExecutor = new ReorderingThreadPoolExecutor<String>(
				dwExecutorSize, dwExecutorSize, 0L, TimeUnit.MILLISECONDS, downloaderQueue,
				downloaderFactory);
		DOWNLOADER_EXECUTOR = downloaderExecutor;

		BITMAP_EXECUTOR_Q = executorQueue;
		DOWNLOADER_EXECUTOR_Q = downloaderQueue;

		DOWNLOAD_FUTURES = new CacheMemoizer<String, Bitmap>(dwExecutorSize);
	}

	private static final String TAG = BitmapProxy.class.getSimpleName();

	private static final ThreadPoolExecutor BITMAP_EXECUTOR;
	private static final ReorderingThreadPoolExecutor<String> DOWNLOADER_EXECUTOR;

	/* private executors blocking queues */
	private static final LinkedBlockingQueue<Runnable> BITMAP_EXECUTOR_Q;
	private static final BlockingQueue<Runnable> DOWNLOADER_EXECUTOR_Q;

	/**
	 * {@link CacheMemoizer} used for loading Bitmaps from the cache.
	 */
	private static final CacheMemoizer<String, Bitmap> DOWNLOAD_FUTURES;

	/**
	 * Executes a runnable task in the bitmap downloader thread pool.
	 * 
	 * @param runnable
	 *            The {@link Runnable} to execute (must be non null)
	 */
	public static synchronized final void executeInDownloader(@Nonnull Runnable runnable) {
		DOWNLOADER_EXECUTOR.execute(runnable);
	}

	/**
	 * Executes a callable task in the bitmap downloader thread pool.
	 * 
	 * @param callable
	 *            The {@link Callable} to execute (must be non null)
	 */
	public static synchronized final Future<Bitmap> submitInDownloader(
			@Nonnull Callable<Bitmap> callable) {
		return DOWNLOADER_EXECUTOR.submit(callable);
	}

	/**
	 * Executes a callable task in the bitmap downloader thread pool.
	 * 
	 * @param key
	 *            The key to associate with the submitted task
	 * @param callable
	 *            The {@link Callable} to execute (must be non null)
	 */
	public static synchronized final Future<Bitmap> submitInDownloader(@Nonnull String key,
			@Nonnull Callable<Bitmap> callable) {
		return DOWNLOADER_EXECUTOR.submitWithKey(key, callable);
	}

	/**
	 * Attempts to prioritize a bitmap download by moving to the top of the
	 * executor queue the task with the passed key, if it exists.
	 * 
	 * @param key
	 *            The string key corresponding to the bitmap
	 */
	@NotForUIThread
	public static synchronized final void moveDownloadToFront(@Nonnull String key) {
		DOWNLOADER_EXECUTOR.moveToFront(key);
	}

	/**
	 * Remove all not-running tasks from all static bitmap executors.
	 */
	public static synchronized final void clearBitmapExecutors() {
		BITMAP_EXECUTOR_Q.clear();
		DOWNLOADER_EXECUTOR_Q.clear();
		DOWNLOADER_EXECUTOR.clearKeysMap();

		if (DroidConfig.DEBUG) {
			BitmapLoader.clearStatsLog();
			Log.d(TAG, "Bitmap executors tasks cleared");
			Log.v(TAG, "Bitmap executor tasks: " + BITMAP_EXECUTOR.getTaskCount() + ", completed: "
					+ BITMAP_EXECUTOR.getCompletedTaskCount());
			Log.v(TAG, "Bitmap downloader executor tasks: " + DOWNLOADER_EXECUTOR.getTaskCount()
					+ ", completed: " + DOWNLOADER_EXECUTOR.getCompletedTaskCount());
		}
	}

	@Override
	public void onEntryRemoved(boolean evicted, String key, Bitmap value) {
		// remove evicted bitmaps from the downloads memoizer to allow GC
		DOWNLOAD_FUTURES.remove(key);
	}

	/**
	 * Method to be called by subclasses to get a bitmap content from any
	 * {@link BitmapProxy} by passing the main request parameters and type of
	 * actions.
	 * 
	 * <b>This needs to be called from the UI thread except when using
	 * {@link ActionType#PRE_FETCH} mode</b>, as the image setting is
	 * asynchronous except in the case we already have the image available in
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
	 * @param setter
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
			@Nullable ActionType action, BitmapAsyncSetter setter,
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
				setter.setBitmapSync(url, bitmap);
			}
			return null; // no Future to return
		} else {
			if (!preFetch) {
				// set temporary placeholder if we are not just pre-fetching
				if (placeholder != null) {
					setter.setPlaceholderSync(placeholder);
				}
			} else {
				setter = null; // make sure there's no callback
			}
			return BITMAP_EXECUTOR.submit(new BitmapLoader(DOWNLOAD_FUTURES, cache, diskCache, url,
					setter));
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