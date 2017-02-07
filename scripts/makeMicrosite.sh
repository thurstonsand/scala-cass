#!/bin/sh
set -ue

enable_cassandra_2=-1
enable_cassandra_3=-1

function help {
  echo "how to use:"
  echo "must be run from project home (same level as build.sbt file)."
  echo "first time running will download cassandra binaries. This will require an internet connection"
  echo "options:"
  echo "no option: compile cassandra 2 and cassandra 3 docs, then combine them"
  echo "-0 -- only combine existing docs"
  echo "-2 -- only compile cassandra 2 and combine with old cassandra 3 docs"
  echo "-3 -- only compile cassandra 3 and combine with old cassandra 2 docs"
  echo "-h -- print out this message"
  exit 1
}

function in_right_location {
  if [[ ! -f build.sbt || ! -n $(cat build.sbt | grep 'com.github.thurstonsand') ]]; then
    echo "not in root project folder!"
    echo
    help
  fi
}
function parse_inputs {
	while getopts ":023h" opt; do
  	case $opt in
    	0)
      	enable_cassandra_2=0
        enable_cassandra_3=0
      	;;
      2)
        enable_cassandra_2=1
        enable_cassandra_3=0
        ;;
      3)
        enable_cassandra_2=0
        enable_cassandra_3=1
        ;;
      h)
        help
        ;;
    	\?)
      	echo "Invalid option: -$OPTARG" >&2
        help
      	;;
      :)
        echo "Option -$OPTARG requires an argument." >&2
        help
        ;;
  	esac
	done
  if [[ enable_cassandra_2 -eq -1 || enable_cassandra_3 -eq -1 ]]; then
    enable_cassandra_2=1
    enable_cassandra_3=1
  fi
}

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

in_right_location
parse_inputs $@

if [[ enable_cassandra_2 -gt 0 ]]; then
  setup_cassandra "2.1.9"
  clear_cassandra

  run_cassandra_21
fi

if [[ enable_cassandra_3 -gt 0 ]]; then
  setup_cassandra "3.0.9"
  clear_cassandra

  run_cassandra_30
fi

compile_results
run_jekyll
