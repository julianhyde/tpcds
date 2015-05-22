[![Build Status](https://travis-ci.org/julianhyde/tpcds.png)](https://travis-ci.org/julianhyde/tpcds)

# tpcds

A port of the TPC-DS data generator to Java.

## Prerequisites

Tpcds requires Java (1.5 or higher; 1.8 preferred), git, maven (3.2.1 or higher).

## Download and build

```bash
$ git clone git://github.com/julianhyde/tpcds.git
$ cd tpcds
$ mvn install
```

## Pre-built artifacts

Each release is published to
<a href="http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22tpcds%22%20g%3A%22net.hydromatic%22">Maven central</a>.
Add the following to the dependencies section of your `pom.xml`:

```xml
<dependency>
  <groupId>net.hydromatic</groupId>
  <artifactId>tpcds</artifactId>
  <version>0.4</version>
</dependency>
```

## More information

* License: Apache License, Version 2.0
* Author: Julian Hyde
* Blog: http://julianhyde.blogspot.com
* Project page: http://www.hydromatic.net/tpcds
* Source code: http://github.com/julianhyde/tpcds
* Developers list:
  <a href="mailto:dev@calcite.incubator.apache.org">dev at calcite.incubator.apache.org</a>
  (<a href="http://mail-archives.apache.org/mod_mbox/incubator-calcite-dev/">archive</a>,
  <a href="mailto:dev-subscribe@calcite.incubator.apache.org">subscribe</a>)
* Issues: https://github.com/julianhyde/quidem/issues
* <a href="HISTORY.md">Release notes and history</a>
