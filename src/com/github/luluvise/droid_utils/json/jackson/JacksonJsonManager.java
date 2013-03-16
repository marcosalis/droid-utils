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
package com.github.luluvise.droid_utils.json.jackson;

import java.io.IOException;

import javax.annotation.CheckForNull;
import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.api.client.util.ObjectParser;

/**
 * Singleton class that provides JSON parsing utilities based on the Jackson
 * library for converting POJO (<i>Plain Old Java Objects</i>) data models into
 * JSON.
 * 
 * See {@link http://wiki.fasterxml.com/JacksonHome} for documentation.
 * 
 * TODO: do we really need a singleton enum here?
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Immutable
public enum JacksonJsonManager {
	INSTANCE;

	private final ObjectMapper mMapper;
	private final JacksonObjectParser mObjParser;

	/**
	 * Shortcut method to return the JsonManager singleton
	 */
	public static JacksonJsonManager get() {
		return INSTANCE;
	}

	/**
	 * Returns the global {@link ObjectParser} instance associated with the
	 * {@link ObjectMapper}
	 * 
	 * @return The {@link JacksonObjectParser} parser
	 */
	public static JacksonObjectParser getObjectParser() {
		return INSTANCE.mObjParser;
	}

	/**
	 * Returns the global {@link ObjectMapper}
	 * 
	 * @return The {@link ObjectMapper}
	 */
	public static ObjectMapper getObjectMapper() {
		return INSTANCE.mMapper;
	}

	/**
	 * Builds a new {@link JacksonHttpContent} from the source object.
	 * 
	 * @param source
	 *            The source object (keys must be specified for the mapping to
	 *            work)
	 * @return The built {@link JacksonHttpContent}
	 */
	public static JacksonHttpContent buildHttpContent(Object source) {
		return new JacksonHttpContent(source);
	}

	/**
	 * Shortcut method to parse a POJO object into a JSON string
	 * 
	 * @param data
	 *            The object to parse
	 * @return The string representation of that object (or null if the parse
	 *         failed for an IOException)
	 */
	@CheckForNull
	public static String toString(Object data) {
		try {
			return INSTANCE.mMapper.writeValueAsString(data);
		} catch (IOException e) {
			// TODO: print trace here?
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Private constructor (only used to initialise the singleton fields)
	 */
	private JacksonJsonManager() {
		// single global shared ObjectMapper instance
		mMapper = new ObjectMapper();
		// registering module for Guava data structures
		mMapper.registerModule(new GuavaModule());
		// global setting to write dates in ISO8601 format (not needed for now)
		// mMapper.configure(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS,
		// false);
		mObjParser = new JacksonObjectParser(mMapper);
	}

}
