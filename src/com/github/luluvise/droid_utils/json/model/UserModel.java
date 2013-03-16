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
package com.github.luluvise.droid_utils.json.model;

/**
 * Common interface for models representing users.
 * 
 * @since 1.0
 * @author Marco Salis
 */
public interface UserModel {

	/**
	 * An unique string identificator for the user. Its value ranges can vary
	 * depending on the implementation and the user type represented.
	 */
	public String getId();

	public String getFirstName();

	public String getLastName();

	public String getFullName();
}