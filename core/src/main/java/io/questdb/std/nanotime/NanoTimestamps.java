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
import io.questdb.std.Misc;
import io.questdb.std.Numbers;
import io.questdb.std.NumericException;
import io.questdb.std.str.CharSink;

import java.math.BigInteger;

final public class NanoTimestamps {

    public static final long WEEK_NANOS = 604800000000000L;
    public static final long DAY_NANOS = 86400000000000L;
    public static final long HOUR_NANOS = 3600000000000L;
    public static final long MINUTE_NANOS = 60000000000L;
    public static final long SECOND_NANOS = 1000000000;
    public static final long SECOND_MICROS = 1000000;
    public static final int SECOND_MILLIS = 1000;
    public static final int MILLI_MICROS = 1000;
    public static final int MILLI_NANOS = 1000000;
    public static final int MICROS_NANOS = 1000;
    public static final int STATE_INIT = 0;
    public static final int STATE_UTC = 1;
    public static final int STATE_GMT = 2;
    public static final int STATE_HOUR = 3;
    public static final int STATE_DELIM = 4;
    public static final int STATE_MINUTE = 5;
    public static final int STATE_END = 6;
    public static final int STATE_SIGN = 7;
    public static final TimestampFloorMethod FLOOR_DD = NanoTimestamps::floorDD;
    public static final TimestampAddMethod ADD_DD = NanoTimestamps::addDays;
    private static final long AVG_YEAR_NANOS = (long) (365.2425 * DAY_NANOS);
    private static final long YEAR_NANOS = 365 * DAY_NANOS;
    private static final long LEAP_YEAR_NANOS = 366 * DAY_NANOS;
    private static final long HALF_YEAR_NANOS = AVG_YEAR_NANOS / 2;
    private static final long EPOCH_NANOS = 1970L * AVG_YEAR_NANOS;
    private static final BigInteger EPOCH_NANOS_BIG = new BigInteger(String.valueOf(AVG_YEAR_NANOS)).multiply(new BigInteger("1970"));
    private static final long HALF_EPOCH_NANOS = EPOCH_NANOS / 2;
    private static final BigInteger HALF_EPOCH_NANOS_BIG = EPOCH_NANOS_BIG.divide(new BigInteger("2"));
    private static final int DAY_HOURS = 24;
    private static final int HOUR_MINUTES = 60;
    private static final int MINUTE_SECONDS = 60;
    private static final int DAYS_0000_TO_1970 = 719527;
    public static final TimestampFloorMethod FLOOR_YYYY = NanoTimestamps::floorYYYY;
    private static final int[] DAYS_PER_MONTH = {
            31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
    };
    private static final long[] MIN_MONTH_OF_YEAR_NANOS = new long[12];
    private static final long[] MAX_MONTH_OF_YEAR_NANOS = new long[12];
    public static final TimestampFloorMethod FLOOR_MM = NanoTimestamps::floorMM;
    public static final TimestampAddMethod ADD_MM = NanoTimestamps::addMonths;
    public static final TimestampAddMethod ADD_YYYY = NanoTimestamps::addYear;
    private static final char BEFORE_ZERO = '0' - 1;
    private static final char AFTER_NINE = '9' + 1;

    static {
        long minSum = 0;
        long maxSum = 0;
        for (int i = 0; i < 11; i++) {
            minSum += DAYS_PER_MONTH[i] * DAY_NANOS;
            MIN_MONTH_OF_YEAR_NANOS[i + 1] = minSum;
            maxSum += getDaysPerMonth(i + 1, true) * DAY_NANOS;
            MAX_MONTH_OF_YEAR_NANOS[i + 1] = maxSum;
        }
    }

    private NanoTimestamps() {
    }

    public static long addDays(long nanos, int days) {
        return nanos + days * DAY_NANOS;
    }

    public static long addHours(long nanos, int hours) {
        return nanos + hours * HOUR_NANOS;
    }

    public static long addMinutes(long nanos, int minutes) {
        return nanos + minutes * MINUTE_NANOS;
    }

    public static long addMonths(final long nanos, int months) {
        if (months == 0) {
            return nanos;
        }
        int y = NanoTimestamps.getYear(nanos);
        boolean l = NanoTimestamps.isLeapYear(y);
        int m = getMonthOfYear(nanos, y, l);
        int _y;
        int _m = m - 1 + months;
        if (_m > -1) {
            _y = y + _m / 12;
            _m = (_m % 12) + 1;
        } else {
            _y = y + _m / 12 - 1;
            _m = -_m % 12;
            if (_m == 0) {
                _m = 12;
            }
            _m = 12 - _m + 1;
            if (_m == 1) {
                _y += 1;
            }
        }
        int _d = getDayOfMonth(nanos, y, m, l);
        int maxDay = getDaysPerMonth(_m, isLeapYear(_y));
        if (_d > maxDay) {
            _d = maxDay;
        }
        return toNanos(_y, _m, _d) + getTimeNanos(nanos) + (nanos < 0 ? 1 : 0);
    }

    public static long addPeriod(long lo, char type, int period) {
        switch (type) {
            case 's':
                return NanoTimestamps.addSeconds(lo, period);
            case 'm':
                return NanoTimestamps.addMinutes(lo, period);
            case 'h':
                return NanoTimestamps.addHours(lo, period);
            case 'd':
                return NanoTimestamps.addDays(lo, period);
            case 'w':
                return NanoTimestamps.addWeeks(lo, period);
            case 'M':
                return NanoTimestamps.addMonths(lo, period);
            case 'y':
                return NanoTimestamps.addYear(lo, period);
            default:
                return Numbers.LONG_NaN;
        }
    }

    public static long addSeconds(long nanos, int seconds) {
        return nanos + seconds * SECOND_NANOS;
    }

    public static long addWeeks(long nanos, int weeks) {
        return nanos + weeks * WEEK_NANOS;
    }

    public static long addYear(long nanos, int years) {
        if (years == 0) {
            return nanos;
        }

        int y = getYear(nanos);
        int m;
        boolean leap1 = isLeapYear(y);
        boolean leap2 = isLeapYear(y + years);

        return yearNanos(y + years, leap2)
                + monthOfYearNanos(m = getMonthOfYear(nanos, y, leap1), leap2)
                + (getDayOfMonth(nanos, y, m, leap1) - 1) * DAY_NANOS
                + getTimeNanos(nanos)
                + (nanos < 0 ? 1 : 0);

    }

    public static long ceilDD(long nanos) {
        int y, m;
        boolean l;
        return yearNanos(y = getYear(nanos), l = isLeapYear(y))
                + monthOfYearNanos(m = getMonthOfYear(nanos, y, l), l)
                + (getDayOfMonth(nanos, y, m, l) - 1) * DAY_NANOS
                + 23 * HOUR_NANOS
                + 59 * MINUTE_NANOS
                + 59 * SECOND_NANOS
                + 999999999L
                ;
    }

    public static long ceilMM(long nanos) {
        int y, m;
        boolean l;
        return yearNanos(y = getYear(nanos), l = isLeapYear(y))
                + monthOfYearNanos(m = getMonthOfYear(nanos, y, l), l)
                + (getDaysPerMonth(m, l) - 1) * DAY_NANOS
                + 23 * HOUR_NANOS
                + 59 * MINUTE_NANOS
                + 59 * SECOND_NANOS
                + 999999999L
                ;
    }

    public static long ceilYYYY(long nanos) {
        int y;
        boolean l;
        return yearNanos(y = getYear(nanos), l = isLeapYear(y))
                + monthOfYearNanos(12, l)
                + (DAYS_PER_MONTH[11] - 1) * DAY_NANOS
                + 23 * HOUR_NANOS
                + 59 * MINUTE_NANOS
                + 59 * SECOND_NANOS
                + 999999999L;
    }

    public static long endOfYear(int year) {
        return toNanos(year, 12, 31, 23, 59) + 59 * SECOND_MILLIS + 999999999L;
    }

    public static long floorDD(long nanos) {
        return nanos - getTimeNanos(nanos);
    }

    public static long floorHH(long nanos) {
        return nanos - nanos % HOUR_NANOS;
    }

    public static long floorMI(long nanos) {
        return nanos - nanos % MINUTE_NANOS;
    }

    public static long floorMM(long nanos) {
        int y;
        boolean l;
        return yearNanos(y = getYear(nanos), l = isLeapYear(y)) + monthOfYearNanos(getMonthOfYear(nanos, y, l), l);
    }

    public static long floorYYYY(long micros) {
        int y;
        return yearNanos(y = getYear(micros), isLeapYear(y));
    }

    public static int getDayOfMonth(long nanos, int year, int month, boolean leap) {
        long yearNanos = yearNanos(year, leap);
        yearNanos += monthOfYearNanos(month, leap);
        return (int) ((nanos - yearNanos) / DAY_NANOS) + 1;
    }

    public static int getDayOfWeek(long nanos) {
        // 1970-01-01 is Thursday.
        long d;
        if (nanos > -1) {
            d = nanos / DAY_NANOS;
        } else {
            d = (nanos - (DAY_NANOS - 1)) / DAY_NANOS;
            if (d < -3) {
                return 7 + (int) ((d + 4) % 7);
            }
        }
        return 1 + (int) ((d + 3) % 7);
    }

    public static int getDayOfWeekSundayFirst(long nanos) {
        // 1970-01-01 is Thursday.
        long d;
        if (nanos > -1) {
            d = nanos / DAY_NANOS;
        } else {
            d = (nanos - (DAY_NANOS - 1)) / DAY_NANOS;
            if (d < -4) {
                return 7 + (int) ((d + 5) % 7);
            }
        }
        return 1 + (int) ((d + 4) % 7);
    }

    public static long getDaysBetween(long a, long b) {
        return Math.abs(a - b) / DAY_NANOS;
    }

    public static long getWeeksBetween(long a, long b) {
        return Math.abs(a - b) / WEEK_NANOS;
    }

    public static long getNanosBetween(long a, long b) {
        return Math.abs(a - b);
    }

    public static long getMillisBetween(long a, long b) {
        return Math.abs(a - b) / MILLI_MICROS / MICROS_NANOS;
    }

    public static long getMicroBetween(long a, long b) {
        return Math.abs(a - b) / MICROS_NANOS;
    }

    public static long getSecondsBetween(long a, long b) {
        return Math.abs(a - b) / SECOND_NANOS;
    }

    public static long getMinutesBetween(long a, long b) {
        return Math.abs(a - b) / MINUTE_NANOS;
    }

    public static long getHoursBetween(long a, long b) {
        return Math.abs(a - b) / HOUR_NANOS;
    }

    public static long getPeriodBetween(char type, long start, long end) {
        switch (type) {
            case 's':
                return NanoTimestamps.getSecondsBetween(start, end);
            case 'm':
                return NanoTimestamps.getMinutesBetween(start, end);
            case 'h':
                return NanoTimestamps.getHoursBetween(start, end);
            case 'd':
                return NanoTimestamps.getDaysBetween(start, end);
            case 'w':
                return NanoTimestamps.getWeeksBetween(start, end);
            case 'M':
                return NanoTimestamps.getMonthsBetween(start, end);
            case 'y':
                return NanoTimestamps.getYearsBetween(start, end);
            default:
                return Numbers.LONG_NaN;
        }
    }

    /**
     * Days in a given month. This method expects you to know if month is in leap year.
     *
     * @param m    month from 1 to 12
     * @param leap true if this is for leap year
     * @return number of days in month.
     */
    public static int getDaysPerMonth(int m, boolean leap) {
        return leap & m == 2 ? 29 : DAYS_PER_MONTH[m - 1];
    }

    public static int getHourOfDay0(long nanos) {
        if (nanos > -1) {
            return (int) ((nanos / HOUR_NANOS) % DAY_HOURS);
        } else {
            return DAY_HOURS - 1 + (int) (((nanos + 1) / HOUR_NANOS) % DAY_HOURS);
        }
    }

    public static int getHourOfDay(long nanos) {
        if (nanos > -1) {
            return (int) ((nanos / HOUR_NANOS) % DAY_HOURS);
        } else {
            return DAY_HOURS - 1 + (int) (((nanos + 1) / HOUR_NANOS) % DAY_HOURS);
        }

        // branchless version of the above

//        long signMask = micros & Numbers.SIGN_BIT_MASK;
//        long increment = signMask >> 63;
//        long mask = increment | signMask >> 62 | signMask >> 61 | signMask >> 60 | signMask >> 59;
//        long res = ((DAY_HOURS - 1) & mask) + ((micros + increment) / HOUR_MICROS) % DAY_HOURS;
//        return (int) res;
    }

    //!@#$
    public static int getNanosOfSecond(long nanos) {
        if (nanos > -1) {
            return (int) (nanos % MILLI_NANOS);
        } else {
            return MILLI_NANOS - 1 + (int) ((nanos + 1) % MILLI_NANOS);
        }
    }

    public static int getMicrosOfSecond(long nanos) {
        if (nanos > -1) {
            return (int) (nanos % MILLI_NANOS);
        } else {
            return MILLI_NANOS - 1 + (int) ((nanos + 1) % MILLI_NANOS);
        }
    }

    public static int getMillisOfSecond(long nanos) {
        if (nanos > -1) {
            return (int) ((nanos / MILLI_NANOS) % SECOND_MILLIS);
        } else {
            return SECOND_MILLIS - 1 + (int) (((nanos + 1) / MILLI_NANOS) % SECOND_MILLIS);
        }
    }

    public static int getMinuteOfHour(long nanos) {
        if (nanos > -1) {
            return (int) ((nanos / MINUTE_NANOS) % HOUR_MINUTES);
        } else {
            return HOUR_MINUTES - 1 + (int) (((nanos + 1) / MINUTE_NANOS) % HOUR_MINUTES);
        }
    }

    /**
     * Calculates month of year from absolute nanos.
     *
     * @param nanos nanos since 1970
     * @param year   year of month
     * @param leap   true if year was leap
     * @return month of year
     */
    public static int getMonthOfYear(long nanos, int year, boolean leap) {
        int i = (int) (((nanos - yearNanos(year, leap)) / 1000) >> 10);
        return leap
                ? ((i < 182 * 84375)
                ? ((i < 91 * 84375)
                ? ((i < 31 * 84375) ? 1 : (i < 60 * 84375) ? 2 : 3)
                : ((i < 121 * 84375) ? 4 : (i < 152 * 84375) ? 5 : 6))
                : ((i < 274 * 84375)
                ? ((i < 213 * 84375) ? 7 : (i < 244 * 84375) ? 8 : 9)
                : ((i < 305 * 84375) ? 10 : (i < 335 * 84375) ? 11 : 12)))
                : ((i < 181 * 84375)
                ? ((i < 90 * 84375)
                ? ((i < 31 * 84375) ? 1 : (i < 59 * 84375) ? 2 : 3)
                : ((i < 120 * 84375) ? 4 : (i < 151 * 84375) ? 5 : 6))
                : ((i < 273 * 84375)
                ? ((i < 212 * 84375) ? 7 : (i < 243 * 84375) ? 8 : 9)
                : ((i < 304 * 84375) ? 10 : (i < 334 * 84375) ? 11 : 12)));
    }

    public static long getMonthsBetween(long a, long b) {
        if (b < a) {
            return getMonthsBetween(b, a);
        }

        int aYear = getYear(a);
        int bYear = getYear(b);
        boolean aLeap = isLeapYear(aYear);
        boolean bLeap = isLeapYear(bYear);
        int aMonth = getMonthOfYear(a, aYear, aLeap);
        int bMonth = getMonthOfYear(b, bYear, bLeap);

        long aResidual = a - yearNanos(aYear, aLeap) - monthOfYearNanos(aMonth, aLeap);
        long bResidual = b - yearNanos(bYear, bLeap) - monthOfYearNanos(bMonth, bLeap);
        long months = 12 * (bYear - aYear) + (bMonth - aMonth);

        if (aResidual > bResidual) {
            return months - 1;
        } else {
            return months;
        }
    }

    public static int getSecondOfMinute(long micros) {
        if (micros > -1) {
            return (int) ((micros / SECOND_MICROS) % MINUTE_SECONDS);
        } else {
            return MINUTE_SECONDS - 1 + (int) (((micros + 1) / SECOND_MICROS) % MINUTE_SECONDS);
        }
    }

    /**
     * Calculates year number from nanos.
     *
     * @param nanos time nanos.
     * @return year
     */
    public static int getYear(long nanos) {
        BigInteger mid = HALF_EPOCH_NANOS_BIG.add(BigInteger.valueOf((nanos >> 1)));
        if (mid.compareTo(BigInteger.ZERO) < 0) {
            mid = mid.subtract(BigInteger.valueOf(HALF_YEAR_NANOS)).add(BigInteger.ONE);
        }
        int year = (int) (mid.divide(BigInteger.valueOf(HALF_YEAR_NANOS))).intValue();

        boolean leap = isLeapYear(year);
        long yearStart = yearNanos(year, leap);
        long diff = nanos - yearStart;

        if (diff < 0) {
            year--;
        } else if (diff >= YEAR_NANOS) {
            yearStart += leap ? LEAP_YEAR_NANOS : YEAR_NANOS;
            if (yearStart <= nanos) {
                year++;
            }
        }

        return year;
    }

    public static long getYearsBetween(long a, long b) {
        return getMonthsBetween(a, b) / 12;
    }

    /**
     * Calculates if year is leap year using following algorithm:
     * <p>
     * http://en.wikipedia.org/wiki/Leap_year
     *
     * @param year the year
     * @return true if year is leap
     */
    public static boolean isLeapYear(int year) {
        return ((year & 3) == 0) && ((year % 100) != 0 || (year % 400) == 0);
    }

    public static long monthOfYearNanos(int month, boolean leap) {
        return leap ? MAX_MONTH_OF_YEAR_NANOS[month - 1] : MIN_MONTH_OF_YEAR_NANOS[month - 1];
    }

    public static long nextOrSameDayOfWeek(long nanos, int dow) {
        int thisDow = getDayOfWeek(nanos);
        if (thisDow == dow) {
            return nanos;
        }

        if (thisDow < dow) {
            return nanos + (dow - thisDow) * DAY_NANOS;
        } else {
            return nanos + (7 - (thisDow - dow)) * DAY_NANOS;
        }
    }

    public static long parseOffset(CharSequence in, int lo, int hi) {
        int p = lo;
        int state = STATE_INIT;
        boolean negative = false;
        int hour = 0;
        int minute = 0;

        try {
            OUT:
            while (p < hi) {
                char c = in.charAt(p);

                switch (state) {
                    case STATE_INIT:
                        switch (c) {
                            case 'U':
                            case 'u':
                                state = STATE_UTC;
                                break;
                            case 'G':
                            case 'g':
                                state = STATE_GMT;
                                break;
                            case 'Z':
                            case 'z':
                                state = STATE_END;
                                break;
                            case '+':
                                negative = false;
                                state = STATE_HOUR;
                                break;
                            case '-':
                                negative = true;
                                state = STATE_HOUR;
                                break;
                            default:
                                if (isDigit(c)) {
                                    state = STATE_HOUR;
                                    p--;
                                } else {
                                    return Long.MIN_VALUE;
                                }
                                break;
                        }
                        p++;
                        break;
                    case STATE_UTC:
                        if (p > hi - 2 || Chars.noMatch(in, p, p + 2, "tc", 0, 2)) {
                            return Long.MIN_VALUE;
                        }
                        state = STATE_SIGN;
                        p += 2;
                        break;
                    case STATE_GMT:
                        if (p > hi - 2 || Chars.noMatch(in, p, p + 2, "mt", 0, 2)) {
                            return Long.MIN_VALUE;
                        }
                        state = STATE_SIGN;
                        p += 2;
                        break;
                    case STATE_SIGN:
                        switch (c) {
                            case '+':
                                negative = false;
                                break;
                            case '-':
                                negative = true;
                                break;
                            default:
                                return Long.MIN_VALUE;
                        }
                        p++;
                        state = STATE_HOUR;
                        break;
                    case STATE_HOUR:
                        if (isDigit(c) && p < hi - 1) {
                            hour = Numbers.parseInt(in, p, p + 2);
                        } else {
                            return Long.MIN_VALUE;
                        }
                        state = STATE_DELIM;
                        p += 2;
                        break;
                    case STATE_DELIM:
                        if (c == ':') {
                            state = STATE_MINUTE;
                            p++;
                        } else if (isDigit(c)) {
                            state = STATE_MINUTE;
                        } else {
                            return Long.MIN_VALUE;
                        }
                        break;
                    case STATE_MINUTE:
                        if (isDigit(c) && p < hi - 1) {
                            minute = Numbers.parseInt(in, p, p + 2);
                        } else {
                            return Long.MIN_VALUE;
                        }
                        p += 2;
                        state = STATE_END;
                        break OUT;
                    default:
                        throw new IllegalStateException("Unexpected state");
                }
            }
        } catch (NumericException e) {
            return Long.MIN_VALUE;
        }

        switch (state) {
            case STATE_DELIM:
            case STATE_END:
                if (hour > 23 || minute > 59) {
                    return Long.MIN_VALUE;
                }
                final int min = hour * 60 + minute;
                return Numbers.encodeLowHighInts(negative ? -min : min, p - lo);
            default:
                return Long.MIN_VALUE;
        }
    }

    public static long previousOrSameDayOfWeek(long nanos, int dow) {
        int thisDow = getDayOfWeek(nanos);
        if (thisDow == dow) {
            return nanos;
        }

        if (thisDow < dow) {
            return nanos - (7 + (thisDow - dow)) * DAY_NANOS;
        } else {
            return nanos - (thisDow - dow) * DAY_NANOS;
        }
    }

    public static long toNanos(int y, int m, int d, int h, int mi) {
        return toNanos(y, isLeapYear(y), m, d, h, mi);
    }

    public static long toNanos(int y, boolean leap, int m, int d, int h, int mi) {
        return yearNanos(y, leap) + monthOfYearNanos(m, leap) + (d - 1) * DAY_NANOS + h * HOUR_NANOS + mi * MINUTE_NANOS;
    }

    public static String toString(long nanos) {
        CharSink sink = Misc.getThreadLocalBuilder();
        NanoTimestampFormatUtils.appendDateTime(sink, nanos);
        return sink.toString();
    }

    /**
     * Calculated start of year in nanos. For example of year 2008 this is
     * equivalent to parsing "2008-01-01T00:00:00.000Z", except this method is faster.
     *
     * @param year the year
     * @param leap true if give year is leap year
     * @return nanos for start of year.
     */
    public static long yearNanos(int year, boolean leap) {
        int leapYears = year / 100;
        if (year < 0) {
            leapYears = ((year + 3) >> 2) - leapYears + ((leapYears + 3) >> 2) - 1;
        } else {
            leapYears = (year >> 2) - leapYears + (leapYears >> 2);
            if (leap) {
                leapYears--;
            }
        }

        return (year * 365L + (leapYears - DAYS_0000_TO_1970)) * DAY_NANOS;
    }

    private static boolean isDigit(char c) {
        return c > BEFORE_ZERO && c < AFTER_NINE;
    }

    private static long getTimeNanos(long nanos) {
        return nanos < 0 ? DAY_NANOS - 1 + (nanos % DAY_NANOS) : nanos % DAY_NANOS;
    }

    private static long toNanos(int y, int m, int d) {
        boolean l = isLeapYear(y);
        return yearNanos(y, l) + monthOfYearNanos(m, l) + (d - 1) * DAY_NANOS;
    }

    @FunctionalInterface
    public interface TimestampFloorMethod {
        long floor(long timestamp);
    }

    @FunctionalInterface
    public interface TimestampAddMethod {
        long calculate(long minTimestamp, int partitionIndex);
    }
}