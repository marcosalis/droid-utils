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
import java.util.concurrent.Callable;

import javax.annotation.CheckForNull;
import javax.annotation.concurrent.Immutable;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.github.luluvise.droid_utils.cache.CacheMemoizer;
import com.github.luluvise.droid_utils.cache.bitmap.BitmapDiskCache;
import com.github.luluvise.droid_utils.cache.bitmap.BitmapLruCache;
import com.github.luluvise.droid_utils.cache.keys.CacheUrlKey;
import com.github.luluvise.droid_utils.content.ContentProxy.ActionType;
import com.github.luluvise.droid_utils.content.bitmap.BitmapAsyncSetter.BitmapSource;
import com.github.luluvise.droid_utils.logging.LogUtils;
import com.github.luluvise.droid_utils.network.ByteArrayDownloader;
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

	@SuppressWarnings("unused")
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
	 * @param cache
	 * @param diskCache
	 * @param url
	 * @param callback
	 *            {@link BitmapAsyncSetter} for the image (can be null)
	 */
	public BitmapLoader(CacheMemoizer<String, Bitmap> downloads, BitmapLruCache<String> cache,
			BitmapDiskCache diskCache, CacheUrlKey url, BitmapAsyncSetter callback) {
		mDownloadsCache = downloads;
		mCache = cache;
		mDiskCache = diskCache;
		mUrl = url;
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
		BitmapProxy.getDownloaderExecutor().submit(memoizer);

		return null;
	}

	/**
	 * Memoizer-related task. It uses the {@link CacheMemoizer} item to check
	 * whether there is another running and unfinished task for the Bitmap we
	 * want to download from the server.
	 * 
	 * This computation gets executed within the
	 * {@link BitmapProxy#getDownloaderExecutor()} executor.
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
	 * when needed.
	 */
	@Immutable
	private static class DownloaderCallable implements Callable<Bitmap> {

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
			Bitmap bitmap = null;
			byte[] imageBytes;
			imageBytes = ByteArrayDownloader.downloadByteArray(mKey.getUrl());
			if (imageBytes != null) { // download successful
				bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
				if (bitmap != null) { // decoding successful
					mCache.put(key, bitmap);
					if (mBitmapCallback != null) {
						mBitmapCallback.onBitmapReceived(mKey, bitmap, BitmapSource.NETWORK);
					}
					if (mDiskCache != null) {
						mDiskCache.put(key, imageBytes);
					}
				}
			}
			return bitmap;
		}
	}

}