#!/bin/sh
set -ue

enable_cassandra_2=-1
enable_cassandra_3=-1
enable_jekyll=1
clean_workspace=0

function help {
  echo "how to use:"
  echo "must be run from project home (same level as build.sbt file)."
  echo "first time running will download cassandra binaries. This will require an internet connection"
  echo "options:"
  echo "no option: compile cassandra 2 and cassandra 3 docs, then combine them"
  echo "-x -- disable start up of jekyll at the end of the script"
  echo "-c -- clean the workspace first"
  echo "-h -- print out this message"
  echo "use one of:"
  echo "  -0 -- only combine existing docs"
  echo "  -2 -- only compile cassandra 2 and combine with old cassandra 3 docs"
  echo "  -3 -- only compile cassandra 3 and combine with old cassandra 2 docs"
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
  while getopts ":023xch" opt; do
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
      x)
        enable_jekyll=0
        ;;
      c)
        clean_workspace=1
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
    clean_workspace=1
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

function run_cassandra {
  local j_version=$1
  local scala_version=$2
  local folder_ext=$3

  jenv local $j_version
  if [[ -z $(java -version 2>&1 | grep $j_version) ]]; then
    echo "java version was not set successfully. Must have jenv installed and working correctly" $(java -version 2>1 | grep $j_version)
    jenv local $old_j_version
    exit 1
  fi

  if ./$cassandra_path/bin/nodetool status 2>/dev/null | grep "^UN" >/dev/null; then
    echo "a version of cassandra is already running. you must stop that instance first"
    exit 1
  fi
  echo "starting cassandra $version"
  trap 'if [[ -n "$cass_pid" ]]; then kill $cass_pid; fi' INT TERM EXIT
  ./$cassandra_path/bin/cassandra -f 2&>/dev/null &
  cass_pid=$!
  wait_for_cassandra "./$cassandra_path/bin"

  if [[ clean_workspace -gt 0 ]]; then
    echo "cleaning and compiling cassandra $folder_ext docs"
    sbt "tut-cass$folder_ext/clean" "tut-cass$folder_ext/tut"
  else
    echo "compiling cassandra $folder_ext docs"
    sbt "tut-cass$folder_ext/tut"
  fi
  kill $cass_pid

  unset cass_pid
  trap - INT TERM EXIT
  rm -rf docs/root/src/main/tut/cass$folder_ext
  cp -r "docs/cass$folder_ext/target/scala-$scala_version/resource_managed/main/jekyll/cass$folder_ext" docs/root/src/main/tut/
}

function compile_results {
  echo "compiling docs"
  sbt "docs/clean" "docs/makeMicrosite"
}

function run_jekyll {
  trap "rm -rf _site" INT TERM EXIT
  jekyll serve -s docs/root/target/jekyll/
}

old_j_version=$(jenv local) 2>/dev/null
old_j_version=${old_j_version:-$(jenv global)}

mkdir -p cassandra-docs

in_right_location
parse_inputs $@

if [[ enable_cassandra_2 -gt 0 ]]; then
  setup_cassandra "2.1.9"
  clear_cassandra

  run_cassandra 1.7 2.11 21
fi

if [[ enable_cassandra_3 -gt 0 ]]; then
  setup_cassandra "3.0.9"
  clear_cassandra

  run_cassandra 1.8 2.12 3
fi

compile_results

if [[ enable_jekyll -gt 0 ]]; then
  run_jekyll
fi
jenv local $old_j_version
