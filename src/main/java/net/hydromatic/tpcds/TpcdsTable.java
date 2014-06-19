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

import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Definition of a TPC-DS table.
 *
 * @param <E> Element type
 */
public abstract class TpcdsTable<E> {
  public static final TpcdsTable<CallCenter> CALL_CENTER =
      new TpcdsTable<CallCenter>("call_center", "cc",
          ImmutableList.copyOf(CallCenter.Column.values())) {
        public Iterable<CallCenter> createGenerator(double scaleFactor,
            int part, int partCount) {
          return new CallCenter.Generator(scaleFactor, part, partCount);
        }

        public void builder(Dsgen dsgen) {
          dsgen.mk_w_call_center(null, 0);
        }

        public void loader1() {
        }

        public void loader2() {
        }

        public void validate(int nTable, long kRow, int[] permutation) {
        }
      };

  public static final TpcdsTable<CatalogPage> CATALOG_PAGE =
      dummy("catalog_page", "cp", CatalogPage.class);

  public static final TpcdsTable<CatalogReturn> CATALOG_RETURNS =
      dummy("catalog_returns", "cr", CatalogReturn.class);

  public static final TpcdsTable<CatalogSale> CATALOG_SALES =
      dummy("catalog_sales", "cs", CatalogSale.class);

  public static final TpcdsTable<Customer> CUSTOMER =
      dummy("customer", "c", Customer.class);

  public static final TpcdsTable<CustomerAddress> CUSTOMER_ADDRESS =
      dummy("customer_address", "ca", CustomerAddress.class);

  public static final TpcdsTable<CustomerDemographic> CUSTOMER_DEMOGRAPHICS =
      dummy("customer_demographics", "cd", CustomerDemographic.class);

  public static final TpcdsTable<DateDim> DATE_DIM =
      dummy("date_dim", "d", DateDim.class);

  public static final TpcdsTable<DbgenVersion> DBGEN_VERSION =
      dummy("dbgen_version", "dv", DbgenVersion.class);

  public static final TpcdsTable<HouseholdDemographic> HOUSEHOLD_DEMOGRAPHICS =
      dummy("household_demographics", "hd", HouseholdDemographic.class);

  public static final TpcdsTable<IncomeBand> INCOME_BAND =
      dummy("income_band", "ib", IncomeBand.class);

  public static final TpcdsTable<Inventory> INVENTORY =
      dummy("inventory", "inv", Inventory.class);

  public static final TpcdsTable<Item> ITEM =
      dummy("item", "i", Item.class);

  public static final TpcdsTable<Promotion> PROMOTION =
      dummy("promotion", "p", Promotion.class);

  public static final TpcdsTable<Reason> REASON =
      dummy("reason", "r", Reason.class);

  public static final TpcdsTable<ShipMode> SHIP_MODE =
      dummy("ship_mode", "sm", ShipMode.class);

  public static final TpcdsTable<Store> STORE =
      dummy("store", "s", Store.class);

  public static final TpcdsTable<StoreReturn> STORE_RETURNS =
      dummy("store_returns", "sr", StoreReturn.class);

  public static final TpcdsTable<StoreSale> STORE_SALES =
      dummy("store_sales", "ss", StoreSale.class);

  public static final TpcdsTable<TimeDim> TIME_DIM =
      dummy("time_dim", "t", TimeDim.class);

  public static final TpcdsTable<Warehouse> WAREHOUSE =
      dummy("warehouse", "w", Warehouse.class);

  public static final TpcdsTable<WebPage> WEB_PAGE =
      dummy("web_page", "wp", WebPage.class);

  public static final TpcdsTable<WebReturn> WEB_RETURNS =
      dummy("web_returns", "wr", WebReturn.class);

  public static final TpcdsTable<WebSale> WEB_SALES =
      dummy("web_sales", "ws", WebSale.class);

  public static final TpcdsTable<WebSite> WEB_SITE =
      dummy("web_site", "web", WebSite.class);

  private static <E extends TpcdsEntity> TpcdsTable<E> dummy(String name,
        final String prefix, Class<E> clazz) {
    ImmutableList.Builder<TpcdsColumn> columns = ImmutableList.builder();
    for (final Field field : clazz.getFields()) {
      columns.add(
          new TpcdsColumn() {
            public String getString(Object o) {
              throw new UnsupportedOperationException();
            }

            public double getDouble(Object o) {
              throw new UnsupportedOperationException();
            }

            public long getLong(Object o) {
              throw new UnsupportedOperationException();
            }

            public String getColumnName() {
              return prefix
                  + "_"
                  + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE,
                      field.getName());
            }

            public Class<?> getType() {
              return field.getType();
            }
          });
    }
    return new TpcdsTable<E>(name, prefix, columns.build()) {
      @Override public Iterable<E> createGenerator(double scaleFactor, int part,
          int partCount) {
        return ImmutableList.of();
      }

      @Override public void builder(Dsgen dsgen) {}

      @Override public void loader1() {}

      @Override public void loader2() {}

      @Override public void validate(int nTable, long kRow, int[] permutation) {
      }
    };
  }

  public final String name;
  public final String prefix;
  public final ImmutableList<TpcdsColumn<E>> columns;
  public final int nParam = 0; // TODO:
  public final int nFirstColumn = 0; // TODO:
  public final int nLastColumn = 0; // TODO:
  public long kNullBitMap; // TODO:
  public final int nNullPct = 0; // TODO:
  public long kNotNullBitMap; // TODO:


  private static final List<TpcdsTable<?>> TABLES =
      ImmutableList.<TpcdsTable<?>>of(
          CALL_CENTER,
          CATALOG_PAGE,
          CATALOG_RETURNS,
          CATALOG_SALES,
          CUSTOMER,
          CUSTOMER_ADDRESS,
          CUSTOMER_DEMOGRAPHICS,
          DATE_DIM,
          DBGEN_VERSION,
          HOUSEHOLD_DEMOGRAPHICS,
          INCOME_BAND,
          INVENTORY,
          ITEM,
          PROMOTION,
          REASON,
          SHIP_MODE,
          STORE,
          STORE_RETURNS,
          STORE_SALES,
          TIME_DIM,
          WAREHOUSE,
          WEB_PAGE,
          WEB_RETURNS,
          WEB_SALES,
          WEB_SITE);

  private static final Map<String, TpcdsTable<?>> TABLES_BY_NAME =
    Maps.uniqueIndex(TABLES, tableNameGetter());

  public TpcdsTable(String name, String prefix, List columns) {
    this.name = checkNotNull(name);
    this.prefix = checkNotNull(prefix);
    //noinspection unchecked
    this.columns = ImmutableList.copyOf(columns);
  }

  public static TpcdsTable[] getTables() {
    return TABLES.toArray(new TpcdsTable[TABLES.size()]);
  }

  public String getTableName() {
    return name;
  }

  public abstract Iterable<E> createGenerator(double scaleFactor, int part,
      int partCount);

  /** To prep output. */
  public abstract void builder(Dsgen dsgen);

  /** To present output 1. */
  public abstract void loader1();

  /** To present output 2. */
  public abstract void loader2();

  public abstract void validate(int nTable, long kRow, int[] permutation);

  public List<TpcdsColumn<E>> getColumns() {
    return columns;
  }

  public static Function<TpcdsTable<?>, String> tableNameGetter() {
    return new Function<TpcdsTable<?>, String>() {
      public String apply(TpcdsTable<?> table) {
        return table.getTableName();
      }
    };
  }
}

// End TpcdsTable.java
