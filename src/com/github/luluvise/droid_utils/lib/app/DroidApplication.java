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
package com.github.luluvise.droid_utils.lib.app;

import javax.annotation.Nonnull;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.github.luluvise.droid_utils.BuildConfig;
import com.github.luluvise.droid_utils.DroidConfig;
import com.github.luluvise.droid_utils.lib.DroidUtils;
import com.github.luluvise.droid_utils.logging.LogUtils;
import com.google.common.annotations.Beta;

/**
 * Subclass of {@link Application} with some utility methods.
 * 
 * In order to use this class you must create a subclass of it and declare it in
 * the manifest like this:
 * 
 * <code>
 *     <application
 *         android:name="com.mypackage.MyApplication"
 *     ...
 * </code>
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
public class DroidApplication extends Application {

	/**
	 * Flag indicating whether the application package is currently built with
	 * the "debuggable" attribute set to true.
	 * 
	 * Do NOT rely on this yet, as it's still incorrect due to an issue in ADT
	 * (v22).
	 */
	static final boolean DEBUGGABLE = BuildConfig.DEBUG;

	private static final String TAG = DroidApplication.class.getSimpleName();

	private volatile String mAppVersion;
	private volatile int mAppVersionCode;
	private volatile int mMemoryClass;
	private volatile boolean mIsDebuggable;

	private volatile int mActivityStack;

	private volatile Display mDefaultDisplay;

	@Override
	public void onCreate() {
		super.onCreate();

		// collect "debuggable" attribute value from application info
		mIsDebuggable = (0 != (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
		if (DroidConfig.DEBUG) {
			Log.e(TAG, "The application is running in DEBUG mode!");
			Log.i(TAG, "Debuggable flag in Manifest: " + mIsDebuggable);
		}

		try { // set application version
			final PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			mAppVersion = packageInfo.versionName;
			mAppVersionCode = packageInfo.versionCode;
		} catch (NameNotFoundException e) {
			// this should never happen
		}

		// max available application heap size
		int memoryClass = ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryClass();
		mMemoryClass = memoryClass * 1024 * 1024; // convert to bytes
		if (DroidConfig.DEBUG) {
			Log.i(TAG, "App available memory: " + mMemoryClass + " bytes");
			Log.i(TAG, "App total cores: " + DroidUtils.CPU_CORES);
			Log.i(TAG, "App available cores: " + Runtime.getRuntime().availableProcessors());
		}

		// loads the default display
		mDefaultDisplay = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();

		LogUtils.log(Log.ERROR, TAG, "onLowMemory()");
	}

	@Override
	@TargetApi(14)
	public void onTrimMemory(int level) {
		super.onTrimMemory(level);

		LogUtils.log(Log.WARN, TAG, "onTrimMemory() - level: " + level);
	}

	/**
	 * Returns whether the currently running application was built with the
	 * Manifest's "debuggable" attribute set to true (which means it has been
	 * exported and signed with a certificate).
	 * 
	 * Note that this is different from the {@link ApiConstants#DEBUG} flag
	 */
	public boolean isDebuggable() {
		return mIsDebuggable;
	}

	/**
	 * Returns the AndroidManifest application version from the
	 * {@link PackageInfo}
	 */
	public String getAppVersion() {
		return mAppVersion;
	}

	/**
	 * Returns the AndroidManifest application version code from the
	 * {@link PackageInfo}
	 */
	public int getAppVersionCode() {
		return mAppVersionCode;
	}

	/**
	 * Returns the maximum available application heap size, in bytes. This is
	 * useful to set the max size of memory caches within the application.
	 */
	public final int getMemoryClass() {
		return mMemoryClass;
	}

	/*
	 * Getters and setters for the application visible (between onResume() and
	 * onPause()) activity stack, which is used (with some approximation) to
	 * know if the application is effectively on the foreground (for displaying
	 * notifications and other maintenance stuff).
	 * 
	 * Note that there is a small window of time when the stack count can be 0
	 * even if the application is in the foreground. This happens when calling
	 * an activity from the top activity: its onPause() is called a short while
	 * before the new activity's onResume().
	 * 
	 * Call incrementActivityStack() and decrementActivityStack() from within
	 * every base activity class which application activities inherit from.
	 */

	/**
	 * Checks whether the application is currently on the device's foreground.
	 * 
	 * @return true if the app is on foreground, false otherwise
	 */
	public boolean isApplicationOnForeground() {
		return mActivityStack > 0;
	}

	/**
	 * Increments the application activity stack by one. Call this in the
	 * activity {@link Activity#onResume()}
	 * 
	 * @return The current (approximated) activity stack count
	 */
	public int incrementVisibleActivitiesStack() {
		return ++mActivityStack;
	}

	/**
	 * Decrements the application activity stack by one. Call this in the
	 * activity {@link Activity#onPause()}
	 * 
	 * @return The current (approximated) activity stack count
	 */
	public int decrementVisibleActivitiesStack() {
		return --mActivityStack;
	}

	/**
	 * Gets the current display's {@link DisplayMetrics}
	 */
	@Nonnull
	public final DisplayMetrics getDisplayMetrics() {
		final DisplayMetrics metrics = new DisplayMetrics();
		mDefaultDisplay.getMetrics(metrics);
		return metrics;
	}

}