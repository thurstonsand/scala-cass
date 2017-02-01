#!/bin/sh
set -u
set -e

function setup_cassandra {
  version=$1
  cassandra_path="cassandra-docs/cassandra-$version"
  if [[ ! -d $cassandra_path ]]; then
    wget -P cassandra-docs "http://archive.apache.org/dist/cassandra/$version/apache-cassandra-$version-bin.tar.gz"
    tar -xzf "cassandra-docs/apache-cassandra-$version-bin.tar.gz" -C cassandra-docs
    mv "cassandra-docs/apache-cassandra-$version" $cassandra_path
    echo "data_file_directories:" >> "$cassandra_path/conf/cassandra.yaml"
    echo "    - $PWD/cassandra-docs/data" >> "$cassandra_path/conf/cassandra.yaml"
    echo "commitlog_directory: $PWD/cassandra-docs/commitlog" >> "$cassandra_path/conf/cassandra.yaml"
  fi
}

function clear_cassandra {
  rm -rf cassandra-docs/data/*
  rm -rf cassandra-docs/commitlog/*
}

function wait_for_cassandra {
  for i in 1 2 3 4 5 6 7 8 9 10; do
    if $1/nodetool status 2>/dev/null | grep "^UN" >/dev/null; then
      echo "cassandra is running"
      return 0
    else
      echo "waiting on cassandra..."
      sleep 2
    fi
  done
  echo "cassandra did not start successfully"
  return -1
}

function run_cassandra_21 {
  jenv local 1.7
  trap "kill %1" INT TERM EXIT
  ./$cassandra_path/bin/cassandra -f 2&>/dev/null &
  wait_for_cassandra "./$cassandra_path/bin"
  sbt "tut-cass21/clean" "tut-cass21/tut"
  kill %1
  trap - INT TERM EXIT
}

function run_cassandra_30 {
  jenv local 1.8
  trap "kill %2" INT TERM EXIT
  ./$cassandra_path/bin/cassandra -f 2&>/dev/null &
  wait_for_cassandra "./$cassandra_path/bin"
  sbt "tut-cass3/clean" "tut-cass3/tut"
  kill %2
  trap - INT TERM EXIT
}

function compile_results {
  rm -rf docs/root/src/main/tut/cass3 docs/root/src/main/tut/cass21
  cp -r "docs/cass3/target/scala-2.11/resource_managed/main/jekyll/cass3" docs/root/src/main/tut/
  cp -r "docs/cass21/target/scala-2.11/resource_managed/main/jekyll/cass21" docs/root/src/main/tut/

  sbt "docs/clean" "docs/makeMicrosite"
}

function run_jekyll {
  trap "rm -rf _site" INT TERM EXIT
  jekyll serve -s docs/root/target/jekyll/
}

mkdir -p cassandra-docs
mkdir -p cassandra-docs/data
mkdir -p cassandra-docs/commitlog

clear_cassandra
setup_cassandra "2.1.9"

run_cassandra_21

clear_cassandra
setup_cassandra "3.0.9"

run_cassandra_30

compile_results
run_jekyll
