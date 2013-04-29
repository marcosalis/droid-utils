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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
 * Note that the elements in the passed dataset <b>must</b> must be already
 * sorted lexicographically, using the same strings order or {@link Comparator}
 * which is used by the {@link ItemSectionStringBuilder}.
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

	static final ImmutableList<String> DEFAULT_INDEXES;

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

	/*
	 * The dataset used are probably a bit redundant, but they guarantee optimal
	 * performances (O(1) when the caches are fully populated) when fast
	 * scrolling so we don't mind for now the increased memory occupation
	 */

	// (sorted) list of all unique existing section strings
	protected final List<String> mActiveSections;

	// cache for section/position
	protected final HashMap<String, Integer> mSectionIndexesCache;
	// cache for position/sections pairs
	protected final SparseArray<String> mStringsCache;
	// cache for position/sectionIndex pairs
	protected final SparseIntArray mSectionsCache;
	// cache for sectionIndex/position (start position for a section)
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
	 * @param addDefaultIndexes
	 *            true to add by default all the chars contained in
	 *            {@link #LATIN_DEFAULT_CHARS} to the index, false to only add
	 *            chars whose sections have at least one element in the dataset
	 */
	public BaseAlphabetSectionIndexer(@Nonnull List<T> dataset,
			@Nonnull ItemSectionStringBuilder<T> sectionBuilder, boolean addDefaultIndexes) {
		mDataset = Collections.unmodifiableList(dataset);
		mSectionBuilder = sectionBuilder;
		mAddDefaultIndexes = addDefaultIndexes;
		final int cacheDefaultSize = DEFAULT_INDEXES.size();
		mActiveSections = new ArrayList<String>(cacheDefaultSize);
		mSectionIndexesCache = new HashMap<String, Integer>(cacheDefaultSize);
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

		if (sectionIndex <= 0) { // boundaries checks
			return 0; // top position
		} else if (sectionIndex >= mActiveSections.size()) {
			return mDataset.size() - 1; // last position
		}

		int position = 0;
		position = mPositionsCache.get(sectionIndex, -1);
		if (position != -1) { // cached position
			return position;
		} else { // no cached position for section index
			String section = mActiveSections.get(sectionIndex);
			final Integer positionBoxed = mSectionIndexesCache.get(section);
			if (positionBoxed != null) { // position for section cached
				position = positionBoxed.intValue();
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

		if (position <= 0) { // boundaries checks
			return 0; // top section
		} else if (position >= mDataset.size()) {
			return mActiveSections.size() - 1; // last section
		}

		int sectionIndex = mSectionsCache.get(position, -1);
		if (sectionIndex != -1) { // section index cached
			return sectionIndex;
		} else {
			String section = mStringsCache.get(position);
			// use binary search to find and cache section index
			sectionIndex = approxBinarySearch(mActiveSections, section);
			mSectionsCache.put(position, sectionIndex);
			mPositionsCache.put(sectionIndex, position);
			return sectionIndex;
		}
	}

	private final void buildCaches() {
		// temporary sorted set to ensure string natural ordering
		final TreeSet<String> sections = new TreeSet<String>();

		if (mAddDefaultIndexes) { // add all default indexes
			sections.addAll(DEFAULT_INDEXES);
		}
		// the dataset is already sorted alphabetically, we add the elements
		// in the sections cache, and only the first item for every section in
		// the positions cache.
		int position = 0;

		for (T item : mDataset) {
			final String section = mSectionBuilder.getStringForItemSection(item);
			mStringsCache.append(position, section);
			if (!mSectionIndexesCache.containsKey(section)) {
				mSectionIndexesCache.put(section, Integer.valueOf(position));
				sections.add(section);
			}
			position++;
		}

		mActiveSections.addAll(sections); // add all sections, sorted
	}

	/**
	 * Performs a binary search of a {@link Comparable} from the passed list.
	 * This method differs from {@link Collections#binarySearch(List, Object)}
	 * for the fact that if the queried element is not found in the list, the
	 * index of the highest element to be lower than the element itself is
	 * returned.
	 * 
	 * The elements of the list should be non-null and already be sorted. If
	 * there are duplicated elements, no guarantees are made that the element
	 * index returned is the first one.
	 * 
	 * TODO: directly use {@link Collections#binarySearch(List, Object)} by
	 * handling the negative returned index to know where the element would be
	 * inserted?
	 * 
	 * @param dataset
	 *            The {@link List} of elements to search
	 * @param section
	 *            The element to search
	 * @return The index of the element if found, or the index of the closest
	 *         lower element if not. Returns -1 if the passed list is empty
	 */
	@Nonnegative
	protected static <T extends Comparable<T>> int approxBinarySearch(@Nonnull List<T> dataset,
			@Nonnull T section) {
		final int size = dataset.size();

		if (size == 0) { // empty list
			return -1;
		}

		int start = 0;
		int end = size - 1;
		int pivot = 0;
		int compare = 0;

		while (start <= end) { // need to compare also last element
			pivot = (start + end) / 2;
			compare = dataset.get(pivot).compareTo(section);
			if (compare == 0) { // found exact match
				return pivot;
			} else if (compare < 0) { // section is bigger
				if (start == end || start == size - 1) {
					return start;
				}
				start = pivot + 1;
			} else { // section is smaller
				if (end == 0) {
					return 0;
				}
				end = pivot - 1;
			}
		}
		if (compare > 0 && start > 0) {
			// we went too far, return lower element
			return start - 1;
		}
		return (start < size) ? start : size - 1;
	}

	@OverridingMethodsMustInvokeSuper
	protected void clear() {
		mActiveSections.clear();
		mSectionIndexesCache.clear();
		mStringsCache.clear();
		mSectionsCache.clear();
		mPositionsCache.clear();
	}

}