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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import com.github.luluvise.droid_utils.logging.LogUtils;
import com.google.common.annotations.Beta;

/**
 * Helper class containing general static utility methods of any Java-related
 * kind.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@ThreadSafe
public class JavaUtils {

	/**
	 * Single application global {@link Random} generator that can be used for
	 * randomizations (do not use for security-sensitive components)
	 */
	public static final Random RANDOM = new Random();

	private JavaUtils() {
		// hidden constructor, no instantiation needed
	}

	/**
	 * Check if a String starts with a specified prefix (case insensitive).
	 * 
	 * @see java.lang.String#startsWith(String)
	 * @param str
	 *            the String to check, may be null
	 * @param prefix
	 *            the prefix to find, may be null
	 * @return <code>true</code> if the String starts with the prefix or both
	 *         <code>null</code>
	 */
	public static boolean startsWithIgnoreCase(String str, String prefix) {
		if (str == null || prefix == null) {
			return (str == null && prefix == null);
		}
		if (prefix.length() > str.length()) {
			return false;
		}
		return str.regionMatches(true, 0, prefix, 0, prefix.length());
	}

	/**
	 * Return an object from the passed array using a random generator for the
	 * index to get it from.
	 * 
	 * @param array
	 *            The array to retrieve the item from
	 * @return The item at the pseudo-randomly generated index, or null if the
	 *         array is empty
	 */
	public static final <E> E getRandom(E[] array) {
		if (array.length == 0) { // avoids out of bounds exceptions
			return null;
		}
		final int randomIndex = RANDOM.nextInt(array.length);
		return array[randomIndex];
	}

	/**
	 * Return an object from the passed {@link List} using a random generator
	 * for the index to get it from.
	 * 
	 * @param list
	 *            The list to retrieve the item from
	 * @return The item at the pseudo-randomly generated index, or null if the
	 *         list is empty
	 */
	public static final <E> E getRandom(List<E> list) {
		if (list.size() == 0) {
			return null;
		}
		final int randomIndex = RANDOM.nextInt(list.size());
		return list.get(randomIndex);
	}

	// APACHE-COMMON-LANG LIBRARY METHODS

	/**
	 * Following methods are taken and modified from the apache-commons-lang
	 * library.<br>
	 * License: {@link http://www.apache.org/licenses/LICENSE-2.0}
	 */

	/**
	 * Escapes a Java string.
	 * 
	 * @param str
	 *            String to escape values in
	 * @param escapeSingleQuotes
	 *            escapes single quotes if <code>true</code>
	 * @param escapeForwardSlash
	 * @return the escaped string
	 */
	public static String escapeJavaString(@Nonnull String str, boolean escapeSingleQuotes,
			boolean escapeForwardSlash) {
		try {
			StringWriter writer = new StringWriter(str.length() * 2);
			escapeJavaString(writer, str, escapeSingleQuotes, escapeForwardSlash);
			return writer.toString();
		} catch (IOException ioe) {
			LogUtils.logException(ioe);
			// this should never ever happen while writing to a StringWriter
			return null;
		}
	}

	/**
	 * Worker method for the {@link #escapeJavaString(String)} method.
	 * 
	 * @param out
	 *            write to receieve the escaped string
	 * @param str
	 *            String to escape values in, may be null
	 * @param escapeSingleQuote
	 *            escapes single quotes if <code>true</code>
	 * @param escapeForwardSlash
	 * @throws IOException
	 *             if an IOException occurs
	 */
	private static void escapeJavaString(@Nonnull Writer out, @Nonnull String str,
			boolean escapeSingleQuote, boolean escapeForwardSlash) throws IOException {
		int sz;
		sz = str.length();
		for (int i = 0; i < sz; i++) {
			char ch = str.charAt(i);

			// handle unicode
			if (ch > 0xfff) {
				out.write("\\u" + hex(ch));
			} else if (ch > 0xff) {
				out.write("\\u0" + hex(ch));
			} else if (ch > 0x7f) {
				out.write("\\u00" + hex(ch));
			} else if (ch < 32) {
				switch (ch) {
				case '\b':
					out.write('\\');
					out.write('b');
					break;
				case '\n':
					out.write('\\');
					out.write('n');
					break;
				case '\t':
					out.write('\\');
					out.write('t');
					break;
				case '\f':
					out.write('\\');
					out.write('f');
					break;
				case '\r':
					out.write('\\');
					out.write('r');
					break;
				default:
					if (ch > 0xf) {
						out.write("\\u00" + hex(ch));
					} else {
						out.write("\\u000" + hex(ch));
					}
					break;
				}
			} else {
				switch (ch) {
				case '\'':
					if (escapeSingleQuote) {
						out.write('\\');
					}
					out.write('\'');
					break;
				case '"':
					out.write('\\');
					out.write('"');
					break;
				case '\\':
					out.write('\\');
					out.write('\\');
					break;
				case '/':
					if (escapeForwardSlash) {
						out.write('\\');
					}
					out.write('/');
					break;
				default:
					out.write(ch);
					break;
				}
			}
		}
	}

	/**
	 * Returns an upper case hexadecimal <code>String</code> for the given
	 * character.
	 * 
	 * @param ch
	 *            The character to convert.
	 * @return An upper case hexadecimal <code>String</code>
	 */
	private static String hex(char ch) {
		return Integer.toHexString(ch).toUpperCase(Locale.ENGLISH);
	}

	// -END- APACHE-COMMON-LANG LIBRARY METHODS

}