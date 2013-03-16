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
package com.github.luluvise.droid_utils.network;

import java.io.IOException;

import org.apache.http.HttpStatus;

import android.util.Log;

import com.github.luluvise.droid_utils.DroidConfig;
import com.google.api.client.http.BackOffPolicy;
import com.google.common.annotations.Beta;

/**
 * Default, general simple {@link BackOffPolicy} implementation that deals with
 * retries on a failed request by using a linear back off policy.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
public class DefaultBackOffPolicy implements BackOffPolicy {

	/**
	 * Default short linear back off time in milliseconds. Can be used to avoid
	 * polluting with consecutive requests the connection library, giving some
	 * time to the socket to be opened or temporary network/server issues to be
	 * fixed.
	 */
	public static final long DEFAULT_LINEAR_BACKOFF = 100;

	private static final String TAG = DefaultBackOffPolicy.class.getSimpleName();

	@Override
	public boolean isBackOffRequired(int statusCode) {
		if (DroidConfig.DEBUG) {
			Log.v(TAG, "Backoff check required for status code: " + statusCode);
		}
		switch (statusCode) {
		case 0:
			/*
			 * No HTTP response (may be caused by a Google's library fault)
			 */
		case HttpStatus.SC_CONFLICT:
			// server-side conflict, retry with backoff
		case HttpStatus.SC_BAD_GATEWAY:
			// server unavailable, or too busy, retry with backoff
			return true;
		default:
			return false;
		}
	}

	@Override
	public void reset() {
		// does nothing
	}

	@Override
	public long getNextBackOffMillis() throws IOException {
		return DEFAULT_LINEAR_BACKOFF;
	}

}