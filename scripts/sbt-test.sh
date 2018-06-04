#!/bin/bash
set -ue

if [[ ! -f build.sbt || ! -n $(cat build.sbt | grep 'com.github.thurstonsand') ]]; then
  echo "not in root project folder!"
  echo
  exit 1
fi

old_j_version=$(sh ./scripts/util/change_j_version.sh)

function do_test {
  local c_version=$1

  sbt -Dcassandra.version=$c_version +test
}

do_test "2.1"
do_test "3.5"