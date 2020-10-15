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

import io.questdb.std.Chars;
import io.questdb.std.Numbers;
import io.questdb.std.NumericException;
import io.questdb.std.Os;
import io.questdb.std.str.CharSink;

public class NanoTimestampFormatUtils {
    public static final int HOUR_24 = 2;
    public static final int HOUR_PM = 1;
    public static final int HOUR_AM = 0;
    public static final NanoTimestampFormat UTC_FORMAT;
    public static final NanoTimestampFormat USEC_UTC_FORMAT;
    public static final NanoTimestampFormat NSEC_UTC_FORMAT;
    public static final NanoTimestampFormat PG_TIMESTAMP_FORMAT;
    public static final String UTC_PATTERN = "yyyy-MM-ddTHH:mm:ss.SSSz";
    public static final NanoTimestampLocale enLocale = NanoTimestampLocaleFactory.INSTANCE.getLocale("en");
    private static final NanoTimestampFormat HTTP_FORMAT;
    static long referenceYear;
    static int thisCenturyLimit;
    static int thisCenturyLow;
    static int prevCenturyLow;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private static long newYear;

    static {
        updateReferenceYear(Os.currentTimeMicros());
        DateFormatCompiler compiler = new DateFormatCompiler();
        UTC_FORMAT = compiler.compile(UTC_PATTERN);
        HTTP_FORMAT = compiler.compile("E, d MMM yyyy HH:mm:ss Z");
        USEC_UTC_FORMAT = compiler.compile("yyyy-MM-ddTHH:mm:ss.SSSUUUz");
        NSEC_UTC_FORMAT = compiler.compile("yyyy-MM-ddTHH:mm:ss.SSSUUUNNNz");
        PG_TIMESTAMP_FORMAT = compiler.compile("yyyy-MM-dd HH:mm:ss.SSSUUU");
    }

    public static void append0(CharSink sink, int val) {
        if (Math.abs(val) < 10) {
            sink.put('0');
        }
        Numbers.append(sink, val);
    }

    public static void append00(CharSink sink, int val) {
        int v = Math.abs(val);
        if (v < 10) {
            sink.put('0').put('0');
        } else if (v < 100) {
            sink.put('0');
        }
        Numbers.append(sink, val);
    }

    public static void append000(CharSink sink, int val) {
        int v = Math.abs(val);
        if (v < 10) {
            sink.put('0').put('0').put('0');
        } else if (v < 100) {
            sink.put('0').put('0');
        } else if (v < 1000) {
            sink.put('0');
        }
        Numbers.append(sink, val);
    }

    // YYYY-MM-DDThh:mm:ss.mmmZ
    public static void appendDateTime(CharSink sink, long nanos) {
        if (nanos == Long.MIN_VALUE) {
            return;
        }
        UTC_FORMAT.format(nanos, null, "Z", sink);
    }

    // YYYY-MM-DDThh:mm:ss.mmmuuuZ
    public static void appendDateTimeUSec(CharSink sink, long micros) {
        if (micros == Long.MIN_VALUE) {
            return;
        }
        USEC_UTC_FORMAT.format(micros, null, "Z", sink);
    }

    // YYYY-MM-DDThh:mm:ss.mmmuuunnnZ
    public static void appendDateTimeNSec(CharSink sink, long nanos) {
        if (nanos == Long.MIN_VALUE) {
            return;
        }
        NSEC_UTC_FORMAT.format(nanos, null, "Z", sink);
    }

    // YYYY-MM-DD
    public static void formatDashYYYYMMDD(CharSink sink, long nanos) {
        int y = NanoTimestamps.getYear(nanos);
        boolean l = NanoTimestamps.isLeapYear(y);
        int m = NanoTimestamps.getMonthOfYear(nanos, y, l);
        Numbers.append(sink, y);
        append0(sink.put('-'), m);
        append0(sink.put('-'), NanoTimestamps.getDayOfMonth(nanos, y, m, l));
    }

    public static void formatHTTP(CharSink sink, long nanos) {
        HTTP_FORMAT.format(nanos, enLocale, "GMT", sink);
    }

    // YYYY-MM
    public static void formatYYYYMM(CharSink sink, long nanos) {
        int y = NanoTimestamps.getYear(nanos);
        int m = NanoTimestamps.getMonthOfYear(nanos, y, NanoTimestamps.isLeapYear(y));
        Numbers.append(sink, y);
        append0(sink.put('-'), m);
    }

    // YYYYMMDD
    public static void formatYYYYMMDD(CharSink sink, long nanos) {
        int y = NanoTimestamps.getYear(nanos);
        boolean l = NanoTimestamps.isLeapYear(y);
        int m = NanoTimestamps.getMonthOfYear(nanos, y, l);
        Numbers.append(sink, y);
        append0(sink, m);
        append0(sink, NanoTimestamps.getDayOfMonth(nanos, y, m, l));
    }

    public static long getReferenceYear() {
        return referenceYear;
    }

    // YYYY-MM-DDThh:mm:ss.mmm
    public static long parseDateTime(CharSequence seq) throws NumericException {
        return parseDateTime(seq, 0, seq.length());
    }

    // YYYY-MM-DDThh:mm:ss.mmmnnn
    public static long parseTimestamp(CharSequence seq) throws NumericException {
        return USEC_UTC_FORMAT.parse(seq, 0, seq.length(), null);
    }

    public static long tryParse(CharSequence s, int lo, int lim) throws NumericException {
        return parseDateTime(s, lo, lim);
    }

    public static void updateReferenceYear(long nanos) {
        referenceYear = nanos;

        int referenceYear = NanoTimestamps.getYear(nanos);
        int centuryOffset = referenceYear % 100;
        thisCenturyLimit = centuryOffset + 20;
        if (thisCenturyLimit > 100) {
            thisCenturyLimit = thisCenturyLimit % 100;
            thisCenturyLow = referenceYear - centuryOffset + 100;
        } else {
            thisCenturyLow = referenceYear - centuryOffset;
        }
        prevCenturyLow = thisCenturyLow - 100;
        newYear = NanoTimestamps.endOfYear(referenceYear);
    }

    static void appendAmPm(CharSink sink, int hour, NanoTimestampLocale locale) {
        if (hour < 12) {
            sink.put(locale.getAMPM(0));
        } else {
            sink.put(locale.getAMPM(1));
        }
    }

    static void assertChar(char c, CharSequence in, int pos, int hi) throws NumericException {
        assertRemaining(pos, hi);
        if (in.charAt(pos) != c) {
            throw NumericException.INSTANCE;
        }
    }

    static int assertString(CharSequence delimiter, int len, CharSequence in, int pos, int hi) throws NumericException {
        if (delimiter.charAt(0) == '\'' && delimiter.charAt(len - 1) == '\'') {
            assertRemaining(pos + len - 3, hi);
            if (!Chars.equals(delimiter, 1, len - 1, in, pos, pos + len - 2)) {
                throw NumericException.INSTANCE;
            }
            return pos + len - 2;
        } else {
            assertRemaining(pos + len - 1, hi);
            if (!Chars.equals(delimiter, in, pos, pos + len)) {
                throw NumericException.INSTANCE;
            }
            return pos + len;
        }
    }

    static void assertRemaining(int pos, int hi) throws NumericException {
        if (pos < hi) {
            return;
        }
        throw NumericException.INSTANCE;
    }

    static void assertNoTail(int pos, int hi) throws NumericException {
        if (pos < hi) {
            throw NumericException.INSTANCE;
        }
    }

    static long compute(
            NanoTimestampLocale locale,
            int era,
            int year,
            int month,
            int day,
            int hour,
            int minute,
            int second,
            int millis,
            int micros,
            int nanos,
            int timezone,
            long offset,
            int hourType) throws NumericException {

        if (era == 0) {
            year = -(year - 1);
        }

        boolean leap = NanoTimestamps.isLeapYear(year);

        // wrong month
        if (month < 1 || month > 12) {
            throw NumericException.INSTANCE;
        }

        switch (hourType) {
            case HOUR_PM:
                hour += 12;
            case HOUR_24:
                // wrong hour
                if (hour < 0 || hour > 23) {
                    throw NumericException.INSTANCE;
                }
                break;
            default:
                // wrong 12-hour clock hour
                if (hour < 0 || hour > 11) {
                    throw NumericException.INSTANCE;
                }
        }

        // wrong day of month
        if (day < 1 || day > NanoTimestamps.getDaysPerMonth(month, leap)) {
            throw NumericException.INSTANCE;
        }

        if (minute < 0 || minute > 59) {
            throw NumericException.INSTANCE;
        }

        if (second < 0 || second > 59) {
            throw NumericException.INSTANCE;
        }

        long datetime = NanoTimestamps.yearNanos(year, leap)
                + NanoTimestamps.monthOfYearNanos(month, leap)
                + (day - 1) * NanoTimestamps.DAY_NANOS
                + hour * NanoTimestamps.HOUR_NANOS
                + minute * NanoTimestamps.MINUTE_NANOS
                + second * NanoTimestamps.SECOND_MICROS
                + millis * NanoTimestamps.MILLI_MICROS
                + micros * NanoTimestamps.MICROS_NANOS
                + nanos;

        if (timezone > -1) {
            datetime -= locale.getZoneRules(timezone).getOffset(datetime, year, leap);
        } else if (offset > Long.MIN_VALUE) {
            datetime -= offset;
        }

        return datetime;
    }

    static long parseYearGreedy(CharSequence in, int pos, int hi) throws NumericException {
        long l = Numbers.parseIntSafely(in, pos, hi);
        int len = Numbers.decodeHighInt(l);
        int year;
        if (len == 2) {
            year = adjustYear(Numbers.decodeLowInt(l));
        } else {
            year = Numbers.decodeLowInt(l);
        }

        return Numbers.encodeLowHighInts(year, len);
    }

    static int adjustYear(int year) {
        return (year < thisCenturyLimit ? thisCenturyLow : prevCenturyLow) + year;
    }

    static void appendHour12(CharSink sink, int hour) {
        if (hour < 12) {
            sink.put(hour);
        } else {
            sink.put(hour - 12);
        }
    }

    static void appendHour12Padded(CharSink sink, int hour) {
        if (hour < 12) {
            append0(sink, hour);
        } else {
            append0(sink, hour - 12);
        }
    }

    static void appendHour121Padded(CharSink sink, int hour) {
        if (hour < 12) {
            append0(sink, hour + 1);
        } else {
            append0(sink, hour - 11);
        }
    }

    static void appendHour121(CharSink sink, int hour) {
        if (hour < 12) {
            sink.put(hour + 1);
        } else {
            sink.put(hour - 11);
        }
    }

    static void appendEra(CharSink sink, int year, NanoTimestampLocale locale) {
        if (year < 0) {
            sink.put(locale.getEra(0));
        } else {
            sink.put(locale.getEra(1));
        }
    }

    private static long parseDateTime(CharSequence seq, int lo, int lim) throws NumericException {
        if (lim - lo == 30) {
            return NSEC_UTC_FORMAT.parse(seq, lo, lim, enLocale);
        } else {
            return UTC_FORMAT.parse(seq, lo, lim, enLocale);
        }
    }

}
