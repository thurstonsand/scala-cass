#!/bin/sh
set -ue

function setup_cassandra {
  version=$1
  cassandra_path="cassandra-docs/cassandra-$version"
  if [[ ! -d $cassandra_path ]]; then
    wget -P cassandra-docs "http://archive.apache.org/dist/cassandra/$version/apache-cassandra-$version-bin.tar.gz"
    tar -xzf "cassandra-docs/apache-cassandra-$version-bin.tar.gz" -C cassandra-docs
    mv "cassandra-docs/apache-cassandra-$version" $cassandra_path
    mkdir -p $cassandra_path/data
    mkdir -p $cassandra_path/commitlog
    echo "data_file_directories:" >> "$cassandra_path/conf/cassandra.yaml"
    echo "    - $PWD/$cassandra_path/data" >> "$cassandra_path/conf/cassandra.yaml"
    echo "commitlog_directory: $PWD/$cassandra_path/commitlog" >> "$cassandra_path/conf/cassandra.yaml"
  fi
}

function clear_cassandra {
  rm -rf $cassandra_path/data/*
  rm -rf $cassandra_path/commitlog/*
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
  echo "starting cassandra $version"
  trap 'kill $!' INT TERM EXIT
  ./$cassandra_path/bin/cassandra -f 2&>/dev/null &
  wait_for_cassandra "./$cassandra_path/bin"
  echo "compiling cassandra 2.1 docs"
  sbt "tut-cass21/clean" "tut-cass21/tut"
  kill $!
  trap - INT TERM EXIT
}

function run_cassandra_30 {
  jenv local 1.8
  echo "starting cassandra $version"
  trap 'kill $!' INT TERM EXIT
  ./$cassandra_path/bin/cassandra -f 2&>/dev/null &
  wait_for_cassandra "./$cassandra_path/bin"
  echo "compiling cassandra 3.0 docs"
  sbt "tut-cass3/clean" "tut-cass3/tut"
  kill $!
  trap - INT TERM EXIT
}

function compile_results {
  rm -rf docs/root/src/main/tut/cass3 docs/root/src/main/tut/cass21
  cp -r "docs/cass3/target/scala-2.11/resource_managed/main/jekyll/cass3" docs/root/src/main/tut/
  cp -r "docs/cass21/target/scala-2.11/resource_managed/main/jekyll/cass21" docs/root/src/main/tut/

  echo "compiling docs"
  sbt "docs/clean" "docs/makeMicrosite"
}

function run_jekyll {
  trap "rm -rf _site" INT TERM EXIT
  jekyll serve -s docs/root/target/jekyll/
}

mkdir -p cassandra-docs

setup_cassandra "2.1.9"
clear_cassandra

run_cassandra_21

setup_cassandra "3.0.9"
clear_cassandra

run_cassandra_30

compile_results
run_jekyll
