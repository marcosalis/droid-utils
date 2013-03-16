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
package com.github.luluvise.droid_utils.logging;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import android.util.Log;

import com.github.luluvise.droid_utils.DroidConfig;
import com.google.common.annotations.Beta;

/**
 * {@link ThreadFactory} implementation that allow callers to set a
 * human-readable concise description of the Thread being created (for example,
 * the thread pool or executor name). The incremental number of built threads is
 * appended to the passed string.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
public class LoggedThreadFactory implements ThreadFactory {

	private static final String TAG = LoggedThreadFactory.class.getSimpleName();

	private final String mThreadName;
	private final AtomicInteger mThreadCount = new AtomicInteger();

	private final int mPriority;

	public LoggedThreadFactory(String threadName) {
		this(threadName, Thread.NORM_PRIORITY);
	}

	/**
	 * Costructor that allows setting a custom priority to the created threads.
	 * 
	 * @param threadName
	 * @param priority
	 */
	public LoggedThreadFactory(String threadName, int priority) {
		mThreadName = threadName;
		mPriority = priority;
	}

	/**
	 * @see java.util.concurrent.ThreadFactory#newThread(java.lang.Runnable)
	 */
	@Override
	public Thread newThread(Runnable r) {
		String name = mThreadName + " #" + mThreadCount.incrementAndGet();
		if (DroidConfig.DEBUG) {
			Log.v(TAG, "Creating thread: " + name);
		}
		final Thread thread = new Thread(r, name);
		thread.setPriority(mPriority);
		return thread;
	}

}
