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

import io.questdb.std.LongList;
import io.questdb.std.ObjList;
import io.questdb.std.Unsafe;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransitionRule;
import java.time.zone.ZoneRules;

public class TimeZoneRulesImpl implements TimeZoneRules {
    public static final long SAVING_INSTANT_TRANSITION = Unsafe.getFieldOffset(ZoneRules.class, "savingsInstantTransitions");
    public static final long STANDARD_OFFSETS = Unsafe.getFieldOffset(ZoneRules.class, "standardOffsets");
    public static final long LAST_RULES = Unsafe.getFieldOffset(ZoneRules.class, "lastRules");
    public static final long SAVINGS_LOCAL_TRANSITION = Unsafe.getFieldOffset(ZoneRules.class, "savingsLocalTransitions");
    public static final long WALL_OFFSETS = Unsafe.getFieldOffset(ZoneRules.class, "wallOffsets");
    private final long cutoffTransition;
    private final LongList historicTransitions = new LongList();
    private final ObjList<TransitionRule> rules;
    private final int ruleCount;
    private final int[] wallOffsets;
    private final long firstWall;
    private final long lastWall;
    private final int historyOverlapCheckCutoff;
    private final long standardOffset;

    public TimeZoneRulesImpl(ZoneRules rules) {
        final long[] savingsInstantTransition = (long[]) Unsafe.getUnsafe().getObject(rules, SAVING_INSTANT_TRANSITION);

        if (savingsInstantTransition.length == 0) {
            ZoneOffset[] standardOffsets = (ZoneOffset[]) Unsafe.getUnsafe().getObject(rules, STANDARD_OFFSETS);
            standardOffset = standardOffsets[0].getTotalSeconds() * NanoTimestamps.SECOND_NANOS;
        } else {
            standardOffset = Long.MIN_VALUE;
        }

        LocalDateTime[] savingsLocalTransitions = (LocalDateTime[]) Unsafe.getUnsafe().getObject(rules, SAVINGS_LOCAL_TRANSITION);
        for (int i = 0, n = savingsLocalTransitions.length; i < n; i++) {
            LocalDateTime dt = savingsLocalTransitions[i];

            historicTransitions.add(NanoTimestamps.toNanos(dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(), dt.getHour(), dt.getMinute()) +
                    dt.getSecond() * NanoTimestamps.SECOND_NANOS + dt.getNano());
        }
        cutoffTransition = historicTransitions.getLast();
        historyOverlapCheckCutoff = historicTransitions.size() - 1;


        ZoneOffsetTransitionRule[] lastRules = (ZoneOffsetTransitionRule[]) Unsafe.getUnsafe().getObject(rules, LAST_RULES);
        this.rules = new ObjList<>(lastRules.length);
        for (int i = 0, n = lastRules.length; i < n; i++) {
            ZoneOffsetTransitionRule zr = lastRules[i];
            TransitionRule tr = new TransitionRule();
            tr.offsetBefore = zr.getOffsetBefore().getTotalSeconds();
            tr.offsetAfter = zr.getOffsetAfter().getTotalSeconds();
            tr.standardOffset = zr.getStandardOffset().getTotalSeconds();
            tr.dow = zr.getDayOfWeek() == null ? -1 : zr.getDayOfWeek().getValue();
            tr.dom = zr.getDayOfMonthIndicator();
            tr.month = zr.getMonth().getValue();
            tr.midnightEOD = zr.isMidnightEndOfDay();
            tr.hour = zr.getLocalTime().getHour();
            tr.minute = zr.getLocalTime().getMinute();
            tr.second = zr.getLocalTime().getSecond();
            switch (zr.getTimeDefinition()) {
                case UTC:
                    tr.timeDef = TransitionRule.UTC;
                    break;
                case STANDARD:
                    tr.timeDef = TransitionRule.STANDARD;
                    break;
                default:
                    tr.timeDef = TransitionRule.WALL;
                    break;
            }
            this.rules.add(tr);
        }

        this.ruleCount = lastRules.length;

        ZoneOffset[] wallOffsets = (ZoneOffset[]) Unsafe.getUnsafe().getObject(rules, WALL_OFFSETS);
        this.wallOffsets = new int[wallOffsets.length];
        for (int i = 0, n = wallOffsets.length; i < n; i++) {
            this.wallOffsets[i] = wallOffsets[i].getTotalSeconds();
        }
        this.firstWall = this.wallOffsets[0] * NanoTimestamps.SECOND_NANOS;
        this.lastWall = this.wallOffsets[wallOffsets.length - 1] * NanoTimestamps.SECOND_NANOS;
    }

    @Override
    public long getOffset(long nanos, int year, boolean leap) {
        if (standardOffset != Long.MIN_VALUE) {
            return standardOffset;
        }

        if (ruleCount > 0 && nanos > cutoffTransition) {
            return fromRules(nanos, year, leap);
        }

        if (nanos > cutoffTransition) {
            return lastWall;
        }

        return fromHistory(nanos);
    }

    @Override
    public long getOffset(long nanos) {
        int y = NanoTimestamps.getYear(nanos);
        return getOffset(nanos, y, NanoTimestamps.isLeapYear(y));
    }

    private long fromHistory(long nanos) {
        int index = historicTransitions.binarySearch(nanos);
        if (index == -1) {
            return firstWall;
        }

        if (index < 0) {
            index = -index - 2;
        } else if (index < historyOverlapCheckCutoff && historicTransitions.getQuick(index) == historicTransitions.getQuick(index + 1)) {
            index++;
        }

        if ((index & 1) == 0) {
            int offsetBefore = wallOffsets[index / 2];
            int offsetAfter = wallOffsets[index / 2 + 1];

            int delta = offsetAfter - offsetBefore;
            if (delta > 0) {
                // engage 0 transition logic
                return (delta + offsetAfter) * NanoTimestamps.SECOND_NANOS;
            } else {
                return offsetBefore * NanoTimestamps.SECOND_NANOS;
            }
        } else {
            return wallOffsets[index / 2 + 1] * NanoTimestamps.SECOND_NANOS;
        }
    }

    private long fromRules(long nanos, int year, boolean leap) {

        int offset = 0;

        for (int i = 0; i < ruleCount; i++) {
            TransitionRule zr = rules.getQuick(i);
            offset = zr.offsetBefore;
            int offsetAfter = zr.offsetAfter;

            int dom = zr.dom;
            int month = zr.month;

            int dow = zr.dow;
            long date;
            if (dom < 0) {
                date = NanoTimestamps.toNanos(year, leap, month, NanoTimestamps.getDaysPerMonth(month, leap) + 1 + dom, zr.hour, zr.minute) + zr.second * NanoTimestamps.SECOND_NANOS;
                if (dow > -1) {
                    date = NanoTimestamps.previousOrSameDayOfWeek(date, dow);
                }
            } else {
                date = NanoTimestamps.toNanos(year, leap, month, dom, zr.hour, zr.minute) + zr.second * NanoTimestamps.SECOND_NANOS;
                if (dow > -1) {
                    date = NanoTimestamps.nextOrSameDayOfWeek(date, dow);
                }
            }

            if (zr.midnightEOD) {
                date = NanoTimestamps.addDays(date, 1);
            }

            switch (zr.timeDef) {
                case TransitionRule.UTC:
                    date += (offset - ZoneOffset.UTC.getTotalSeconds()) * NanoTimestamps.SECOND_NANOS;
                    break;
                case TransitionRule.STANDARD:
                    date += (offset - zr.standardOffset) * NanoTimestamps.SECOND_NANOS;
                    break;
                default:  // WALL
                    break;
            }

            long delta = offsetAfter - offset;

            if (delta > 0) {
                if (nanos < date) {
                    return offset * NanoTimestamps.SECOND_NANOS;
                }

                if (nanos < date + delta) {
                    return (offsetAfter + delta) * NanoTimestamps.SECOND_NANOS;
                } else {
                    offset = offsetAfter;
                }
            } else {
                if (nanos < date) {
                    return offset * NanoTimestamps.SECOND_NANOS;
                } else {
                    offset = offsetAfter;
                }
            }
        }

        return offset * NanoTimestamps.SECOND_NANOS;
    }

    private static class TransitionRule {
        public static final int UTC = 0;
        public static final int STANDARD = 1;
        public static final int WALL = 2;
        int offsetBefore;
        int offsetAfter;
        int standardOffset;
        int dow;
        int dom;
        int month;
        boolean midnightEOD;
        int hour;
        int minute;
        int second;
        int timeDef;
    }
}
