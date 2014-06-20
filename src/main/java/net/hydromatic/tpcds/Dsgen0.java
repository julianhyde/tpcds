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

import java.io.*;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.Map;

/**
 * TPC-DS generator. */
class Dsgen0 extends Dsgen {
  Dsgen0(Map<String, Object> param) {
    super(param);
  }

  /** Command line entry point. */
  public static void main(String[] args) {
  }

  // build_support.c

  static final String szXlate = "ABCDEFGHIJKLMNOP";
  static int ltoc(char[] szDest, int offset, long nVal) {
    for (int i = 0; i < 8; i++) {
      char c = szXlate.charAt((int) (nVal & 0xF));
      szDest[offset++] = c;
      nVal >>= 4;
    }
    return offset;
  }

  static String mk_bkey(long kPrimary, int nStream) {
    char[] buf = new char[16];
    ltoc(buf, 0, kPrimary >> 32);
    ltoc(buf, 8, kPrimary & 0xFFFFFFFFL);
    return new String(buf);
  }

  // porting.h

  public static final int MAXINT = Integer.MAX_VALUE;

  // genrand.h

  // based on "typedef struct RNG_T rng_t"
  class Rng {
    int nUsed;
    int nUsedPerRow;
    long nSeed;
    long nInitialSeed; /* used to allow skip_row() to back up */
    int nColumn; /* column where this stream is used */
    int nTable; /* table where this stream is used */
    int nDuplicateOf;   /* duplicate streams allow independent tables to share data streams */
    long nTotal;
  }

  static Rng[] streams;

  public static final int FL_SEED_OVERRUN       = 0x0001;
  public static final String ALPHANUM   =
      "abcdefghijklmnopqrstuvxyzABCDEFGHIJKLMNOPQRSTUVXYZ0123456789";
  public static final String DIGITS     = "0123456789";
  public static final int RNG_SEED      = 19620718;

  // genrand.c

  static long Mult = 16807;       /* the multiplier */
  static long nQ = 127773;        /* the quotient MAXINT / Mult */
  static long nR = 2836;          /* the remainder MAXINT % Mult */

  static long next_random(int stream) {
    long s = streams[stream].nSeed,
        div_res,
        mod_res;

    div_res = s / nQ;
    mod_res = s - nQ * div_res;  /* i.e., mod_res = s % nQ */
    s = Mult * mod_res - div_res * nR;
    if (s < 0) {
      s += MAXINT;
    }
    streams[stream].nSeed = s;
    streams[stream].nUsed += 1;
    streams[stream].nTotal += 1;
    return (s);
  }

  /** Generates a random integer given the distribution and limits. */
  static int genrand_integer(int dist, int min, int max, int mean, int stream) {
    switch (dist) {
    case DIST_UNIFORM:
      int res = (int) next_random(stream);
      res %= max - min + 1;
      res += min;
      return res;
    case DIST_EXPONENTIAL:
      double fres = 0;
      for (int i = 0; i < 12; i++) {
        fres += (double) (next_random(stream) / MAXINT) - 0.5;
      }
      return min + (int) ((max - min + 1) * fres);
    default:
      throw new RuntimeException("Undefined distribution");
    }
  }

  static long genrand_key(int dist, long min, long max, long mean, int stream) {
    switch (dist) {
    case DIST_UNIFORM:
      long res = next_random (stream);
      res %= (int) (max - min + 1);
      res += (int) min;
      return res;
    case DIST_EXPONENTIAL:
      double fres = 0;
      for (int i = 0; i < 12; i++) {
        fres += (double) (next_random(stream) / MAXINT) - 0.5;
      }
      return (int) min + (int) ((max - min + 1) * fres);
    default:
      throw new AssertionError("Undefined distribution");
    }
  }

  // r_dist.h

  public static final int DIST_UNIFORM = 0x0001;
  public static final int DIST_EXPONENTIAL = 0x0002;
  // sales and returns are special; they must match calendar.dst
  public static final int DIST_SALES                    = 3;
  public static final int DIST_RETURNS          = 5;
  public static final int DIST_CHAR                     = 0x0004;
  public static final int DIST_INT            = 0x0008;
  public static final int DIST_NAMES_SET                = 0xff00;

  // DistNameIndex needs to know what sort of name we are trying to match
  public static final int VALUE_NAME                    = 0x0000;
  public static final int WEIGHT_NAME                   = 0x0001;

  // decimal.c

  /** Convert an ascii string to a decimal_t structure. */
  Decimal strtodec(String s) {
    return Decimal.parse(s);
  }

  // r_params.c

  /** Returns the value of an integer parameter */
  int get_int(String var) {
    return (Integer) param.get(var);
  }

  /** Returns the value of a character parameter. */
  String get_str(String var) {
    return (String) param.get(var);
  }

  // nulls.c

  static int nLastTable = 0;

  static boolean nullCheck(int nColumn) {
    long kBitMask = 1;

    nLastTable = getTableFromColumn(nColumn);
    TpcdsTable pTdef = getSimpleTdefsByNumber(nLastTable);

    kBitMask <<= nColumn - pTdef.nFirstColumn;

    return (pTdef.kNullBitMap & kBitMask) != 0;
  }

  /** Sets the kNullBitMap for a particular table.
   *
   * <p>Algorithm:<br>
   *    1. if random[1,100] >= table's NULL pct, clear map and return<br>
   *    2. set map</p>
   *
   * <p>Side Effects: uses 2 RNG calls
  */
  long nullSet(int nStream) {
    nLastTable = getTableFromColumn(nStream);
    TpcdsTable pTdef = getSimpleTdefsByNumber(nLastTable);

    // burn the RNG calls
    int nThreshold = genrand_integer(DIST_UNIFORM, 0, 9999, 0, nStream);
    long kBitMap = genrand_key(DIST_UNIFORM, 1, MAXINT, 0, nStream);

    // set the bitmap based on threshold and NOT NULL definitions
    long result = 0;
    if (nThreshold < pTdef.nNullPct) {
      result = kBitMap;
      result &= ~pTdef.kNotNullBitMap;
    }

    return result;
  }

  // date.c

  /** Convert an ascii string to a date_t structure */
  static Date strtodt(String s) {
    return Date.valueOf(s);
  }

  /** Converts a date_t to a number of julian days
   *
   * <p>Algorithm: http://quasar.as.utexas.edu/BillInfo/JulianDatesG.html
   */
  static int dttoj(Date dt) {
    int y, m, res;

    y = dt.getYear();
    m = dt.getMonth();
    if (m <= 2)
    {
      m += 12;
      y -= 1;
    }

    // added 1 to get dttoj and jtodt to match
    res = dt.getDay() + (153 * m - 457) / 5 + 365 * y + (y / 4) - (y / 100) + (y / 400) + 1721118 + 1;

    return(res);
  }

  // corresponds to "typedef struct DECIMAL_T ... decimal_t"
  static class Decimal {
    int flags;
    int precision;
    int scale;
    long number;

    public static Decimal parse(String s) {
      final BigDecimal bigDecimal = new BigDecimal(s);
      final Decimal decimal = new Decimal();
      decimal.number = bigDecimal.longValue();
      decimal.scale = bigDecimal.scale();
      return decimal;
    }
  }

  // address.h

  // corresponds to "typedef struct DS_ADDR_T ds_addr_t"
  static class Address {
    char[]            suite_num = new char[RS_CC_SUITE_NUM + 1];
    int                     street_num;
    String street_name1;
    String street_name2;
    String street_type;
    String city;
    String county;
    String state;
    char[]            country = new char[RS_CC_COUNTRY + 1];
    int                     zip;
    int                     plus4;
    int                     gmt_offset;
  }

  public static final int DS_ADDR_SUITE_NUM = 0; 
  public static final int DS_ADDR_STREET_NUM = 1; 
  public static final int DS_ADDR_STREET_NAME1 = 2; 
  public static final int DS_ADDR_STREET_NAME2 = 3; 
  public static final int DS_ADDR_STREET_TYPE = 4; 
  public static final int DS_ADDR_CITY = 5; 
  public static final int DS_ADDR_COUNTY = 6; 
  public static final int DS_ADDR_STATE = 7; 
  public static final int DS_ADDR_COUNTRY = 8; 
  public static final int DS_ADDR_ZIP = 9; 
  public static final int DS_ADDR_PLUS4 = 10; 
  public static final int DS_ADDR_GMT_OFFSET = 11; 

  // constants.h

  /***
   *** Multi-table/Global Defines
   ***/
  public static final String DATA_START_DATE =          "1998-01-01";   /* earliest date in the data set */
  public static final String DATA_END_DATE =            "2003-12-31";   /* latest date in the data set */
  public static final int LINES_PER_ORDER =    16;              /* max number of lineitems per order for all channels */

  /***
   *** C_xxx Cutomer Defines
   ***/
  public static final int C_PREFERRED_PCT =     50;

  /***
   *** CC_xxx Call Center Defines
   ***/
  public static final int CC_EMPLOYEE_MAX =             7;                              /* rises ~ scale ^ 2 */


  /***
   *** CP_xxx Catalog Page Defines
   ***/
  public static final int CP_CATALOGS_PER_YEAR =        18;
  public static int CP_SK(int c, int s, int p) { return c * s + p; }

  /***
   *** CR_xxx Catalog Returns Defines
   ***/
  public static final int CR_RETURN_PCT =       10;     /* percentage of catalog sales that are returned */

  /***
   *** CS_xxx Customer Sales Defines
   ***/
  public static final String CS_QUANTITY_MAX =          "100";
  public static final String CS_MARKUP_MAX =            "2.00";
  public static final String CS_DISCOUNT_MAX =          "1.00";
  public static final String CS_WHOLESALE_MAX = "100.00";
  public static final String CS_COUPON_MAX =            "0.50";
  public static final int CS_MIN_SHIP_DELAY =   2;              /* minimum days from order to ship */
  public static final int CS_MAX_SHIP_DELAY =   90;             /* maximum days from order to ship */
  public static final int CS_ITEMS_PER_ORDER =  10;             /* number of items in each order */
  public static final int CS_GIFT_PCT =                 10;             /* ship-to != bill-to */

  /*
  * DATE SETTINGS
  *
  * The benchmarks sense of "today". Should this be a sliding scale/parameter?
  */
  public static final int CURRENT_YEAR =        2003;
  public static final int CURRENT_MONTH =       1;
  public static final int CURRENT_DAY =         8;
  public static final int CURRENT_QUARTER =     1;
  public static final int CURRENT_WEEK =        2;
  public static final String DATE_MINIMUM =     "1998-01-01";
  public static final String DATE_MAXIMUM =     "2002-12-31";
  public static final int YEAR_MINIMUM =        1998;
  public static final int YEAR_MAXIMUM =        2002;
  public static final String WAREHOUSE_LOAD_DATE =      "2001-07-18";
  public static final int UPDATE_INTERVAL =             30;     /* refresh interval in days */
  public static final String TODAYS_DATE =      "2003-01-08";

  /***
   *** INV_xxx Inventory Defines
   ***/
  public static final int INV_QUANTITY_MIN =    0;
  public static final int INV_QUANTITY_MAX =    1000;

  /***
   *** ITEM_xxx Item Defines
   ***/
  public static final int ITEM_DESC_LEN =               5;
  public static final int ITEM_NAME_LEN =               10;
  public static final int ITEM_MANFACTURER_COUNT = 1000;        /* number of brands handled by a particular manufacturer */

  /***
   *** PROMO_xxx Promotions Defines
   ***/
  public static final int PROMO_NAME_LEN =              5;
  public static final int PROMO_START_MIN =             -720;
  public static final int PROMO_START_MAX =             100;
  public static final int PROMO_START_MEAN =    0;
  public static final int PROMO_LEN_MIN =               1;
  public static final int PROMO_LEN_MAX =               60;
  public static final int PROMO_LEN_MEAN =              0;
  public static final int PROMO_DETAIL_LEN_MIN =                20;
  public static final int PROMO_DETAIL_LEN_MAX =                60;

  /***
   *** SR_xxx Store Returns Defines
   ***/
  public static final int SR_RETURN_PCT =       10;     /* percentage of store sales that are returned */

  /***
   *** SS_xxx Store Sales Defines
   ***/
  public static final int SS_MIN_SHIP_DELAY =   2;              /* minimum days from order to ship */
  public static final int SS_MAX_SHIP_DELAY =   90;             /* maximum days from order to ship */
  public static final String SS_QUANTITY_MAX =          "100";
  public static final String SS_MARKUP_MAX =            "1.00";
  public static final String SS_DISCOUNT_MAX =          "1.00";
  public static final String SS_WHOLESALE_MAX = "100.00";
  public static final String SS_COUPON_MAX =            "0.50";

  /***
   *** WP_xxx Web Page Defines
   ***/
  public static final int WP_AUTOGEN_PCT =      30;
  public static final int WP_LINK_MIN =         2;
  public static final int WP_LINK_MAX =         25;
  public static final int WP_IMAGE_MIN =        1;
  public static final int WP_IMAGE_MAX =        7;
  public static final int WP_AD_MIN =           0;
  public static final int WP_AD_MAX =           4;
  public static final int WP_MAX_REC_DURATION = 1000;   /* maximum time from start to end of record */
  public static final int WP_IDLE_TIME_MAX =    100;            /* maximum time since last page access */

  /***
   *** W_xxx Warehouse Defines
   ***/
  public static final int W_DESC_MIN =          5;
  public static final int W_SQFT_MIN =          50000;
  public static final int W_SQFT_MAX =          1000000;
  public static final int W_NAME_MIN =          10;

  /***
   *** WR_xxx Web Returns Defines
   ***/
  public static final int WR_RETURN_PCT =       10;     /* percentage of web sales that are returned */
  public static final int WR_SHIP_LAG_MIN =     2;      /* lag time between receiving and returning */
  public static final int WR_SHIP_LAG_MAX =     12;

  /***
   *** WEB_xxx Web Site Defines
   ***/
  public static final String WEB_START_DATE =                   DATE_MINIMUM;   /* range of open/close dates; actual dates can exceed these values */
  public static final String WEB_END_DATE =                     DATE_MAXIMUM;   /* due to staggered start of each site */
  public static final int WEB_DATE_STAGGER =            17;                             /* time between site creation on leading/trailing edge */
  public static final int WEB_PAGES_PER_SITE =          123;                            /* number of pages on a web site */
  /* some of the web sites are completely replaced in the date range. */
  public static final int WEB_MORTALITY =                       50;                             /* percentage of sites that "die" between start and end */
  public static boolean WEB_IS_REPLACED(int j) { return ((j % (100 / WEB_MORTALITY)) == 0); }   /* does this site get replaced? */
  public static int WEB_IS_REPLACEMENT(int j) { return ((j / (100 / WEB_MORTALITY)) % 2); }     /* is this the replacement? */

  /***
   *** SOURCE SCHEMA CONSTANTS
   ***/
  public static final int DAYS_PER_UPDATE =     3;

  /***
   *** RS_xxx: Row and column sizes
   ***/
/* sizes used in various tables */
  public static final int RS_BKEY =                             16;
/* table-specific sizes */

  public static final int RS_BRND_NAME =                50;
  public static final int RS_C_SALUTATION =             5;
  public static final int RS_C_FIRST_NAME =             20;
  public static final int RS_C_LAST_NAME =              30;
  public static final int RS_C_BIRTH_COUNTRY =  20;
  public static final int RS_C_LOGIN =                  13;
  public static final int RS_C_PASSWORD =               13;
  public static final int RS_C_EMAIL =                  50;
  public static final int RS_C_PRIMARY_MACHINE_ID =             15;
  public static final int RS_C_SECONDARY_MACHINE_ID =   15;
  public static final int RS_CA_SUITE_NUMBER =  10;
  public static final int RS_CA_STREET_NAME =   60;
  public static final int RS_CA_STREET_TYPE =   15;
  public static final int RS_CA_CITY =                  60;
  public static final int RS_CA_COUNTY =                30;
  public static final int RS_CA_STATE =                 2;
  public static final int RS_CA_COUNTRY =               20;
  public static final int RS_CA_ZIP =                   10;
  public static final int RS_CA_LOCATION_TYPE = 20;
  public static final int RS_CATG_DESC =                20;
  public static final int RS_CC_NAME =                  50;
  public static final int RS_CC_CLASS =                 50;
  public static final int RS_CC_HOURS =                 20;
  public static final int RS_CC_MANAGER =               40;
  public static final int RS_CC_MARKET_MANAGER =        40;
  public static final int RS_CC_MARKET_CLASS =  50;
  public static final int RS_CC_MARKET_DESC =   100;
  public static final int RS_CC_DIVISION_NAME = 50;
  public static final int RS_CC_COMPANY_NAME =  60;
  public static final int RS_CC_SUITE_NUM =             10;
  public static final int RS_CC_STREET_NAME =   60;
  public static final int RS_CC_STREET_TYPE =   15;
  public static final int RS_CC_CITY =                  60;
  public static final int RS_CC_COUNTY =                30;
  public static final int RS_CC_STATE =                 2;
  public static final int RS_CC_COUNTRY =               20;
  public static final int RS_CC_ZIP =                   10;
  public static final int RS_CD_GENDER =                1;
  public static final int RS_CD_MARITAL_STATUS =        1;
  public static final int RS_CD_EDUCATION_STATUS =      20;
  public static final int RS_CD_CREDIT_RATING = 10;
  public static final int RS_CP_DEPARTMENT =    20;
  public static final int RS_CLAS_DESC =                100;
  public static final int RS_CMPY_NAME =        50;
  public static final int RS_CP_DESCRIPTION =   100;
  public static final int RS_CP_TYPE =                  100;
  public static final int RS_CTGR_NAME =                25;
  public static final int RS_CTGR_DESC =                100;
  public static final int RS_CUST_CREDIT =              100;
  public static final int RS_D_DAY_NAME =               4;
  public static final int RS_D_QUARTER_NAME =   4;
  public static final int RS_DVSN_NAME =                50;
  public static final int RS_HD_BUY_POTENTIAL = 7;
  public static final int RS_I_ITEM_DESC =              200;
  public static final int RS_I_BRAND =                  50;
  public static final int RS_I_SUBCLASS =               50;
  public static final int RS_I_CLASS =                  50;
  public static final int RS_I_SUBCATEGORY =    50;
  public static final int RS_I_CATEGORY =               50;
  public static final int RS_I_MANUFACT =               50;
  public static final int RS_I_SIZE =                   20;
  public static final int RS_I_FORMULATION =    20;
  public static final int RS_I_FLAVOR =                 20;
  public static final int RS_I_UNITS =                  10;
  public static final int RS_I_CONTAINER =              10;
  public static final int RS_I_PRODUCT_NAME =   50;
  public static final int RS_MANF_NAME =                50;
  public static final int RS_MNGR_NAME =                50;
  public static final int RS_P_PROMO_NAME =             50;
  public static final int RS_P_CHANNEL_DETAILS =        100;
  public static final int RS_P_PURPOSE =                15;
  public static final int RS_PB_DESCRIPTION =   100;
  public static final int RS_PLIN_COMMENT =             100;
  public static final int RS_PROD_NAME =                100;
  public static final int RS_PROD_TYPE =                100;
  public static final int RS_R_REASON_DESCRIPTION =     100;
  public static final int RS_STORE_NAME =               50;
  public static final int RS_STORE_HOURS =                      20;
  public static final int RS_S_STORE_MANAGER =          40;
  public static final int RS_S_GEOGRAPHY_CLASS =        100;
  public static final int RS_S_MARKET_DESC =    100;
  public static final int RS_S_MARKET_MANAGER =         40;
  public static final int RS_S_DIVISION_NAME =  50;
  public static final int RS_S_COMPANY_NAME =   50;
  public static final int RS_S_SUITE_NUM =              10;
  public static final int RS_S_STREET_NAME =    60;
  public static final int RS_S_STREET_TYPE =    15;
  public static final int RS_S_CITY =                   60;
  public static final int RS_S_STATE =                  2;
  public static final int RS_S_COUNTY =                 30;
  public static final int RS_S_COUNTRY =                30;
  public static final int RS_S_ZIP =                    10;
  public static final int RS_SM_TYPE =                  30;
  public static final int RS_SM_CODE =                  10;
  public static final int RS_SM_CONTRACT =              20;
  public static final int RS_SM_CARRIER =               20;
  public static final int RS_SBCT_NAME =                100;
  public static final int RS_SBCT_DESC =                100;
  public static final int RS_SUBC_NAME =                100;
  public static final int RS_SUBC_DESC =                100;
  public static final int RS_T_AM_PM =                  2;
  public static final int RS_T_SHIFT =                  20;
  public static final int RS_T_SUB_SHIFT =              20;
  public static final int RS_T_MEAL_TIME =              20;
  public static final int RS_W_WAREHOUSE_NAME = 20;
  public static final int RS_W_STREET_NAME =    60;
  public static final int RS_W_SUITE_NUM =              10;
  public static final int RS_W_STREET_TYPE =    15;
  public static final int RS_W_CITY =                   60;
  public static final int RS_W_COUNTY =                 30;
  public static final int RS_W_STATE =                  2;
  public static final int RS_W_COUNTRY =                20;
  public static final int RS_W_ZIP =                    10;
  public static final int RS_WEB_MANAGER =                      50;
  public static final int RS_WEB_NAME =                 50;
  public static final int RS_WEB_CLASS =                50;
  public static final int RS_WEB_MARKET_CLASS = 50;
  public static final int RS_WEB_MARKET_DESC =          100;
  public static final int RS_WEB_MARKET_MANAGER =               40;
  public static final int RS_WEB_COMPANY_NAME = 100;
  public static final int RS_WEB_SUITE_NUMBER = 10;
  public static final int RS_WEB_STREET_NAME =  60;
  public static final int RS_WEB_STREET_TYPE =  15;
  public static final int RS_WEB_CITY =                 60;
  public static final int RS_WEB_COUNTY =               30;
  public static final int RS_WEB_STATE =                2;
  public static final int RS_WEB_COUNTRY =              20;
  public static final int RS_WEB_ZIP =                  10;
  public static final int RS_WP_URL =                   100;
  public static final int RS_WEB_TYPE =                 50;
  public static final int RS_WRHS_DESC =                100;
  public static final int RS_WORD_COMMENT =             100;
  public static final int RS_ZIPG_ZIP =                 5;

  // tables.h

  public static final int CALL_CENTER = 0;
  public static final int CATALOG_PAGE = 1;
  public static final int CATALOG_RETURNS = 2;
  public static final int CATALOG_SALES = 3;
  public static final int CUSTOMER = 4;
  public static final int CUSTOMER_ADDRESS = 5;
  public static final int CUSTOMER_DEMOGRAPHICS = 6;
  public static final int DATE = 7;
  public static final int HOUSEHOLD_DEMOGRAPHICS = 8;
  public static final int INCOME_BAND = 9;
  public static final int INVENTORY = 10;
  public static final int ITEM = 11;
  public static final int PROMOTION = 12;
  public static final int REASON = 13;
  public static final int SHIP_MODE = 14;
  public static final int STORE = 15;
  public static final int STORE_RETURNS = 16;
  public static final int STORE_SALES = 17;
  public static final int TIME = 18;
  public static final int WAREHOUSE = 19;
  public static final int WEB_PAGE = 20;
  public static final int WEB_RETURNS = 21;
  public static final int WEB_SALES = 22;
  public static final int WEB_SITE = 23;
  public static final int DBGEN_VERSION = 24;
  public static final int S_BRAND = 25;
  public static final int S_CUSTOMER_ADDRESS = 26;
  public static final int S_CALL_CENTER = 27;
  public static final int S_CATALOG = 28;
  public static final int S_CATALOG_ORDER = 29;
  public static final int S_CATALOG_ORDER_LINEITEM = 30;
  public static final int S_CATALOG_PAGE = 31;
  public static final int S_CATALOG_PROMOTIONAL_ITEM = 32;
  public static final int S_CATALOG_RETURNS = 33;
  public static final int S_CATEGORY = 34;
  public static final int S_CLASS = 35;
  public static final int S_COMPANY = 36;
  public static final int S_CUSTOMER = 37;
  public static final int S_DIVISION = 38;
  public static final int S_INVENTORY = 39;
  public static final int S_ITEM = 40;
  public static final int S_MANAGER = 41;
  public static final int S_MANUFACTURER = 42;
  public static final int S_MARKET = 43;
  public static final int S_PRODUCT = 44;
  public static final int S_PROMOTION = 45;
  public static final int S_PURCHASE = 46;
  public static final int S_PURCHASE_LINEITEM = 47;
  public static final int S_REASON = 48;
  public static final int S_STORE = 49;
  public static final int S_STORE_PROMOTIONAL_ITEM = 50;
  public static final int S_STORE_RETURNS = 51;
  public static final int S_SUBCATEGORY = 52;
  public static final int S_SUBCLASS = 53;
  public static final int S_WAREHOUSE = 54;
  public static final int S_WEB_ORDER = 55;
  public static final int S_WEB_ORDER_LINEITEM = 56;
  public static final int S_WEB_PAGE = 57;
  public static final int S_WEB_PROMOTIONAL_ITEM = 58;
  public static final int S_WEB_RETURNS = 59;
  public static final int S_WEB_SITE = 60;
  public static final int S_ZIPG = 61;
  public static final int PSEUDO_TABLE_START = 62;

  // PSEUDO TABLES from here on; used in hierarchies
  public static final int ITEM_BRAND = 62;
  public static final int ITEM_CLASS = 63;
  public static final int ITEM_CATEGORY = 64;
  public static final int DIVISIONS = 65;
  public static final int COMPANY = 66;
  public static final int CONCURRENT_WEB_SITES = 67;
  public static final int ACTIVE_CITIES = 68;
  public static final int ACTIVE_COUNTIES = 69;
  public static final int ACTIVE_STATES = 70;
  public static final int MAX_TABLE = 70;

  // columns.h
  public static final int CALL_CENTER_START = 1; 
  public static final int CC_CALL_CENTER_SK = 1; 
  public static final int CC_CALL_CENTER_ID = 2; 
  public static final int CC_REC_START_DATE_ID = 3; 
  public static final int CC_REC_END_DATE_ID = 4; 
  public static final int CC_CLOSED_DATE_ID = 5; 
  public static final int CC_OPEN_DATE_ID = 6; 
  public static final int CC_NAME = 7; 
  public static final int CC_CLASS = 8; 
  public static final int CC_EMPLOYEES = 9; 
  public static final int CC_SQ_FT = 10; 
  public static final int CC_HOURS = 11; 
  public static final int CC_MANAGER = 12; 
  public static final int CC_MARKET_ID = 13; 
  public static final int CC_MARKET_CLASS = 14; 
  public static final int CC_MARKET_DESC = 15; 
  public static final int CC_MARKET_MANAGER = 16; 
  public static final int CC_DIVISION = 17; 
  public static final int CC_DIVISION_NAME = 18; 
  public static final int CC_COMPANY = 19; 
  public static final int CC_COMPANY_NAME = 20; 
  public static final int CC_STREET_NUMBER = 21; 
  public static final int CC_STREET_NAME = 22; 
  public static final int CC_STREET_TYPE = 23; 
  public static final int CC_SUITE_NUMBER = 24; 
  public static final int CC_CITY = 25; 
  public static final int CC_COUNTY = 26; 
  public static final int CC_STATE = 27; 
  public static final int CC_ZIP = 28; 
  public static final int CC_COUNTRY = 29; 
  public static final int CC_GMT_OFFSET = 30; 
  public static final int CC_ADDRESS = 31; 
  public static final int CC_TAX_PERCENTAGE = 32; 
  public static final int CC_SCD = 33; 
  public static final int CC_NULLS = 34; 
  public static final int CALL_CENTER_END = 34; 
  public static final int CATALOG_PAGE_START = 35; 
  public static final int CP_CATALOG_PAGE_SK = 35; 
  public static final int CP_CATALOG_PAGE_ID = 36; 
  public static final int CP_START_DATE_ID = 37; 
  public static final int CP_END_DATE_ID = 38; 
  public static final int CP_PROMO_ID = 39; 
  public static final int CP_DEPARTMENT = 40; 
  public static final int CP_CATALOG_NUMBER = 41; 
  public static final int CP_CATALOG_PAGE_NUMBER = 42; 
  public static final int CP_DESCRIPTION = 43; 
  public static final int CP_TYPE = 44; 
  public static final int CP_NULLS = 45; 
  public static final int CATALOG_PAGE_END = 45; 
  public static final int CATALOG_RETURNS_START = 46; 
  public static final int CR_RETURNED_DATE_SK = 46; 
  public static final int CR_RETURNED_TIME_SK = 47; 
  public static final int CR_ITEM_SK = 48; 
  public static final int CR_REFUNDED_CUSTOMER_SK = 49; 
  public static final int CR_REFUNDED_CDEMO_SK = 50; 
  public static final int CR_REFUNDED_HDEMO_SK = 51; 
  public static final int CR_REFUNDED_ADDR_SK = 52; 
  public static final int CR_RETURNING_CUSTOMER_SK = 53; 
  public static final int CR_RETURNING_CDEMO_SK = 54; 
  public static final int CR_RETURNING_HDEMO_SK = 55; 
  public static final int CR_RETURNING_ADDR_SK = 56; 
  public static final int CR_CALL_CENTER_SK = 57; 
  public static final int CR_CATALOG_PAGE_SK = 58; 
  public static final int CR_SHIP_MODE_SK = 59; 
  public static final int CR_WAREHOUSE_SK = 60; 
  public static final int CR_REASON_SK = 61; 
  public static final int CR_ORDER_NUMBER = 62; 
  public static final int CR_PRICING_QUANTITY = 63; 
  public static final int CR_PRICING_NET_PAID = 64; 
  public static final int CR_PRICING_EXT_TAX = 65; 
  public static final int CR_PRICING_NET_PAID_INC_TAX = 66; 
  public static final int CR_PRICING_FEE = 67; 
  public static final int CR_PRICING_EXT_SHIP_COST = 68; 
  public static final int CR_PRICING_REFUNDED_CASH = 69; 
  public static final int CR_PRICING_REVERSED_CHARGE = 70; 
  public static final int CR_PRICING_STORE_CREDIT = 71; 
  public static final int CR_PRICING_NET_LOSS = 72; 
  public static final int CR_NULLS = 73; 
  public static final int CR_PRICING = 74; 
  public static final int CATALOG_RETURNS_END = 74; 
  public static final int CATALOG_SALES_START = 75; 
  public static final int CS_SOLD_DATE_SK = 75; 
  public static final int CS_SOLD_TIME_SK = 76; 
  public static final int CS_SHIP_DATE_SK = 77; 
  public static final int CS_BILL_CUSTOMER_SK = 78; 
  public static final int CS_BILL_CDEMO_SK = 79; 
  public static final int CS_BILL_HDEMO_SK = 80; 
  public static final int CS_BILL_ADDR_SK = 81; 
  public static final int CS_SHIP_CUSTOMER_SK = 82; 
  public static final int CS_SHIP_CDEMO_SK = 83; 
  public static final int CS_SHIP_HDEMO_SK = 84; 
  public static final int CS_SHIP_ADDR_SK = 85; 
  public static final int CS_CALL_CENTER_SK = 86; 
  public static final int CS_CATALOG_PAGE_SK = 87; 
  public static final int CS_SHIP_MODE_SK = 88; 
  public static final int CS_WAREHOUSE_SK = 89; 
  public static final int CS_SOLD_ITEM_SK = 90; 
  public static final int CS_PROMO_SK = 91; 
  public static final int CS_ORDER_NUMBER = 92; 
  public static final int CS_PRICING_QUANTITY = 93; 
  public static final int CS_PRICING_WHOLESALE_COST = 94; 
  public static final int CS_PRICING_LIST_PRICE = 95; 
  public static final int CS_PRICING_SALES_PRICE = 96; 
  public static final int CS_PRICING_COUPON_AMT = 97; 
  public static final int CS_PRICING_EXT_SALES_PRICE = 98; 
  public static final int CS_PRICING_EXT_DISCOUNT_AMOUNT = 99; 
  public static final int CS_PRICING_EXT_WHOLESALE_COST = 100; 
  public static final int CS_PRICING_EXT_LIST_PRICE = 101; 
  public static final int CS_PRICING_EXT_TAX = 102; 
  public static final int CS_PRICING_EXT_SHIP_COST = 103; 
  public static final int CS_PRICING_NET_PAID = 104; 
  public static final int CS_PRICING_NET_PAID_INC_TAX = 105; 
  public static final int CS_PRICING_NET_PAID_INC_SHIP = 106; 
  public static final int CS_PRICING_NET_PAID_INC_SHIP_TAX = 107; 
  public static final int CS_PRICING_NET_PROFIT = 108; 
  public static final int CS_PRICING = 109; 
  public static final int CS_PERMUTE = 110; 
  public static final int CS_NULLS = 111; 
  public static final int CR_IS_RETURNED = 112; 
  public static final int CS_PERMUTATION = 113; 
  public static final int CATALOG_SALES_END = 113; 
  public static final int CUSTOMER_START = 114; 
  public static final int C_CUSTOMER_SK = 114; 
  public static final int C_CUSTOMER_ID = 115; 
  public static final int C_CURRENT_CDEMO_SK = 116; 
  public static final int C_CURRENT_HDEMO_SK = 117; 
  public static final int C_CURRENT_ADDR_SK = 118; 
  public static final int C_FIRST_SHIPTO_DATE_ID = 119; 
  public static final int C_FIRST_SALES_DATE_ID = 120; 
  public static final int C_SALUTATION = 121; 
  public static final int C_FIRST_NAME = 122; 
  public static final int C_LAST_NAME = 123; 
  public static final int C_PREFERRED_CUST_FLAG = 124; 
  public static final int C_BIRTH_DAY = 125; 
  public static final int C_BIRTH_MONTH = 126; 
  public static final int C_BIRTH_YEAR = 127; 
  public static final int C_BIRTH_COUNTRY = 128; 
  public static final int C_LOGIN = 129; 
  public static final int C_EMAIL_ADDRESS = 130; 
  public static final int C_LAST_REVIEW_DATE = 131; 
  public static final int C_NULLS = 132; 
  public static final int CUSTOMER_END = 132; 
  public static final int CUSTOMER_ADDRESS_START = 133; 
  public static final int CA_ADDRESS_SK = 133; 
  public static final int CA_ADDRESS_ID = 134; 
  public static final int CA_ADDRESS_STREET_NUM = 135; 
  public static final int CA_ADDRESS_STREET_NAME1 = 136; 
  public static final int CA_ADDRESS_STREET_TYPE = 137; 
  public static final int CA_ADDRESS_SUITE_NUM = 138; 
  public static final int CA_ADDRESS_CITY = 139; 
  public static final int CA_ADDRESS_COUNTY = 140; 
  public static final int CA_ADDRESS_STATE = 141; 
  public static final int CA_ADDRESS_ZIP = 142; 
  public static final int CA_ADDRESS_COUNTRY = 143; 
  public static final int CA_ADDRESS_GMT_OFFSET = 144; 
  public static final int CA_LOCATION_TYPE = 145; 
  public static final int CA_NULLS = 146; 
  public static final int CA_ADDRESS = 147; 
  public static final int CA_ADDRESS_STREET_NAME2 = 148; 
  public static final int CUSTOMER_ADDRESS_END = 148; 
  public static final int CUSTOMER_DEMOGRAPHICS_START = 149; 
  public static final int CD_DEMO_SK = 149; 
  public static final int CD_GENDER = 150; 
  public static final int CD_MARITAL_STATUS = 151; 
  public static final int CD_EDUCATION_STATUS = 152; 
  public static final int CD_PURCHASE_ESTIMATE = 153; 
  public static final int CD_CREDIT_RATING = 154; 
  public static final int CD_DEP_COUNT = 155; 
  public static final int CD_DEP_EMPLOYED_COUNT = 156; 
  public static final int CD_DEP_COLLEGE_COUNT = 157; 
  public static final int CD_NULLS = 158; 
  public static final int CUSTOMER_DEMOGRAPHICS_END = 158; 
  public static final int DATE_START = 159; 
  public static final int D_DATE_SK = 159; 
  public static final int D_DATE_ID = 160; 
  public static final int D_DATE = 161; 
  public static final int D_MONTH_SEQ = 162; 
  public static final int D_WEEK_SEQ = 163; 
  public static final int D_QUARTER_SEQ = 164; 
  public static final int D_YEAR = 165; 
  public static final int D_DOW = 166; 
  public static final int D_MOY = 167; 
  public static final int D_DOM = 168; 
  public static final int D_QOY = 169; 
  public static final int D_FY_YEAR = 170; 
  public static final int D_FY_QUARTER_SEQ = 171; 
  public static final int D_FY_WEEK_SEQ = 172; 
  public static final int D_DAY_NAME = 173; 
  public static final int D_QUARTER_NAME = 174; 
  public static final int D_HOLIDAY = 175; 
  public static final int D_WEEKEND = 176; 
  public static final int D_FOLLOWING_HOLIDAY = 177; 
  public static final int D_FIRST_DOM = 178; 
  public static final int D_LAST_DOM = 179; 
  public static final int D_SAME_DAY_LY = 180; 
  public static final int D_SAME_DAY_LQ = 181; 
  public static final int D_CURRENT_DAY = 182; 
  public static final int D_CURRENT_WEEK = 183; 
  public static final int D_CURRENT_MONTH = 184; 
  public static final int D_CURRENT_QUARTER = 185; 
  public static final int D_CURRENT_YEAR = 186; 
  public static final int D_NULLS = 187; 
  public static final int DATE_END = 187; 
  public static final int HOUSEHOLD_DEMOGRAPHICS_START = 188; 
  public static final int HD_DEMO_SK = 188; 
  public static final int HD_INCOME_BAND_ID = 189; 
  public static final int HD_BUY_POTENTIAL = 190; 
  public static final int HD_DEP_COUNT = 191; 
  public static final int HD_VEHICLE_COUNT = 192; 
  public static final int HD_NULLS = 193; 
  public static final int HOUSEHOLD_DEMOGRAPHICS_END = 193; 
  public static final int INCOME_BAND_START = 194; 
  public static final int IB_INCOME_BAND_ID = 194; 
  public static final int IB_LOWER_BOUND = 195; 
  public static final int IB_UPPER_BOUND = 196; 
  public static final int IB_NULLS = 197; 
  public static final int INCOME_BAND_END = 197; 
  public static final int INVENTORY_START = 198; 
  public static final int INV_DATE_SK = 198; 
  public static final int INV_ITEM_SK = 199; 
  public static final int INV_WAREHOUSE_SK = 200; 
  public static final int INV_QUANTITY_ON_HAND = 201; 
  public static final int INV_NULLS = 202; 
  public static final int INVENTORY_END = 202; 
  public static final int ITEM_START = 203; 
  public static final int I_ITEM_SK = 203; 
  public static final int I_ITEM_ID = 204; 
  public static final int I_REC_START_DATE_ID = 205; 
  public static final int I_REC_END_DATE_ID = 206; 
  public static final int I_ITEM_DESC = 207; 
  public static final int I_CURRENT_PRICE = 208; 
  public static final int I_WHOLESALE_COST = 209; 
  public static final int I_BRAND_ID = 210; 
  public static final int I_BRAND = 211; 
  public static final int I_CLASS_ID = 212; 
  public static final int I_CLASS = 213; 
  public static final int I_CATEGORY_ID = 214; 
  public static final int I_CATEGORY = 215; 
  public static final int I_MANUFACT_ID = 216; 
  public static final int I_MANUFACT = 217; 
  public static final int I_SIZE = 218; 
  public static final int I_FORMULATION = 219; 
  public static final int I_COLOR = 220; 
  public static final int I_UNITS = 221; 
  public static final int I_CONTAINER = 222; 
  public static final int I_MANAGER_ID = 223; 
  public static final int I_PRODUCT_NAME = 224; 
  public static final int I_NULLS = 225; 
  public static final int I_SCD = 226; 
  public static final int I_PROMO_SK = 227; 
  public static final int ITEM_END = 227; 
  public static final int PROMOTION_START = 228; 
  public static final int P_PROMO_SK = 228; 
  public static final int P_PROMO_ID = 229; 
  public static final int P_START_DATE_ID = 230; 
  public static final int P_END_DATE_ID = 231; 
  public static final int P_ITEM_SK = 232; 
  public static final int P_COST = 233; 
  public static final int P_RESPONSE_TARGET = 234; 
  public static final int P_PROMO_NAME = 235; 
  public static final int P_CHANNEL_DMAIL = 236; 
  public static final int P_CHANNEL_EMAIL = 237; 
  public static final int P_CHANNEL_CATALOG = 238; 
  public static final int P_CHANNEL_TV = 239; 
  public static final int P_CHANNEL_RADIO = 240; 
  public static final int P_CHANNEL_PRESS = 241; 
  public static final int P_CHANNEL_EVENT = 242; 
  public static final int P_CHANNEL_DEMO = 243; 
  public static final int P_CHANNEL_DETAILS = 244; 
  public static final int P_PURPOSE = 245; 
  public static final int P_DISCOUNT_ACTIVE = 246; 
  public static final int P_NULLS = 247; 
  public static final int PROMOTION_END = 247; 
  public static final int REASON_START = 248; 
  public static final int R_REASON_SK = 248; 
  public static final int R_REASON_ID = 249; 
  public static final int R_REASON_DESCRIPTION = 250; 
  public static final int R_NULLS = 251; 
  public static final int REASON_END = 251; 
  public static final int SHIP_MODE_START = 252; 
  public static final int SM_SHIP_MODE_SK = 252; 
  public static final int SM_SHIP_MODE_ID = 253; 
  public static final int SM_TYPE = 254; 
  public static final int SM_CODE = 255; 
  public static final int SM_CONTRACT = 256; 
  public static final int SM_CARRIER = 257; 
  public static final int SM_NULLS = 258; 
  public static final int SHIP_MODE_END = 258; 
  public static final int STORE_START = 259; 
  public static final int W_STORE_SK = 259; 
  public static final int W_STORE_ID = 260; 
  public static final int W_STORE_REC_START_DATE_ID = 261; 
  public static final int W_STORE_REC_END_DATE_ID = 262; 
  public static final int W_STORE_CLOSED_DATE_ID = 263; 
  public static final int W_STORE_NAME = 264; 
  public static final int W_STORE_EMPLOYEES = 265; 
  public static final int W_STORE_FLOOR_SPACE = 266; 
  public static final int W_STORE_HOURS = 267; 
  public static final int W_STORE_MANAGER = 268; 
  public static final int W_STORE_MARKET_ID = 269; 
  public static final int W_STORE_TAX_PERCENTAGE = 270; 
  public static final int W_STORE_GEOGRAPHY_CLASS = 271; 
  public static final int W_STORE_MARKET_DESC = 272; 
  public static final int W_STORE_MARKET_MANAGER = 273; 
  public static final int W_STORE_DIVISION_ID = 274; 
  public static final int W_STORE_DIVISION_NAME = 275; 
  public static final int W_STORE_COMPANY_ID = 276; 
  public static final int W_STORE_COMPANY_NAME = 277; 
  public static final int W_STORE_ADDRESS_STREET_NUM = 278; 
  public static final int W_STORE_ADDRESS_STREET_NAME1 = 279; 
  public static final int W_STORE_ADDRESS_STREET_TYPE = 280; 
  public static final int W_STORE_ADDRESS_SUITE_NUM = 281; 
  public static final int W_STORE_ADDRESS_CITY = 282; 
  public static final int W_STORE_ADDRESS_COUNTY = 283; 
  public static final int W_STORE_ADDRESS_STATE = 284; 
  public static final int W_STORE_ADDRESS_ZIP = 285; 
  public static final int W_STORE_ADDRESS_COUNTRY = 286; 
  public static final int W_STORE_ADDRESS_GMT_OFFSET = 287; 
  public static final int W_STORE_NULLS = 288; 
  public static final int W_STORE_TYPE = 289; 
  public static final int W_STORE_SCD = 290; 
  public static final int W_STORE_ADDRESS = 291; 
  public static final int STORE_END = 291; 
  public static final int STORE_RETURNS_START = 292; 
  public static final int SR_RETURNED_DATE_SK = 292; 
  public static final int SR_RETURNED_TIME_SK = 293; 
  public static final int SR_ITEM_SK = 294; 
  public static final int SR_CUSTOMER_SK = 295; 
  public static final int SR_CDEMO_SK = 296; 
  public static final int SR_HDEMO_SK = 297; 
  public static final int SR_ADDR_SK = 298; 
  public static final int SR_STORE_SK = 299; 
  public static final int SR_REASON_SK = 300; 
  public static final int SR_TICKET_NUMBER = 301; 
  public static final int SR_PRICING_QUANTITY = 302; 
  public static final int SR_PRICING_NET_PAID = 303; 
  public static final int SR_PRICING_EXT_TAX = 304; 
  public static final int SR_PRICING_NET_PAID_INC_TAX = 305; 
  public static final int SR_PRICING_FEE = 306; 
  public static final int SR_PRICING_EXT_SHIP_COST = 307; 
  public static final int SR_PRICING_REFUNDED_CASH = 308; 
  public static final int SR_PRICING_REVERSED_CHARGE = 309; 
  public static final int SR_PRICING_STORE_CREDIT = 310; 
  public static final int SR_PRICING_NET_LOSS = 311; 
  public static final int SR_PRICING = 312; 
  public static final int SR_NULLS = 313; 
  public static final int STORE_RETURNS_END = 313; 
  public static final int STORE_SALES_START = 314; 
  public static final int SS_SOLD_DATE_SK = 314; 
  public static final int SS_SOLD_TIME_SK = 315; 
  public static final int SS_SOLD_ITEM_SK = 316; 
  public static final int SS_SOLD_CUSTOMER_SK = 317; 
  public static final int SS_SOLD_CDEMO_SK = 318; 
  public static final int SS_SOLD_HDEMO_SK = 319; 
  public static final int SS_SOLD_ADDR_SK = 320; 
  public static final int SS_SOLD_STORE_SK = 321; 
  public static final int SS_SOLD_PROMO_SK = 322; 
  public static final int SS_TICKET_NUMBER = 323; 
  public static final int SS_PRICING_QUANTITY = 324; 
  public static final int SS_PRICING_WHOLESALE_COST = 325; 
  public static final int SS_PRICING_LIST_PRICE = 326; 
  public static final int SS_PRICING_SALES_PRICE = 327; 
  public static final int SS_PRICING_COUPON_AMT = 328; 
  public static final int SS_PRICING_EXT_SALES_PRICE = 329; 
  public static final int SS_PRICING_EXT_WHOLESALE_COST = 330; 
  public static final int SS_PRICING_EXT_LIST_PRICE = 331; 
  public static final int SS_PRICING_EXT_TAX = 332; 
  public static final int SS_PRICING_NET_PAID = 333; 
  public static final int SS_PRICING_NET_PAID_INC_TAX = 334; 
  public static final int SS_PRICING_NET_PROFIT = 335; 
  public static final int SR_IS_RETURNED = 336; 
  public static final int SS_PRICING = 337; 
  public static final int SS_NULLS = 338; 
  public static final int SS_PERMUTATION = 339; 
  public static final int STORE_SALES_END = 339; 
  public static final int TIME_START = 340; 
  public static final int T_TIME_SK = 340; 
  public static final int T_TIME_ID = 341; 
  public static final int T_TIME = 342; 
  public static final int T_HOUR = 343; 
  public static final int T_MINUTE = 344; 
  public static final int T_SECOND = 345; 
  public static final int T_AM_PM = 346; 
  public static final int T_SHIFT = 347; 
  public static final int T_SUB_SHIFT = 348; 
  public static final int T_MEAL_TIME = 349; 
  public static final int T_NULLS = 350; 
  public static final int TIME_END = 350; 
  public static final int WAREHOUSE_START = 351; 
  public static final int W_WAREHOUSE_SK = 351; 
  public static final int W_WAREHOUSE_ID = 352; 
  public static final int W_WAREHOUSE_NAME = 353; 
  public static final int W_WAREHOUSE_SQ_FT = 354; 
  public static final int W_ADDRESS_STREET_NUM = 355; 
  public static final int W_ADDRESS_STREET_NAME1 = 356; 
  public static final int W_ADDRESS_STREET_TYPE = 357; 
  public static final int W_ADDRESS_SUITE_NUM = 358; 
  public static final int W_ADDRESS_CITY = 359; 
  public static final int W_ADDRESS_COUNTY = 360; 
  public static final int W_ADDRESS_STATE = 361; 
  public static final int W_ADDRESS_ZIP = 362; 
  public static final int W_ADDRESS_COUNTRY = 363; 
  public static final int W_ADDRESS_GMT_OFFSET = 364; 
  public static final int W_NULLS = 365; 
  public static final int W_WAREHOUSE_ADDRESS = 366; 
  public static final int WAREHOUSE_END = 366; 
  public static final int WEB_PAGE_START = 367; 
  public static final int WP_PAGE_SK = 367; 
  public static final int WP_PAGE_ID = 368; 
  public static final int WP_REC_START_DATE_ID = 369; 
  public static final int WP_REC_END_DATE_ID = 370; 
  public static final int WP_CREATION_DATE_SK = 371; 
  public static final int WP_ACCESS_DATE_SK = 372; 
  public static final int WP_AUTOGEN_FLAG = 373; 
  public static final int WP_CUSTOMER_SK = 374; 
  public static final int WP_URL = 375; 
  public static final int WP_TYPE = 376; 
  public static final int WP_CHAR_COUNT = 377; 
  public static final int WP_LINK_COUNT = 378; 
  public static final int WP_IMAGE_COUNT = 379; 
  public static final int WP_MAX_AD_COUNT = 380; 
  public static final int WP_NULLS = 381; 
  public static final int WP_SCD = 382; 
  public static final int WEB_PAGE_END = 382; 
  public static final int WEB_RETURNS_START = 383; 
  public static final int WR_RETURNED_DATE_SK = 383; 
  public static final int WR_RETURNED_TIME_SK = 384; 
  public static final int WR_ITEM_SK = 385; 
  public static final int WR_REFUNDED_CUSTOMER_SK = 386; 
  public static final int WR_REFUNDED_CDEMO_SK = 387; 
  public static final int WR_REFUNDED_HDEMO_SK = 388; 
  public static final int WR_REFUNDED_ADDR_SK = 389; 
  public static final int WR_RETURNING_CUSTOMER_SK = 390; 
  public static final int WR_RETURNING_CDEMO_SK = 391; 
  public static final int WR_RETURNING_HDEMO_SK = 392; 
  public static final int WR_RETURNING_ADDR_SK = 393; 
  public static final int WR_WEB_PAGE_SK = 394; 
  public static final int WR_REASON_SK = 395; 
  public static final int WR_ORDER_NUMBER = 396; 
  public static final int WR_PRICING_QUANTITY = 397; 
  public static final int WR_PRICING_NET_PAID = 398; 
  public static final int WR_PRICING_EXT_TAX = 399; 
  public static final int WR_PRICING_NET_PAID_INC_TAX = 400; 
  public static final int WR_PRICING_FEE = 401; 
  public static final int WR_PRICING_EXT_SHIP_COST = 402; 
  public static final int WR_PRICING_REFUNDED_CASH = 403; 
  public static final int WR_PRICING_REVERSED_CHARGE = 404; 
  public static final int WR_PRICING_STORE_CREDIT = 405; 
  public static final int WR_PRICING_NET_LOSS = 406; 
  public static final int WR_PRICING = 407; 
  public static final int WR_NULLS = 408; 
  public static final int WEB_RETURNS_END = 408; 
  public static final int WEB_SALES_START = 409; 
  public static final int WS_SOLD_DATE_SK = 409; 
  public static final int WS_SOLD_TIME_SK = 410; 
  public static final int WS_SHIP_DATE_SK = 411; 
  public static final int WS_ITEM_SK = 412; 
  public static final int WS_BILL_CUSTOMER_SK = 413; 
  public static final int WS_BILL_CDEMO_SK = 414; 
  public static final int WS_BILL_HDEMO_SK = 415; 
  public static final int WS_BILL_ADDR_SK = 416; 
  public static final int WS_SHIP_CUSTOMER_SK = 417; 
  public static final int WS_SHIP_CDEMO_SK = 418; 
  public static final int WS_SHIP_HDEMO_SK = 419; 
  public static final int WS_SHIP_ADDR_SK = 420; 
  public static final int WS_WEB_PAGE_SK = 421; 
  public static final int WS_WEB_SITE_SK = 422; 
  public static final int WS_SHIP_MODE_SK = 423; 
  public static final int WS_WAREHOUSE_SK = 424; 
  public static final int WS_PROMO_SK = 425; 
  public static final int WS_ORDER_NUMBER = 426; 
  public static final int WS_PRICING_QUANTITY = 427; 
  public static final int WS_PRICING_WHOLESALE_COST = 428; 
  public static final int WS_PRICING_LIST_PRICE = 429; 
  public static final int WS_PRICING_SALES_PRICE = 430; 
  public static final int WS_PRICING_EXT_DISCOUNT_AMT = 431; 
  public static final int WS_PRICING_EXT_SALES_PRICE = 432; 
  public static final int WS_PRICING_EXT_WHOLESALE_COST = 433; 
  public static final int WS_PRICING_EXT_LIST_PRICE = 434; 
  public static final int WS_PRICING_EXT_TAX = 435; 
  public static final int WS_PRICING_COUPON_AMT = 436; 
  public static final int WS_PRICING_EXT_SHIP_COST = 437; 
  public static final int WS_PRICING_NET_PAID = 438; 
  public static final int WS_PRICING_NET_PAID_INC_TAX = 439; 
  public static final int WS_PRICING_NET_PAID_INC_SHIP = 440; 
  public static final int WS_PRICING_NET_PAID_INC_SHIP_TAX = 441; 
  public static final int WS_PRICING_NET_PROFIT = 442; 
  public static final int WS_PRICING = 443; 
  public static final int WS_NULLS = 444; 
  public static final int WR_IS_RETURNED = 445; 
  public static final int WS_PERMUTATION = 446; 
  public static final int WEB_SALES_END = 446; 
  public static final int WEB_SITE_START = 447; 
  public static final int WEB_SITE_SK = 447; 
  public static final int WEB_SITE_ID = 448; 
  public static final int WEB_REC_START_DATE_ID = 449; 
  public static final int WEB_REC_END_DATE_ID = 450; 
  public static final int WEB_NAME = 451; 
  public static final int WEB_OPEN_DATE = 452; 
  public static final int WEB_CLOSE_DATE = 453; 
  public static final int WEB_CLASS = 454; 
  public static final int WEB_MANAGER = 455; 
  public static final int WEB_MARKET_ID = 456; 
  public static final int WEB_MARKET_CLASS = 457; 
  public static final int WEB_MARKET_DESC = 458; 
  public static final int WEB_MARKET_MANAGER = 459; 
  public static final int WEB_COMPANY_ID = 460; 
  public static final int WEB_COMPANY_NAME = 461; 
  public static final int WEB_ADDRESS_STREET_NUM = 462; 
  public static final int WEB_ADDRESS_STREET_NAME1 = 463; 
  public static final int WEB_ADDRESS_STREET_TYPE = 464; 
  public static final int WEB_ADDRESS_SUITE_NUM = 465; 
  public static final int WEB_ADDRESS_CITY = 466; 
  public static final int WEB_ADDRESS_COUNTY = 467; 
  public static final int WEB_ADDRESS_STATE = 468; 
  public static final int WEB_ADDRESS_ZIP = 469; 
  public static final int WEB_ADDRESS_COUNTRY = 470; 
  public static final int WEB_ADDRESS_GMT_OFFSET = 471; 
  public static final int WEB_TAX_PERCENTAGE = 472; 
  public static final int WEB_NULLS = 473; 
  public static final int WEB_ADDRESS = 474; 
  public static final int WEB_SCD = 475; 
  public static final int WEB_SITE_END = 475; 
  public static final int DBGEN_VERSION_START = 476; 
  public static final int DV_VERSION = 476; 
  public static final int DV_CREATE_DATE = 477; 
  public static final int DV_CREATE_TIME = 478; 
  public static final int DV_CMDLINE_ARGS = 479; 
  public static final int VALIDATE_STREAM = 480; 
  public static final int DBGEN_VERSION_END = 480; 
  public static final int S_BRAND_START = 481; 
  public static final int S_BRAND_ID = 481; 
  public static final int S_BRAND_SUBCLASS_ID = 482; 
  public static final int S_BRAND_MANAGER_ID = 483; 
  public static final int S_BRAND_MANUFACTURER_ID = 484; 
  public static final int S_BRAND_NAME = 485; 
  public static final int S_BRAND_END = 485; 
  public static final int S_CUSTOMER_ADDRESS_START = 486; 
  public static final int S_CADR_ID = 486; 
  public static final int S_CADR_ADDRESS_STREET_NUMBER = 487; 
  public static final int S_CADR_ADDRESS_STREET_NAME1 = 488; 
  public static final int S_CADR_ADDRESS_STREET_NAME2 = 489; 
  public static final int S_CADR_ADDRESS_STREET_TYPE = 490; 
  public static final int S_CADR_ADDRESS_SUITE_NUM = 491; 
  public static final int S_CADR_ADDRESS_CITY = 492; 
  public static final int S_CADR_ADDRESS_COUNTY = 493; 
  public static final int S_CADR_ADDRESS_STATE = 494; 
  public static final int S_CADR_ADDRESS_ZIP = 495; 
  public static final int S_CADR_ADDRESS_COUNTRY = 496; 
  public static final int S_BADDR_ADDRESS = 497; 
  public static final int S_CUSTOMER_ADDRESS_END = 497; 
  public static final int S_CALL_CENTER_START = 498; 
  public static final int S_CALL_CENTER_ID = 498; 
  public static final int S_CALL_CENTER_DIVISION_ID = 499; 
  public static final int S_CALL_CENTER_OPEN_DATE = 500; 
  public static final int S_CALL_CENTER_CLOSED_DATE = 501; 
  public static final int S_CALL_CENTER_NAME = 502; 
  public static final int S_CALL_CENTER_CLASS = 503; 
  public static final int S_CALL_CENTER_EMPLOYEES = 504; 
  public static final int S_CALL_CENTER_SQFT = 505; 
  public static final int S_CALL_CENTER_HOURS = 506; 
  public static final int S_CALL_CENTER_MANAGER_ID = 507; 
  public static final int S_CALL_CENTER_MARKET_ID = 508; 
  public static final int S_CALL_CENTER_ADDRESS_ID = 509; 
  public static final int S_CALL_CENTER_TAX_PERCENTAGE = 510; 
  public static final int S_CALL_CENTER_SCD = 511; 
  public static final int S_CALL_CENTER_END = 511; 
  public static final int S_CATALOG_START = 512; 
  public static final int S_CATALOG_NUMBER = 512; 
  public static final int S_CATALOG_START_DATE = 513; 
  public static final int S_CATALOG_END_DATE = 514; 
  public static final int S_CATALOG_DESC = 515; 
  public static final int S_CATALOG_TYPE = 516; 
  public static final int S_CATALOG_END = 516; 
  public static final int S_CATALOG_ORDER_START = 517; 
  public static final int S_CORD_ID = 517; 
  public static final int S_CORD_BILL_CUSTOMER_ID = 518; 
  public static final int S_CORD_SHIP_CUSTOMER_ID = 519; 
  public static final int S_CORD_ORDER_DATE = 520; 
  public static final int S_CORD_ORDER_TIME = 521; 
  public static final int S_CORD_SHIP_MODE_ID = 522; 
  public static final int S_CORD_CALL_CENTER_ID = 523; 
  public static final int S_CLIN_ITEM_ID = 524; 
  public static final int S_CORD_COMMENT = 525; 
  public static final int S_CATALOG_ORDER_END = 525; 
  public static final int S_CATALOG_ORDER_LINEITEM_START = 526; 
  public static final int S_CLIN_ORDER_ID = 526; 
  public static final int S_CLIN_LINE_NUMBER = 527; 
  public static final int S_CLIN_PROMOTION_ID = 528; 
  public static final int S_CLIN_QUANTITY = 529; 
  public static final int S_CLIN_COUPON_AMT = 530; 
  public static final int S_CLIN_WAREHOUSE_ID = 531; 
  public static final int S_CLIN_SHIP_DATE = 532; 
  public static final int S_CLIN_CATALOG_ID = 533; 
  public static final int S_CLIN_CATALOG_PAGE_ID = 534; 
  public static final int S_CLIN_PRICING = 535; 
  public static final int S_CLIN_SHIP_COST = 536; 
  public static final int S_CLIN_IS_RETURNED = 537; 
  public static final int S_CLIN_PERMUTE = 538; 
  public static final int S_CATALOG_ORDER_LINEITEM_END = 538; 
  public static final int S_CATALOG_PAGE_START = 539; 
  public static final int S_CATALOG_PAGE_CATALOG_NUMBER = 539; 
  public static final int S_CATALOG_PAGE_NUMBER = 540; 
  public static final int S_CATALOG_PAGE_DEPARTMENT = 541; 
  public static final int S_CP_ID = 542; 
  public static final int S_CP_START_DATE = 543; 
  public static final int S_CP_END_DATE = 544; 
  public static final int S_CP_DESCRIPTION = 545; 
  public static final int S_CP_TYPE = 546; 
  public static final int S_CATALOG_PAGE_END = 546; 
  public static final int S_CATALOG_PROMOTIONAL_ITEM_START = 547; 
  public static final int S_CATALOG_PROMOTIONAL_ITEM_CATALOG_NUMBER = 547; 
  public static final int S_CATALOG_PROMOTIONAL_ITEM_CATALOG_PAGE_NUMBER = 548; 
  public static final int S_CATALOG_PROMOTIONAL_ITEM_ITEM_ID = 549; 
  public static final int S_CATALOG_PROMOTIONAL_ITEM_PROMOTION_ID = 550; 
  public static final int S_CATALOG_PROMOTIONAL_ITEM_END = 550; 
  public static final int S_CATALOG_RETURNS_START = 551; 
  public static final int S_CRET_CALL_CENTER_ID = 551; 
  public static final int S_CRET_ORDER_ID = 552; 
  public static final int S_CRET_LINE_NUMBER = 553; 
  public static final int S_CRET_ITEM_ID = 554; 
  public static final int S_CRET_RETURN_CUSTOMER_ID = 555; 
  public static final int S_CRET_REFUND_CUSTOMER_ID = 556; 
  public static final int S_CRET_DATE = 557; 
  public static final int S_CRET_TIME = 558; 
  public static final int S_CRET_QUANTITY = 559; 
  public static final int S_CRET_AMOUNT = 560; 
  public static final int S_CRET_TAX = 561; 
  public static final int S_CRET_FEE = 562; 
  public static final int S_CRET_SHIP_COST = 563; 
  public static final int S_CRET_REFUNDED_CASH = 564; 
  public static final int S_CRET_REVERSED_CHARGE = 565; 
  public static final int S_CRET_MERCHANT_CREDIT = 566; 
  public static final int S_CRET_REASON_ID = 567; 
  public static final int S_CRET_PRICING = 568; 
  public static final int S_CRET_SHIPMODE_ID = 569; 
  public static final int S_CRET_WAREHOUSE_ID = 570; 
  public static final int S_CRET_CATALOG_PAGE_ID = 571; 
  public static final int S_CATALOG_RETURNS_END = 571; 
  public static final int S_CATEGORY_START = 572; 
  public static final int S_CATEGORY_ID = 572; 
  public static final int S_CATEGORY_NAME = 573; 
  public static final int S_CATEGORY_DESC = 574; 
  public static final int S_CATEGORY_END = 574; 
  public static final int S_CLASS_START = 575; 
  public static final int S_CLASS_ID = 575; 
  public static final int S_CLASS_SUBCAT_ID = 576; 
  public static final int S_CLASS_DESC = 577; 
  public static final int S_CLASS_END = 577; 
  public static final int S_COMPANY_START = 578; 
  public static final int S_COMPANY_ID = 578; 
  public static final int S_COMPANY_NAME = 579; 
  public static final int S_COMPANY_END = 579; 
  public static final int S_CUSTOMER_START = 580; 
  public static final int S_CUST_ID = 580; 
  public static final int S_CUST_SALUTATION = 581; 
  public static final int S_CUST_LAST_NAME = 582; 
  public static final int S_CUST_FIRST_NAME = 583; 
  public static final int S_CUST_PREFERRED_FLAG = 584; 
  public static final int S_CUST_BIRTH_DATE = 585; 
  public static final int S_CUST_FIRST_PURCHASE_DATE = 586; 
  public static final int S_CUST_FIRST_SHIPTO_DATE = 587; 
  public static final int S_CUST_BIRTH_COUNTRY = 588; 
  public static final int S_CUST_LOGIN = 589; 
  public static final int S_CUST_EMAIL = 590; 
  public static final int S_CUST_LAST_LOGIN = 591; 
  public static final int S_CUST_LAST_REVIEW = 592; 
  public static final int S_CUST_PRIMARY_MACHINE = 593; 
  public static final int S_CUST_SECONDARY_MACHINE = 594; 
  public static final int S_CUST_ADDRESS = 595; 
  public static final int S_CUST_ADDRESS_STREET_NUM = 596; 
  public static final int S_CUST_ADDRESS_STREET_NAME1 = 597; 
  public static final int S_CUST_ADDRESS_STREET_NAME2 = 598; 
  public static final int S_CUST_ADDRESS_STREET_TYPE = 599; 
  public static final int S_CUST_ADDRESS_SUITE_NUM = 600; 
  public static final int S_CUST_ADDRESS_CITY = 601; 
  public static final int S_CUST_ADDRESS_ZIP = 602; 
  public static final int S_CUST_ADDRESS_COUNTY = 603; 
  public static final int S_CUST_ADDRESS_STATE = 604; 
  public static final int S_CUST_ADDRESS_COUNTRY = 605; 
  public static final int S_CUST_LOCATION_TYPE = 606; 
  public static final int S_CUST_GENDER = 607; 
  public static final int S_CUST_MARITAL_STATUS = 608; 
  public static final int S_CUST_EDUCATION = 609; 
  public static final int S_CUST_CREDIT_RATING = 610; 
  public static final int S_CUST_PURCHASE_ESTIMATE = 611; 
  public static final int S_CUST_BUY_POTENTIAL = 612; 
  public static final int S_CUST_DEPENDENT_CNT = 613; 
  public static final int S_CUST_EMPLOYED_CNT = 614; 
  public static final int S_CUST_COLLEGE_CNT = 615; 
  public static final int S_CUST_VEHICLE_CNT = 616; 
  public static final int S_CUST_INCOME = 617; 
  public static final int S_CUSTOMER_END = 617; 
  public static final int S_DIVISION_START = 618; 
  public static final int S_DIVISION_ID = 618; 
  public static final int S_DIVISION_COMPANY = 619; 
  public static final int S_DIVISION_NAME = 620; 
  public static final int S_DIVISION_END = 620; 
  public static final int S_INVENTORY_START = 621; 
  public static final int S_INVN_WAREHOUSE = 621; 
  public static final int S_INVN_ITEM = 622; 
  public static final int S_INVN_DATE = 623; 
  public static final int S_INVN_QUANTITY = 624; 
  public static final int S_INVENTORY_END = 624; 
  public static final int S_ITEM_START = 625; 
  public static final int S_ITEM_ID = 625; 
  public static final int S_ITEM_PERMUTE = 626; 
  public static final int S_ITEM_PRODUCT_ID = 627; 
  public static final int S_ITEM_DESC = 628; 
  public static final int S_ITEM_LIST_PRICE = 629; 
  public static final int S_ITEM_WHOLESALE_COST = 630; 
  public static final int S_ITEM_MANAGER_ID = 631; 
  public static final int S_ITEM_SIZE = 632; 
  public static final int S_ITEM_FORMULATION = 633; 
  public static final int S_ITEM_FLAVOR = 634; 
  public static final int S_ITEM_UNITS = 635; 
  public static final int S_ITEM_CONTAINER = 636; 
  public static final int S_ITEM_SCD = 637; 
  public static final int S_ITEM_END = 637; 
  public static final int S_MANAGER_START = 638; 
  public static final int S_MANAGER_ID = 638; 
  public static final int S_MANAGER_NAME = 639; 
  public static final int S_MANAGER_END = 639; 
  public static final int S_MANUFACTURER_START = 640; 
  public static final int S_MANUFACTURER_ID = 640; 
  public static final int S_MANUFACTURER_NAME = 641; 
  public static final int S_MANUFACTURER_END = 641; 
  public static final int S_MARKET_START = 642; 
  public static final int S_MARKET_ID = 642; 
  public static final int S_MARKET_CLASS_NAME = 643; 
  public static final int S_MARKET_DESC = 644; 
  public static final int S_MARKET_MANAGER_ID = 645; 
  public static final int S_MARKET_END = 645; 
  public static final int S_PRODUCT_START = 646; 
  public static final int S_PRODUCT_ID = 646; 
  public static final int S_PRODUCT_BRAND_ID = 647; 
  public static final int S_PRODUCT_NAME = 648; 
  public static final int S_PRODUCT_TYPE = 649; 
  public static final int S_PRODUCT_END = 649; 
  public static final int S_PROMOTION_START = 650; 
  public static final int S_PROMOTION_ID = 650; 
  public static final int S_PROMOTION_ITEM_ID = 651; 
  public static final int S_PROMOTION_START_DATE = 652; 
  public static final int S_PROMOTION_END_DATE = 653; 
  public static final int S_PROMOTION_COST = 654; 
  public static final int S_PROMOTION_RESPONSE_TARGET = 655; 
  public static final int S_PROMOTION_DMAIL = 656; 
  public static final int S_PROMOTION_EMAIL = 657; 
  public static final int S_PROMOTION_CATALOG = 658; 
  public static final int S_PROMOTION_TV = 659; 
  public static final int S_PROMOTION_RADIO = 660; 
  public static final int S_PROMOTION_PRESS = 661; 
  public static final int S_PROMOTION_EVENT = 662; 
  public static final int S_PROMOTION_DEMO = 663; 
  public static final int S_PROMOTION_DETAILS = 664; 
  public static final int S_PROMOTION_PURPOSE = 665; 
  public static final int S_PROMOTION_DISCOUNT_ACTIVE = 666; 
  public static final int S_PROMOTION_DISCOUNT_PCT = 667; 
  public static final int S_PROMOTION_NAME = 668; 
  public static final int S_PROMOTION_BITFIELD = 669; 
  public static final int S_PROMOTION_END = 669; 
  public static final int S_PURCHASE_START = 670; 
  public static final int S_PURCHASE_ID = 670; 
  public static final int S_PURCHASE_STORE_ID = 671; 
  public static final int S_PURCHASE_CUSTOMER_ID = 672; 
  public static final int S_PURCHASE_DATE = 673; 
  public static final int S_PURCHASE_TIME = 674; 
  public static final int S_PURCHASE_REGISTER = 675; 
  public static final int S_PURCHASE_CLERK = 676; 
  public static final int S_PURCHASE_COMMENT = 677; 
  public static final int S_PURCHASE_PRICING = 678; 
  public static final int S_PLINE_ITEM_ID = 679; 
  public static final int S_PURCHASE_END = 679; 
  public static final int S_PURCHASE_LINEITEM_START = 680; 
  public static final int S_PLINE_PURCHASE_ID = 680; 
  public static final int S_PLINE_NUMBER = 681; 
  public static final int S_PLINE_PROMOTION_ID = 682; 
  public static final int S_PLINE_SALE_PRICE = 683; 
  public static final int S_PLINE_QUANTITY = 684; 
  public static final int S_PLINE_COUPON_AMT = 685; 
  public static final int S_PLINE_COMMENT = 686; 
  public static final int S_PLINE_PRICING = 687; 
  public static final int S_PLINE_IS_RETURNED = 688; 
  public static final int S_PLINE_PERMUTE = 689; 
  public static final int S_PURCHASE_LINEITEM_END = 689; 
  public static final int S_REASON_START = 690; 
  public static final int S_REASON_ID = 690; 
  public static final int S_REASON_DESC = 691; 
  public static final int S_REASON_END = 691; 
  public static final int S_STORE_START = 692; 
  public static final int S_STORE_ID = 692; 
  public static final int S_STORE_ADDRESS_ID = 693; 
  public static final int S_STORE_DIVISION_ID = 694; 
  public static final int S_STORE_OPEN_DATE = 695; 
  public static final int S_STORE_CLOSE_DATE = 696; 
  public static final int S_STORE_NAME = 697; 
  public static final int S_STORE_CLASS = 698; 
  public static final int S_STORE_EMPLOYEES = 699; 
  public static final int S_STORE_FLOOR_SPACE = 700; 
  public static final int S_STORE_HOURS = 701; 
  public static final int S_STORE_MARKET_MANAGER_ID = 702; 
  public static final int S_STORE_MANAGER_ID = 703; 
  public static final int S_STORE_MARKET_ID = 704; 
  public static final int S_STORE_GEOGRAPHY_CLASS = 705; 
  public static final int S_STORE_TAX_PERCENTAGE = 706; 
  public static final int S_STORE_END = 706; 
  public static final int S_STORE_PROMOTIONAL_ITEM_START = 707; 
  public static final int S_SITM_PROMOTION_ID = 707; 
  public static final int S_SITM_ITEM_ID = 708; 
  public static final int S_SITM_STORE_ID = 709; 
  public static final int S_STORE_PROMOTIONAL_ITEM_END = 709; 
  public static final int S_STORE_RETURNS_START = 710; 
  public static final int S_SRET_STORE_ID = 710; 
  public static final int S_SRET_PURCHASE_ID = 711; 
  public static final int S_SRET_LINENUMBER = 712; 
  public static final int S_SRET_ITEM_ID = 713; 
  public static final int S_SRET_CUSTOMER_ID = 714; 
  public static final int S_SRET_RETURN_DATE = 715; 
  public static final int S_SRET_RETURN_TIME = 716; 
  public static final int S_SRET_TICKET_NUMBER = 717; 
  public static final int S_SRET_RETURN_QUANTITY = 718; 
  public static final int S_SRET_RETURN_AMT = 719; 
  public static final int S_SRET_RETURN_TAX = 720; 
  public static final int S_SRET_RETURN_FEE = 721; 
  public static final int S_SRET_RETURN_SHIP_COST = 722; 
  public static final int S_SRET_REFUNDED_CASH = 723; 
  public static final int S_SRET_REVERSED_CHARGE = 724; 
  public static final int S_SRET_MERCHANT_CREDIT = 725; 
  public static final int S_SRET_REASON_ID = 726; 
  public static final int S_SRET_PRICING = 727; 
  public static final int S_STORE_RETURNS_END = 727; 
  public static final int S_SUBCATEGORY_START = 728; 
  public static final int S_SBCT_ID = 728; 
  public static final int S_SBCT_CATEGORY_ID = 729; 
  public static final int S_SBCT_NAME = 730; 
  public static final int S_SBCT_DESC = 731; 
  public static final int S_SUBCATEGORY_END = 731; 
  public static final int S_SUBCLASS_START = 732; 
  public static final int S_SUBC_ID = 732; 
  public static final int S_SUBC_CLASS_ID = 733; 
  public static final int S_SUBC_NAME = 734; 
  public static final int S_SUBC_DESC = 735; 
  public static final int S_SUBCLASS_END = 735; 
  public static final int S_WAREHOUSE_START = 736; 
  public static final int S_WRHS_ID = 736; 
  public static final int S_WRHS_DESC = 737; 
  public static final int S_WRHS_SQFT = 738; 
  public static final int S_WRHS_ADDRESS_ID = 739; 
  public static final int S_WAREHOUSE_END = 739; 
  public static final int S_WEB_ORDER_START = 740; 
  public static final int S_WORD_ID = 740; 
  public static final int S_WORD_BILL_CUSTOMER_ID = 741; 
  public static final int S_WORD_SHIP_CUSTOMER_ID = 742; 
  public static final int S_WORD_ORDER_DATE = 743; 
  public static final int S_WORD_ORDER_TIME = 744; 
  public static final int S_WORD_SHIP_MODE_ID = 745; 
  public static final int S_WORD_WEB_SITE_ID = 746; 
  public static final int S_WORD_COMMENT = 747; 
  public static final int S_WLIN_ITEM_ID = 748; 
  public static final int S_WEB_ORDER_END = 748; 
  public static final int S_WEB_ORDER_LINEITEM_START = 749; 
  public static final int S_WLIN_ID = 749; 
  public static final int S_WLIN_LINE_NUMBER = 750; 
  public static final int S_WLIN_PROMOTION_ID = 751; 
  public static final int S_WLIN_QUANTITY = 752; 
  public static final int S_WLIN_COUPON_AMT = 753; 
  public static final int S_WLIN_WAREHOUSE_ID = 754; 
  public static final int S_WLIN_SHIP_DATE = 755; 
  public static final int S_WLIN_WEB_PAGE_ID = 756; 
  public static final int S_WLIN_PRICING = 757; 
  public static final int S_WLIN_SHIP_COST = 758; 
  public static final int S_WLIN_IS_RETURNED = 759; 
  public static final int S_WLIN_PERMUTE = 760; 
  public static final int S_WEB_ORDER_LINEITEM_END = 760; 
  public static final int S_WEB_PAGE_START = 761; 
  public static final int S_WPAG_SITE_ID = 761; 
  public static final int S_WPAG_ID = 762; 
  public static final int S_WPAG_CREATE_DATE = 763; 
  public static final int S_WPAG_ACCESS_DATE = 764; 
  public static final int S_WPAG_AUTOGEN_FLAG = 765; 
  public static final int S_WPAG_DEPARTMENT = 766; 
  public static final int S_WPAG_URL = 767; 
  public static final int S_WPAG_TYPE = 768; 
  public static final int S_WPAG_CHAR_CNT = 769; 
  public static final int S_WPAG_LINK_CNT = 770; 
  public static final int S_WPAG_IMAGE_CNT = 771; 
  public static final int S_WPAG_MAX_AD_CNT = 772; 
  public static final int S_WPAG_PERMUTE = 773; 
  public static final int S_WEB_PAGE_END = 773; 
  public static final int S_WEB_PROMOTIONAL_ITEM_START = 774; 
  public static final int S_WITM_SITE_ID = 774; 
  public static final int S_WITM_PAGE_ID = 775; 
  public static final int S_WITM_ITEM_ID = 776; 
  public static final int S_WITM_PROMOTION_ID = 777; 
  public static final int S_WEB_PROMOTIONAL_ITEM_END = 777; 
  public static final int S_WEB_RETURNS_START = 778; 
  public static final int S_WRET_SITE_ID = 778; 
  public static final int S_WRET_ORDER_ID = 779; 
  public static final int S_WRET_LINE_NUMBER = 780; 
  public static final int S_WRET_ITEM_ID = 781; 
  public static final int S_WRET_RETURN_CUST_ID = 782; 
  public static final int S_WRET_REFUND_CUST_ID = 783; 
  public static final int S_WRET_RETURN_DATE = 784; 
  public static final int S_WRET_RETURN_TIME = 785; 
  public static final int S_WRET_REASON_ID = 786; 
  public static final int S_WRET_PRICING = 787; 
  public static final int S_WEB_RETURNS_END = 787; 
  public static final int S_WEB_SITE_START = 788; 
  public static final int S_WSIT_ID = 788; 
  public static final int S_WSIT_OPEN_DATE = 789; 
  public static final int S_WSIT_CLOSE_DATE = 790; 
  public static final int S_WSIT_NAME = 791; 
  public static final int S_WSIT_ADDRESS_ID = 792; 
  public static final int S_WSIT_DIVISION_ID = 793; 
  public static final int S_WSIT_CLASS = 794; 
  public static final int S_WSIT_MANAGER_ID = 795; 
  public static final int S_WSIT_MARKET_ID = 796; 
  public static final int S_WSIT_TAX_PERCENTAGE = 797; 
  public static final int S_WEB_SITE_END = 797; 
  public static final int S_ZIPG_START = 798; 
  public static final int S_ZIPG_ZIP = 798; 
  public static final int S_ZIPG_GMT = 799; 
  public static final int S_ZIPG_END = 799; 
  public static final int MAX_COLUMN = 799;

  // dist.h

  static class d_idx_t {
  final String name;
    int length() {return 0;}

    d_idx_t(String name) {
      this.name = name;
    }
  }

  // dist.c

  public static final int D_NAME_LEN    = 20;
  public static final int FL_LOADED     = 0x01;

  /** Comparison routine for two d_idx_t entries; used by qsort. */
  int di_compare(d_idx_t op1, d_idx_t op2) {
    return op1.name.compareTo(op2.name);
  }

  static Map<String, d_idx_t> idxMap = null;

  /** Translates from dist_t name to d_idx_t. */
  d_idx_t find_dist(String name) {
    d_idx_t key,
        id = null;
    int i;
    FileInputStream ifp;

    // load the index if this is the first time through
    //
    // TODO: thread-safe loading
/*
    if (idxMap == null) {
      // open the dist file
      final String distributions = get_str("DISTRIBUTIONS");
      try {
        ifp = new FileInputStream(distributions);
      } catch (FileNotFoundException e) {
        throw new RuntimeException("Error: open of distributions failed: ", e);
      }
      int temp = ifp.read;
        if (fread( &temp,  1, sizeof(int32_t), ifp) != sizeof(int32_t))
        {
          fprintf(stderr, "Error: read of index count failed: ");
          perror(distributions);
          exit(2);
        }
        entry_count = ntohl(temp);
        if ((temp = fseek(ifp, -entry_count * IDX_SIZE, SEEK_END)) < 0)
        {
          fprintf(stderr, "Error: lseek to index failed: ");
          fprintf(stderr, "attempting to reach %d\nSystem error: ",
              (int)(-entry_count * IDX_SIZE));
          perror(distributions);
          exit(3);
        }
        idx = (d_idx_t *)malloc(entry_count * sizeof(d_idx_t));
        MALLOC_CHECK(idx);
        for (i=0; i < entry_count; i++)
        {
          memset(idx + i, 0, sizeof(d_idx_t));
          if (fread( idx[i].name,  1, D_NAME_LEN, ifp) < D_NAME_LEN)
          {
            fprintf(stderr, "Error: read index failed (1): ");
            perror(distributions);
            exit(2);
          }
          idx[i].name[D_NAME_LEN] = '\0';
          if (fread( &temp,  1, sizeof(int32_t), ifp) != sizeof(int32_t))
          {
            fprintf(stderr, "Error: read index failed (2): ");
            perror(distributions);
            exit(2);
          }
          idx[i].index = ntohl(temp);
          if (fread( &temp,  1, sizeof(int32_t), ifp) != sizeof(int32_t))
          {
            fprintf(stderr, "Error: read index failed (4): ");
            perror(distributions);
            exit(2);
          }
          idx[i].offset = ntohl(temp);
          if (fread( &temp,  1, sizeof(int32_t), ifp) != sizeof(int32_t))
          {
            fprintf(stderr, "Error: read index failed (5): ");
            perror(distributions);
            exit(2);
          }
          idx[i].str_space = ntohl(temp);
          if (fread( &temp, 1, sizeof(int32_t), ifp) != sizeof(int32_t))
          {
            fprintf(stderr, "Error: read index failed (6): ");
            perror(distributions);
            exit(2);
          }
          idx[i].length = ntohl(temp);
          if (fread( &temp, 1, sizeof(int32_t), ifp) != sizeof(int32_t))
          {
            fprintf(stderr, "Error: read index failed (7): ");
            perror(distributions);
            exit(2);
          }
          idx[i].w_width = ntohl(temp);
          if (fread( &temp, 1, sizeof(int32_t), ifp) != sizeof(int32_t))
          {
            fprintf(stderr, "Error: read index failed (8): ");
            perror(distributions);
            exit(2);
          }
          idx[i].v_width = ntohl(temp);
          if (fread( &temp,  1, sizeof(int32_t), ifp) != sizeof(int32_t))
          {
            fprintf(stderr, "Error: read index failed (9): ");
            perror(distributions);
            exit(2);
          }
          idx[i].name_space = ntohl(temp);
          idx[i].dist = NULL;
        }
        qsort((void *)idx, entry_count, sizeof(d_idx_t), di_compare);
        index_loaded = 1;

                                /* make sure that this is read one thread at a time o/
        fclose(ifp);

    }

        /* find the distribution, if it exists and move to it o/
    strcpy(key.name, name);
    id = (d_idx_t *)bsearch((void *)&key, (void *)idx, entry_count,
      sizeof(d_idx_t), di_compare);
    if (id != NULL)     /* found a valid distribution o/
      if (id->flags != FL_LOADED)        /* but it needs to be loaded o/
        load_dist(id);

*/

    return(id);
  }

  /*
  * Routine: load_dist(int fd, dist_t *d)
  * Purpose: load a particular distribution
  * Algorithm:
  * Data Structures:
  *
  * Params:
  * Returns:
  * Called By:
  * Calls:
  * Assumptions:
  * Side Effects:
  * TODO: None
  */
  static int
  load_dist(d_idx_t di) {
    int res = 0,
        i,
        j;
/*
    dist_t *d;
    int32_t temp;
    FILE *ifp;
    if (di.flags != FL_LOADED) { // make sure no one beat us to it
      if ((ifp = fopen(get_str("DISTRIBUTIONS"), "rb")) == NULL)
      {
        fprintf(stderr, "Error: open of distributions failed: ");
        perror(get_str("DISTRIBUTIONS"));
        exit(1);
      }

      if ((temp = fseek(ifp, di->offset, SEEK_SET)) < 0)
      {
        fprintf(stderr, "Error: lseek to distribution failed: ");
        perror("load_dist()");
        exit(2);
      }

      di->dist = (dist_t *)malloc(sizeof(struct DIST_T));
      MALLOC_CHECK(di->dist);
      d = di->dist;

                /* load the type information o/
      d->type_vector = (int *)malloc(sizeof(int32_t) * di->v_width);
      MALLOC_CHECK(d->type_vector);
      for (i=0; i < di->v_width; i++)
      {
        if (fread(&temp, 1, sizeof(int32_t), ifp) != sizeof(int32_t))
        {
          fprintf(stderr, "Error: read of type vector failed for '%s': ", di->name);
          perror("load_dist()");
          exit(3);
        }
        d->type_vector[i] = ntohl(temp);
      }

                /* load the weights o/
      d->weight_sets = (int **)malloc(sizeof(int *) * di->w_width);
      d->maximums = (int *)malloc(sizeof(int32_t) * di->w_width);
      MALLOC_CHECK(d->weight_sets);
      MALLOC_CHECK(d->maximums);
      for (i=0; i < di->w_width; i++)
      {
        *(d->weight_sets + i) = (int *)malloc(di->length * sizeof(int32_t));
        MALLOC_CHECK(*(d->weight_sets + i));
        d->maximums[i] = 0;
        for (j=0; j < di->length; j++)
        {
          if (fread(&temp, 1, sizeof(int32_t), ifp) < 0)
          {
            fprintf(stderr, "Error: read of weights failed: ");
            perror("load_dist()");
            exit(3);
          }
          *(*(d->weight_sets + i) + j) = ntohl(temp);
                        /* calculate the maximum weight and convert sets to cummulative o/
          d->maximums[i] += d->weight_sets[i][j];
          d->weight_sets[i][j] = d->maximums[i];
        }
      }

                /* load the value offsets o/
      d->value_sets = (int **)malloc(sizeof(int *) * di->v_width);
      MALLOC_CHECK(d->value_sets);
      for (i=0; i < di->v_width; i++)
      {
        *(d->value_sets + i) = (int *)malloc(di->length * sizeof(int32_t));
        MALLOC_CHECK(*(d->value_sets + i));
        for (j=0; j < di->length; j++)
        {
          if (fread(&temp, 1, sizeof(int32_t), ifp) != sizeof(int32_t))
          {
            fprintf(stderr, "Error: read of values failed: ");
            perror("load_dist()");
            exit(4);
          }
          *(*(d->value_sets + i) + j) = ntohl(temp);
        }
      }

                /* load the column aliases, if they were defined o/
      if (di->name_space)
      {
        d->names = (char *)malloc(di->name_space);
        MALLOC_CHECK(d->names);
        if (fread(d->names, 1, di->name_space * sizeof(char), ifp) < 0)
        {
          fprintf(stderr, "Error: read of names failed: ");
          perror("load_dist()");
          exit(599);
        }

      }

                /* and finally the values themselves o/
      d->strings = (char *)malloc(sizeof(char) * di->str_space);
      MALLOC_CHECK(d->strings);
      if (fread(d->strings, 1, di->str_space * sizeof(char), ifp) < 0)
      {
        fprintf(stderr, "Error: read of strings failed: ");
        perror("load_dist()");
        exit(5);
      }

      fclose(ifp);
      di->flags = FL_LOADED;
    }


*/
    return(res);
  }

  /*
  * Routine: void *dist_op()
  * Purpose: select a value/weight from a distribution
  * Algorithm:
  * Data Structures:
  *
  * Params:     char *d_name
  *                     int vset: which set of values
  *                     int wset: which set of weights
  * Returns: appropriate data type cast as a void *
  * Called By:
  * Calls:
  * Assumptions:
  * Side Effects:
  * TODO: 20000317 Need to be sure this is portable to NT and others
  o/
  int
  dist_op(void *dest, int op, char *d_name, int vset, int wset, int stream)
  {
    d_idx_t *d;
    dist_t *dist;
    int level,
        index = 0,
        dt;
    char *char_val;
    int i_res = 1;

    if ((d = find_dist(d_name)) == NULL)
    {
      char msg[80];
      sprintf(msg, "Invalid distribution name '%s'", d_name);
      INTERNAL(msg);
      assert(d != NULL);
    }

    dist = d->dist;

    if (op == 0)
    {
      genrand_integer(&level, DIST_UNIFORM, 1,
          dist->maximums[wset - 1], 0, stream);
      while (level > dist->weight_sets[wset - 1][index] &&
          index < d->length)
        index += 1;
      dt = vset - 1;
      if ((index >= d->length) || (dt > d->v_width))
      INTERNAL("Distribution overrun");
      char_val = dist->strings + dist->value_sets[dt][index];
    }
    else
    {
      index = vset - 1;
      dt = wset - 1;
      if (index >= d->length || index < 0)
      {
        fprintf(stderr, "Runtime ERROR: Distribution over-run/under-run\n");
        fprintf(stderr, "Check distribution definitions and usage for %s.\n",
            d->name);
        fprintf(stderr, "index = %d, length=%d.\n",
            index, d->length);
        exit(1);
      }
      char_val = dist->strings + dist->value_sets[dt][index];
    }


    switch(dist->type_vector[dt])
    {
    case TKN_VARCHAR:
      if (dest)
      *(char **)dest = (char *)char_val;
      break;
    case TKN_INT:
      i_res = atoi(char_val);
      if (dest)
      *(int *)dest = i_res;
      break;
    case TKN_DATE:
      if (dest == NULL)
      {
        dest = (date_t *)malloc(sizeof(date_t));
        MALLOC_CHECK(dest);
      }
      strtodt(*(date_t **)dest, char_val);
      break;
    case TKN_DECIMAL:
      if (dest == NULL)
      {
        dest = (decimal_t *)malloc(sizeof(decimal_t));
        MALLOC_CHECK(dest);
      }
      strtodec(*(decimal_t **)dest,char_val);
      break;
    }

    return((dest == NULL)?i_res:index + 1);     /* shift back to the 1-based indexing scheme o/
  }
*/

  /*
  * Routine: int dist_weight
  * Purpose: return the weight of a particular member of a distribution
  * Algorithm:
  * Data Structures:
  *
  * Params:     distribution *d
  *                     int index: which "row"
  *                     int wset: which set of weights
  * Returns:
  * Called By:
  * Calls:
  * Assumptions:
  * Side Effects:
  * TODO:
  *     20000405 need to add error checking
  o/
  int
  dist_weight(int *dest, char *d, int index, int wset)
  {
    d_idx_t *d_idx;
    dist_t *dist;
    int res;

    if ((d_idx = find_dist(d)) == NULL)
    {
      char msg[80];
      sprintf(msg, "Invalid distribution name '%s'", d);
      INTERNAL(msg);
    }

    dist = d_idx->dist;

    res = dist->weight_sets[wset - 1][index - 1];
        /* reverse the accumulation of weights o/
    if (index > 1)
      res -= dist->weight_sets[wset - 1][index - 2];

    if (dest == NULL)
      return(res);

    *dest = res;

    return(0);
  }
*/

  /*
  * Routine: int DistNameIndex()
  * Purpose: return the index of a column alias
  * Algorithm:
  * Data Structures:
  *
  * Params:
  * Returns:
  * Called By:
  * Calls:
  * Assumptions:
  * Side Effects:
  * TODO:
  o/
  int
  DistNameIndex(char *szDist, int nNameType, char *szName)
  {
    d_idx_t *d_idx;
    dist_t *dist;
    int res;
    char *cp = NULL;

    if ((d_idx = find_dist(szDist)) == NULL)
      return(-1);
    dist = d_idx->dist;

    if (dist->names == NULL)
      return(-1);

    res = 0;
    cp = dist->names;
    do {
      if (strcasecmp(szName, cp) == 0)
        break;
      cp += strlen(cp) + 1;
      res += 1;
    } while (res < (d_idx->v_width + d_idx->w_width));

    if (res >= 0)
    {
      if ((nNameType == VALUE_NAME) && (res < d_idx->v_width))
      return(res + 1);
      if ((nNameType == WEIGHT_NAME) && (res > d_idx->v_width))
      return(res - d_idx->v_width + 1);
    }

    return(-1);
  }
*/

  /*
  * Routine: int distsize(char *name)
  * Purpose: return the size of a distribution
  * Algorithm:
  * Data Structures:
  *
  * Params:
  * Returns:
  * Called By:
  * Calls:
  * Assumptions:
  * Side Effects:
  * TODO:
  *     20000405 need to add error checking
  */
  int
  distsize(String name)
  {
    d_idx_t dist = find_dist(name);

    if (dist == null)
      return(-1);

    return(dist.length());
  }

  // scd.c

  /** an array of the most recent business key for each table */
  static final String[] arBKeys = new String[MAX_TABLE];

  static boolean scd_bInit = false;

  static long jMinimumDataDate,
      jMaximumDataDate,
      jH1DataDate,
      jT1DataDate,
      jT2DataDate;

  /** Handles the versioning and date stamps for slowly changing dimensions.
   *
   * <p>Params: 1 if there is a new id; 0 otherwise
   *
   * <p>Assumptions: Table indexes (surrogate keys) are 1-based. This assures
   * that the {@link #arBKeys} entry for each table is initialized. Otherwise,
   * parallel generation would be more difficult.
   *
   * @param handler Call back to set key, start, end
   */
  boolean setSCDKeys(int nColumnID, long kIndex, ScdHandler handler) {
    if (!scd_bInit) {
      Date dtTemp = strtodt(DATA_START_DATE);
      jMinimumDataDate = dttoj(dtTemp);
      dtTemp = strtodt(DATA_END_DATE);
      jMaximumDataDate = dttoj(dtTemp);
      jH1DataDate = jMinimumDataDate + (jMaximumDataDate - jMinimumDataDate) / 2;
      jT2DataDate = (jMaximumDataDate - jMinimumDataDate) / 3;
      jT1DataDate = jMinimumDataDate + jT2DataDate;
      jT2DataDate += jT1DataDate;
      scd_bInit = true;
    }

    int nTableID = getTableFromColumn(nColumnID);
    int nModulo = (int)(kIndex % 6);
    boolean bNewBKey = false;
    long start, end;
    switch (nModulo) {
    case 1: /* 1 revision */
      arBKeys[nTableID] = mk_bkey(kIndex, nColumnID);
      bNewBKey = true;
      start = jMinimumDataDate - nTableID * 6;
      end = -1;
      break;
    case 2:     /* 1 of 2 revisions */
      arBKeys[nTableID] = mk_bkey(kIndex, nColumnID);
      bNewBKey = true;
      start = jMinimumDataDate - nTableID * 6;
      end = jH1DataDate - nTableID * 6;
      break;
    case 3:     /* 2 of 2 revisions */
      arBKeys[nTableID] = mk_bkey(kIndex - 1, nColumnID);
      start = jH1DataDate - nTableID * 6 + 1;
      end = -1;
      break;
    case 4:     /* 1 of 3 revisions */
      arBKeys[nTableID] = mk_bkey(kIndex, nColumnID);
      bNewBKey = true;
      start = jMinimumDataDate - nTableID * 6;
      end = jT1DataDate - nTableID * 6;
      break;
    case 5:     /* 2 of 3 revisions */
      arBKeys[nTableID] = mk_bkey(kIndex - 1, nColumnID);
      start = jT1DataDate - nTableID * 6 + 1;
      end = jT2DataDate - nTableID * 6;
      break;
    case 0:     /* 3 of 3 revisions */
    default:
      arBKeys[nTableID] = mk_bkey(kIndex - 2, nColumnID);
      start = jT2DataDate - nTableID * 6 + 1;
      end = -1;
      break;
    }

    // can't have a revision in the future, per bug 114
    if (end > jMaximumDataDate) {
      end = -1L;
    }

    handler.apply(arBKeys[nTableID], start, end);

    return bNewBKey;
  }

  interface ScdHandler {
    void apply(String s, long start, long end);
  }

  // tdefs.c

  static TpcdsTable getSimpleTdefsByNumber(int nTable) {
    return TpcdsTable.getTables()[nTable];
  }

  static int getTableFromColumn(int nColumn) {
    for (int i = 0; i <= MAX_TABLE; i++) {
      TpcdsTable pT = getSimpleTdefsByNumber(i);
      if ((nColumn >= pT.nFirstColumn) && (nColumn <= pT.nLastColumn)) {
        return i;
      }
    }
    return -1;
  }

  // w_call_center.h

  public static final String MIN_CC_TAX_PERCENTAGE = "0.00";
  public static final String MAX_CC_TAX_PERCENTAGE = "0.12";

  /**
   * CALL_CENTER table structure
   */
  static class CALL_CENTER_TBL {
    long        cc_call_center_sk;
    String cc_call_center_id; // char[RS_BKEY + 1]
    long cc_rec_start_date_id;
    long cc_rec_end_date_id;
    long cc_closed_date_id;
    long cc_open_date_id;
    String cc_name; // char[RS_CC_NAME + 1];
    String cc_class;
    int                 cc_employees;
    int                 cc_sq_ft;
    String cc_hours;
    String cc_manager; // char[RS_CC_MANAGER + 1];
    int                 cc_market_id;
    String cc_market_class; // new char[RS_CC_MARKET_CLASS + 1];
    String cc_market_desc; // char[RS_CC_MARKET_DESC + 1];
    String cc_market_manager; // char[RS_CC_MARKET_MANAGER + 1];
    int                 cc_division_id;
    String cc_division_name; // RS_CC_DIVISION_NAME
    int                 cc_company;
    String cc_company_name; // char[RS_CC_COMPANY_NAME + 1];
    Address cc_address;
    Decimal cc_tax_percentage;
  }

  // w_call_center.c

  static int jDateStart,
      nDaysPerRevision;

  static boolean bInit = false;
  static int nScale;

  static Decimal dMinTaxPercentage = new Decimal(),
      dMaxTaxPercentage = new Decimal();

  static CALL_CENTER_TBL g_OldValues,
  g_w_call_center;

  public int mk_w_call_center(Object row, long index) {
    int res = 0;
    int nSuffix,
        bFirstRecord = 0,
        jDateEnd,
        nDateRange;
    long nFieldChangeFlags;
    String cp, sName1 = null, sName2 = null;
    TpcdsTable pTdef = getSimpleTdefsByNumber(CALL_CENTER);

        /* begin locals declarations */
    Date dTemp;
    final CALL_CENTER_TBL r,
    rOldValues = g_OldValues;

    if (row == null) {
      r = g_w_call_center;
    } else {
      r = (CALL_CENTER_TBL) row;
    }

    if (!bInit) {
      // begin locals allocation/initialization
      dTemp = strtodt(DATA_START_DATE);
      jDateStart = dttoj(dTemp) - WEB_SITE;
      dTemp = strtodt(DATA_END_DATE);
      jDateEnd = dttoj(dTemp);
      nDateRange = jDateEnd - jDateStart + 1;
      nDaysPerRevision = nDateRange / pTdef.nParam + 1;
      nScale = get_int("SCALE");

      // these fields need to be handled as part of SCD code or further definition
      r.cc_division_id = -1;
      r.cc_closed_date_id = -1;
      r.cc_division_name = "No Name";

      dMinTaxPercentage = strtodec(MIN_CC_TAX_PERCENTAGE);
      dMaxTaxPercentage = strtodec(MAX_CC_TAX_PERCENTAGE);
      bInit = true;
    }

    pTdef.kNullBitMap = nullSet(CC_NULLS);
    r.cc_call_center_sk = index;

    // If we have generated the required history for this business key and
    // generate a new one then reset associate fields (e.g., rec_start_date
    // minimums)
    if (setSCDKeys(CC_CALL_CENTER_ID, index, new ScdHandler() {
          public void apply(String s, long start, long end) {
            r.cc_call_center_id = s;
            r.cc_rec_start_date_id = start;
            r.cc_rec_end_date_id = end;
          }
        })) {
      r.cc_open_date_id = jDateStart
          - genrand_integer(DIST_UNIFORM, -365, 0, 0, CC_OPEN_DATE_ID);

      // some fields are not changed, even when a new version of the row is
      // written
      nSuffix = (int)index / distsize("call_centers");
      cp = dist_member ("call_centers", (int) (index % distsize("call_centers")) + 1, 1);
      if (nSuffix > 0) {
        r.cc_name = String.format("%s_%d", cp, nSuffix);
      } else {
        r.cc_name = cp;
      }
      r.cc_address = mk_address(CC_ADDRESS);
      bFirstRecord = 1;
    }

 /*
  * this is  where we select the random number that controls if a field changes from
  * one record to the next.
  */
    nFieldChangeFlags = next_random(CC_SCD);


        /* the rest of the record in a history-keeping dimension can either be a new data value or not;
         * use a random number and its bit pattern to determine which fields to replace and which to retain
         */
    pick_distribution(r.cc_class, "call_center_class", 1, 1, CC_CLASS);
    changeSCD(SCD_PTR, r.cc_class, rOldValues.cc_class, nFieldChangeFlags, bFirstRecord);

    r.cc_employees = genrand_integer(DIST_UNIFORM, 1, CC_EMPLOYEE_MAX * nScale * nScale, 0, CC_EMPLOYEES);
    changeSCD(SCD_INT, r.cc_employees, rOldValues.cc_employees, nFieldChangeFlags, bFirstRecord);

    r.cc_sq_ft = genrand_integer(DIST_UNIFORM, 100, 700, 0, CC_SQ_FT);
    r.cc_sq_ft *= r.cc_employees;
    changeSCD(SCD_INT, r.cc_sq_ft, rOldValues.cc_sq_ft, nFieldChangeFlags,  bFirstRecord);

    pick_distribution(r.cc_hours, "call_center_hours", 1, 1, CC_HOURS);
    changeSCD(SCD_PTR, r.cc_hours, rOldValues.cc_hours, nFieldChangeFlags, bFirstRecord);

    pick_distribution(sName1, "first_names", 1, 1, CC_MANAGER);
    pick_distribution(sName2, "last_names", 1, 1, CC_MANAGER);
    r.cc_manager = String.format("%s %s", sName1, sName2);
    changeSCD(SCD_CHAR, r.cc_manager, rOldValues.cc_manager, nFieldChangeFlags, bFirstRecord);

    r.cc_market_id = genrand_integer(DIST_UNIFORM, 1, 6, 0, CC_MARKET_ID);
    changeSCD(SCD_INT, r.cc_market_id, rOldValues.cc_market_id,  nFieldChangeFlags,  bFirstRecord);

    gen_text(r.cc_market_class, 20, RS_CC_MARKET_CLASS, CC_MARKET_CLASS);
    changeSCD(SCD_CHAR, r.cc_market_class, rOldValues.cc_market_class,  nFieldChangeFlags,  bFirstRecord);

    gen_text(r.cc_market_desc, 20, RS_CC_MARKET_DESC, CC_MARKET_DESC);
    changeSCD(SCD_CHAR, r.cc_market_desc, rOldValues.cc_market_desc,  nFieldChangeFlags,  bFirstRecord);

    pick_distribution(sName1, "first_names", 1, 1, CC_MARKET_MANAGER);
    pick_distribution(sName2, "last_names", 1, 1, CC_MARKET_MANAGER);
    r.cc_market_manager = String.format("%s %s", sName1, sName2);
    changeSCD(SCD_CHAR, r.cc_market_manager, rOldValues.cc_market_manager,  nFieldChangeFlags,  bFirstRecord);

    r.cc_company = genrand_integer (DIST_UNIFORM, 1, 6, 0, CC_COMPANY);
    changeSCD(SCD_INT, r.cc_company, rOldValues.cc_company,  nFieldChangeFlags,  bFirstRecord);

    r.cc_division_id = genrand_integer (DIST_UNIFORM, 1, 6, 0, CC_COMPANY);
    changeSCD(SCD_INT, r.cc_division_id, rOldValues.cc_division_id,  nFieldChangeFlags,  bFirstRecord);

    mk_word(r.cc_division_name, "syllables", r.cc_division_id, RS_CC_DIVISION_NAME, CC_DIVISION_NAME);
    changeSCD(SCD_CHAR, r.cc_division_name, rOldValues.cc_division_name,  nFieldChangeFlags,  bFirstRecord);

    mk_companyname (r.cc_company_name, CC_COMPANY_NAME, r.cc_company);
    changeSCD(SCD_CHAR, r.cc_company_name, rOldValues.cc_company_name,  nFieldChangeFlags,  bFirstRecord);

    r.cc_tax_percentage  = genrand_decimal(DIST_UNIFORM, dMinTaxPercentage, dMaxTaxPercentage, null, CC_TAX_PERCENTAGE);
    changeSCD(SCD_DEC, r.cc_tax_percentage, rOldValues.cc_tax_percentage,  nFieldChangeFlags,  bFirstRecord);

    return (res);

  }

  // All functions below this point are TODO.
  // Replace them with functions from .c files.

  // TODO:
  private void changeSCD(int scdDec, Decimal cc_tax_percentage,
      Decimal cc_tax_percentage1, long nFieldChangeFlags, int bFirstRecord) {

  }

  // TODO:
  private Decimal genrand_decimal(int distUniform, Decimal dMinTaxPercentage,
      Decimal dMaxTaxPercentage, Object o, int ccTaxPercentage) {
    return null;
  }

  // TODO:
  private void mk_companyname(String cc_company_name, int ccCompanyName,
      int cc_company) {

  }

  // TODO:
  private void mk_word(String cc_division_name, String syllables,
      int cc_division_id, int rsCcDivisionName, int ccDivisionName) {

  }

  // TODO:
  private void gen_text(String cc_market_class, int i, int rsCcMarketClass,
      int ccMarketClass) {
    
  }

  // TODO:
  private void changeSCD(int scd_ptr, String cc_class, String cc_class1,
      long nFieldChangeFlags, int bFirstRecord) {
    
  }

  // TODO:
  private void changeSCD(int scd_ptr, int cc_class, int cc_class1,
      long nFieldChangeFlags, int bFirstRecord) {
    
  }

  static final int SCD_PTR = 0; // TODO:
  static final int SCD_INT = 1; // TODO:
  static final int SCD_CHAR = 2; // TODO:
  static final int SCD_DEC = 3; // TODO:

  // TODO:
  private String pick_distribution(String cc_class, String call_center_class,
      int i, int i1, int ccClass) {
    return null;
  }

  // TODO:
  private Address mk_address(int ccAddress) {
    return null;
  }

  // TODO:
  private String dist_member(String distName, int length, int ordinal) {
    return null;
  }
}

// End Dsgen.java
