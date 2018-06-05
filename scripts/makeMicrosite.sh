#!/bin/sh
set -ue

enable_cassandra_2=-1
enable_cassandra_3=-1
enable_jekyll=1
clean_workspace=0
publish=0

function help {
  echo "how to use:"
  echo "must be run from project home (same level as build.sbt file)."
  echo "first time running will download cassandra binaries. This will require an internet connection"
  echo "must use one of:"
  echo "  -0 -- only combine existing docs"
  echo "  -2 -- compile cassandra 2 docs"
  echo "  -3 -- compile cassandra 3 docs"
  echo "  -23 -- compile cassandra 2 and 3 docs"
  echo "  -h -- print out this message"
  echo "may optionally include any of:"
  echo "  -x -- disable start up of jekyll at the end of the script"
  echo "  -c -- clean the workspace first"
  echo "  -p -- publish the microsite instead of starting jekyll"
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
  while getopts ":023xcph" opt; do
    case $opt in
      0)
        enable_cassandra_2=0
        enable_cassandra_3=0
        ;;
      2)
        enable_cassandra_2=1
        ;;
      3)
        enable_cassandra_3=1
        ;;
      x)
        enable_jekyll=0
        ;;
      c)
        clean_workspace=1
        ;;
      p)
        publish=1
        enable_jekyll=0
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
  if [[ $enable_cassandra_2 -ne -1 && $enable_cassandra_3 -eq -1 ]]; then
    enable_cassandra_3=0
  elif [[ $enable_cassandra_2 -eq -1 && $enable_cassandra_3 -ne -1 ]]; then
    enable_cassandra_2=0
  elif [[ $enable_cassandra_2 -eq -1 && $enable_cassandra_3 -eq -1 ]]; then
    help
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
  for i in $(seq 1 60); do
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
  local folder_ext=$1
  local cassandra_version=""
  if [[ "$folder_ext" -eq "21" ]]; then
    cassandra_version="2.1.10.3"
  else
    cassandra_version="3.5.0"
  fi

  if ./$cassandra_path/bin/nodetool status 2>/dev/null | grep "^UN" >/dev/null; then
    echo "cassandra is already running. you must stop that instance first"
    exit 1
  fi
  echo "starting cassandra $version"
  trap 'if [[ -n "$cass_pid" ]]; then kill $cass_pid; fi' INT TERM EXIT
  ./$cassandra_path/bin/cassandra -f 2&>/dev/null &
  cass_pid=$!
  wait_for_cassandra "./$cassandra_path/bin"

  if [[ clean_workspace -gt 0 ]]; then
    echo "cleaning and compiling cassandra $folder_ext docs"
    sbt -Dcassandra-driver.version=$cassandra_version "tut-cass$folder_ext/clean" "tut-cass$folder_ext/tut"
  else
    echo "compiling cassandra $folder_ext docs"
    sbt -Dcassandra-driver.version=$cassandra_version "tut-cass$folder_ext/tut"
  fi
  kill $cass_pid

  unset cass_pid
  trap - INT TERM EXIT
  rm -rf docs/root/src/main/tut/cass$folder_ext
  cp -r "docs/cass$folder_ext/target/scala-2.12/resource_managed/main/jekyll/cass$folder_ext" docs/root/src/main/tut/
}

function compile_results {
  echo "compiling docs"
  sbt -Dmicrosite.baseurl="" "docs/clean" "docs/makeMicrosite"
}

function run_jekyll {
  trap "rm -rf _site" INT TERM EXIT
  jekyll serve -s docs/root/target/jekyll/
}

function publish_site {
  echo "publishing docs"
  sbt "docs/clean" "docs/publishMicrosite"
}

mkdir -p cassandra-docs

in_right_location
parse_inputs $@

if [[ enable_cassandra_2 -gt 0 ]]; then
  setup_cassandra "2.1.20"
  clear_cassandra

  run_cassandra 21
fi

if [[ enable_cassandra_3 -gt 0 ]]; then
  setup_cassandra "3.5"
  clear_cassandra

  run_cassandra 3
fi

if [[ publish -gt 0 ]]; then
  publish_site
else
  compile_results

  if [[ enable_jekyll -gt 0 ]]; then
    run_jekyll
  fi
fi
