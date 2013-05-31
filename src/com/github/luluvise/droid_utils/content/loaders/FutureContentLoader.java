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
package com.github.luluvise.droid_utils.content.loaders;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.annotation.concurrent.Immutable;

import android.util.Log;

import com.github.luluvise.droid_utils.DroidConfig;
import com.github.luluvise.droid_utils.cache.CacheMemoizer;
import com.github.luluvise.droid_utils.cache.ExpiringFutureTask;
import com.github.luluvise.droid_utils.cache.ModelDiskCache;
import com.github.luluvise.droid_utils.cache.ModelLruCache;
import com.github.luluvise.droid_utils.content.ContentProxy.ActionType;
import com.github.luluvise.droid_utils.content.loaders.ContentLoader.ContentUpdateCallback;
import com.github.luluvise.droid_utils.content.loaders.ContentLoader.RequestHandler;
import com.github.luluvise.droid_utils.json.model.JsonModel;
import com.github.luluvise.droid_utils.json.rest.AbstractModelRequest;
import com.github.luluvise.droid_utils.lib.network.ConnectionMonitor;
import com.github.luluvise.droid_utils.lib.network.ConnectionMonitorInterface;
import com.google.common.annotations.Beta;

/**
 * Generic implementation of {@link ContentLoader} that uses a slightly complex
 * version the Memoizer pattern (see {@link CacheMemoizer}) that allows handling
 * the different types of cache accesses specified by {@link ActionType}.
 * 
 * In order to allow this, the 100% reliability of the Memoizer in terms of
 * serial retrieval of a single item by multiple threads making concurrent
 * requests is sacrified in favor of a major flexibility.
 * 
 * All the actions on a cache, including {@link ActionType#PRE_FETCH}, are
 * blocking for now<br>
 * TODO: delegate pre-fetching to a separate executor and return to the caller
 * immediately TODO: create builder
 * 
 * In case the application {@link ConnectionMonitor} is registered and returns a
 * network connection fail, the set {@link ActionType} is turned into
 * {@link ActionType#CACHE_ONLY} to allow retrieving old data from the caches
 * (if any) by ignoring expiration.
 * 
 * @deprecated Use {@link ModelDiskContentLoader} instead
 * @param <R>
 *            Requests extending {@link AbstractModelRequest}
 * @param <M>
 *            Models extending {@link JsonModel}
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@Deprecated
@Immutable
public class FutureContentLoader<R extends AbstractModelRequest<M>, M extends JsonModel> {

	private static final String TAG = FutureContentLoader.class.getSimpleName();

	private static final ConnectionMonitorInterface CONN_MONITOR = ConnectionMonitor.get();

	private final ActionType mAction;
	private final AbstractModelRequest<M> mRequest;
	private final ModelLruCache<String, ExpiringFutureTask<M>> mMemCache;
	private final ModelDiskCache<M> mDiskCache;
	private final long mExpiration;
	private final ContentUpdateCallback<M> mUpdateCallback;
	private final RequestHandler mRequestHandler;

	/**
	 * Instantiates a new {@link FutureContentLoader}.
	 * 
	 * @param action
	 *            The {@link ActionType} to perform (can be null, in that case
	 *            {@link ActionType#NORMAL} will be executed
	 * @param request
	 *            The {@link AbstractModelRequest} to execute
	 * @param memCache
	 *            The {@link ModelLruCache} to use
	 * @param diskCache
	 *            The (optional) {@link ModelDiskCache} to use
	 * @param expiration
	 *            Expiration offset time to use for {@link ActionType}s that
	 *            require it
	 * @param callback
	 *            An optional {@link ContentUpdateCallback}
	 * @param requestHandler
	 *            The (optional) {@link RequestHandler} for this loader
	 */
	public FutureContentLoader(ActionType action, R request, ModelLruCache<String, ExpiringFutureTask<M>> memCache,
			ModelDiskCache<M> diskCache, long expiration, ContentUpdateCallback<M> callback,
			RequestHandler requestHandler) {
		// if network is not active, turn every action to CACHE_ONLY
		final boolean networkActive = CONN_MONITOR.isNetworkActive();
		mAction = (networkActive) ? ((action != null) ? action : ActionType.NORMAL) : ActionType.CACHE_ONLY;
		mRequest = request;
		mMemCache = memCache;
		mDiskCache = diskCache;
		mExpiration = expiration;
		mUpdateCallback = callback;
		mRequestHandler = requestHandler;
	}

	/**
	 * @see com.github.luluvise.droid_utils.content.loaders.ContentLoader#execute()
	 */
	public M execute() throws Exception {
		final String key = mRequest.hash();
		// we try to retrieve item from our task cache
		ExpiringFutureTask<M> future = mMemCache.get(key);
		/*
		 * we try to get a new task either if there is no previous one, if the
		 * action is REFRESH or the previous is expired and this is not a
		 * CACHE_ONLY action.
		 */
		if (future == null // not found
				|| (mAction == ActionType.REFRESH //
				|| (future.isExpired() && mAction != ActionType.CACHE_ONLY))) {

			/** cache debugging */
			if (DroidConfig.DEBUG) {
				final String url = mRequest.getRequestUrl();
				if (future == null) {
					Log.v(TAG, "Memory cache miss for " + url);
				} else if (mAction == ActionType.REFRESH) {
					Log.v(TAG, "Refreshing cache for " + url);
				} else if (future.isExpired() && mAction != ActionType.CACHE_ONLY) {
					Log.v(TAG, "Memory cache expired for " + url);
				}
			}
			/** cache debugging - END */

			if (mAction == ActionType.REFRESH) {
				// invalidate cache item if any and start new task
				mMemCache.remove(key, future);
			}

			final ExpiringFutureTask<M> newFutureTask = new ExpiringFutureTask<M>(new IOContentLoader(), mExpiration);
			future = mMemCache.putIfAbsent(key, newFutureTask);
			if (future == null) {
				// no tasks inserted in the meantime, execute it
				future = newFutureTask;
				newFutureTask.run();
			}
		}
		try {
			// wait for the task execution completion
			M model = future.get();
			if (model == null) {
				mMemCache.remove(key, future);
			}
			return model;
		} catch (CancellationException e) {
			mMemCache.remove(key, future);
		} catch (ExecutionException e) {
			// we don't want the cache to be polluted with failed attempts
			mMemCache.remove(key, future);
			throw CacheMemoizer.launderThrowable(e.getCause());
		}
		return null;
	}

	/**
	 * Callable that executes loading of data from disk or network
	 */
	private class IOContentLoader implements Callable<M> {

		@Override
		@SuppressWarnings("unchecked")
		public M call() throws Exception {

			final String key = mRequest.hash();
			M model = null;

			if (mDiskCache != null) {
				if (mAction == ActionType.CACHE_ONLY) {
					// do not take care of cache expiration
					model = mDiskCache.get(key);

					/** cache debugging */
					if (DroidConfig.DEBUG) {
						final String url = mRequest.getRequestUrl();
						if (model != null) {
							Log.v(TAG, "CACHE_ONLY: Disk cache hit for " + url);
						} else {
							Log.v(TAG, "CACHE_ONLY: Disk cache miss for " + url);
						}
					}
					/** cache debugging - END */

					// caches retrieval failed, don't attempt a request
					return model;
				} else if (mAction == ActionType.REFRESH) {
					mDiskCache.remove(key);
				} else {
					// check disk cache (and verify item expiration)
					if ((model = mDiskCache.get(key, mExpiration)) != null) {
						return model;
					} else {
						/** cache debugging */
						if (DroidConfig.DEBUG) {
							Log.v(TAG, "Disk cache miss or expired for " + mRequest.getRequestUrl());
						}
						/** cache debugging - END */
					}
				}
			}

			// execute GET request to the server
			// cast is safe here, we're using LuluviseModel subclasses
			if (mRequestHandler != null) {
				model = (M) mRequestHandler.execRequest(mRequest);
			} else {
				model = mRequest.execute();
			}
			if (model != null) { // update caches
				if (mDiskCache != null) {
					mDiskCache.put(key, model);
				}
				if (mUpdateCallback != null) {
					mUpdateCallback.onContentUpdated(model);
				}
			} else {
				// TODO: attempt retrieving stale data from disk cache?
			}
			// FIXME: we should throw an exception rather than returning null
			return model;
		}
	}

}