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


import org.junit.Assert;
import org.junit.Test;

public class Int128Test {

    @Test
    public void testNewByte() {

        Int128 x = new Int128((byte)-1);
        Assert.assertEquals(x.toString(), "-1");

        x = new Int128((byte)12);
        Assert.assertEquals(x.toString(), "12");

        x = new Int128((byte)127);
        Assert.assertEquals(x.toString(), "127");

        x = new Int128((short)-12);
        Assert.assertEquals(x.toString(), "-12");

        x = new Int128((short)-128);
        Assert.assertEquals(x.toString(), "-128");
    }

    @Test
    public void testNewShort() {
        Int128 x = new Int128((short)128);
        Assert.assertEquals(x.toString(), "128");

        x = new Int128((short)32767);
        Assert.assertEquals(x.toString(), "32767");

        x = new Int128((short)-128);
        Assert.assertEquals(x.toString(), "-128");

        x = new Int128((short)-32768);
        Assert.assertEquals(x.toString(), "-32768");
    }

}