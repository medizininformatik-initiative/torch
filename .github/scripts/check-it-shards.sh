#!/usr/bin/env bash
# Checks that the integration-tests shards in build.yml cover every integration test.
#
# Reads the shard patterns from the integration-tests matrix, so there is only one list to
# maintain. Every IT must match exactly one shard, every shard must match at least one IT,
# and no BlackBoxIT may match a shard (-Dit.test overrides the Failsafe <excludes>, so a
# matching BlackBoxIT would run there). Run from the repository root; needs yq.
set -euo pipefail

mapfile -t shards < <(yq '.jobs["integration-tests"].strategy.matrix.include[] | .shard + "=" + .pattern' .github/workflows/build.yml)
if (( ${#shards[@]} == 0 )); then
  echo "::error::could not read shards from the integration-tests matrix"
  exit 1
fi
to_regex() {
  local p=$1
  [[ $p == */* ]] || p="**/$p"
  p=${p//\*\*\//@}
  p=${p//\*/[^/]*}
  p=${p//@/(.*/)?}
  echo "^$p\$"
}
declare -A count
for shard in "${shards[@]}"; do count[${shard%%=*}]=0; done
failed=0
while read -r file; do
  class=${file#src/test/java/}
  class=${class%.java}
  hits=()
  for shard in "${shards[@]}"; do
    IFS=, read -ra patterns <<< "${shard#*=}"
    for pattern in "${patterns[@]}"; do
      if [[ $class =~ $(to_regex "$pattern") ]]; then
        hits+=("${shard%%=*}")
        break
      fi
    done
  done
  for hit in "${hits[@]}"; do count[$hit]=$(( count[$hit] + 1 )); done
  expected=1
  [[ $file == *BlackBoxIT.java ]] && expected=0
  if (( ${#hits[@]} != expected )); then
    echo "::error file=$file::$class matches ${#hits[@]} integration-tests shards (${hits[*]:-none}), expected $expected"
    failed=1
  elif (( expected == 1 )); then
    echo "covered: $class (${hits[0]})"
  fi
done < <(find src/test/java \( -name '*IT.java' -o -name 'IT*.java' -o -name '*ITCase.java' \) | sort)
for shard in "${!count[@]}"; do
  if (( count[$shard] == 0 )); then
    echo "::error::integration-tests shard '$shard' matches no integration tests"
    failed=1
  fi
done
exit $failed
