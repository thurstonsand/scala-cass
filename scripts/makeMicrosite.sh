#!/bin/sh

cassandra -f 2&>/dev/null &
#jenv local 1.7
#sbt "tut-cass21/clean" "tut-cass21/tut"
jenv local 1.8
#sbt "tut-cass3/clean" "tut-cass3/tut"
sbt "tut-cass3/tut"
kill %1

rm -rf docs/src/main/tut/cass3 docs/src/main/tut/cass21
cp -r "cass3/target/scala-2.11/resource_managed/main/jekyll/cass3" docs/src/main/tut/
cp -r "cass21/target/scala-2.11/resource_managed/main/jekyll/cass21" docs/src/main/tut/

sbt "docs/clean" "docs/makeMicrosite"

jekyll serve -s docs/target/site
