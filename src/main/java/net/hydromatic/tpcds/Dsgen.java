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
package net.hydromatic.tpcds;

import java.util.HashMap;
import java.util.Map;

/**
 * TPC-DS generator. */
public class Dsgen {
  protected final Map<String, Object> param;

  /** Creates a Dsgen. */
  public static Dsgen create() {
    return new Dsgen(new HashMap<String, Object>());
  }

  protected Dsgen(Map<String, Object> param) {
    this.param = param;
  }

  public int mk_w_call_center(Object row, long index) {
    return 0;
  }
}

// End Dsgen.java
