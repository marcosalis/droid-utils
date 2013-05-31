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

import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;

import com.github.luluvise.droid_utils.DroidConfig;
import com.google.api.client.http.BackOffPolicy;
import com.google.common.annotations.Beta;

/**
 * Simple class holding default constants related to network connections, such
 * as settings (timeouts, keep-alive) and so on.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
public class NetworkConstants {

	/**
	 * Default connection keep-alive (in milliseconds)
	 */
	public static final int DEFAULT_KEEP_ALIVE = 20 * 1000;

	/**
	 * Default connection socket timeout (in milliseconds)
	 */
	public static final int DEFAULT_CONN_TIMEOUT = 20 * 1000;

	/**
	 * Default socket read timeout (in milliseconds)<br>
	 * TODO: check for best-performance value
	 */
	public static final int DEFAULT_READ_TIMEOUT = (DroidConfig.DEBUG) ? 30 * 1000 : 30 * 1000;

	/**
	 * Number of retries allowed for a failed request
	 * 
	 * Should be decreased when the HTTPS connections issues will be fixed.
	 */
	public static final int REQUEST_RETRIES = 8;

	/**
	 * Default, immutable {@link BackOffPolicy} to be used for HTTP requests
	 */
	public static final BackOffPolicy BACKOFF_POLICY = new DefaultBackOffPolicy();

	/**
	 * Attempts to retrieve a "Keep-Alive" header from the passed
	 * {@link HttpResponse}.
	 * 
	 * @return The keep alive time or -1 if not found
	 */
	public static long getKeepAliveHeader(HttpResponse response) {
		HeaderElementIterator it = new BasicHeaderElementIterator(
				response.headerIterator(HTTP.CONN_KEEP_ALIVE));
		while (it.hasNext()) {
			HeaderElement he = it.nextElement();
			String param = he.getName();
			String value = he.getValue();
			if (value != null && param.equalsIgnoreCase("timeout")) {
				try {
					return Long.parseLong(value) * 1000;
				} catch (NumberFormatException ignore) {
					return -1;
				}
			}
		}
		return -1;
	}

	private NetworkConstants() {
		// hidden constructor, no instantiation needed
	}

}