#!/bin/sh
set -u
set -e

function wait_for_cassandra {
  for i in 1 2 3 4 5 6 7 8 9 10; do
    set +e
    if $1/nodetool status 2>/dev/null | grep "^UN" >/dev/null; then
      return 0
    else
      sleep 2
    fi
  done
  set -e
  return -1
}

mkdir -p cassandra-docs
mkdir -p cassandra-docs/data
mkdir -p cassandra-docs/commitlog

if [[ ! -d cassandra-docs/cassandra22 ]]; then
  wget -P cassandra-docs http://archive.apache.org/dist/cassandra/2.1.9/apache-cassandra-2.1.9-bin.tar.gz
  tar -xzf cassandra-docs/apache-cassandra-2.1.9-bin.tar.gz -C cassandra-docs
  mv cassandra-docs/apache-cassandra-2.1.9 cassandra-docs/cassandra22
  echo "data_file_directories:" >> cassandra-docs/cassandra22/conf/cassandra.yaml
  echo "    - $(pwd)/cassandra-docs/data" >> cassandra-docs/cassandra22/conf/cassandra.yaml
  echo "commitlog_directory: $(pwd)/cassandra-docs/commitlog" >> cassandra-docs/cassandra22/conf/cassandra.yaml
fi

rm -rf cassandra-docs/data/*
rm -rf cassandra-docs/commitlog/*

jenv local 1.7
trap "kill %1" INT TERM EXIT
./cassandra-docs/cassandra22/bin/cassandra -f 2&>/dev/null &
wait_for_cassandra ./cassandra-docs/cassandra22/bin
sbt "tut-cass21/clean" "tut-cass21/tut"
kill %1
trap - INT TERM EXIT

if [[ ! -d cassandra-docs/cassandra30 ]]; then
  wget -P cassandra-docs http://archive.apache.org/dist/cassandra/3.0.9/apache-cassandra-3.0.9-bin.tar.gz
  tar -xzf cassandra-docs/apache-cassandra-3.0.9-bin.tar.gz -C cassandra-docs
  mv cassandra-docs/apache-cassandra-3.0.9 cassandra-docs/cassandra30
  echo "data_file_directories:" >> cassandra-docs/cassandra30/conf/cassandra.yaml
  echo "    - $(pwd)/cassandra-docs/data" >> cassandra-docs/cassandra30/conf/cassandra.yaml
  echo "commitlog_directory: $(pwd)/cassandra-docs/commitlog" >> cassandra-docs/cassandra30/conf/cassandra.yaml
fi

rm -rf cassandra-docs/data/*
rm -rf cassandra-docs/commitlog/*

jenv local 1.8
trap "kill %1" INT TERM EXIT
./cassandra-docs/cassandra30/bin/cassandra -f 2&>/dev/null &
wait_for_cassandra ./cassandra-docs/cassandra30/bin
sbt "tut-cass3/clean" "tut-cass3/tut"
#sbt "tut-cass3/tut"
kill %1
trap - INT TERM EXIT

rm -rf docs/root/src/main/tut/cass3 docs/root/src/main/tut/cass21
cp -r "docs/cass3/target/scala-2.11/resource_managed/main/jekyll/cass3" docs/root/src/main/tut/
cp -r "docs/cass21/target/scala-2.11/resource_managed/main/jekyll/cass21" docs/root/src/main/tut/

sbt "docs/clean" "docs/makeMicrosite"

trap "rm -r _site" INT TERM EXIT
jekyll serve -s docs/root/target/jekyll/
