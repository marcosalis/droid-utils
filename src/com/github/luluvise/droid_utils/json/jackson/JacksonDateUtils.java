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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.util.ISO8601Utils;
import com.fasterxml.jackson.databind.util.StdDateFormat;

/**
 * Helper class that contains static utility methods and classes to
 * (de)/serialize Dates into JSON.
 * 
 * Documentation: see {@link StdDateFormat} class.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Immutable
public class JacksonDateUtils {

	private JacksonDateUtils() {
		// hidden constructor, no instantiation needed
	}

	/**
	 * Deserialize a Date from the {@code "yyyy-MM-dd"} format
	 */
	public static class SimpleDateDeserializer extends JsonDeserializer<Date> {
		@Override
		public Date deserialize(JsonParser jsonparser, DeserializationContext deserializationcontext)
				throws IOException, JsonProcessingException {
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
			String date = jsonparser.getText();
			try {
				return format.parse(date);
			} catch (ParseException e) {
				return null;
			}
		}
	}

	/**
	 * Serialize a Date to the {@code "yyyy-MM-dd"} string format
	 */
	public static class SimpleDateSerializer extends JsonSerializer<Date> {
		@Override
		public void serialize(Date value, JsonGenerator jgen, SerializerProvider provider)
				throws IOException, JsonProcessingException {
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
			String formattedDate = formatter.format(value);
			jgen.writeString(formattedDate);
		}
	}

	public static class MicrosecondsISO8601Date {

		/**
		 * Parse a ISO8601 with microseconds date string to an equivalent
		 * {@link java.util.Date} object.
		 * 
		 * Accepted string format is like {@code 2012-07-26T18:01:45.430133Z}
		 */
		public static Date parse(String date) {
			if (date == null || !looksLikeISO8601(date))
				return null;
			try {
				// check that it's a valid date
				int dotIndex = date.lastIndexOf('.'); // milliseconds after here
				int length = date.length();
				if (dotIndex != -1 && dotIndex < length - 4) { // 3 chars min
					String microsecs = date.substring(dotIndex + 1, length - 1);
					switch (microsecs.length()) {
					case 3: // we already have a standard ISO8601 string
						return ISO8601Utils.parse(date);
					case 6: // date of 2012-07-26T18:01:45.430133Z format
						return ISO8601Utils.parse(date.substring(0, length - 4) + "Z");
					}
				}
			} catch (IllegalArgumentException e) {
				return null; // no can do
			}
			return null;
		}

		/**
		 * Decrements the last digit of the passed microseconds ISO8601 date.
		 * 
		 * @param date
		 * @return
		 */
		public static String decrementMicrosecond(String date) {
			if (date == null || !looksLikeISO8601(date))
				return null;
			int length = date.length();
			int dotIndex = date.lastIndexOf('.');
			int lastDigits = Integer.valueOf(date.substring(dotIndex + 1, length - 1));
			return date.substring(0, dotIndex + 1) + (--lastDigits) + "Z";
		}

		/**
		 * Copied from {@link StdDateFormat#looksLikeISO8601(String)}
		 * 
		 * @return true if the string appears to be an ISO8601 date
		 */
		public static boolean looksLikeISO8601(String dateStr) {
			if (dateStr.length() >= 5 && Character.isDigit(dateStr.charAt(0))
					&& Character.isDigit(dateStr.charAt(3)) && dateStr.charAt(4) == '-') {
				return true;
			}
			return false;
		}
	}

}
