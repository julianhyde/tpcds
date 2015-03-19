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

import net.hydromatic.tpcds.CallCenter;
import net.hydromatic.tpcds.Dsgen;
import net.hydromatic.tpcds.TpcdsTable;
import net.hydromatic.tpcds.query.Query;

import org.junit.Test;

import java.util.Random;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

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

  @Test public void testQueryValues() {
    assertThat(Query.values().length, equalTo(99));
  }

  @Test public void testQuery01() {
    assertThat(Query.Q01.sql(new Random(0)),
        equalTo("with customer_total_return as\n"
            + "(select sr_customer_sk as ctr_customer_sk\n"
            + ",sr_store_sk as ctr_store_sk\n,"
            + "sum(SR_RETURN_AMT_INC_TAX) as ctr_total_return\n"
            + "from store_returns\n"
            + ",date_dim\n"
            + "where sr_returned_date_sk = d_date_sk\n"
            + "and d_year =2001\n"
            + "group by sr_customer_sk\n"
            + ",sr_store_sk)\n"
            + " select  c_customer_id\n"
            + "from customer_total_return ctr1\n"
            + ",store\n"
            + ",customer\n"
            + "where ctr1.ctr_total_return > (select avg(ctr_total_return)*1.2\n"
            + "from customer_total_return ctr2\n"
            + "where ctr1.ctr_store_sk = ctr2.ctr_store_sk)\n"
            + "and s_store_sk = ctr1.ctr_store_sk\n"
            + "and s_state = 'distmember(fips_county, [COUNTY], 3)'\n"
            + "and ctr1.ctr_customer_sk = c_customer_sk\n"
            + "order by c_customer_id\n"
            + "LIMIT 100\n"));
  }

  @Test public void testQuery55() {
    assertThat(Query.Q55.template,
        equalTo(
            "[_LIMITA]  select [_LIMITB] i_brand_id brand_id, i_brand brand,\n"
            + " \tsum(ss_ext_sales_price) ext_price\n"
            + " from date_dim, store_sales, item\n"
            + " where d_date_sk = ss_sold_date_sk\n"
            + " \tand ss_item_sk = i_item_sk\n"
            + " \tand i_manager_id=[MANAGER]\n"
            + " \tand d_moy=[MONTH]\n"
            + " \tand d_year=[YEAR]\n"
            + " group by i_brand, i_brand_id\n"
            + " order by ext_price desc, i_brand_id\n"
            + "[_LIMITC]\n"));
  }

  @Test public void testQuery72() {
    assertThat(Query.Q72.sql(new Random(0)),
        not(containsString("[")));
  }

  @Test public void testGenerateAll() {
    for (Query query : Query.values()) {
      assertThat(query.sql(new Random(0)), notNullValue());
    }
  }
}

// End TpcdsTest.java
