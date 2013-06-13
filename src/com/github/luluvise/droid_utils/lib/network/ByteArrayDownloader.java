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
package com.github.luluvise.droid_utils.lib.network;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import javax.annotation.concurrent.Immutable;

import android.util.Log;

import com.github.luluvise.droid_utils.DroidConfig;
import com.github.luluvise.droid_utils.logging.LogUtils;
import com.github.luluvise.droid_utils.network.HttpConnectionManager;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.common.annotations.Beta;
import com.google.common.io.ByteStreams;

/**
 * Simple utility {@link Callable} task that downloads raw data through a GET
 * request to the given URL and converts the resulting stream to a byte array.
 * 
 * When possible, prefer using the {@link #downloadByteArray(String)} static
 * method to save the object instantiation.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@Immutable
public final class ByteArrayDownloader implements Callable<byte[]> {

	private static final String TAG = ByteArrayDownloader.class.getSimpleName();

	private final String mUrl;

	public ByteArrayDownloader(String url) {
		mUrl = url;
	}

	@Override
	public byte[] call() throws IOException {
		return downloadByteArray(mUrl);
	}

	/**
	 * Directly execute download without using any task executor
	 * 
	 * @param url
	 *            The string URL to download from
	 * @return The byte array from the stream, null if an error response
	 *         occurred
	 * @throws IOException
	 */
	public static byte[] downloadByteArray(String url) throws IOException {
		HttpRequest request = null;
		HttpResponse response = null;
		byte[] bytes = null;

		if (DroidConfig.DEBUG) {
			Log.d(TAG, "Executing GET request to: " + url);
		}

		try {
			request = HttpConnectionManager.get().buildRequest(HttpMethods.GET, url, null);
			response = request.execute();

			if (response.isSuccessStatusCode()) {
				// get input stream and converts it to byte array
				InputStream stream = new BufferedInputStream(response.getContent());
				bytes = ByteStreams.toByteArray(stream);

				if (DroidConfig.DEBUG && bytes != null) {
					Log.v(TAG, "GET request successful to: " + url);
				}
			}
		} finally {
			if (response != null) {
				try {
					response.disconnect();
				} catch (IOException e) { // just an attempt to close the stream
					LogUtils.logException(e);
				}
			}
		}
		return bytes;
	}

}