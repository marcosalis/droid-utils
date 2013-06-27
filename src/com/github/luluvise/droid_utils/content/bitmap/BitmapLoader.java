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

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.github.luluvise.droid_utils.DroidConfig;
import com.github.luluvise.droid_utils.cache.CacheMemoizer;
import com.github.luluvise.droid_utils.cache.bitmap.BitmapDiskCache;
import com.github.luluvise.droid_utils.cache.bitmap.BitmapLruCache;
import com.github.luluvise.droid_utils.cache.keys.CacheUrlKey;
import com.github.luluvise.droid_utils.content.ContentProxy.ActionType;
import com.github.luluvise.droid_utils.content.bitmap.BitmapAsyncSetter.BitmapSource;
import com.github.luluvise.droid_utils.lib.network.ByteArrayDownloader;
import com.github.luluvise.droid_utils.logging.LogUtils;
import com.google.common.annotations.Beta;

/**
 * General loader for a {@link Bitmap} from a {@link BitmapProxy}.<br>
 * If the mode set for the request is {@link ActionType#PRE_FETCH}, the
 * retrieved image is only downloaded and put in the memory cache if necessary.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@Immutable
class BitmapLoader implements Callable<Bitmap> {

	private static final String TAG = BitmapLoader.class.getSimpleName();

	private final CacheMemoizer<String, Bitmap> mDownloadsCache;
	private final BitmapLruCache<String> mCache;
	private final BitmapDiskCache mDiskCache;
	private final CacheUrlKey mUrl;
	private final BitmapAsyncSetter mBitmapCallback;

	/**
	 * Instantiates a {@link BitmapLoader}.
	 * 
	 * @param downloads
	 *            The {@link CacheMemoizer} used to retrieve cached items
	 * @param cache
	 *            The {@link BitmapLruCache} where bitmaps in memory are stored
	 * @param diskCache
	 *            The (optional) {@link BitmapDiskCache} where bitmaps saved on
	 *            disk are handled
	 * @param key
	 *            The {@link CacheUrlKey} to retrieve the bitmap
	 * @param callback
	 *            {@link BitmapAsyncSetter} for the image (can be null)
	 */
	public BitmapLoader(@Nonnull CacheMemoizer<String, Bitmap> downloads,
			@Nonnull BitmapLruCache<String> cache, @Nullable BitmapDiskCache diskCache,
			@Nonnull CacheUrlKey key, @Nullable BitmapAsyncSetter callback) {
		mDownloadsCache = downloads;
		mCache = cache;
		mDiskCache = diskCache;
		mUrl = key;
		mBitmapCallback = callback;
	}

	@Override
	@CheckForNull
	public Bitmap call() {
		final String key = mUrl.hash();
		Bitmap bitmap;

		// 1- check memory cache again
		if ((bitmap = mCache.get(key)) != null) { // memory cache hit
			if (mBitmapCallback != null) {
				mBitmapCallback.onBitmapReceived(mUrl, bitmap, BitmapSource.MEMORY);
			}
			return bitmap;
		}

		// 2- check disk cache
		if (mDiskCache != null) {
			if ((bitmap = mDiskCache.get(key)) != null) {
				// disk cache hit, load file into Bitmap
				if (mBitmapCallback != null) {
					mBitmapCallback.onBitmapReceived(mUrl, bitmap, BitmapSource.DISK);
				} // and put it into memory cache
				mCache.put(key, bitmap);
				return bitmap;
			}
		}
		/*
		 * 3- Memory and disk cache miss, execute GET request to retrieve image.
		 * 
		 * We delegate the task to another, separated executor to download
		 * images to avoid blocking delivery of cached images to the UI
		 */
		final MemoizerCallable memoizer = new MemoizerCallable(mDownloadsCache, mCache, mDiskCache,
				mUrl, mBitmapCallback);
		// attempt prioritizing the download task if already in queue
		BitmapProxy.moveDownloadToFront(key);
		// submit new memoizer task to downloder executor
		BitmapProxy.submitInDownloader(key, memoizer);

		return null;
	}

	/**
	 * Memoizer-related task. It uses the {@link CacheMemoizer} item to check
	 * whether there is another running and unfinished task for the Bitmap we
	 * want to download from the server.
	 * 
	 * This computation gets executed with the
	 * {@link BitmapProxy#submitInDownloader(Callable)} method.
	 */
	@Immutable
	private static class MemoizerCallable implements Callable<Bitmap> {

		private final CacheMemoizer<String, Bitmap> mDownloadsCache;
		private final BitmapLruCache<String> mCache;
		private final BitmapDiskCache mDiskCache;
		private final CacheUrlKey mUrl;
		private final BitmapAsyncSetter mBitmapCallback;

		public MemoizerCallable(CacheMemoizer<String, Bitmap> downloads,
				BitmapLruCache<String> cache, BitmapDiskCache diskCache, CacheUrlKey url,
				BitmapAsyncSetter callback) {
			mDownloadsCache = downloads;
			mCache = cache;
			mDiskCache = diskCache;
			mUrl = url;
			mBitmapCallback = callback;
		}

		@Override
		@CheckForNull
		public Bitmap call() throws InterruptedException {
			try {
				final DownloaderCallable downloader = new DownloaderCallable(mCache, mDiskCache,
						mUrl, mBitmapCallback);
				final Bitmap bitmap = mDownloadsCache.execute(mUrl.hash(), downloader);
				if (mBitmapCallback != null) {
					mBitmapCallback.onBitmapReceived(mUrl, bitmap, BitmapSource.NETWORK);
				}
				return bitmap;
			} catch (InterruptedException e) {
				throw e;
			} catch (Exception e) {
				LogUtils.logException(e);
				return null; // something unexpected happened, can do nothing
			}
		}
	}

	/**
	 * Bitmap cache task that effectively downloads a Bitmap from the network
	 * when needed. When debugging is active, it also handles some
	 * instrumentation and logs some statistics about the download.
	 */
	@Immutable
	private static class DownloaderCallable implements Callable<Bitmap> {

		// for logging purposes only
		private static final Set<String> downloaderStats = Collections
				.synchronizedSet(new HashSet<String>());
		static final AtomicLong downloaderTimer = new AtomicLong();
		static final AtomicInteger downloaderCounter = new AtomicInteger();

		private final BitmapLruCache<String> mCache;
		private final BitmapDiskCache mDiskCache;
		private final CacheUrlKey mKey;
		private final BitmapAsyncSetter mBitmapCallback;

		public DownloaderCallable(BitmapLruCache<String> cache, BitmapDiskCache diskCache,
				CacheUrlKey url, BitmapAsyncSetter callback) {
			mCache = cache;
			mDiskCache = diskCache;
			mKey = url;
			mBitmapCallback = callback;
		}

		@Override
		@CheckForNull
		public Bitmap call() throws IOException {
			final String key = mKey.hash();
			final String url = mKey.getUrl();
			Bitmap bitmap = null;

			long startDownload = 0;
			if (DroidConfig.DEBUG) {
				startDownload = System.currentTimeMillis();
			}

			final byte[] imageBytes = ByteArrayDownloader.downloadByteArray(url);

			long endDownload = 0;
			long endDecoding = 0;
			if (DroidConfig.DEBUG) {
				endDownload = System.currentTimeMillis();
			}
			if (imageBytes != null) { // download successful
				bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
				if (bitmap != null) { // decoding successful
					if (DroidConfig.DEBUG) {
						endDecoding = System.currentTimeMillis();
					}
					mCache.put(key, bitmap);
					if (mBitmapCallback != null) {
						mBitmapCallback.onBitmapReceived(mKey, bitmap, BitmapSource.NETWORK);
					}
					if (mDiskCache != null) {
						mDiskCache.put(key, imageBytes);
					}
				}
			}
			if (DroidConfig.DEBUG) { // logging download statistics
				if (bitmap != null) {
					final long downloadTime = endDownload - startDownload;
					downloaderTimer.addAndGet(downloadTime);
					downloaderCounter.incrementAndGet();
					Log.d(TAG, key + " download took ms " + downloadTime);
					Log.v(TAG, key + " decoding took ms " + (endDecoding - endDownload));
					if (!downloaderStats.add(key)) {
						// bitmap was already downloaded!
						Log.w(TAG, "Downloading bitmap twice: " + url);
					}
				}
			}
			return bitmap;
		}
	}

	// for debugging purposes only
	static void clearStatsLog() {
		final AtomicInteger counter = DownloaderCallable.downloaderCounter;
		final AtomicLong timer = DownloaderCallable.downloaderTimer;
		final long averageMs = timer.get() / counter.get();
		Log.i(TAG, counter.get() + " bitmaps downloaded in average ms " + averageMs);
		DownloaderCallable.downloaderStats.clear();
		timer.set(0);
		counter.set(0);
	}

}