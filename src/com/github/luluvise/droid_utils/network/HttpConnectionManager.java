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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.concurrent.ThreadSafe;

import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpClient;

import com.github.luluvise.droid_utils.DroidConfig;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.common.annotations.Beta;

/**
 * Library level class to handle network connections from a single start point.
 * This default implementation provides basic functionalities, and it just takes
 * care of using the same {@link HttpTransport} for any connection and can be
 * used to retrieve an {@link HttpRequestFactory} initialised with default
 * connection parameters, user agent and timeouts.
 * 
 * Please note that no limits are put on the number of concurrent connection at
 * this abstraction level. Clients must implement their own pooling mechanism to
 * limit it or just rely on the ApacheHttp library pooling policies.
 * 
 * To debug network connections executed from the {@link HttpTransport} library
 * do the following:
 * 
 * <ul>
 * <li>set the DEBUG flag to true in the {@link DroidConfig} class</li>
 * <li>add
 * <i>Logger.getLogger(HttpTransport.class.getName()).setLevel(Level.CONFIG
 * );</i> before the execute call in your code</li>
 * <li>type <i>adb shell setprop log.tag.HttpTransport DEBUG</i> in a terminal
 * to enable debug logging for the transport class in your connected device or
 * emulator</li>
 * </ul>
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@ThreadSafe
public class HttpConnectionManager implements HttpConnectionManagerInterface {

	@SuppressWarnings("unused")
	private static final String TAG = HttpConnectionManager.class.getSimpleName();

	/**
	 * Globally accessible instance of the default HTTP connection manager.
	 */
	private static final HttpConnectionManager INSTANCE = new HttpConnectionManager();

	private transient HttpTransport mDefaultHttpTransport;
	private transient HttpRequestFactory mDefaultRequestFactory;

	/**
	 * Shortcut method to return the {@link HttpConnectionManager} global
	 * instance.
	 */
	public static HttpConnectionManager get() {
		return INSTANCE;
	}

	private HttpConnectionManager() {
		// set keep-alive global property (NOT NEEDED)
		// System.setProperty("http.keepAlive", "false");

		if (DroidConfig.DEBUG) {
			Logger.getLogger(HttpTransport.class.getName()).setLevel(Level.CONFIG);
		}
	}

	/**
	 * Initializes the {@link HttpConnectionManager}
	 * 
	 * @param keepAliveStrategy
	 *            The {@link ConnectionKeepAliveStrategy} for the default http
	 *            client used.
	 */
	@OverridingMethodsMustInvokeSuper
	public synchronized void initialize(@CheckForNull ConnectionKeepAliveStrategy keepAliveStrategy) {
		/* Get the best HTTP client for the current Android version */
		// mDefaultHttpTransport = AndroidHttp.newCompatibleTransport();
		/*
		 * No we don't get it anymore. ApacheHttpTransport turns out to be
		 * incredibly faster than NetHttpTransport with HTTPS connections.
		 * 
		 * TODO: benchmark different HTTP client performances
		 */
		/* AndroidHttpClient.newInstance("Android", context) */
		// mDefaultHttpTransport = new ApacheHttpTransport();
		/*
		 * Creating a custom DefaultHttpClient to set the keep alive strategy
		 */
		DefaultHttpClient httpClient = ApacheHttpTransport.newDefaultHttpClient();
		if (keepAliveStrategy != null) {
			httpClient.setKeepAliveStrategy(keepAliveStrategy);
		}
		mDefaultHttpTransport = new ApacheHttpTransport(httpClient);
		mDefaultRequestFactory = createStandardRequestFactory(mDefaultHttpTransport);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public HttpRequestFactory getRequestFactory() {
		return mDefaultRequestFactory;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public HttpRequestFactory createRequestFactory(HttpTransport transport) {
		return createStandardRequestFactory(transport);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public HttpRequest buildRequest(String method, String urlString, HttpContent content)
			throws IOException {
		return mDefaultRequestFactory.buildRequest(method, new GenericUrl(urlString), content);
	}

	/**
	 * This implementation just throws an {@link UnsupportedOperationException}
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	public HttpRequest buildCustomRequest(String method, String urlString) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * This implementation just throws an {@link UnsupportedOperationException}
	 * 
	 * @throws UnsupportedOperationException
	 */
	@Override
	public HttpRequest buildCustomRequest(String method, String urlString, HttpContent content)
			throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns the default {@link HttpTransport} used by the manager.
	 */
	public HttpTransport getDefaultHttpTransport() {
		return mDefaultHttpTransport;
	}

	/**
	 * Initialize here {@link HttpRequest}'s parameters for the request factory
	 * to other servers
	 * 
	 * @param transport
	 *            The {@link HttpTransport} used to create requests
	 * @return The created {@link HttpRequestFactory}
	 */
	private HttpRequestFactory createStandardRequestFactory(HttpTransport transport) {
		return transport.createRequestFactory(new DefaultHttpRequestInitializer());
	}

	/**
	 * Default implementation of {@link HttpRequestInitializer}. Every request
	 * is initialized with default timeouts, number of retries, exception
	 * handler and back off policies using the constants in
	 * {@link NetworkConstants}.
	 */
	public static class DefaultHttpRequestInitializer implements HttpRequestInitializer {

		@Override
		@OverridingMethodsMustInvokeSuper
		public void initialize(@Nonnull HttpRequest request) throws IOException {
			setDefaultRequestParams(request);
		}

		/**
		 * Sets common parameters to every request made through this manager,
		 * which will be applied through the {@link HttpRequestInitializer}.
		 * 
		 * @param request
		 *            The request to set the parameters in
		 */
		protected static void setDefaultRequestParams(@Nonnull HttpRequest request) {
			request.setConnectTimeout(NetworkConstants.DEFAULT_CONN_TIMEOUT);
			request.setReadTimeout(NetworkConstants.DEFAULT_READ_TIMEOUT);
			request.setNumberOfRetries(NetworkConstants.REQUEST_RETRIES);
			final HttpBackOffUnsuccessfulResponseHandler handler = new HttpBackOffUnsuccessfulResponseHandler(
					NetworkConstants.DEFAULT_BACKOFF);
			handler.setBackOffRequired(NetworkConstants.DEFAULT_BACKOFF_REQUIRED);
			request.setUnsuccessfulResponseHandler(handler);
			// TODO: test this when using NetHttpTransport
			request.setIOExceptionHandler(NetworkConstants.IO_EXCEPTION_HANDLER);
			request.setThrowExceptionOnExecuteError(false);

			// enable logging only when in debug mode
			request.setLoggingEnabled(DroidConfig.DEBUG);
			request.setCurlLoggingEnabled(DroidConfig.DEBUG);
		}
	}

}