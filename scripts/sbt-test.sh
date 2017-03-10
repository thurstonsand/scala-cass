#!/bin/bash
set -ue

if [[ ! -f build.sbt || ! -n $(cat build.sbt | grep 'com.github.thurstonsand') ]]; then
  echo "not in root project folder!"
  echo
  exit 1
fi

old_j_version=$(sh ./scripts/util/change_j_version.sh)

function do_test {
	local j_version=$1
  sh ./scripts/util/change_j_version.sh $j_version

	sbt +test
}

do_test "1.7"
do_test "1.8"
sh ./scripts.util/change_j_version.sh $old_j_version
