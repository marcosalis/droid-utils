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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nonnull;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.concurrent.Immutable;

import com.github.luluvise.droid_utils.lib.DroidUtils;
import com.github.luluvise.droid_utils.logging.LoggedThreadFactory;
import com.google.common.annotations.Beta;

/**
 * Abstract base implementation of {@link ContentProxy}.<br>
 * Just provides some library common executors to perform the content retrieval.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@Immutable
public abstract class AbstractContentProxy implements ContentProxy {

	@SuppressWarnings("unused")
	private static final String TAG = AbstractContentProxy.class.getSimpleName();

	static {
		final int executorSize = DroidUtils.getIOBoundPoolSize();
		final int prefetchSize = (int) Math.ceil((double) executorSize / 2);

		PROXY_EXECUTOR = Executors.unconfigurableExecutorService(Executors.newFixedThreadPool(executorSize,
				new LoggedThreadFactory("Proxy executor")));

		PRE_FETCH_EXECUTOR = Executors.unconfigurableExecutorService(Executors.newFixedThreadPool(prefetchSize,
				new LoggedThreadFactory("Proxy pre-fetch", Thread.MIN_PRIORITY)));

		LOW_PRIORITY_EXECUTOR = Executors.unconfigurableExecutorService(Executors
				.newSingleThreadExecutor(new LoggedThreadFactory("Low priority executor", Thread.MIN_PRIORITY)));
	}

	/**
	 * Executor to be used by all content proxies to make requests.
	 */
	protected static final ExecutorService PROXY_EXECUTOR;

	/**
	 * Executor to be used by all content proxies when pre-fetching content.
	 */
	protected static final ExecutorService PRE_FETCH_EXECUTOR;

	/**
	 * Low priority, single thread executor to be used for network requests that
	 * don't immediately affect the user experience and therefore don't
	 * necessarily need to be completed in a short time.
	 */
	protected static final ExecutorService LOW_PRIORITY_EXECUTOR;

	/**
	 * Executes a {@link Runnable} to pre-fetch generic data inside the content
	 * proxies common pre-fetching {@link ExecutorService}.
	 * 
	 * @param runnable
	 *            The {@link Runnable} to execute (must be non null)
	 */
	public static final void prefetch(@Nonnull Runnable runnable) {
		PRE_FETCH_EXECUTOR.execute(runnable);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@OverridingMethodsMustInvokeSuper
	public void clearCache() {
		clearMemoryCache();
		scheduleClearDiskCache();
	}

}
