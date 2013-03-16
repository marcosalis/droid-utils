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
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.common.annotations.Beta;

/**
 * {@link HttpUnsuccessfulResponseHandler} that handles certain failure response
 * codes for a request and attempts a retry if supported.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
public class UnsuccessfulResponseHandler implements HttpUnsuccessfulResponseHandler {

	private static final String TAG = UnsuccessfulResponseHandler.class.getSimpleName();

	/**
	 * Default {@link UnsuccessfulResponseHandler} for non-authenticated
	 * requests.
	 */
	public static final UnsuccessfulResponseHandler DEFAULT_HANDLER = new UnsuccessfulResponseHandler();

	@Override
	public boolean handleResponse(HttpRequest request, HttpResponse response, boolean supportsRetry)
			throws IOException {
		switch (response.getStatusCode()) {
		case 0:
			return handleZero(request);
		case HttpStatus.SC_BAD_GATEWAY:
			return handle502(request);
		}
		return false;
	}

	/**
	 * Attempt to solve random issue with the HTTP library that causes a request
	 * to fail immediately with a '0' response code, just issue a retry
	 */
	protected static boolean handleZero(HttpRequest request) {
		if (DroidConfig.DEBUG) {
			Log.w(TAG, "Handle '0' response status code for request " + request.getUrl().build());
		}
		return true;
	}

	/**
	 * Attempt to issue a retry when a '502 - Bad Gateway' response is given by
	 * the server.
	 */
	protected static boolean handle502(HttpRequest request) {
		if (DroidConfig.DEBUG) {
			Log.w(TAG, "Handle '502 - Bad Gateway' response status code for request "
					+ request.getUrl().build());
		}
		return true;
	}

}