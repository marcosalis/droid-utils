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

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import android.app.Application;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.github.luluvise.droid_utils.logging.LogUtils;
import com.google.common.annotations.Beta;

/**
 * Application global network connection monitor.<br>
 * Useful to easily and reliably retrieve the device's current connection status
 * without querying the {@link ConnectivityManager}.
 * 
 * Must be initialized by calling {@link #register(Application)} within the
 * {@link Application#onCreate()} method.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@ThreadSafe
public enum ConnectionMonitor implements ConnectionMonitorInterface {
	INSTANCE;

	/**
	 * Shortcut method to return the {@link ConnectionMonitor} singleton
	 * instance.
	 */
	public static ConnectionMonitor get() {
		return INSTANCE;
	}

	private final static String TAG = ConnectionMonitor.class.getSimpleName();

	private final AtomicBoolean mConnectionActive;
	private final AtomicBoolean mIsRegistered;
	private final NetworkReceiver mNetReceiver = new NetworkReceiver() {
		@Override
		public void onConnectionActive(int type) {
			mConnectionActive.compareAndSet(false, true);
		}

		@Override
		public void onConnectionGone() {
			mConnectionActive.compareAndSet(true, false);
		}
	};

	private ConnectionMonitor() {
		// defaults to true to avoid issue at initialization
		mConnectionActive = new AtomicBoolean(true);
		mIsRegistered = new AtomicBoolean(false);
	}

	/**
	 * Initializes the {@link ConnectionMonitor}. It registers a
	 * {@link NetworkReceiver} in order to be notified about network state
	 * changes.
	 * 
	 * @param application
	 *            The {@link Application} object
	 */
	public void register(@Nonnull Application application) {
		if (mIsRegistered.compareAndSet(false, true)) {
			// permanently register the listener using the application context
			final IntentFilter filter = NetworkReceiver.getFilter();
			LocalBroadcastManager.getInstance(application).registerReceiver(mNetReceiver, filter);
		} else {
			LogUtils.log(Log.WARN, TAG, "ConnectionMonitor multiple initialization attempt");
		}
	}

	/**
	 * Unregisters the {@link NetworkReceiver} to stop being notified about
	 * network state changes.
	 * 
	 * @param application
	 *            The {@link Application} object
	 */
	public void unregister(@Nonnull Application application) {
		if (mIsRegistered.compareAndSet(true, false)) {
			LocalBroadcastManager.getInstance(application).unregisterReceiver(mNetReceiver);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isRegistered() {
		return mIsRegistered.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isNetworkActive() {
		return mConnectionActive.get();
	}

}
