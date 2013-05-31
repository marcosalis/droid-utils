/*
 * Copyright 2012 Emil Sjšlander
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.emilsjolander.components.stickylistheaders;

import android.content.Context;
import android.widget.SectionIndexer;

public class StickyListHeadersSectionIndexerAdapterWrapper extends StickyListHeadersAdapterWrapper implements
		SectionIndexer {

	private final SectionIndexer delegate;

	StickyListHeadersSectionIndexerAdapterWrapper(Context context, StickyListHeadersAdapter delegate) {
		super(context, delegate);
		this.delegate = (SectionIndexer) delegate;
	}

	@Override
	public int getPositionForSection(int section) {
		int position = delegate.getPositionForSection(section);
		position = translateAdapterPosition(position);
		return position;
	}

	@Override
	public int getSectionForPosition(int position) {
		position = translateListViewPosition(position);
		return delegate.getSectionForPosition(position);
	}

	@Override
	public Object[] getSections() {
		return delegate.getSections();
	}

}
