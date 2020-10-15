/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2020 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.std.nanotime;

import io.questdb.std.IntList;
import io.questdb.std.Numbers;
import io.questdb.std.NumericException;
import io.questdb.std.ObjList;
import io.questdb.std.str.CharSink;

public class GenericNanoTimestampFormat extends AbstractNanoTimestampFormat {
    private final IntList compiledOps;
    private final ObjList<String> delimiters;

    public GenericNanoTimestampFormat(IntList compiledOps, ObjList<String> delimiters) {
        this.compiledOps = compiledOps;
        this.delimiters = delimiters;
    }

    @Override
    public void format(long nanos, NanoTimestampLocale locale, CharSequence timeZoneName, CharSink sink) {
        int day = -1;
        int month = -1;
        int year = Integer.MIN_VALUE;
        int hour = -1;
        int minute = -1;
        int second = -1;
        int dayOfWeek = -1;
        boolean leap = false;
        int millis = -1;
        int micros0 = -1;
        int nanos0 = -1;

        for (int i = 0, n = compiledOps.size(); i < n; i++) {
            int op = compiledOps.getQuick(i);
            switch (op) {

                // AM/PM
                case DateFormatCompiler.OP_AM_PM:
                    if (hour == -1) {
                        hour = NanoTimestamps.getHourOfDay(nanos);
                    }
                    NanoTimestampFormatUtils.appendAmPm(sink, hour, locale);
                    break;

                // NANOS
                case DateFormatCompiler.OP_NANOS_ONE_DIGIT:
                case DateFormatCompiler.OP_NANOS_GREEDY:
                    if (nanos0 == -1) {
                        nanos0 = NanoTimestamps.getNanosOfSecond(nanos);
                    }
                    sink.put(nanos0);
                    break;

                case DateFormatCompiler.OP_NANOS_THREE_DIGITS:
                    if (nanos0 == -1) {
                        nanos0 = NanoTimestamps.getNanosOfSecond(nanos);
                    }
                    NanoTimestampFormatUtils.append00(sink, nanos0);
                    break;

                // MICROS
                case DateFormatCompiler.OP_MICROS_ONE_DIGIT:
                case DateFormatCompiler.OP_MICROS_GREEDY:
                    if (micros0 == -1) {
                        micros0 = NanoTimestamps.getMicrosOfSecond(nanos);
                    }
                    sink.put(micros0);
                    break;

                case DateFormatCompiler.OP_MICROS_THREE_DIGITS:
                    if (micros0 == -1) {
                        micros0 = NanoTimestamps.getMicrosOfSecond(nanos);
                    }
                    NanoTimestampFormatUtils.append00(sink, micros0);
                    break;

                // MILLIS
                case DateFormatCompiler.OP_MILLIS_ONE_DIGIT:
                case DateFormatCompiler.OP_MILLIS_GREEDY:
                    if (millis == -1) {
                        millis = NanoTimestamps.getMillisOfSecond(nanos);
                    }
                    sink.put(millis);
                    break;

                case DateFormatCompiler.OP_MILLIS_THREE_DIGITS:
                    if (millis == -1) {
                        millis = NanoTimestamps.getMillisOfSecond(nanos);
                    }
                    NanoTimestampFormatUtils.append00(sink, millis);
                    break;

                // SECOND
                case DateFormatCompiler.OP_SECOND_ONE_DIGIT:
                case DateFormatCompiler.OP_SECOND_GREEDY:
                    if (second == -1) {
                        second = NanoTimestamps.getSecondOfMinute(nanos);
                    }
                    sink.put(second);
                    break;

                case DateFormatCompiler.OP_SECOND_TWO_DIGITS:
                    if (second == -1) {
                        second = NanoTimestamps.getSecondOfMinute(nanos);
                    }
                    NanoTimestampFormatUtils.append0(sink, second);
                    break;


                // MINUTE
                case DateFormatCompiler.OP_MINUTE_ONE_DIGIT:
                case DateFormatCompiler.OP_MINUTE_GREEDY:
                    if (minute == -1) {
                        minute = NanoTimestamps.getMinuteOfHour(nanos);
                    }
                    sink.put(minute);
                    break;

                case DateFormatCompiler.OP_MINUTE_TWO_DIGITS:
                    if (minute == -1) {
                        minute = NanoTimestamps.getMinuteOfHour(nanos);
                    }
                    NanoTimestampFormatUtils.append0(sink, minute);
                    break;


                // HOUR (0-11)
                case DateFormatCompiler.OP_HOUR_12_ONE_DIGIT:
                case DateFormatCompiler.OP_HOUR_12_GREEDY:
                    if (hour == -1) {
                        hour = NanoTimestamps.getHourOfDay(nanos);
                    }
                    NanoTimestampFormatUtils.appendHour12(sink, hour);
                    break;

                case DateFormatCompiler.OP_HOUR_12_TWO_DIGITS:
                    if (hour == -1) {
                        hour = NanoTimestamps.getHourOfDay(nanos);
                    }
                    NanoTimestampFormatUtils.appendHour12Padded(sink, hour);
                    break;

                // HOUR (1-12)
                case DateFormatCompiler.OP_HOUR_12_ONE_DIGIT_ONE_BASED:
                case DateFormatCompiler.OP_HOUR_12_GREEDY_ONE_BASED:
                    if (hour == -1) {
                        hour = NanoTimestamps.getHourOfDay(nanos);
                    }
                    NanoTimestampFormatUtils.appendHour121(sink, hour);
                    break;

                case DateFormatCompiler.OP_HOUR_12_TWO_DIGITS_ONE_BASED:
                    if (hour == -1) {
                        hour = NanoTimestamps.getHourOfDay(nanos);
                    }
                    NanoTimestampFormatUtils.appendHour121Padded(sink, hour);
                    break;

                // HOUR (0-23)
                case DateFormatCompiler.OP_HOUR_24_ONE_DIGIT:
                case DateFormatCompiler.OP_HOUR_24_GREEDY:
                    if (hour == -1) {
                        hour = NanoTimestamps.getHourOfDay(nanos);
                    }
                    sink.put(hour);
                    break;

                case DateFormatCompiler.OP_HOUR_24_TWO_DIGITS:
                    if (hour == -1) {
                        hour = NanoTimestamps.getHourOfDay(nanos);
                    }
                    NanoTimestampFormatUtils.append0(sink, hour);
                    break;

                // HOUR (1 - 24)
                case DateFormatCompiler.OP_HOUR_24_ONE_DIGIT_ONE_BASED:
                case DateFormatCompiler.OP_HOUR_24_GREEDY_ONE_BASED:
                    if (hour == -1) {
                        hour = NanoTimestamps.getHourOfDay(nanos);
                    }
                    sink.put(hour + 1);
                    break;

                case DateFormatCompiler.OP_HOUR_24_TWO_DIGITS_ONE_BASED:
                    if (hour == -1) {
                        hour = NanoTimestamps.getHourOfDay(nanos);
                    }
                    NanoTimestampFormatUtils.append0(sink, hour + 1);
                    break;

                // DAY
                case DateFormatCompiler.OP_DAY_ONE_DIGIT:
                case DateFormatCompiler.OP_DAY_GREEDY:
                    if (day == -1) {
                        if (year == Integer.MIN_VALUE) {
                            year = NanoTimestamps.getYear(nanos);
                            leap = NanoTimestamps.isLeapYear(year);
                        }

                        if (month == -1) {
                            month = NanoTimestamps.getMonthOfYear(nanos, year, leap);
                        }

                        day = NanoTimestamps.getDayOfMonth(nanos, year, month, leap);
                    }
                    sink.put(day);
                    break;
                case DateFormatCompiler.OP_DAY_TWO_DIGITS:
                    if (day == -1) {
                        if (year == Integer.MIN_VALUE) {
                            year = NanoTimestamps.getYear(nanos);
                            leap = NanoTimestamps.isLeapYear(year);
                        }

                        if (month == -1) {
                            month = NanoTimestamps.getMonthOfYear(nanos, year, leap);
                        }

                        day = NanoTimestamps.getDayOfMonth(nanos, year, month, leap);
                    }
                    NanoTimestampFormatUtils.append0(sink, day);
                    break;

                case DateFormatCompiler.OP_DAY_NAME_LONG:
                    if (dayOfWeek == -1) {
                        dayOfWeek = NanoTimestamps.getDayOfWeekSundayFirst(nanos);
                    }
                    sink.put(locale.getWeekday(dayOfWeek));
                    break;

                case DateFormatCompiler.OP_DAY_NAME_SHORT:
                    if (dayOfWeek == -1) {
                        dayOfWeek = NanoTimestamps.getDayOfWeekSundayFirst(nanos);
                    }
                    sink.put(locale.getShortWeekday(dayOfWeek));
                    break;

                case DateFormatCompiler.OP_DAY_OF_WEEK:
                    if (dayOfWeek == -1) {
                        dayOfWeek = NanoTimestamps.getDayOfWeekSundayFirst(nanos);
                    }
                    sink.put(dayOfWeek);
                    break;

                // MONTH

                case DateFormatCompiler.OP_MONTH_ONE_DIGIT:
                case DateFormatCompiler.OP_MONTH_GREEDY:
                    if (month == -1) {
                        if (year == Integer.MIN_VALUE) {
                            year = NanoTimestamps.getYear(nanos);
                            leap = NanoTimestamps.isLeapYear(year);
                        }

                        month = NanoTimestamps.getMonthOfYear(nanos, year, leap);
                    }
                    sink.put(month);
                    break;
                case DateFormatCompiler.OP_MONTH_TWO_DIGITS:
                    if (month == -1) {
                        if (year == Integer.MIN_VALUE) {
                            year = NanoTimestamps.getYear(nanos);
                            leap = NanoTimestamps.isLeapYear(year);
                        }

                        month = NanoTimestamps.getMonthOfYear(nanos, year, leap);
                    }
                    NanoTimestampFormatUtils.append0(sink, month);
                    break;

                case DateFormatCompiler.OP_MONTH_SHORT_NAME:
                    if (month == -1) {
                        if (year == Integer.MIN_VALUE) {
                            year = NanoTimestamps.getYear(nanos);
                            leap = NanoTimestamps.isLeapYear(year);
                        }

                        month = NanoTimestamps.getMonthOfYear(nanos, year, leap);
                    }
                    sink.put(locale.getShortMonth(month - 1));
                    break;
                case DateFormatCompiler.OP_MONTH_LONG_NAME:
                    if (month == -1) {
                        if (year == Integer.MIN_VALUE) {
                            year = NanoTimestamps.getYear(nanos);
                            leap = NanoTimestamps.isLeapYear(year);
                        }

                        month = NanoTimestamps.getMonthOfYear(nanos, year, leap);
                    }
                    sink.put(locale.getMonth(month - 1));
                    break;

                // YEAR

                case DateFormatCompiler.OP_YEAR_ONE_DIGIT:
                case DateFormatCompiler.OP_YEAR_GREEDY:
                    if (year == Integer.MIN_VALUE) {
                        year = NanoTimestamps.getYear(nanos);
                        leap = NanoTimestamps.isLeapYear(year);
                    }
                    sink.put(year);
                    break;
                case DateFormatCompiler.OP_YEAR_TWO_DIGITS:
                    if (year == Integer.MIN_VALUE) {
                        year = NanoTimestamps.getYear(nanos);
                        leap = NanoTimestamps.isLeapYear(year);
                    }
                    NanoTimestampFormatUtils.append0(sink, year % 100);
                    break;
                case DateFormatCompiler.OP_YEAR_FOUR_DIGITS:
                    if (year == Integer.MIN_VALUE) {
                        year = NanoTimestamps.getYear(nanos);
                        leap = NanoTimestamps.isLeapYear(year);
                    }
                    NanoTimestampFormatUtils.append000(sink, year);
                    break;

                // ERA
                case DateFormatCompiler.OP_ERA:
                    if (year == Integer.MIN_VALUE) {
                        year = NanoTimestamps.getYear(nanos);
                        leap = NanoTimestamps.isLeapYear(year);
                    }
                    NanoTimestampFormatUtils.appendEra(sink, year, locale);
                    break;

                // TIMEZONE
                case DateFormatCompiler.OP_TIME_ZONE_SHORT:
                case DateFormatCompiler.OP_TIME_ZONE_GMT_BASED:
                case DateFormatCompiler.OP_TIME_ZONE_ISO_8601_1:
                case DateFormatCompiler.OP_TIME_ZONE_ISO_8601_2:
                case DateFormatCompiler.OP_TIME_ZONE_ISO_8601_3:
                case DateFormatCompiler.OP_TIME_ZONE_LONG:
                case DateFormatCompiler.OP_TIME_ZONE_RFC_822:
                    sink.put(timeZoneName);
                    break;

                // SEPARATORS
                default:
                    sink.put(delimiters.getQuick(-op - 1));
                    break;
            }
        }
    }

    @Override
    public long parse(CharSequence in, int lo, int hi, NanoTimestampLocale locale) throws NumericException {
        int day = 1;
        int month = 1;
        int year = 1970;
        int hour = 0;
        int minute = 0;
        int second = 0;
        int millis = 0;
        int micros = 0;
        int nanos = 0;
        int era = 1;
        int timezone = -1;
        long offset = Long.MIN_VALUE;
        int hourType = NanoTimestampFormatUtils.HOUR_24;
        int pos = lo;
        long l;
        int len;

        for (int i = 0, n = compiledOps.size(); i < n; i++) {
            int op = compiledOps.getQuick(i);
            switch (op) {

                // AM/PM
                case DateFormatCompiler.OP_AM_PM:
                    l = locale.matchAMPM(in, pos, hi);
                    hourType = Numbers.decodeLowInt(l);
                    pos += Numbers.decodeHighInt(l);
                    break;

                // NANOS
                case DateFormatCompiler.OP_NANOS_ONE_DIGIT:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    nanos = Numbers.parseInt(in, pos, ++pos);
                    break;

                case DateFormatCompiler.OP_NANOS_THREE_DIGITS:
                    NanoTimestampFormatUtils.assertRemaining(pos + 2, hi);
                    nanos = Numbers.parseInt(in, pos, pos += 3);
                    break;

                case DateFormatCompiler.OP_NANOS_GREEDY:
                    l = Numbers.parseIntSafely(in, pos, hi);
                    nanos = Numbers.decodeLowInt(l);
                    pos += Numbers.decodeHighInt(l);
                    break;

                // MICROS
                case DateFormatCompiler.OP_MICROS_ONE_DIGIT:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    micros = Numbers.parseInt(in, pos, ++pos);
                    break;

                case DateFormatCompiler.OP_MICROS_THREE_DIGITS:
                    NanoTimestampFormatUtils.assertRemaining(pos + 2, hi);
                    micros = Numbers.parseInt(in, pos, pos += 3);
                    break;

                case DateFormatCompiler.OP_MICROS_GREEDY:
                    l = Numbers.parseIntSafely(in, pos, hi);
                    micros = Numbers.decodeLowInt(l);
                    pos += Numbers.decodeHighInt(l);
                    break;

                // MILLIS
                case DateFormatCompiler.OP_MILLIS_ONE_DIGIT:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    millis = Numbers.parseInt(in, pos, ++pos);
                    break;

                case DateFormatCompiler.OP_MILLIS_THREE_DIGITS:
                    NanoTimestampFormatUtils.assertRemaining(pos + 2, hi);
                    millis = Numbers.parseInt(in, pos, pos += 3);
                    break;

                case DateFormatCompiler.OP_MILLIS_GREEDY:
                    l = Numbers.parseIntSafely(in, pos, hi);
                    millis = Numbers.decodeLowInt(l);
                    pos += Numbers.decodeHighInt(l);
                    break;

                // SECOND
                case DateFormatCompiler.OP_SECOND_ONE_DIGIT:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    second = Numbers.parseInt(in, pos, ++pos);
                    break;

                case DateFormatCompiler.OP_SECOND_TWO_DIGITS:
                    NanoTimestampFormatUtils.assertRemaining(pos + 1, hi);
                    second = Numbers.parseInt(in, pos, pos += 2);
                    break;

                case DateFormatCompiler.OP_SECOND_GREEDY:
                    l = Numbers.parseIntSafely(in, pos, hi);
                    second = Numbers.decodeLowInt(l);
                    pos += Numbers.decodeHighInt(l);
                    break;

                // MINUTE
                case DateFormatCompiler.OP_MINUTE_ONE_DIGIT:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    minute = Numbers.parseInt(in, pos, ++pos);
                    break;

                case DateFormatCompiler.OP_MINUTE_TWO_DIGITS:
                    NanoTimestampFormatUtils.assertRemaining(pos + 1, hi);
                    minute = Numbers.parseInt(in, pos, pos += 2);
                    break;

                case DateFormatCompiler.OP_MINUTE_GREEDY:
                    l = Numbers.parseIntSafely(in, pos, hi);
                    minute = Numbers.decodeLowInt(l);
                    pos += Numbers.decodeHighInt(l);
                    break;

                // HOUR (0-11)
                case DateFormatCompiler.OP_HOUR_12_ONE_DIGIT:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    hour = Numbers.parseInt(in, pos, ++pos);
                    if (hourType == NanoTimestampFormatUtils.HOUR_24) {
                        hourType = NanoTimestampFormatUtils.HOUR_AM;
                    }
                    break;

                case DateFormatCompiler.OP_HOUR_12_TWO_DIGITS:
                    NanoTimestampFormatUtils.assertRemaining(pos + 1, hi);
                    hour = Numbers.parseInt(in, pos, pos += 2);
                    if (hourType == NanoTimestampFormatUtils.HOUR_24) {
                        hourType = NanoTimestampFormatUtils.HOUR_AM;
                    }
                    break;

                case DateFormatCompiler.OP_HOUR_12_GREEDY:
                    l = Numbers.parseIntSafely(in, pos, hi);
                    hour = Numbers.decodeLowInt(l);
                    pos += Numbers.decodeHighInt(l);
                    if (hourType == NanoTimestampFormatUtils.HOUR_24) {
                        hourType = NanoTimestampFormatUtils.HOUR_AM;
                    }
                    break;

                // HOUR (1-12)
                case DateFormatCompiler.OP_HOUR_12_ONE_DIGIT_ONE_BASED:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    hour = Numbers.parseInt(in, pos, ++pos) - 1;
                    if (hourType == NanoTimestampFormatUtils.HOUR_24) {
                        hourType = NanoTimestampFormatUtils.HOUR_AM;
                    }
                    break;

                case DateFormatCompiler.OP_HOUR_12_TWO_DIGITS_ONE_BASED:
                    NanoTimestampFormatUtils.assertRemaining(pos + 1, hi);
                    hour = Numbers.parseInt(in, pos, pos += 2) - 1;
                    if (hourType == NanoTimestampFormatUtils.HOUR_24) {
                        hourType = NanoTimestampFormatUtils.HOUR_AM;
                    }
                    break;

                case DateFormatCompiler.OP_HOUR_12_GREEDY_ONE_BASED:
                    l = Numbers.parseIntSafely(in, pos, hi);
                    hour = Numbers.decodeLowInt(l) - 1;
                    pos += Numbers.decodeHighInt(l);
                    if (hourType == NanoTimestampFormatUtils.HOUR_24) {
                        hourType = NanoTimestampFormatUtils.HOUR_AM;
                    }
                    break;

                // HOUR (0-23)
                case DateFormatCompiler.OP_HOUR_24_ONE_DIGIT:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    hour = Numbers.parseInt(in, pos, ++pos);
                    break;

                case DateFormatCompiler.OP_HOUR_24_TWO_DIGITS:
                    NanoTimestampFormatUtils.assertRemaining(pos + 1, hi);
                    hour = Numbers.parseInt(in, pos, pos += 2);
                    break;

                case DateFormatCompiler.OP_HOUR_24_GREEDY:
                    l = Numbers.parseIntSafely(in, pos, hi);
                    hour = Numbers.decodeLowInt(l);
                    pos += Numbers.decodeHighInt(l);
                    break;

                // HOUR (1 - 24)
                case DateFormatCompiler.OP_HOUR_24_ONE_DIGIT_ONE_BASED:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    hour = Numbers.parseInt(in, pos, ++pos) - 1;
                    break;

                case DateFormatCompiler.OP_HOUR_24_TWO_DIGITS_ONE_BASED:
                    NanoTimestampFormatUtils.assertRemaining(pos + 1, hi);
                    hour = Numbers.parseInt(in, pos, pos += 2) - 1;
                    break;

                case DateFormatCompiler.OP_HOUR_24_GREEDY_ONE_BASED:
                    l = Numbers.parseIntSafely(in, pos, hi);
                    hour = Numbers.decodeLowInt(l) - 1;
                    pos += Numbers.decodeHighInt(l);
                    break;

                // DAY
                case DateFormatCompiler.OP_DAY_ONE_DIGIT:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    day = Numbers.parseInt(in, pos, ++pos);
                    break;
                case DateFormatCompiler.OP_DAY_TWO_DIGITS:
                    NanoTimestampFormatUtils.assertRemaining(pos + 1, hi);
                    day = Numbers.parseInt(in, pos, pos += 2);
                    break;
                case DateFormatCompiler.OP_DAY_GREEDY:
                    l = Numbers.parseIntSafely(in, pos, hi);
                    day = Numbers.decodeLowInt(l);
                    pos += Numbers.decodeHighInt(l);
                    break;

                case DateFormatCompiler.OP_DAY_NAME_LONG:
                case DateFormatCompiler.OP_DAY_NAME_SHORT:
                    l = locale.matchWeekday(in, pos, hi);
                    // ignore weekday
                    pos += Numbers.decodeHighInt(l);
                    break;

                case DateFormatCompiler.OP_DAY_OF_WEEK:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    // ignore weekday
                    Numbers.parseInt(in, pos, ++pos);
                    break;

                // MONTH

                case DateFormatCompiler.OP_MONTH_ONE_DIGIT:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    month = Numbers.parseInt(in, pos, ++pos);
                    break;
                case DateFormatCompiler.OP_MONTH_TWO_DIGITS:
                    NanoTimestampFormatUtils.assertRemaining(pos + 1, hi);
                    month = Numbers.parseInt(in, pos, pos += 2);
                    break;
                case DateFormatCompiler.OP_MONTH_GREEDY:
                    l = Numbers.parseIntSafely(in, pos, hi);
                    month = Numbers.decodeLowInt(l);
                    pos += Numbers.decodeHighInt(l);
                    break;

                case DateFormatCompiler.OP_MONTH_SHORT_NAME:
                case DateFormatCompiler.OP_MONTH_LONG_NAME:
                    l = locale.matchMonth(in, pos, hi);
                    month = Numbers.decodeLowInt(l) + 1;
                    pos += Numbers.decodeHighInt(l);
                    break;

                // YEAR

                case DateFormatCompiler.OP_YEAR_ONE_DIGIT:
                    NanoTimestampFormatUtils.assertRemaining(pos, hi);
                    year = Numbers.parseInt(in, pos, ++pos);
                    break;
                case DateFormatCompiler.OP_YEAR_TWO_DIGITS:
                    NanoTimestampFormatUtils.assertRemaining(pos + 1, hi);
                    year = NanoTimestampFormatUtils.adjustYear(Numbers.parseInt(in, pos, pos += 2));
                    break;
                case DateFormatCompiler.OP_YEAR_FOUR_DIGITS:
                    if (pos < hi && in.charAt(pos) == '-') {
                        NanoTimestampFormatUtils.assertRemaining(pos + 4, hi);
                        year = -Numbers.parseInt(in, pos + 1, pos += 5);
                    } else {
                        NanoTimestampFormatUtils.assertRemaining(pos + 3, hi);
                        year = Numbers.parseInt(in, pos, pos += 4);
                    }
                    break;
                case DateFormatCompiler.OP_YEAR_GREEDY:
                    l = NanoTimestampFormatUtils.parseYearGreedy(in, pos, hi);
                    year = Numbers.decodeLowInt(l);
                    pos += Numbers.decodeHighInt(l);
                    break;

                // ERA
                case DateFormatCompiler.OP_ERA:
                    l = locale.matchEra(in, pos, hi);
                    era = Numbers.decodeLowInt(l);
                    pos += Numbers.decodeHighInt(l);
                    break;

                // TIMEZONE
                case DateFormatCompiler.OP_TIME_ZONE_SHORT:
                case DateFormatCompiler.OP_TIME_ZONE_GMT_BASED:
                case DateFormatCompiler.OP_TIME_ZONE_ISO_8601_1:
                case DateFormatCompiler.OP_TIME_ZONE_ISO_8601_2:
                case DateFormatCompiler.OP_TIME_ZONE_ISO_8601_3:
                case DateFormatCompiler.OP_TIME_ZONE_LONG:
                case DateFormatCompiler.OP_TIME_ZONE_RFC_822:

                    l = NanoTimestamps.parseOffset(in, pos, hi);
                    if (l == Long.MIN_VALUE) {
                        l = locale.matchZone(in, pos, hi);
                        timezone = Numbers.decodeLowInt(l);
                    } else {
                        offset = Numbers.decodeLowInt(l) * NanoTimestamps.MINUTE_NANOS;
                    }
                    pos += Numbers.decodeHighInt(l);
                    break;

                // SEPARATORS
                default:
                    String delimiter = delimiters.getQuick(-op - 1);
                    len = delimiter.length();
                    if (len == 1) {
                        NanoTimestampFormatUtils.assertChar(delimiter.charAt(0), in, pos++, hi);
                    } else {
                        pos = NanoTimestampFormatUtils.assertString(delimiter, len, in, pos, hi);
                    }
                    break;
            }
        }

        NanoTimestampFormatUtils.assertNoTail(pos, hi);

        return NanoTimestampFormatUtils.compute(locale, era, year, month, day, hour, minute, second, millis, micros, nanos, timezone, offset, hourType);
    }
}
