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
package com.github.luluvise.droid_utils.lib.adapters;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.concurrent.NotThreadSafe;

import android.database.Cursor;
import android.database.DataSetObserver;
import android.util.SparseIntArray;
import android.widget.AlphabetIndexer;
import android.widget.ListAdapter;
import android.widget.SectionIndexer;

import com.google.common.annotations.Beta;

/**
 * Delegate implementation of {@link SectionIndexer} that provides alphabetical
 * indexes to any kind of {@link ListAdapter}, even the ones that are not backed
 * by a {@link Cursor} (unlike the standard library's {@link AlphabetIndexer}).
 * 
 * The delegating adapter must implement the {@link ItemSectionStringBuilder} to
 * provide the string labels for the sections, and the {@link SectionIndexer}
 * interface itself, calling this object's delegate methods.
 * 
 * In order for the indexer to be notified of changes to the backing dataset,
 * the adapter must register it as a {@link DataSetObserver} by calling
 * {@link ListAdapter#registerDataSetObserver(DataSetObserver)} in its
 * constructor.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@NotThreadSafe
public class BaseAlphabetSectionIndexer<T> extends DataSetObserver implements SectionIndexer {

	/**
	 * This interface is used by the section indexer to retrieve the string
	 * labels for the sections that are currently existing in the dataset.
	 * 
	 * @param <T>
	 *            The item parametrized type, corresponding to the adapter type
	 */
	public interface ItemSectionStringBuilder<T> {

		/**
		 * Implement this method to provide a (non-null) string to be used as a
		 * section label.
		 * 
		 * @param item
		 *            The adapter item to build the label from
		 * @return The label string (must preferably be a single char string)
		 */
		@Nonnull
		public String getStringForItemSection(@Nonnull T item);
	}

	protected final List<T> mDataset;
	protected final ItemSectionStringBuilder<T> mSectionBuilder;

	// sorted set of all existing section strings
	protected final SortedSet<String> mActiveSections;
	// cache for position/section pairs
	protected final SparseIntArray mSectionsCache;
	// cache for section/position (start position for a section)
	protected final SparseIntArray mPositionsCache;

	/**
	 * Constructor for a {@link BaseAlphabetSectionIndexer}
	 * 
	 * @param dataset
	 *            The adapter's backing {@link List}. This list is wrapped with
	 *            {@link Collections#unmodifiableList(List)} and won't be
	 *            modified by the indexer
	 * @param sectionBuilder
	 *            The {@link ItemSectionStringBuilder} to use to build the
	 *            section string labels
	 */
	public BaseAlphabetSectionIndexer(@Nonnull List<T> dataset,
			@Nonnull ItemSectionStringBuilder<T> sectionBuilder) {
		mDataset = Collections.unmodifiableList(dataset);
		mSectionBuilder = sectionBuilder;
		mActiveSections = new TreeSet<String>(); // string natural order
		mSectionsCache = new SparseIntArray();
		mPositionsCache = new SparseIntArray();
		buildCaches();
	}

	@Override
	public void onChanged() {
		super.onChanged();

		clear();
		buildCaches();
	}

	@Override
	public void onInvalidated() {
		super.onInvalidated();

		clear();
	}

	@Override
	public Object[] getSections() {
		return mActiveSections.toArray();
	}

	@Override
	public int getPositionForSection(int section) {
		return mPositionsCache.get(section);
	}

	@Override
	public int getSectionForPosition(int position) {
		return mSectionsCache.get(position, 0);
	}

	private final void buildCaches() {
		// the dataset is already sorted alphabetically, we add add the elements
		// in the sections cache, and only the first item for every section in
		// the positions cache. We don't need to care about any sorting, if not
		// of the one of the section strings which is already guaranteed by the
		// SortedSet
		int position = 0;
		int sectionIndex = 0;
		for (T item : mDataset) {
			final String section = mSectionBuilder.getStringForItemSection(item);
			if (!mActiveSections.contains(section)) {
				// first item of a new section, add to caches
				mActiveSections.add(section);
				sectionIndex = mActiveSections.size() - 1;
				mPositionsCache.append(sectionIndex, position);
			}
			mSectionsCache.append(position, sectionIndex);
			position++;
		}
	}

	@OverridingMethodsMustInvokeSuper
	protected void clear() {
		mActiveSections.clear();
		mSectionsCache.clear();
		mPositionsCache.clear();
	}

}