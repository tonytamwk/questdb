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

package io.questdb.cairo.sql;

import io.questdb.griffin.SqlExecutionContext;
import io.questdb.std.BinarySequence;
import io.questdb.std.Long256;
import io.questdb.std.str.CharSink;

import java.io.Closeable;

public interface Function extends Closeable {

    @Override
    default void close() {
    }

    BinarySequence getBin(Record rec);

    long getBinLen(Record rec);

    boolean getBool(Record rec);

    byte getByte(Record rec);

    long getDate(Record rec);

    double getDouble(Record rec);

    float getFloat(Record rec);

    int getInt(Record rec);

    long getLong(Record rec);

    Long256 getLong256A(Record rec);

    Long256 getLong256B(Record rec);

    void getLong256(Record rec, CharSink sink);

    RecordMetadata getMetadata();

    int getPosition();

    RecordCursorFactory getRecordCursorFactory();

    short getShort(Record rec);

    char getChar(Record rec);

    CharSequence getStr(Record rec);

    void getStr(Record rec, CharSink sink);

    CharSequence getStrB(Record rec);

    int getStrLen(Record rec);

    CharSequence getSymbol(Record rec);

    long getTimestamp(Record rec);

    long getNanoTimestamp(Record rec);

    int getType();

    default void init(SymbolTableSource symbolTableSource, SqlExecutionContext executionContext) {
    }

    default boolean isConstant() {
        return false;
    }

    default void toTop() {
    }
}
