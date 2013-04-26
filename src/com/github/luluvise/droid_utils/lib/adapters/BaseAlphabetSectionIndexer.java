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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.concurrent.NotThreadSafe;

import android.database.Cursor;
import android.database.DataSetObserver;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.widget.AlphabetIndexer;
import android.widget.ListAdapter;
import android.widget.SectionIndexer;

import com.google.common.annotations.Beta;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;

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

	public static final String LATIN_DEFAULT_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

	private static final ImmutableList<String> DEFAULT_INDEXES;

	static {
		final ArrayList<String> indexes = new ArrayList<String>(LATIN_DEFAULT_CHARS.length());
		for (char c : LATIN_DEFAULT_CHARS.toCharArray()) {
			indexes.add(Character.toString(c));
		}
		DEFAULT_INDEXES = ImmutableList.copyOf(indexes);
	}

	protected final List<T> mDataset;
	protected final ItemSectionStringBuilder<T> mSectionBuilder;
	private final boolean mAddDefaultIndexes;

	// sorted set of all existing section strings
	protected final List<String> mActiveSections;

	// cache for strings/position
	ArrayListMultimap<String, Integer> mMultimapCache;
	// cache for position/strings pairs
	protected SparseArray<String> mStringsCache;
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
		this(dataset, sectionBuilder, true);
	}

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
			@Nonnull ItemSectionStringBuilder<T> sectionBuilder, boolean addDefaultIndexes) {
		mDataset = Collections.unmodifiableList(dataset);
		mSectionBuilder = sectionBuilder;
		mAddDefaultIndexes = addDefaultIndexes;
		mActiveSections = new ArrayList<String>(DEFAULT_INDEXES.size());
		mMultimapCache = ArrayListMultimap.create();
		mStringsCache = new SparseArray<String>();
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
		return mActiveSections.toArray(new String[mActiveSections.size()]);
	}

	@Override
	public int getPositionForSection(int sectionIndex) {
		if (sectionIndex <= 0) { // top position
			return 0;
		}
		int position = 0;
		position = mPositionsCache.get(sectionIndex, -1);
		if (position != -1) { // cached position
			return position;
		} else {
			String section = mActiveSections.get(sectionIndex);
			final List<Integer> list = mMultimapCache.get(section);
			if (!list.isEmpty()) {
				position = list.get(0).intValue();
				mSectionsCache.put(position, sectionIndex);
				mPositionsCache.put(sectionIndex, position);
				return position;
			} else { // recursively search for previous existing section
				return getPositionForSection(sectionIndex - 1);
			}
		}
	}

	@Override
	public int getSectionForPosition(int position) {
		final int size = mDataset.size();
		if (position < size) {
			int sectionIndex = mSectionsCache.get(position, -1);
			if (sectionIndex != -1) {
				return sectionIndex;
			} else {
				String section = mStringsCache.get(position);
				// TODO: use binary search
				sectionIndex = mActiveSections.indexOf(section);
				mSectionsCache.put(position, sectionIndex);
				mPositionsCache.put(sectionIndex, position);
				return sectionIndex;
			}
		} else {
			return size - 1;
		}
	}

	private final void buildCaches() {
		// temporary sorted set by natural string order
		final TreeSet<String> sections = new TreeSet<String>();

		if (mAddDefaultIndexes) { // add all default indexes
			sections.addAll(DEFAULT_INDEXES);
		}
		// the dataset is already sorted alphabetically, we add the elements
		// in the sections cache, and only the first item for every section in
		// the positions cache. We don't need to care about any sorting, if not
		// of the one of the section strings which is already guaranteed by the
		// SortedSet
		int position = 0;

		for (T item : mDataset) {
			final String section = mSectionBuilder.getStringForItemSection(item);
			mStringsCache.append(position, section);
			mMultimapCache.put(section, Integer.valueOf(position));
			sections.add(section);
			position++;
		}

		mActiveSections.addAll(sections); // add all sections
	}

	@Nonnegative
	static int approxBinarySearch(@Nonnull String section, @Nonnull SortedSet<String> dataSet) {
		final ArrayList<String> sections = new ArrayList<String>(dataSet);

		int start = 0;
		int end = sections.size() - 1;
		int pivot = 0;

		while (start < end) {
			pivot = (start + end) / 2;
			final int compare = sections.get(pivot).compareTo(section);
			if (compare == 0) {
				return pivot;
			} else if (compare < 0) {
				start = pivot + 1;
			} else {
				end = pivot - 1;
			}
		}

		return start; // return lower element
	}

	@OverridingMethodsMustInvokeSuper
	protected void clear() {
		mActiveSections.clear();
		mMultimapCache.clear();
		mStringsCache.clear();
		mSectionsCache.clear();
		mPositionsCache.clear();
	}

}