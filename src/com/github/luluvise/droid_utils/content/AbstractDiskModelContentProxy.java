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
package com.github.luluvise.droid_utils.content;

import java.io.IOException;
import java.util.concurrent.Callable;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.concurrent.ThreadSafe;

import android.content.Context;
import android.util.Log;

import com.github.luluvise.droid_utils.annotations.NotForUIThread;
import com.github.luluvise.droid_utils.cache.DiskCache;
import com.github.luluvise.droid_utils.cache.ExpiringFutureTask;
import com.github.luluvise.droid_utils.cache.ModelDiskCache;
import com.github.luluvise.droid_utils.cache.ModelLruCache;
import com.github.luluvise.droid_utils.content.loaders.ContentLoader;
import com.github.luluvise.droid_utils.content.loaders.ContentLoader.ContentUpdateCallback;
import com.github.luluvise.droid_utils.content.loaders.ModelDiskContentLoader;
import com.github.luluvise.droid_utils.content.loaders.ModelDiskContentLoaderFactory;
import com.github.luluvise.droid_utils.json.model.JsonModel;
import com.github.luluvise.droid_utils.json.rest.AbstractModelRequest;
import com.github.luluvise.droid_utils.logging.LogUtils;
import com.google.common.annotations.Beta;

/**
 * Generic, abstract implementation of {@link ContentProxy} that handles the
 * retrieval of {@link JsonModel}s using a 2-levels cache ({@link ModelLruCache}
 * and {@link ModelDiskCache}) before retrieving content from the network.
 * 
 * It is backed by a {@link ContentLoader} for the content retrieval.
 * 
 * It provides methods to load and put models from and to the content proxy, and
 * perform common maintenance operations to the content proxy caches.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@ThreadSafe
public abstract class AbstractDiskModelContentProxy<MODEL extends JsonModel> extends ModelContentProxy<MODEL> {

	private static final String TAG = AbstractDiskModelContentProxy.class.getSimpleName();

	private final ModelLruCache<String, ExpiringFutureTask<MODEL>> mModelCache;
	private volatile ModelDiskCache<MODEL> mModelDisk;

	private final ContentLoader<AbstractModelRequest<MODEL>, MODEL> mContentLoader;
	private final long mExpiration;

	/**
	 * Constructor for an {@link AbstractDiskModelContentProxy}.<br>
	 * Takes care of initializing memory and disk caches.
	 * 
	 * @param context
	 * @param modelClass
	 * @param modelsInCache
	 * @param diskFolder
	 * @param expiration
	 * @param loaderFactory
	 *            An (optional) custom {@link ModelDiskContentLoaderFactory}
	 */
	public AbstractDiskModelContentProxy(@Nonnull Context context, @Nonnull Class<MODEL> modelClass, int modelsInCache,
			@Nonnull String diskFolder, long expiration,
			ModelDiskContentLoaderFactory<AbstractModelRequest<MODEL>, MODEL> loaderFactory) {
		// initialize memory LRU caches
		mModelCache = new ModelLruCache<String, ExpiringFutureTask<MODEL>>(modelsInCache);
		try { // initialize disk caches
			mModelDisk = new ModelDiskCache<MODEL>(context, diskFolder, modelClass);
		} catch (IOException e) {
			// something went wrong. TODO: handle this!
			LogUtils.log(Log.ERROR, TAG, "Unable to create disk cache for " + diskFolder);
		}
		if (loaderFactory != null) {
			mContentLoader = loaderFactory.getContentLoader(mModelCache, mModelDisk);
		} else {
			mContentLoader = new ModelDiskContentLoader<MODEL>(mModelCache, mModelDisk, expiration, null, null);
		}
		mExpiration = expiration;
	}

	/**
	 * @see {@link #AbstractDiskModelContentProxy(Context, Class, int, String, long, ModelDiskContentLoaderFactory)}
	 *      with default content loader factory.
	 */
	public AbstractDiskModelContentProxy(@Nonnull Context context, @Nonnull Class<MODEL> modelClass, int modelsInCache,
			@Nonnull String diskFolder, long expiration) {
		this(context, modelClass, modelsInCache, diskFolder, expiration, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@CheckForNull
	@NotForUIThread
	@OverridingMethodsMustInvokeSuper
	public MODEL getModel(ActionType action, AbstractModelRequest<MODEL> request, ContentUpdateCallback<MODEL> callback)
			throws Exception {
		return mContentLoader.load(action, request, callback);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@CheckForNull
	@NotForUIThread
	@OverridingMethodsMustInvokeSuper
	public MODEL getModel(ActionType action, AbstractModelRequest<MODEL> request) throws Exception {
		return mContentLoader.load(action, request, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@NotForUIThread
	@OverridingMethodsMustInvokeSuper
	public void putModel(final MODEL model) {
		if (model == null) {
			return; // fail-safe attitude
		}
		// auto-generate key
		final String key = generateModelKey(model);
		putModel(key, model);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@NotForUIThread
	@OverridingMethodsMustInvokeSuper
	public void putModel(@Nonnull final String key, final MODEL model) {
		if (model == null) {
			return; // fail-safe attitude
		}
		final ExpiringFutureTask<MODEL> innerFuture = new ExpiringFutureTask<MODEL>(new Callable<MODEL>() {
			@Override
			public MODEL call() throws Exception {
				return model;
			}
		}, mExpiration);
		/*
		 * Run the "task" in this thread: it's just getting in return a value we
		 * already have, in order to be able to store it as a Future we need to
		 * execute it
		 */
		innerFuture.run();
		// update model into caches
		mModelCache.put(key, innerFuture);
		if (mModelDisk != null) {
			mModelDisk.put(key, model);
		}
	}

	/**
	 * Removes the model with the passed key from the cache. This must be used
	 * in specific cases where we need to explicitly invalidate an item only.
	 * 
	 * @param key
	 *            The string key for the model to remove
	 */
	@NotForUIThread
	@OverridingMethodsMustInvokeSuper
	protected void invalidateModel(@Nonnull String key) {
		mModelCache.remove(key);
		if (mModelDisk != null) {
			mModelDisk.remove(key);
		}
	}

	/**
	 * Needed from some subclasses to properly generate the key to store a model
	 * into the cache.
	 * 
	 * The base implementation throws an {@link IllegalArgumentException}.
	 * 
	 * @param model
	 *            The model to generate the key from
	 * @return The generated key
	 */
	protected String generateModelKey(@Nonnull MODEL model) {
		throw new IllegalArgumentException("Not implemented");
	}

	/**
	 * {@inheritDoc}<br>
	 * Always call to the superclass when overriding.
	 */
	@Override
	@OverridingMethodsMustInvokeSuper
	public void clearMemoryCache() {
		mModelCache.clear();
	}

	/**
	 * {@inheritDoc}<br>
	 * Always call to the superclass when overriding.
	 */
	@Override
	@OverridingMethodsMustInvokeSuper
	public void scheduleClearDiskCache() {
		if (mModelDisk != null) {
			mModelDisk.clear();
		}
	}

	/**
	 * {@inheritDoc}<br>
	 * Always call to the superclass when overriding.
	 */
	@Override
	@NotForUIThread
	@OverridingMethodsMustInvokeSuper
	public void clearDiskCache(DiskCache.DiskCacheClearMode mode) {
		if (mModelDisk != null) {
			mModelDisk.clear(mode);
		}
	}

}
