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
package net.hydromatic.tpcds.query;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Query definition.
 */
public enum Query {
  Q01, Q02, Q03, Q04, Q05, Q06, Q07, Q08, Q09,
  Q10, Q11, Q12, Q13, Q14, Q15, Q16, Q17, Q18, Q19,
  Q20, Q21, Q22, Q23, Q24, Q25, Q26, Q27, Q28, Q29,
  Q30, Q31, Q32, Q33, Q34, Q35, Q36, Q37, Q38, Q39,
  Q40, Q41, Q42, Q43, Q44, Q45, Q46, Q47, Q48, Q49,
  Q50, Q51, Q52, Q53, Q54, Q55, Q56, Q57, Q58, Q59,
  Q60, Q61, Q62, Q63, Q64, Q65, Q66, Q67, Q68, Q69,
  Q70, Q71, Q72, Q73, Q74, Q75, Q76, Q77, Q78, Q79,
  Q80, Q81, Q82, Q83, Q84, Q85, Q86, Q87, Q88, Q89,
  Q90, Q91, Q92, Q93, Q94, Q95, Q96, Q97, Q98, Q99;

  public final int id;
  public final String template;
  public final ImmutableMap<String, Generator> args;

  private static final Generator EMPTY = Generators.fixed("");
  private static final ImmutableMap<String, Generator> BUILTIN_ARGS =
      ImmutableMap.of("__LIMITA", EMPTY,
          "__LIMITB", EMPTY,
          "__LIMITC", Generators.fixed("LIMIT %d"),
          "_BEGIN", EMPTY,
          "_END", EMPTY);

  Query() {
    id = Integer.valueOf(name().substring(1));
    Init init;
    try {
      init = new Init();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    template = init.template;
    args = ImmutableMap.copyOf(init.args);
  }

  public Iterable<Map.Entry<String, Generator>> allArgs() {
    return Iterables.concat(BUILTIN_ARGS.entrySet(),
        ImmutableMap.of("_QUERY", EMPTY,
            "_STREAM", EMPTY,
            "_TEMPLATE", EMPTY).entrySet(),
        args.entrySet());
  }

  public String sql(Random random) {
    String s = template;
    for (Map.Entry<String, Generator> entry : args.entrySet()) {
      final String key = entry.getKey();
      final Generator generator = entry.getValue();
      String value = generator.generate(random);
      s = s.replace("[" + key + "]", value);
    }
    return s;
  }

  /** Contains state for initializing a query. */
  private class Init {
    String template;
    final Map<String, Generator> args = new LinkedHashMap<String, Generator>();

    Init() throws IOException {
      final InputStream stream =
          Query.class.getResourceAsStream("/query_templates/query" + id
              + ".tpl");
      final BufferedReader reader =
          new BufferedReader(new InputStreamReader(stream));
      final StringBuilder buf = new StringBuilder();
      for (;;) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        if (line.startsWith("--")) {
          continue;
        }
        if (line.matches("^ *$")) {
          continue;
        }
        if (line.matches("^ *define .*$")) {
          line = line.trim();
          int eq = line.indexOf('=');
          assert eq >= 0;
          String name = line.substring("define ".length(), eq).trim();
          String rest = line.substring(eq + 1, line.length() - 1);
          rest = rest.replaceAll("--.*", "");
          rest = rest.replaceAll("; *$", "");
          args.put(name, Generators.fixed(rest));
        } else {
          buf.append(line).append("\n");
        }
      }
      template = buf.toString().replaceAll(" *; *$", "");
    }
  }

  /** Value generator. */
  interface Generator {
    String generate(Random random);
  }

  /** Utilities for {@link Generator}. */
  static class Generators {
    /** Creates a generator that returns the same string every time. */
    public static Generator fixed(final String s) {
      return new Generator() {
        public String generate(Random random) {
          return s;
        }
      };
    }
  }
}

// End Query.java
