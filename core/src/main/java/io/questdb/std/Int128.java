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

package io.questdb.std;

public class Int128 {

    int base = 10;
    static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL;

    public static final Int128 MAX_VALUE = new Int128(0x7FFFFFFFFFFFFFFFL, UINT64_MAX);
    private static final long LONG_SIGN_BIT = 0x8000000000000000L;
    public static final Int128 MIN_VALUE = new Int128(LONG_SIGN_BIT, 0);

    public static final Int128 ZERO = new Int128(0L, 0L);
    public static final Int128 ONE = new Int128(0L, 1L);
    public static final Int128 TWO = new Int128(0L, 2L);

    private long hi;
    private long lo;

    public Int128() {
        this(0L, 0L);
    }

    public Int128(long hi, long lo) {
        this.hi = hi;
        this.lo = lo;
    }

    public Int128(byte value) {
        if (value < 0) {
            lo = ((Byte.toUnsignedLong((byte)-value) ^ 0xFF) + 1) | 0xFFFFFFFFFFFFFF00L;
            hi =  UINT64_MAX;
        } else {
            lo = Byte.toUnsignedLong(value);
            hi = 0;
        }
    }

//        explicit Int128(std::int8_t value)
//        : low(value < 0
//            ? (((static_cast<uint64_t>(-value) ^ 0xFF) + 1) | (0xFFFFFFFFFFFFFF00))
//            : static_cast<uint64_t>(value)),
//    high(value < 0 ? UINT64_MAX : 0) {}

    public Int128(short value) {
        if (value < 0) {
            lo = ((Short.toUnsignedLong((short)-value) ^ 0xFF) + 1) | 0xFFFFFFFFFFFFFF00L;
            hi = UINT64_MAX;
        } else {
            lo = Short.toUnsignedLong(value);
            hi = 0;
        }
    }

//    explicit Int128(std::int8_t value)
//        : low(value < 0
//            ? (((static_cast<uint64_t>(-value) ^ 0xFF) + 1) | (0xFFFFFFFFFFFFFF00))
//            : static_cast<uint64_t>(value)),
//    high(value < 0 ? UINT64_MAX : 0) {}

    public boolean isNegative() {
        return (hi & 0x8000000000000000L) != 0;
    }

    public void increment() {
        lo = lo + 1;
        if (lo == 0) {
            hi = hi + 1;
            lo = 0;
        }
    }

    /**
     * Return the base 10 string representation of the integer.
     * @return
     */
    @Override
    public String toString() {
        boolean negative = isNegative();
        if (base == 10 && (hi == 0 || (lo < 0x8000000000000000L && hi == UINT64_MAX))) {
            if (negative) {
                return Long.toUnsignedString((lo - 1) ^ UINT64_MAX);
            } else {
                return Long.toUnsignedString(lo);
            }
        } else {
            char[] buffer = new char[129];
            int bufferIndex = 128;

            long[] divAndRem;
            do {
                divAndRem = divideAndRemainder128(hi, lo, base);
                if (divAndRem[1] < 10) {
                    buffer[--bufferIndex] = (char) (divAndRem[1] + '0');
                } else if (divAndRem[1] < 36) {
                    buffer[--bufferIndex] = (char) (divAndRem[1] - 10 + 'a');
                }
            } while (divAndRem[0] != 0);

            if (negative) {
                buffer[--bufferIndex] = '-';
            }

            Long.toUnsignedString(lo);

//            char buffer[129] = {0};
//            std::size_t bufferIndex = 128;
//            std::pair<Int128, Int128> quotientAndRemainder(getAbsoluteValue(), zero());
//
//            do {
//                quotientAndRemainder = quotientAndRemainder.first.unsignedDivisionWithRemainder(Int128(base));
//                if (quotientAndRemainder.second.low < 10) {
//                    buffer[--bufferIndex] = static_cast<char>(quotientAndRemainder.second.low + '0');
//                } else if (quotientAndRemainder.second.low < 36) {
//                    buffer[--bufferIndex] = static_cast<char>(quotientAndRemainder.second.low - 10 + 'a');
//                }
//            } while (!quotientAndRemainder.first.isZero());
//
//            if (negative) {
//                buffer[--bufferIndex] = '-';
//            }
//
//            return std::string(&buffer[bufferIndex]);

            return new String(buffer);
        }
    }

    /**
     * Returns highest 64 bits of (signed) long multiplication.
     *
     * @param x the number
     * @param y the number
     * @return highest 64 bits of (signed) long multiplication.
     */
    public static long multiplyHighSigned(long x, long y) {
        long x_high = x >> 32;
        long x_low = x & 0xFFFFFFFFL;
        long y_high = y >> 32;
        long y_low = y & 0xFFFFFFFFL;

        long z2 = x_low * y_low;
        long t = x_high * y_low + (z2 >>> 32);
        long z1 = t & 0xFFFFFFFFL;
        long z0 = t >> 32;
        z1 += x_low * y_high;
        return x_high * y_high + z0 + (z1 >> 32);
    }

    /**
     * Returns highest 64 bits of (unsigned) long multiplication.
     *
     * @param x the number
     * @param y the number
     * @return highest 64 bits of (unsigned) long multiplication.
     */
    public static long multiplyHighUnsigned(long x, long y) {
        long x_high = x >>> 32;
        long y_high = y >>> 32;
        long x_low = x & 0xFFFFFFFFL;
        long y_low = y & 0xFFFFFFFFL;

        long z2 = x_low * y_low;
        long t = x_high * y_low + (z2 >>> 32);
        long z1 = t & 0xFFFFFFFFL;
        long z0 = t >>> 32;
        z1 += x_low * y_high;
        return x_high * y_high + z0 + (z1 >>> 32);
    }

    /**
     * Returns lowest 64 bits of either signed or unsigned long multiplication.
     *
     * @param x the number
     * @param y the number
     * @return lowest 64 bits of long multiplication.
     */
    public static long multiplyLow(long x, long y) {
        long n = x * y;
        return n;
    }

    /**
     * Return's quotient and remainder of 128 bit integer division by 64 bit integer. <p> Code taken from Hacker's
     * Delight: http://www.hackersdelight.org/HDcode/divlu.c.
     *
     * @param u1 highest 64 dividend bits
     * @param u0 lowest 64 dividend bits
     * @param v  the divider
     * @return {quotient, remainder}
     */
    public static long[] divideAndRemainder128(long u1, long u0, long v) {
        long b = (1L << 32); // Number base (16 bits).
        long
                un1, un0,           // Norm. dividend LSD's.
                vn1, vn0,           // Norm. divisor digits.
                q1, q0,             // Quotient digits.
                un64, un21, un10,   // Dividend digit pairs.
                rhat;               // A remainder.
        int s;              // Shift amount for norm.

        if (u1 >= v)                          // If overflow, set rem.
            return new long[]{-1L, -1L};      // possible quotient.


        // count leading zeros
        s = Long.numberOfLeadingZeros(v); // 0 <= s <= 63.
        if (s > 0) {
            v = v << s;         // Normalize divisor.
            un64 = (u1 << s) | ((u0 >>> (64 - s)) & (-s >> 31));
            un10 = u0 << s;     // Shift dividend left.
        } else {
            // Avoid undefined behavior.
            un64 = u1 | u0;
            un10 = u0;
        }

        vn1 = v >>> 32;            // Break divisor up into
        vn0 = v & 0xFFFFFFFFL;     // two 32-bit digits.

        un1 = un10 >>> 32;         // Break right half of
        un0 = un10 & 0xFFFFFFFFL;  // dividend into two digits.

        q1 = Long.divideUnsigned(un64, vn1);            // Compute the first
        rhat = un64 - q1 * vn1;     // quotient digit, q1.
        while (true) {
            if (Long.compareUnsigned(q1, b) >= 0 || Long.compareUnsigned(q1 * vn0, b * rhat + un1) > 0) { //if (q1 >= b || q1 * vn0 > b * rhat + un1) {
                q1 = q1 - 1;
                rhat = rhat + vn1;
                if (Long.compareUnsigned(rhat, b) < 0)
                    continue;
            }
            break;
        }

        un21 = un64 * b + un1 - q1 * v;  // Multiply and subtract.

        q0 = Long.divideUnsigned(un21, vn1);            // Compute the second
        rhat = un21 - q0 * vn1;     // quotient digit, q0.
        while (true) {
            if (Long.compareUnsigned(q0, b) >= 0 || Long.compareUnsigned(q0 * vn0, b * rhat + un0) > 0) {
                q0 = q0 - 1;
                rhat = rhat + vn1;
                if (Long.compareUnsigned(rhat, b) < 0)
                    continue;
            }
            break;
        }
        long r = (un21 * b + un0 - q0 * v) >>> s;    // return it.
        return new long[]{q1 * b + q0, r};
    }



}
