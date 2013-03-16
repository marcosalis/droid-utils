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
package com.github.luluvise.droid_utils.lib;

import android.os.AsyncTask;

import com.google.common.annotations.Beta;

/**
 * Abstract extension of Android's library class {@link AsyncTask} which allows
 * its callers to pass a callback and be asynchronously notified when the task
 * is completed and get the task result.
 * 
 * Subclasses must take care of:
 * <ul>
 * <li>Providing a constructor or setter for the callback instance, and holding
 * a reference to it.</li>
 * <li>Dereferencing the callback field after the result has been notified to
 * the caller to avoid memory leaks</li>
 * </ul>
 * 
 * CallbackAsyncTask facilitates AsyncTask unit testing: to avoid unit test
 * deadlocks while waiting for the onPostExecute() method to be called (which
 * would never happen), if the mIsTesting flag is set the result is propagated
 * within the setResult() method, that must be called from a non-UI thread
 * (usually inside the doInBackground() method right before its return).
 * 
 * @author Marco Salis
 */
@Beta
public abstract class CallbackAsyncTask<Params, Progress, Result> extends
		AsyncTask<Params, Progress, Result> {

	/**
	 * Indicates whether we are executing the task within a unit test
	 */
	protected final boolean mIsTesting;

	/**
	 * @param isTesting
	 *            true if we are inside an Android unit test, false otherwise
	 */
	public CallbackAsyncTask(boolean isTesting) {
		mIsTesting = isTesting;
	}

	/**
	 * Utility method to be called as a result value of the return method of a
	 * doInBackground() method like this:
	 * 
	 * <pre>
	 * @Override
	 * protected TaskResult doInBackground(Void... params) {
	 * 		[...]
	 * 		return setResult(TaskResult.SUCCESS);
	 * }
	 * </pre>
	 * 
	 * Subclasses must return here the result value to the callback if the
	 * mIsTesting flag is set, otherwise this method must simply return the
	 * parameter and the callback will be handled in the onPostExecute().
	 * 
	 * This method's implementations should be synchronized to ensure memory
	 * consistency.
	 * 
	 * @param result
	 *            The task result that will be propagated
	 */
	protected abstract TaskResult setResult(TaskResult result);

	/**
	 * Set of results to define the possible "exit states" of a task
	 */
	public static enum TaskResult {
		SUCCESS,
		FAILURE,
		CANCELED,
		EXCEPTION
	}

	/**
	 * Simple callback interface to allow caller get a task result
	 */
	public static interface TaskResultCallback {

		public void onResult(TaskResult result);

	}

}
