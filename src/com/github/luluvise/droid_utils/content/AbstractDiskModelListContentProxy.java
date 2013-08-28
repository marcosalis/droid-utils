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

import java.io.File;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import android.content.Context;

import com.github.luluvise.droid_utils.annotations.NotForUIThread;
import com.github.luluvise.droid_utils.cache.DiskCache.DiskCacheClearMode;
import com.github.luluvise.droid_utils.content.loaders.ContentLoader;
import com.github.luluvise.droid_utils.content.loaders.ContentLoader.ContentUpdateCallback;
import com.github.luluvise.droid_utils.content.loaders.ModelDiskContentLoaderFactory;
import com.github.luluvise.droid_utils.json.model.JsonModel;
import com.github.luluvise.droid_utils.json.rest.AbstractModelRequest;
import com.google.common.annotations.Beta;

/**
 * Generic, abstract extension of an {@link AbstractDiskModelContentProxy} that
 * handles the retrieval of {@link JsonModel}s that can be grouped in a list.
 * 
 * This is useful to handle the case where a content can be provided as a single
 * JSON model object or as a list of models with the same representation, which
 * is grouped in another {@link JsonModel}. When this happens, we usually want
 * to automatically update the "single model" cache when we get a fresh list of
 * models and invalidate the list of models when we get a fresh single model.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@ThreadSafe
public abstract class AbstractDiskModelListContentProxy<MODEL extends JsonModel, LIST extends JsonModel>
		extends AbstractDiskModelContentProxy<MODEL> implements ContentUpdateCallback<LIST> {

	private final ListContentProxy mListContentProxy;

	public AbstractDiskModelListContentProxy(
			@Nonnull Context context,
			@Nonnull Class<MODEL> modelClass,
			int modelsInCache,
			@Nonnull Class<LIST> modelListClass,
			int listsInCache,
			@Nonnull String diskFolder,
			final long expiration,
			@Nonnull ModelDiskContentLoaderFactory<AbstractModelRequest<MODEL>, MODEL> loaderFactory,
			@Nonnull ModelDiskContentLoaderFactory<AbstractModelRequest<LIST>, LIST> listLoaderFactory) {
		super(context, modelClass, modelsInCache, diskFolder, expiration, loaderFactory);
		final String subFolder = diskFolder + File.separator + "list";
		mListContentProxy = new ListContentProxy(context, modelListClass, listsInCache, subFolder,
				expiration, listLoaderFactory);
	}

	public AbstractDiskModelListContentProxy(@Nonnull Context context,
			@Nonnull Class<MODEL> modelClass, int modelsInCache,
			@Nonnull Class<LIST> modelListClass, int listsInCache, @Nonnull String diskFolder,
			final long expiration) {
		super(context, modelClass, modelsInCache, diskFolder, expiration);
		final String subFolder = diskFolder + File.separator + "list";
		mListContentProxy = new ListContentProxy(context, modelListClass, listsInCache, subFolder,
				expiration, null);
	}

	/**
	 * Retrieves a model list from the content proxy. See {@link ContentLoader}.
	 * 
	 * @param action
	 *            The {@link ActionType} to use
	 * @param request
	 *            The {@link AbstractModelRequest} for the list
	 * @return The model list, or null of unsuccessful
	 * @throws Exception
	 */
	@NotForUIThread
	public final LIST getModelList(ActionType action, AbstractModelRequest<LIST> request)
			throws Exception {
		return mListContentProxy.getModel(action, request, this);
	}

	@Override
	public abstract void onContentUpdated(LIST newContent);

	/**
	 * See
	 * {@link AbstractDiskModelContentProxy#putModel(com.luluvise.android.api.model.JsonModel)}
	 * 
	 * <b>Warning:</b> With the invalidate flag set to on, this call will
	 * invalidate all the model list caches, in order not to retrieve outdated
	 * content from future cache queries.
	 * 
	 * @param model
	 *            The model to put into the cache
	 */
	@NotForUIThread
	public final void putModel(final MODEL model, boolean invalidate) {
		super.putModel(model);
		// clear list caches to avoid stale data if needed
		if (invalidate) {
			clearListCache();
		}
	}

	/**
	 * Uses {@link #putModel(JsonModel, boolean)} with invalidate flag set by
	 * default to true.
	 */
	@Override
	@NotForUIThread
	public final void putModel(final MODEL model) {
		putModel(model, true);
	}

	/**
	 * Forces a model list object to be put into the cache.<br>
	 * This must be ONLY used for injection testing purposes.
	 * 
	 * @param list
	 *            The model list to put into the cache
	 */
	public final void putModelList(String key, final LIST list) {
		if (list == null) {
			return; // fail-safe attitude
		}
		// update model into caches
		mListContentProxy.putModel(key, list);
		// TODO: call onContentUpdated?
	}

	/**
	 * Clears the model lists memory and disk caches.
	 * 
	 * It's safe to execute this method from the UI thread.
	 */
	public void clearListCache() {
		mListContentProxy.clearCache();
	}

	@Override
	public void clearMemoryCache() {
		super.clearMemoryCache();
		mListContentProxy.clearMemoryCache();
	}

	@Override
	public void scheduleClearDiskCache() {
		super.scheduleClearDiskCache();
		mListContentProxy.scheduleClearDiskCache();
	}

	@Override
	@NotForUIThread
	public void clearDiskCache(DiskCacheClearMode mode) {
		super.clearDiskCache(mode);
		mListContentProxy.clearDiskCache(mode);
	}

	/**
	 * {@link AbstractDiskModelContentProxy} extension for a list model.
	 */
	private class ListContentProxy extends AbstractDiskModelContentProxy<LIST> {

		public ListContentProxy(Context context, Class<LIST> modelClass, int modelsInCache,
				String diskFolder, long expiration,
				ModelDiskContentLoaderFactory<AbstractModelRequest<LIST>, LIST> loaderFactory) {
			super(context, modelClass, modelsInCache, diskFolder, expiration, loaderFactory);
		}
	}

}