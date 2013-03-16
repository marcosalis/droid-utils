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

import android.os.Environment;

/**
 * Helper class containing static methods to retrieve information and perform
 * operations on the device storage units, either internal or external.
 * 
 * @since 1.0
 * @author Marco Salis
 */
public class StorageUtils {

	private StorageUtils() {
		// hidden constructor, no instantiation needed
	}

	/**
	 * Check whether the device external storage is mounted using the
	 * {@link Environment} Android API helper methods.
	 * 
	 * @return true if the external storage is mounted, false otherwise
	 */
	public static boolean isExternalStorageMounted() {
		return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
	}

}