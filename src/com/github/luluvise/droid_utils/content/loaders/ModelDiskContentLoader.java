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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import android.util.Log;

import com.github.luluvise.droid_utils.DroidConfig;
import com.github.luluvise.droid_utils.cache.CacheMemoizer;
import com.github.luluvise.droid_utils.cache.ExpiringFutureTask;
import com.github.luluvise.droid_utils.cache.ModelDiskCache;
import com.github.luluvise.droid_utils.cache.ModelLruCache;
import com.github.luluvise.droid_utils.content.ContentProxy.ActionType;
import com.github.luluvise.droid_utils.json.model.JsonModel;
import com.github.luluvise.droid_utils.json.rest.AbstractModelRequest;
import com.github.luluvise.droid_utils.lib.network.ConnectionMonitor;
import com.github.luluvise.droid_utils.lib.network.ConnectionMonitorInterface;
import com.google.common.annotations.Beta;

/**
 * 2-level cache (memory-disk) implementation of {@link ContentLoader} that uses
 * a slightly complex version the Memoizer pattern (see {@link CacheMemoizer})
 * that allows handling the different types of cache accesses specified by
 * {@link ActionType}.
 * 
 * In order to allow this, the 100% reliability of the Memoizer in terms of
 * serial retrieval of a single item by multiple threads making concurrent
 * requests is sacrified in favor of a major flexibility.
 * 
 * In case the application {@link ConnectionMonitor} is registered and returns a
 * network connection fail, the set {@link ActionType} is turned into
 * {@link ActionType#CACHE_ONLY} to allow retrieving old data from the caches
 * (if any) by ignoring expiration.
 * 
 * All the actions on a cache, including {@link ActionType#PRE_FETCH}, are
 * blocking for now<br>
 * TODO: delegate pre-fetching to a separate executor and return to the caller
 * immediately<br>
 * 
 * @param <R>
 *            Requests extending {@link AbstractModelRequest}
 * @param <M>
 *            Models extending {@link JsonModel}
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@ThreadSafe
public final class ModelDiskContentLoader<M extends JsonModel> implements
		ContentLoader<AbstractModelRequest<M>, M> {

	private static final String TAG = ModelDiskContentLoader.class.getSimpleName();

	private final ModelLruCache<String, ExpiringFutureTask<M>> mMemCache;
	@Nullable
	private final ModelDiskCache<M> mDiskCache;
	private final long mExpiration;
	@Nullable
	private final RequestHandler mRequestHandler;
	@Nullable
	private final ConnectionMonitorInterface mConnMonitor;

	/**
	 * Instantiates a new {@link ModelDiskContentLoader}.
	 * 
	 * @param memCache
	 *            The {@link ModelLruCache} to use
	 * @param diskCache
	 *            The (optional) {@link ModelDiskCache} to use
	 * @param expiration
	 *            Expiration offset time to use for {@link ActionType}s that
	 *            require it
	 * @param requestHandler
	 *            The (optional) {@link RequestHandler} for this loader
	 * @param connMonitor
	 *            The (optional) {@link ConnectionMonitorInterface} for this
	 *            loader
	 */
	public ModelDiskContentLoader(@Nonnull ModelLruCache<String, ExpiringFutureTask<M>> memCache,
			@CheckForNull ModelDiskCache<M> diskCache, long expiration,
			@Nullable RequestHandler requestHandler,
			@Nullable ConnectionMonitorInterface connMonitor) {
		mMemCache = memCache;
		mDiskCache = diskCache;
		mExpiration = expiration;
		mRequestHandler = requestHandler;
		mConnMonitor = connMonitor;
	}

	/**
	 * @see ContentLoader#load(ActionType, AbstractModelRequest,
	 *      ContentUpdateCallback)
	 */
	@Override
	public M load(ActionType action, AbstractModelRequest<M> request,
			ContentUpdateCallback<M> callback) throws Exception {
		// TODO: improve this, it's almost procedural

		// if network is not active, turn every action to CACHE_ONLY
		boolean networkActive = (mConnMonitor != null) ? mConnMonitor.isNetworkActive() : true;
		action = (networkActive) ? ((action != null) ? action : ActionType.NORMAL)
				: ActionType.CACHE_ONLY;

		final String key = request.hash();
		// we try to retrieve item from our task cache
		ExpiringFutureTask<M> oldFutureTask = null;
		ExpiringFutureTask<M> future = oldFutureTask = mMemCache.get(key);
		/*
		 * we try to get a new task either if there is no previous one, if the
		 * action is REFRESH or the previous is expired and this is not a
		 * CACHE_ONLY action.
		 */
		final boolean isExpired = future != null && future.isExpired()
				&& action != ActionType.CACHE_ONLY;

		if (future == null || action == ActionType.REFRESH || isExpired) {

			/** cache debugging */
			if (DroidConfig.DEBUG) {
				final String url = request.getRequestUrl();
				if (future == null) {
					Log.v(TAG, "Memory cache miss for " + url);
				} else if (action == ActionType.REFRESH) {
					Log.v(TAG, "Refreshing cache for " + url);
				} else if (isExpired) {
					Log.v(TAG, "Memory cache expired for " + url);
				}
			}
			/** cache debugging - END */

			// build the new loading task
			final ExpiringFutureTask<M> newFutureTask = new ExpiringFutureTask<M>(
					new IOContentLoader(action, request, callback), mExpiration);

			if (action == ActionType.REFRESH || isExpired) {
				// invalidate cache item if any to start new task
				mMemCache.remove(key, future);
			}

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
				revertOnFailure(key, oldFutureTask, future, isExpired);
			}
			return model;
		} catch (CancellationException e) {
			revertOnFailure(key, oldFutureTask, future, isExpired);
		} catch (ExecutionException e) {
			// we don't want the cache to be polluted with failed attempts
			revertOnFailure(key, oldFutureTask, future, isExpired);
			throw CacheMemoizer.launderThrowable(e.getCause());
		}
		return null;
	}

	/**
	 * Fallback method to handle the case when a task isn't successful and needs
	 * to be removed from the cache to avoid pollution. For some
	 * {@link ActionType}s, it is also needed to replace the failed new task
	 * with the old one (if any).
	 * 
	 * @param key
	 *            The cache key string
	 * @param oldTask
	 *            The old, removed future task
	 * @param newTask
	 *            The new, failed future task to be removed
	 * @param putOld
	 *            true to synchronously put the old model in the cache again if
	 *            no other new tasks have been added, false otherwise
	 */
	private void revertOnFailure(String key, ExpiringFutureTask<M> oldTask,
			ExpiringFutureTask<M> newTask, boolean putOld) {
		mMemCache.remove(key, newTask);
		if (oldTask != null && putOld) {
			mMemCache.putIfAbsent(key, oldTask);
		}
	}

	/**
	 * Callable that executes loading of data from disk or network
	 */
	private class IOContentLoader implements Callable<M> {

		private final ActionType mAction;
		private final AbstractModelRequest<M> mRequest;
		@Nullable
		private final ContentUpdateCallback<M> mUpdateCallback;

		public IOContentLoader(ActionType action, AbstractModelRequest<M> request,
				ContentUpdateCallback<M> callback) {
			mAction = action;
			mRequest = request;
			mUpdateCallback = callback;
		}

		@Override
		@SuppressWarnings("unchecked")
		public M call() throws Exception {

			final String key = mRequest.hash();
			M model = null;

			/** Disk cache access */
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

			/** execute GET request to the server */
			// cast is safe here, we're using JsonModel subclasses
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
			} else if (mAction == ActionType.NORMAL) {
				// fallback when request failed
				if (mDiskCache != null) {
					// set model to any stale data we might find
					model = mDiskCache.get(key);
				}
				/** cache debugging */
				if (DroidConfig.DEBUG) {
					final String url = mRequest.getRequestUrl();
					if (model != null) {
						Log.d(TAG, "Fallback: Disk cache hit for " + url);
					} else {
						Log.d(TAG, "Fallback: Disk cache miss for " + url);
					}
				}
				/** cache debugging - END */
			}
			// FIXME: should we throw an exception rather than returning null?
			return model;
		}
	}

}