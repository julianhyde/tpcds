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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.io.File;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Definition of a TPC-DS table.
 *
 * @param <E> Element type
 */
public abstract class TpcdsTable<E> {
  public static final Set<Flag> NOP_SOURCE_DDL =
      EnumSet.of(Flag.NOP, Flag.SOURCE_DDL);

  /*
  tdef s_tdefs[] = {
{"s_brand",                             "s_br", FL_NOP|FL_SOURCE_DDL,  S_BRAND_START, S_BRAND_END, S_BRAND},
{"s_customer_address",  "s_ca", FL_SOURCE_DDL|FL_PASSTHRU,  S_CUSTOMER_ADDRESS_START, S_CUSTOMER_ADDRESS_END, S_CUSTOMER_ADDRESS, -1, NULL, 0, 0, 0, 0x0, 0x01, NULL},
{"s_call_center",               "s_cc", FL_SOURCE_DDL,  S_CALL_CENTER_START, S_CALL_CENTER_END, S_CALL_CENTER, -1, NULL, 0, 0, 0, 0x0, 0x02, NULL},
{"s_catalog",                   "s_ct", FL_SOURCE_DDL|FL_NOP,  S_CATALOG_START, S_CATALOG_END, S_CATALOG, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_catalog_order",             "s_cord", FL_SOURCE_DDL|FL_PARENT|FL_DATE_BASED, S_CATALOG_ORDER_START, S_CATALOG_ORDER_END, S_CATALOG_ORDER, S_CATALOG_ORDER_LINEITEM, NULL, 0, 0, 0, 0x0, 0x01, NULL},
{"s_catalog_order_lineitem",  "s_cl", FL_SOURCE_DDL|FL_CHILD|FL_PARENT, S_CATALOG_ORDER_LINEITEM_START, S_CATALOG_ORDER_LINEITEM_END, S_CATALOG_ORDER_LINEITEM, S_CATALOG_RETURNS, NULL, 0, 0, 0, 0x0, 0x07, NULL},
{"s_catalog_page",              "s_cp", FL_SOURCE_DDL|FL_PASSTHRU,  S_CATALOG_PAGE_START, S_CATALOG_PAGE_END, S_CATALOG_PAGE, -1, NULL, 0, 0, 0, 0x0, 0x033, NULL},
{"s_catalog_promotional_item",    "s_ci", FL_NOP|FL_SOURCE_DDL, S_CATALOG_PROMOTIONAL_ITEM_START, S_CATALOG_PROMOTIONAL_ITEM_END, S_CATALOG_PROMOTIONAL_ITEM, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_catalog_returns",   "s_cr", FL_SOURCE_DDL|FL_CHILD, S_CATALOG_RETURNS_START, S_CATALOG_RETURNS_END, S_CATALOG_RETURNS, -1, NULL, 0, 0, 0, 0x0, 0x0E, NULL},
{"s_category",                  "s_cg", FL_NOP|FL_SOURCE_DDL,  S_CATEGORY_START, S_CATEGORY_END, S_CATEGORY, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_class",                             "s_cl", FL_NOP|FL_SOURCE_DDL,  S_CLASS_START, S_CLASS_END, S_CLASS, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_company",                   "s_co", FL_NOP|FL_SOURCE_DDL,  S_COMPANY_START, S_COMPANY_END, S_COMPANY, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_customer",                  "s_cu", FL_SOURCE_DDL,  S_CUSTOMER_START, S_CUSTOMER_END, S_CUSTOMER, -1, NULL, 0, 0, 0, 0x0, 0x01, NULL},
{"s_division",                  "s_di", FL_NOP|FL_SOURCE_DDL,  S_DIVISION_START, S_DIVISION_END, S_DIVISION, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_inventory",                 "s_in", FL_SOURCE_DDL|FL_DATE_BASED,  S_INVENTORY_START, S_INVENTORY_END, S_INVENTORY, -1, NULL, 0, 0, 0, 0x0, 0x07, NULL},
{"s_item",                              "s_it", FL_SOURCE_DDL,  S_ITEM_START, S_ITEM_END, S_ITEM, -1, NULL, 0, 0, 0, 0x0, 0x01, NULL},
{"s_manager",                   "s_mg", FL_NOP|FL_SOURCE_DDL,  S_MANAGER_START, S_MANAGER_END, S_MANAGER, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_manufacturer",              "s_mn", FL_NOP|FL_SOURCE_DDL,  S_MANUFACTURER_START, S_MANUFACTURER_END, S_MANUFACTURER, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_market",                    "s_mk", FL_NOP|FL_SOURCE_DDL,  S_MARKET_START, S_MARKET_END, S_MARKET, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_product",                   "s_pr", FL_NOP|FL_SOURCE_DDL,  S_PRODUCT_START, S_PRODUCT_END, S_PRODUCT, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_promotion",                 "s_pm", FL_SOURCE_DDL|FL_PASSTHRU,  S_PROMOTION_START, S_PROMOTION_END, S_PROMOTION, -1, NULL, 0, 0, 0, 0x0, 0x01, NULL},
{"s_purchase",                  "s_pu", FL_SOURCE_DDL|FL_PARENT|FL_DATE_BASED, S_PURCHASE_START, S_PURCHASE_END, S_PURCHASE, S_PURCHASE_LINEITEM, NULL, 0, 0, 0, 0x0, 0x01, NULL},
{"s_purchase_lineitem", "s_pl", FL_SOURCE_DDL|FL_CHILD|FL_PARENT, S_PURCHASE_LINEITEM_START, S_PURCHASE_LINEITEM_END, S_PURCHASE_LINEITEM, S_STORE_RETURNS, NULL, 0, 0, 0, 0x0, 0x07, NULL},
{"s_reason",                    "s_re", FL_NOP|FL_SOURCE_DDL,  S_REASON_START, S_REASON_END, S_REASON, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_store",                             "s_st", FL_SOURCE_DDL,  S_STORE_START, S_STORE_END, S_STORE, -1, NULL, 0, 0, 0, 0x0, 0x01, NULL},
{"s_store_promotional_item","s_sp",FL_NOP|FL_SOURCE_DDL,S_STORE_PROMOTIONAL_ITEM_START, S_STORE_PROMOTIONAL_ITEM_END, S_STORE_PROMOTIONAL_ITEM, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_store_returns",             "s_sr", FL_SOURCE_DDL|FL_CHILD, S_STORE_RETURNS_START, S_STORE_RETURNS_END, S_STORE_RETURNS, -1, NULL, 0, 0, 0, 0x0, 0x0E, NULL},
{"s_subcategory",               "s_ct", FL_NOP|FL_SOURCE_DDL,   S_SUBCATEGORY_START, S_SUBCATEGORY_END, S_SUBCATEGORY, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_subclass",                  "s_sc", FL_NOP|FL_SOURCE_DDL,   S_SUBCLASS_START, S_SUBCLASS_END, S_SUBCLASS, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_warehouse",                 "s_wh", FL_SOURCE_DDL,   S_WAREHOUSE_START, S_WAREHOUSE_END, S_WAREHOUSE, -1, NULL, 0, 0, 0, 0x0, 0x01, NULL},
{"s_web_order",                 "s_wo", FL_SOURCE_DDL|FL_PARENT|FL_DATE_BASED, S_WEB_ORDER_START, S_WEB_ORDER_END, S_WEB_ORDER, S_WEB_ORDER_LINEITEM, NULL, 0, 0, 0, 0x0, 0x01, NULL},
{"s_web_order_lineitem","s_wl", FL_SOURCE_DDL|FL_CHILD|FL_PARENT, S_WEB_ORDER_LINEITEM_START, S_WEB_ORDER_LINEITEM_END, S_WEB_ORDER_LINEITEM, S_WEB_RETURNS, NULL, 0, 0, 0, 0x0, 0x07, NULL},
{"s_web_page",                  "s_wp", FL_SOURCE_DDL|FL_PASSTHRU,   S_WEB_PAGE_START, S_WEB_PAGE_END, S_WEB_PAGE, -1, NULL, 0, 0, 0, 0x0, 0x01, NULL},
{"s_web_promotional_item","s_wi",FL_NOP|FL_SOURCE_DDL,  S_WEB_PROMOTIONAL_ITEM_START, S_WEB_PROMOTIONAL_ITEM_END, S_WEB_PROMOTIONAL_ITEM, -1, NULL, 0, 0, 0, 0x0, 0x0, NULL},
{"s_web_returns",               "s_wr", FL_SOURCE_DDL|FL_CHILD, S_WEB_RETURNS_START, S_WEB_RETURNS_END, S_WEB_RETURNS, -1, NULL, 0, 0, 0, 0x0, 0x0E, NULL},
{"s_web_site",                  "s_ws", FL_SOURCE_DDL,   S_WEB_SITE_START, S_WEB_SITE_END, S_WEB_SITE, -1, NULL, 0, 0, 0, 0x0, 0x01, NULL},
{"s_zip_to_gmt",                "s_zi", FL_SOURCE_DDL|FL_VPRINT,   S_ZIPG_START, S_ZIPG_END, S_ZIPG, -1, NULL, 0, 0, 0, 0x0, 0x03, NULL},
{NULL}
};





   */
  public static final TpcdsTable<CallCenter> CALL_CENTER =
      new TpcdsTable<CallCenter>("call_center", "cc", NOP_SOURCE_DDL,
          CallCenter.Column.values(), -1, null, 0, 0, 0, 0x0, 0x0, null) {
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
    return new TpcdsTable<E>(name, prefix, NOP_SOURCE_DDL, columns.build()) {
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
  public final Set<Flag> flags;
  public final List<TpcdsColumn<E>> columns;
  public final int nParam;
  public final int nFirstColumn;
  public final int nLastColumn;
  public long kNullBitMap;
  public final int nNullPct;
  public long kNotNullBitMap;

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

  public <E> TpcdsTable(String name, String prefix, Set<Flag> flags,
      TpcdsColumn[] columns, int nTableIndex, int nParam, File outFile, int nUpdateSize, int nNewRowPct, int nNullPct) {
    this.name = checkNotNull(name);
    this.prefix = checkNotNull(prefix);
    this.flags = ImmutableSet.copyOf(flags);
    //noinspection unchecked
    this.columns = (ImmutableList) ImmutableList.copyOf(columns);
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

  enum Flag {
    NOP,
    SOURCE_DDL,
    PASSTHRU,
  }
}

// End TpcdsTable.java
