<!--
{% comment %}
Licensed to Julian Hyde under one or more contributor license
agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.

Julian Hyde licenses this file to you under the Apache License,
Version 2.0 (the "License"); you may not use this file except in
compliance with the License.  You may obtain a copy of the License at:

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
{% endcomment %}
-->
[![Build Status](https://travis-ci.org/julianhyde/tpcds.png)](https://travis-ci.org/julianhyde/tpcds)

# tpcds

A port of the TPC-DS data generator to Java.

## Prerequisites

Tpcds requires Java (1.7 or higher; 1.9 preferred), git, maven (3.2.1 or higher).

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
  <a href="mailto:dev@calcite.apache.org">dev at calcite.apache.org</a>
  (<a href="http://mail-archives.apache.org/mod_mbox/calcite-dev/">archive</a>,
  <a href="mailto:dev-subscribe@calcite.apache.org">subscribe</a>)
* Issues: https://github.com/julianhyde/quidem/issues
* <a href="HISTORY.md">Release notes and history</a>
