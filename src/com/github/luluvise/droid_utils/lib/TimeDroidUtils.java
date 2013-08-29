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

import javax.annotation.Nonnull;

import android.content.Context;

import com.github.luluvise.droid_utils.R;
import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;

/**
 * Helper class containing general static utility methods to handle time,
 * relative time spans and time stamps.
 * 
 * @since 1.0
 * @author Marco Salis, Gerlac Farrus
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
	 * @param date
	 *            The date of the event
	 * @return The localized string
	 */
	@Nonnull
	public static CharSequence getTimespanString(@Nonnull Context context, @Nonnull Date date) {
		final long now = System.currentTimeMillis();
		final long time = date.getTime();

		// calculate the seconds span between now and the date
		final int span = (int) ((now - time) / 1000);

		// get time span string
		return getTimespanString(context, span);
	}

	/**
	 * Calculates and returns a string from the given timeSpan, for example
	 * "3 months ago" or "an hour ago".
	 * 
	 * @param context
	 *            {@link Context} to get the string resource from
	 * @param timeSpan
	 *            The time span in seconds
	 * @return The localized string
	 */
	@Nonnull
	@VisibleForTesting
	static String getTimespanString(@Nonnull Context context, int timeSpan) {

		// this also covers the (unlikely) case of a negative, hence future time
		if (timeSpan <= MINUTE * 5) {
			return context.getString(R.string.time_moment_ago);
		} else if (timeSpan < 45 * MINUTE) { // minutes
			return context.getString(R.string.time_minutes_ago, (int) (timeSpan / MINUTE));
		} else if (timeSpan <= 2 * HOUR) { // an hour
			return context.getString(R.string.time_hour_ago);
		} else if (timeSpan < DAY) { // hours
			return context.getString(R.string.time_few_hours_ago);
		} else if (timeSpan < 2 * DAY) { // yesterday
			return context.getString(R.string.time_yesterday);
		} else if (timeSpan < MONTH) { // days
			return context.getString(R.string.time_days_ago, (int) (timeSpan / DAY));
		} else if (timeSpan < 2 * MONTH) { // a month
			return context.getString(R.string.time_month_ago);
		} else if (timeSpan < YEAR) { // months
			return context.getString(R.string.time_months_ago, (int) (timeSpan / MONTH));
		} else if (timeSpan < 2 * YEAR) { // a year
			return context.getString(R.string.time_year_ago);
		} else { // years
			return context.getString(R.string.time_years_ago, (int) (timeSpan / YEAR));
		}
	}

}