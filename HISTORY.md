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
# TPC-DS release history and change log

For a full list of releases, see <a href="https://github.com/julianhyde/tpcds/releases">github</a>.

## <a href="https://github.com/julianhyde/tpcds/releases/tag/tpcds-0.4">0.4</a> / 2015-05-22

* Document how to get artifacts from Maven central
* Not all queries have a `LIMIT` parameter
* Add hack for `distmember`
* Test that all queries can generate (something) successfully

## <a href="https://github.com/julianhyde/tpcds/releases/tag/tpcds-0.3">0.3</a> / 2015-03-05

* Publish releases to <a href="http://search.maven.org/">Maven Central</a>
  (previous releases are in <a href="http://www.conjars.org/">Conjars</a>)
* Sign jars

## <a href="https://github.com/julianhyde/tpcds/releases/tag/tpcds-0.2">0.2</a> / 2015-01-05

* Upgrade guava, various maven plugins
* Read `_LIMIT` from query template

## <a href="https://github.com/julianhyde/tpcds/releases/tag/tpcds-0.1">0.1</a> / 2014-06-18

* First release
* Templates for all 99 queries
* Expansion of "text" and "random" parameters in query templates
* Definitions of tables and columns
* Start work on row-generation for "call_center" table.
