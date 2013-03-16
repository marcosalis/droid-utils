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
package com.github.luluvise.droid_utils.json;

import java.io.IOException;

import javax.annotation.concurrent.Immutable;

import com.github.luluvise.droid_utils.DroidConfig;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.common.annotations.Beta;

/**
 * Singleton class that provides JSON parsing utilities based on a concrete
 * implementation of the Google's {@link JsonFactory} interface.
 * 
 * The JsonFactory pluggable library from the google-http-java-client library is
 * used as interface and for this implementation the Jackson JSON library is the
 * default.
 * 
 * See {@link http://code.google.com/p/google-http-java-client/wiki/JSON} for
 * documentation.
 * 
 * <b>Thread-safety:</b> Yes (Immutable)
 * 
 * @author Marco Salis
 */
@Beta
@Immutable
public enum GoogleJsonManager {
	INSTANCE;

	private final transient JsonFactory mFactory;
	private final transient JsonObjectParser mObjParser;

	/**
	 * Shortcut method to return the JsonManager singleton
	 */
	public static GoogleJsonManager get() {
		return INSTANCE;
	}

	/**
	 * Returns the currently used global JsonFactory instance
	 * 
	 * @return a JsonFactory object
	 */
	public static JsonFactory getFactory() {
		return INSTANCE.mFactory;
	}

	/**
	 * Returns the global JsonObjectParser instance associated with the
	 * JsonFactory
	 * 
	 * @return a JsonObjectParser parser
	 */
	public static JsonObjectParser getObjectParser() {
		return INSTANCE.mObjParser;
	}

	/**
	 * Builds a new JsonHttpContent from the source object using the default
	 * factory
	 * 
	 * @param source
	 *            The source object (keys must be specified for the mapping to
	 *            work)
	 * @return The built JsonHttpContent
	 */
	public static JsonHttpContent buildHttpContent(Object source) {
		return new JsonHttpContent(INSTANCE.mFactory, source);
	}

	/**
	 * Shortcut method to parse a POJO object into a JSON string
	 * 
	 * @param data
	 *            The object to parse
	 * @return The string representation of that object
	 */
	public static String toString(Object data) {
		try {
			return INSTANCE.mFactory.toString(data);
		} catch (IOException e) {
			if (DroidConfig.DEBUG) {
				e.printStackTrace();
			}
			return null;
		}
	}

	/**
	 * Private constructor (only used to initialise the singleton fields)
	 */
	private GoogleJsonManager() {
		// we use JacksonFactory as it's the currently fastest implementation
		mFactory = new JacksonFactory();
		mObjParser = new JsonObjectParser(mFactory);
	}

}
