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
package com.github.luluvise.droid_utils.json.rest;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import com.github.luluvise.droid_utils.annotations.NotForUIThread;
import com.github.luluvise.droid_utils.concurrent.PriorityThreadFactory;
import com.github.luluvise.droid_utils.http.HttpConnectionManager;
import com.github.luluvise.droid_utils.http.HttpConnectionManagerInterface;
import com.github.luluvise.droid_utils.json.jackson.JacksonJsonManager;
import com.github.luluvise.droid_utils.json.jackson.JacksonObjectParser;
import com.github.luluvise.droid_utils.json.model.JsonModel;
import com.github.luluvise.droid_utils.lib.HashUtils;
import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.util.ObjectParser;
import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * General abstract class for an HTTP request which can return a
 * {@link JsonModel} that gets parsed with the default {@link ObjectParser}. It
 * implements {@link Callable} so that in can be directly executed in an
 * executor without wrapping it.
 * 
 * An {@link HttpRequest} must be used by the
 * {@link #execute(HttpConnectionManagerInterface)} method to perform the actual
 * request.
 * 
 * There are some extra features that can be used:
 * <ul>
 * <li>Support for use in caches: a content can be stored in a cache map by
 * using an hashed representation of its URL by calling {@link #hash()} or its
 * static equivalent {@link #hashUrl(String)} by passing the URL of any request.
 * </li>
 * <li>A thread pool executor ({@link #REQUESTS_EXECUTOR}) that can be used by
 * subclasses to directly execute requests</li>
 * </ul>
 * 
 * Note that most of the implemented methods perform network connections so they
 * can't be called from the UI thread. Callers must implement their own task
 * mechanism to handle UI updates following the requests.
 * 
 * <b>Thread-safety:</b> Yes. Subclasses might <i>not</i> be thread-safe.
 * 
 * @since 1.0
 * @author Marco Salis
 * 
 * @param <E>
 *            The JSON model type that this request will be returning.
 */
@Beta
@ThreadSafe
public abstract class AbstractModelRequest<E extends JsonModel> implements Callable<E> {

	static {
		/*
		 * The private executor is set with the same values as the default in
		 * Android's AsyncTask class. Further tuning could be made in next
		 * releases after some benchmarking on various devices.
		 */
		final int CORE_POOL_SIZE = 5;
		final int MAXIMUM_POOL_SIZE = 128;
		final int KEEP_ALIVE = 1;
		final BlockingQueue<Runnable> poolWorkQueue = new LinkedBlockingQueue<Runnable>(10);

		REQUESTS_EXECUTOR = Executors.unconfigurableExecutorService(new ThreadPoolExecutor(
				CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, poolWorkQueue,
				new PriorityThreadFactory("AbstractModelRequest executor thread")));
	}

	/* default components for HTTP requests and JSON parsing */

	protected static final ExecutorService REQUESTS_EXECUTOR;
	protected static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();

	protected static final HttpConnectionManager DEFAULT_CONN_MANAGER = HttpConnectionManager.get();
	private static final JacksonObjectParser OBJECT_PARSER = JacksonJsonManager.getObjectParser();

	protected final String mHttpMethod;

	@CheckForNull
	@GuardedBy("this")
	private volatile String mRequestUrl;

	/**
	 * Lazily initialized hash value for this request. Override the
	 * {@link #hash()} method to provide a custom value.
	 */
	@GuardedBy("this")
	private volatile String mHash; // lazily initialized
	/**
	 * {@link ModelResponseCallback} for the request. Update this before
	 * executing the request or it won't be used.
	 */
	@GuardedBy("this")
	protected volatile ModelResponseCallback<E> mCallback;

	@GuardedBy("this")
	private volatile HttpUnsuccessfulResponseHandler mHttpUnsuccessfulResponseHandler;

	/**
	 * Constructor for a model request whose request URL is generated
	 * dinamically. Call {@link #setRequestUrl(String)} to set the URL.
	 * 
	 * @param httpMethod
	 *            The HTTP method for the request (must be one of the ones
	 *            listed in {@link HttpMethods})
	 * @throws IllegalArgumentException
	 *             if httpMethod is null
	 */
	public AbstractModelRequest(@Nonnull String httpMethod) {
		Preconditions.checkNotNull(httpMethod);
		mHttpMethod = httpMethod;
		mRequestUrl = null;
	}

	/**
	 * Constructor to pass HTTP method and URL to use for the request.
	 * 
	 * @param httpMethod
	 *            The HTTP method for the request (must be one of the ones
	 *            listed in {@link HttpMethods})
	 * @param requestUrl
	 *            The full request URL
	 * @throws IllegalArgumentException
	 *             if any parameter is null
	 */
	public AbstractModelRequest(@Nonnull String httpMethod, @Nonnull String requestUrl) {
		Preconditions.checkNotNull(httpMethod);
		Preconditions.checkNotNull(requestUrl);
		mHttpMethod = httpMethod;
		mRequestUrl = requestUrl;
	}

	/**
	 * Dynamically set the request URL (and invalidate the request's hash value
	 * if already set).
	 * 
	 * Keep in mind that overriding the request URL after a hash key (
	 * {@link #hash()} value) has been lazily initialized can create caches
	 * inconsistencies.
	 * 
	 * @param requestUrl
	 *            The request URL string
	 * @throws IllegalArgumentException
	 *             if requestUrl is null
	 */
	public synchronized final void setRequestUrl(@Nonnull String requestUrl) {
		Preconditions.checkNotNull(requestUrl);
		mRequestUrl = requestUrl;
		mHash = null; // reset hash value
	}

	/**
	 * Returns the request's URL. Override this in subclasses if the request URL
	 * cannot be generated at object instantiation.
	 */
	@CheckForNull
	public synchronized String getRequestUrl() {
		return mRequestUrl;
	}

	/**
	 * Sets a custom {@link HttpUnsuccessfulResponseHandler} to handle error
	 * responses.<br>
	 * Call this before executing the request, otherwise the call will have no
	 * effect.
	 * 
	 * @param handler
	 *            The handler to set into the request
	 */
	public void setHttpUnsuccessfulResponseHandler(@Nullable HttpUnsuccessfulResponseHandler handler) {
		mHttpUnsuccessfulResponseHandler = handler;
	}

	/**
	 * Gets the currently set {@link HttpUnsuccessfulResponseHandler}.
	 * 
	 * @return The handler or null if not set
	 */
	@CheckForNull
	public HttpUnsuccessfulResponseHandler getHttpUnsuccessfulResponseHandler() {
		return mHttpUnsuccessfulResponseHandler;
	}

	/**
	 * Returns the concrete request class HTTP method as per {@link HttpMethods}
	 */
	@Nonnull
	protected final String getHttpMethod() {
		return mHttpMethod;
	}

	/*
	 * Methods for executing the request, either by using the default HTTP
	 * connection manager or injecting a custom one.
	 */

	@Override
	@CheckForNull
	@NotForUIThread
	public final E call() throws Exception {
		return execute();
	}

	/**
	 * Synchronously executes the request.
	 * 
	 * @return The model if it can be retrieved or null
	 * @throws Exception
	 *             If something went wrong
	 */
	@CheckForNull
	@NotForUIThread
	public abstract E execute() throws Exception;

	/**
	 * Synchronously executes a request like {@link #execute()} using a custom
	 * {@link HttpConnectionManagerInterface}
	 */
	@CheckForNull
	@NotForUIThread
	public abstract E execute(@Nonnull HttpConnectionManagerInterface connManager) throws Exception;

	/**
	 * Asynchronously executes a request using the passed
	 * {@link ModelResponseCallback} to get the response. The class default
	 * {@link Executor} ({@link #REQUESTS_EXECUTOR} is used to submit the task.
	 * 
	 * Note that the callback methods are not executed from the UI thread.
	 * 
	 * Properly override {@link #execute()} to use a custom
	 * {@link HttpConnectionManagerInterface} for the request execution.
	 * 
	 * @param callback
	 *            The callback to propagate the result to
	 * @throws IllegalArgumentException
	 *             If the callback object is null
	 * @throws IllegalStateException
	 *             If the request object hasn't been initialised properly
	 */
	@Nonnull
	public Future<E> executeAsync(@Nonnull ModelResponseCallback<E> callback) {
		Preconditions.checkNotNull(callback);
		// save a callback reference
		mCallback = callback;
		// submit task to default executor
		return REQUESTS_EXECUTOR.submit(this);
	}

	/**
	 * Returns a 128-bit unique hash code string representation for this request
	 * to be used as a key in caches.
	 * 
	 * The function currently uses the {@code murmur3_128} algorithm giving the
	 * URL request as the only input parameter.
	 * 
	 * Subclasses might override this to implement different policies to use for
	 * cache keys.
	 * 
	 * @return The String representation of this hash function
	 */
	@Nonnull
	public synchronized String hash() {
		if (mHash == null) { // lazy initialization
			final String requestUrl = mRequestUrl; // FindBugs, don't complain
			if (requestUrl != null) {
				// TODO: performance benchmark between murmur3_128 and MD5
				mHash = hashUrl(requestUrl);
			} else {
				mHash = String.valueOf(hashCode());
			}
		}
		return mHash;
	}

	/**
	 * Returns a 128-bit unique hash code string representation for the given
	 * URL. The returned hash will match the hash dynamically generated for a
	 * request with the same URL.
	 * 
	 * <b>Warning:</b> Note that a subclass can override its {@link #hash()}
	 * method: in this case, the hash string generated by this method won't
	 * match the actual hash value, hence the key, for the model of that
	 * request. Check the request documentation.
	 * 
	 * @param url
	 *            The URL to hash
	 * @return The String representation of this hash function
	 */
	@Nonnull
	public static String hashUrl(@Nonnull String url) {
		return HashUtils.getHash(HASH_FUNCTION, url);
	}

	/**
	 * Set 'Authorization' header for this request
	 * 
	 * @param request
	 *            The request to set the authorization in
	 * @param authorization
	 *            The authorization string
	 */
	protected void setAuth(@Nonnull HttpRequest request, @Nullable String authorization) {
		request.getHeaders().setAuthorization(authorization);
	}

	/**
	 * Get the parser for this request. Override this method from subclasses to
	 * use another object parser.
	 * 
	 * @return The {@link ObjectParser} to use for the Json parsing
	 */
	@Nonnull
	protected ObjectParser getParser() {
		// use default JSON object parser
		return OBJECT_PARSER;
	}

	/**
	 * Must be implemented by subclasses to configure the {@link HttpRequest}
	 * object prior to be executed, for example to add an authorization header
	 * or a content to the request.
	 * 
	 * @param request
	 *            The current HTTP request
	 */
	protected abstract void configRequest(@Nonnull HttpRequest request);

	@CheckForNull
	protected abstract E parseResponse(HttpResponse response) throws IOException,
			IllegalArgumentException;

	/**
	 * Returns the concrete request class logging TAG. Override this to provide
	 * a custom TAG for subclasses that can be used for debugging purposes.
	 */
	protected abstract String getTag();

}