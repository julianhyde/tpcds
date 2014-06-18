/*
// Licensed to Julian Hyde under one or more contributor license
// agreements. See the NOTICE file distributed with this work for
// additional information regarding copyright ownership.
//
// Julian Hyde licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except in
// compliance with the License. You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/
package net.hydromatic.tpcds.test;

import net.hydromatic.tpcds.*;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/** Unit test for TPC-DS. */
public class TpcdsTest {
  private <E> void assertRowCount(Iterable<E> generator, int expectedRowCount) {
    int rowCount = 0;
    for (E row : generator) {
      ++rowCount;
    }
    assertThat(rowCount, equalTo(expectedRowCount));
  }

  @Test public void testCallCenter() {
    TpcdsTable.CALL_CENTER.builder(Dsgen.create());
    final Iterable<CallCenter> generator =
        TpcdsTable.CALL_CENTER.createGenerator(0d, 0, 0);
    assertRowCount(generator, 0);
  }

}

// End TpcdsTest.java
