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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

  /** Returns the query with a given id. (1 &le; {@code id} &le; 99.) */
  public static Query of(int id) {
    return values()[id - 1];
  }

  public Iterable<Map.Entry<String, Generator>> allArgs(int limit) {
    final ImmutableMap<String, Generator> builtinArgs =
        ImmutableMap.of("_LIMITA", EMPTY,
            "_LIMITB", EMPTY,
            "_LIMITC", limit < 0 ? EMPTY : Generators.fixed("LIMIT " + limit),
            "_BEGIN", EMPTY,
            "_END", EMPTY);
    return Iterables.concat(builtinArgs.entrySet(),
        ImmutableMap.of("_QUERY", EMPTY,
            "_STREAM", EMPTY,
            "_TEMPLATE", EMPTY).entrySet(),
        args.entrySet());
  }

  public String sql(int limit, Random random) {
    String s = template;
    for (Map.Entry<String, Generator> entry : allArgs(limit)) {
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
          rest = rest.replaceAll("^ *", "");
          args.put(name, Generators.parse(rest));
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
      return new FixedGenerator(s);
    }

    /** Creates a generator that generates uniform values over an integer
     * range. */
    private static Generator uniform(Generator start, Generator end) {
      return new UniformGenerator(start, end);
    }

    private static String remove(String s, String start, String end) {
      assert s.startsWith(start) : s;
      assert s.endsWith(end) : s;
      return s.substring(start.length(), s.length() - end.length());
    }

    private static List<String> parseArgs(String s, String start, String end) {
      s = remove(s, start, end);
      final char[] chars = s.toCharArray();
      int parenCount = 0;
      boolean inQuote = false;
      int x = 0;
      List<String> list = new ArrayList<String>();
      for (int i = 0; i < chars.length; i++) {
        char c = chars[i];
        switch (c) {
        case '(':
        case '{':
          ++parenCount;
          break;
        case ')':
        case '}':
          --parenCount;
          break;
        case '"':
          inQuote = !inQuote;
          break;
        case ',':
          if (parenCount == 0 && !inQuote) {
            list.add(s.substring(x, i));
            while (i + 1 < chars.length && chars[i + 1] == ' ') {
              ++i;
            }
            x = i + 1;
            break;
          }
        }
      }
      if (chars.length > x) {
        list.add(s.substring(x));
      }
      return list;
    }

    public static Generator parse(String s) {
      final String original = s;
      if (s.startsWith("text(")) {
        List<String> args = parseArgs(s, "text(", ")");
        if (args.size() == 1) {
          return fixed(args.get(0));
        }
        final ImmutableList.Builder<Pair> builder =
            ImmutableList.builder();
        for (String arg : args) {
          if (arg.startsWith("{")) {
            List<String> subArgs = parseArgs(arg, "{", "}");
            assert subArgs.size() == 2;
            final String text = subArgs.get(0);
            final int weight = Integer.parseInt(subArgs.get(1));
            builder.add(Pair.of(text.substring(1, text.length() - 1), weight));
          }
        }
        return text(builder.build());
      }
      if (s.startsWith("ulist(")) {
        // Example:
        //  ulist(random(10000,99999,uniform),400)
        List<String> args = parseArgs(s, "ulist(", ")");
        return fixed(s); // TODO:
      }
      if (s.startsWith("dist(")) {
        // Example:
        //  dist(gender, 1, 1)
        List<String> args = parseArgs(s, "dist(", ")");
        return fixed(s); // TODO:
      }
      if (s.startsWith("DIST(")) {
        List<String> args = parseArgs(s, "DIST(", ")");
        return fixed(s); // TODO:
      }
      if (s.startsWith("date(")) {
        // Example:
        //  date([YEAR]+"-08-01",[YEAR]+"-08-30",sales)
        List<String> args = parseArgs(s, "date(", ")");
        return fixed(s); // TODO:
      }
      if (s.startsWith("rowcount(")) {
        // Example:
        //  rowcount("active_counties", "store")
        List<String> args = parseArgs(s, "rowcount(", ")");
        return fixed("100"); // TODO:
      }
      if (s.startsWith("distmember(")) {
        // Example:
        //  distmember(fips_county, [COUNTY], 3)
        List<String> args = parseArgs(s, "distmember(", ")");
        return fixed(s);
      }
      if (s.startsWith("random(")) {
        List<String> parts = parseArgs(s, "random(", ")");
        assert parts.size() == 3 : s;
        assert parts.get(2).equals("uniform") : s;
        final Generator start = parse(parts.get(0));
        final Generator end = parse(parts.get(1));
        return uniform(start, end);
      }
      try {
        int i = Integer.valueOf(s);
        return fixed(s);
      } catch (NumberFormatException e) {
        throw new AssertionError("unknown generator: " + s + " (original="
            + original + ")");
      }
    }

    private static Generator text(final ImmutableList<Pair> map) {
      return new Generator() {
        public String generate(Random random) {
          int n = 0;
          for (Pair pair : map) {
            n += pair.i;
          }
          final int r = random.nextInt(n);
          int x = 0;
          for (Pair pair : map) {
            x += pair.i;
            if (x >= r) {
              return pair.s;
            }
          }
          throw new AssertionError();
        }
      };
    }

    private static int asInt(Generator generator) {
      return Integer.parseInt(asString(generator));
    }

    private static String asString(Generator generator) {
      return ((FixedGenerator) generator).s;
    }

    /** Generator that generates the same string every time. */
    private static class FixedGenerator implements Generator {
      private final String s;

      public FixedGenerator(String s) {
        this.s = s;
      }

      public String generate(Random random) {
        return s;
      }
    }

    /** Generator that generates uniformly distributed values over a range.
     * The start and end points of the range are defined by generators. */
    private static class UniformGenerator implements Generator {
      private final Generator end;
      private final Generator start;

      public UniformGenerator(Generator start, Generator end) {
        this.end = end;
        this.start = start;
      }

      public String generate(Random random) {
        final String startValue = start.generate(random);
        final int startInt = Integer.parseInt(startValue);
        final String endValue = end.generate(random);
        final int endInt = Integer.parseInt(endValue);
        int range = endInt - startInt + 1;
        return Integer.toString(startInt + random.nextInt(range));
      }
    }
  }

  /** String-int pair. */
  private static class Pair {
    final String s;
    final int i;

    public Pair(String s, int i) {
      this.s = s;
      this.i = i;
    }

    public static Pair of(String s, int i) {
      return new Pair(s, i);
    }
  }
}

// End Query.java
