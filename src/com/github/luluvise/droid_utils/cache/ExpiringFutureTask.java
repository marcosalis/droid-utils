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
package com.github.luluvise.droid_utils.cache;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.github.luluvise.droid_utils.concurrent.SettableFutureTask;
import com.github.luluvise.droid_utils.json.model.JsonModel;

/**
 * Extension of a {@link SettableFutureTask} to hold a future computation of a
 * {@link JsonModel} with an expiration time.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Immutable
public class ExpiringFutureTask<E extends JsonModel> extends SettableFutureTask<E> {

	/**
	 * Expiration time, set at instantiation time.
	 */
	private final long mExpiration;

	/**
	 * Instantiate an {@link ExpiringFutureTask} from a {@link Callable}
	 * 
	 * @param callable
	 *            The content {@link Future} computation
	 * @param expiration
	 *            The expiration time, in milliseconds, or
	 *            {@link Long#MAX_VALUE} for no expiration.
	 */
	public ExpiringFutureTask(@Nonnull Callable<E> callable, @Nonnegative long expiration) {
		super(callable);
		if (expiration == Long.MAX_VALUE) {
			// force task to never expire
			mExpiration = Long.MAX_VALUE;
		} else {
			mExpiration = System.currentTimeMillis() + expiration;
		}
	}

	/**
	 * Instantiate an {@link ExpiringFutureTask} from a {@link Runnable}
	 * 
	 * @param runnable
	 *            The {@link Runnable} that executes the task
	 * @param result
	 *            The result of the computation
	 * @param expiration
	 *            The expiration time, in milliseconds, or
	 *            {@link Long#MAX_VALUE} for no expiration.
	 */
	public ExpiringFutureTask(@Nonnull Runnable runnable, @Nullable E result,
			@Nonnegative long expiration) {
		super(runnable, result);
		if (expiration == Long.MAX_VALUE) {
			// force task to never expire
			mExpiration = Long.MAX_VALUE;
		} else {
			mExpiration = System.currentTimeMillis() + expiration;
		}
	}

	/**
	 * Returns whether the held cache item is expired or not.
	 * 
	 * @return true if the item is expired, false otherwise
	 */
	public boolean isExpired() {
		return System.currentTimeMillis() > mExpiration;
	}

}