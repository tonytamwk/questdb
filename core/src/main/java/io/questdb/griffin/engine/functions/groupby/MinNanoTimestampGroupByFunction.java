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

package io.questdb.griffin.engine.functions.groupby;

import io.questdb.cairo.ArrayColumnTypes;
import io.questdb.cairo.ColumnType;
import io.questdb.cairo.map.MapValue;
import io.questdb.cairo.sql.Function;
import io.questdb.cairo.sql.Record;
import io.questdb.griffin.engine.functions.GroupByFunction;
import io.questdb.griffin.engine.functions.NanoTimestampFunction;
import io.questdb.griffin.engine.functions.UnaryFunction;
import io.questdb.std.Numbers;
import org.jetbrains.annotations.NotNull;

public class MinNanoTimestampGroupByFunction extends NanoTimestampFunction implements GroupByFunction, UnaryFunction {
    private final Function arg;
    private int valueIndex;

    public MinNanoTimestampGroupByFunction(int position, @NotNull Function arg) {
        super(position);
        this.arg = arg;
    }

    @Override
    public void computeFirst(MapValue mapValue, Record record) {
        mapValue.putLong(valueIndex, arg.getLong(record));
    }

    @Override
    public void computeNext(MapValue mapValue, Record record) {
        long min = mapValue.getNanoTimestamp(valueIndex);
        long next = arg.getNanoTimestamp(record);
        if (next != Numbers.LONG_NaN && next < min || min == Numbers.LONG_NaN) {
            mapValue.putNanoTimestamp(valueIndex, next);
        }
    }

    @Override
    public void pushValueTypes(ArrayColumnTypes columnTypes) {
        this.valueIndex = columnTypes.getColumnCount();
        columnTypes.add(ColumnType.NANOTIMESTAMP);
    }

    @Override
    public void setNull(MapValue mapValue) {
        mapValue.putNanoTimestamp(valueIndex, Numbers.LONG_NaN);
    }

    @Override
    public Function getArg() {
        return arg;
    }

    @Override
    public long getNanoTimestamp(Record rec) {
        return rec.getNanoTimestamp(valueIndex);
    }
}