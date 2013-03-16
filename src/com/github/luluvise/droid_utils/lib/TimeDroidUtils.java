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

import java.util.Date;
import java.util.UnknownFormatConversionException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import android.content.Context;
import android.text.format.DateUtils;

import com.github.luluvise.droid_utils.R;
import com.github.luluvise.droid_utils.logging.LogUtils;
import com.google.common.annotations.Beta;

/**
 * Helper class containing general static utility methods to handle time,
 * relative time spans and time stamps.
 * 
 * TODO: unit tests
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
public class TimeDroidUtils {

	/* conversions from seconds to other time units */
	public static final int MINUTE = 60;
	public static final int HOUR = 60 * MINUTE;
	public static final int DAY = 24 * HOUR;
	public static final int WEEK = 7 * DAY;
	public static final int MONTH = 30 * DAY;
	public static final int YEAR = 12 * MONTH;

	private TimeDroidUtils() {
		// hidden constructor, no instantiation needed
	}

	/**
	 * Returns a localized time span string from the given event date to the
	 * current system time, for example "3 months ago".
	 * 
	 * This method relies on the {@link DateUtils} class for some of the string
	 * definitions.
	 * 
	 * @param date
	 *            The date of the event
	 * @return The localized string or null if an error occurred
	 */
	@CheckForNull
	public static CharSequence getTimespanString(@Nonnull Context context, @Nonnull Date date) {
		final long now = System.currentTimeMillis();
		final long time = date.getTime();
		// calculate the seconds span between now and the date
		final int span = (int) ((now - time) / 1000); // to seconds
		// DateUtils.
		// this also covers the (unlikely) case of a negative, hence future time
		if (span < MINUTE) {
			return context.getString(R.string.time_moment_ago);
		} else if (span < 2 * MINUTE) {
			return context.getString(R.string.time_minute_ago);
		} else if (span < 45 * MINUTE) { // minutes
			return getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS);
		} else if (span < 2 * HOUR) {
			return context.getString(R.string.time_hour_ago);
		} else if (span < DAY) { // hours
			return getRelativeTimeSpanString(time, now, DateUtils.HOUR_IN_MILLIS);
		} else if (span < 2 * DAY) {
			return context.getString(R.string.time_yesterday);
		} else if (span < MONTH) { // days
			return context.getString(R.string.time_days_ago, (int) (span / DAY));
		} else if (span < 2 * MONTH) {
			return context.getString(R.string.time_month_ago);
		} else if (span < YEAR) { // months
			return context.getString(R.string.time_months_ago, (int) (span / MONTH));
		} else if (span < 18 * MONTH) {
			return context.getString(R.string.time_year_ago);
		} else { // years
			return context.getString(R.string.time_years_ago, (int) (span / YEAR));
		}
	}

	@CheckForNull
	private static CharSequence getRelativeTimeSpanString(long time, long now, long minResolution) {
		CharSequence spanString = null;
		try {
			spanString = DateUtils.getRelativeTimeSpanString(time, now, minResolution);
		} catch (UnknownFormatConversionException e) {
			// with some Android version, it unexpectedly throws this exception
			LogUtils.logException(e);
		}
		return spanString;
	}

}
