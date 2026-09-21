#!/usr/bin/env sh
set -eu

if [ "$#" -ne 3 ]; then
  echo "Usage: $0 TARGET VERSION SHA" >&2
  exit 64
fi

target=$1
version=$2
sha=$3

if ! printf '%s\n' "$version" | grep -Eq '^v[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "Invalid deployment version." >&2
  exit 64
fi
if ! printf '%s\n' "$sha" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "Invalid deployment SHA." >&2
  exit 64
fi

directory=$(dirname -- "$target")
filename=$(basename -- "$target")
expected="$version $sha"
temporary=''

cleanup() {
  if [ -n "$temporary" ] && [ -e "$temporary" ]; then
    rm -f -- "$temporary"
  fi
}
trap cleanup EXIT HUP INT TERM

umask 077
temporary=$(mktemp "$directory/${filename}.tmp.XXXXXX")
printf '%s\n' "$expected" > "$temporary"
chmod 600 "$temporary"
test "$(cat "$temporary")" = "$expected"
mv -f -- "$temporary" "$target"
temporary=''

test "$(cat "$target")" = "$expected"
echo "DEPLOYMENT_VERSION_UPDATED version=$version sha=$sha"
