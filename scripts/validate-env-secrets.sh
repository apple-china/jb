#!/bin/sh
set -eu

secrets_file=${1:-}
if [ -z "$secrets_file" ]; then
  echo 'Secrets file path is required.' >&2
  exit 1
fi
shift

if [ ! -s "$secrets_file" ]; then
  echo 'Secrets file is missing or empty.' >&2
  exit 1
fi
if [ "$(stat -c '%a' "$secrets_file")" != '600' ]; then
  echo 'Secrets file permissions must be 600.' >&2
  exit 1
fi

for key in "$@"; do
  line=$(grep -E "^${key}=" "$secrets_file" | tail -n 1 || true)
  value=${line#*=}
  compact=$(printf '%s' "$value" | tr -d '[:space:]')
  if [ -z "$compact" ] || [ "$compact" = '""' ] || [ "$compact" = "''" ]; then
    echo "Required secret $key is missing or empty." >&2
    exit 1
  fi
done
