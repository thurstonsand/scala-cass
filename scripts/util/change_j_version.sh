#!/bin/bash
set -ue

if [[ ! $(which jenv) ]]; then
  echo "must have jenv installed"
  exit 1
fi

if [[ $(jenv versions | egrep '1.7(\s.*)?$') >/dev/null && $(jenv versions | egrep '1.8(\s.*)?$') >/dev/null ]]; then
  echo 'must have java 7 and 8 installed and configured with jenv as "1.7" and "1.8"'
  exit 1
fi

if [[ $# -eq 0 ]]; then
  jv=($(jenv version))
  echo ${jv[0]}
  exit 0
fi

j_version=$1
if [[ "$j_version" != "1.7" ]] && [[ "$j_version" != "1.8" ]]; then
  echo 'must choose either "1.7" or "1.8"'
  exit 1
fi
old_j_version=$(jenv local) 2>/dev/null
old_j_version=${old_j_version:-$(jenv global)}
j_version=$1
jenv local $j_version
if [[ -z $(java -version 2>&1 | grep $j_version) ]]; then
  echo "java version was not set successfully. Must have jenv installed and working correctly"     $(java -version 2>1 | grep $j_version)
  jenv local $old_j_version
  exit 1
fi
