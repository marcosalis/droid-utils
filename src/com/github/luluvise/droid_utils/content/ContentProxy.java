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

import com.github.luluvise.droid_utils.cache.DiskCache;
import com.github.luluvise.droid_utils.content.loaders.ContentLoader;
import com.google.common.annotations.Beta;

/**
 * Interface for general content proxies.
 * 
 * The purpose of a content proxy (or content provider) is to ensure a level of
 * abstraction for clients of the proxy, in order to avoid the need for them to
 * manage the way a content is retrieved: the calls to the proxy should be the
 * same whether the content is available in a cache or needs to be retrieved
 * from a server. A {@link ContentLoader} is used internally to effectively
 * retrieve the requested content.
 * 
 * The {@link ActionType} enum should be the only parameter that clients must
 * set to give an indication to the content proxy on how a content must be
 * retrieved.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
public interface ContentProxy {

	/**
	 * Enum that represents the possible access modes to a content proxy.
	 */
	public enum ActionType {
		/**
		 * Normal content fetching: passes through all levels of cache, if there
		 * is a cache miss, download the content from the server.
		 */
		NORMAL,
		/**
		 * Attempt a cache only content fetching.<br>
		 * If there is a total cache miss, do nothing.
		 */
		CACHE_ONLY,
		/**
		 * Pre-fetch data that could be needed soon.<br>
		 * If the data is not available in cache, schedule a content download.
		 */
		PRE_FETCH,
		/**
		 * Refresh the cache data, and return the updated content if any.
		 */
		REFRESH
	}

	/**
	 * Completely clears the memory cache of this content manager.
	 */
	public void clearMemoryCache();

	/**
	 * Synchronously clears permanent "disk" (DB or flash storage) cache items
	 * of this content manager according to the passed
	 * {@link DiskCache.DiskCacheClearMode}
	 */
	public void clearDiskCache(DiskCache.DiskCacheClearMode mode);

	/**
	 * Completely wipes any permanent "disk" (DB or flash storage) cache of this
	 * content manager.
	 * 
	 * Note: implementations must be asynchronous and non-blocking.
	 */
	public void scheduleClearDiskCache();

	/**
	 * Completely wipe any kind of cache of this content manager.
	 * 
	 * Note: implementations must be asynchronous and non-blocking.
	 */
	public void clearCache();

}