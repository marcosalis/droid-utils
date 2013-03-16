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
package com.github.luluvise.droid_utils.content.mock;

import com.github.luluvise.droid_utils.cache.DiskCache.DiskCacheClearMode;
import com.github.luluvise.droid_utils.content.ContentManagerInterface;
import com.github.luluvise.droid_utils.content.ContentProxy;

/**
 * Mock implementation of {@link ContentManagerInterface} for testing.
 * 
 * All methods throw {@link UnsupportedOperationException}
 * 
 * @since 1.0
 * @author Marco Salis
 */
public class MockContentManager<E> implements ContentManagerInterface<E> {

	@Override
	public boolean registerContent(E contentId, ContentProxy content) {
		throw new UnsupportedOperationException("Mock!");
	}

	@Override
	public ContentProxy getContent(E contentId) {
		throw new UnsupportedOperationException("Mock!");
	}

	@Override
	public void clearAllCaches() {
		throw new UnsupportedOperationException("Mock!");
	}

	@Override
	public void clearMemoryCaches() {
		throw new UnsupportedOperationException("Mock!");
	}

	@Override
	public void scheduleClearDiskCaches() {
		throw new UnsupportedOperationException("Mock!");
	}

	@Override
	public void clearDiskCaches(DiskCacheClearMode mode) {
		throw new UnsupportedOperationException("Mock!");
	}

}
