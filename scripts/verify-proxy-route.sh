#!/usr/bin/env sh
set -eu

if [ "$#" -ne 6 ]; then
  echo "Usage: $0 NETWORK NGINX_CONTAINER FRONTEND_CONTAINER ALIAS ATTEMPTS INTERVAL_SECONDS" >&2
  exit 64
fi

network_name=$1
nginx_container=$2
frontend_container=$3
alias_name=$4
max_attempts=$5
retry_interval=$6

case "$max_attempts" in
  ''|*[!0-9]*|0) echo "Invalid retry count." >&2; exit 64 ;;
esac
case "$retry_interval" in
  ''|*[!0-9]*) echo "Invalid retry interval." >&2; exit 64 ;;
esac

echo "PROXY_GATE_START alias=$alias_name attempts=$max_attempts interval_seconds=$retry_interval"

proxy_members=$(docker network inspect "$network_name" --format '{{range .Containers}}{{println .Name .IPv4Address}}{{end}}')
if ! printf '%s\n' "$proxy_members" | awk -v expected="$nginx_container" '$1 == expected { found = 1 } END { exit !found }'; then
  echo "PROXY_GATE_FAILED item=network_member alias=$alias_name expected=nginx-member actual=missing" >&2
  exit 1
fi

frontend_name=$(docker inspect "$frontend_container" --format '{{.Name}}')
frontend_name=${frontend_name#/}
expected_ip=$(printf '%s\n' "$proxy_members" | awk -v expected="$frontend_name" '$1 == expected { split($2, address, "/"); print address[1]; exit }')
if [ -z "$expected_ip" ]; then
  echo "PROXY_GATE_FAILED item=target_ip alias=$alias_name expected=target-container-ip actual=missing" >&2
  exit 1
fi

attempt=1
resolved_ip=''
while [ "$attempt" -le "$max_attempts" ]; do
  resolved_ip=$(docker exec "$nginx_container" getent hosts "$alias_name" 2>/dev/null | awk 'NR == 1 { print $1 }' || true)
  [ -n "$resolved_ip" ] && break
  [ "$attempt" -lt "$max_attempts" ] && sleep "$retry_interval"
  attempt=$((attempt + 1))
done
if [ -z "$resolved_ip" ]; then
  echo "PROXY_GATE_FAILED item=dns alias=$alias_name attempts=$max_attempts expected=resolved actual=unresolved" >&2
  exit 1
fi

attempt=1
while [ "$attempt" -le "$max_attempts" ]; do
  resolved_ip=$(docker exec "$nginx_container" getent hosts "$alias_name" 2>/dev/null | awk 'NR == 1 { print $1 }' || true)
  [ "$resolved_ip" = "$expected_ip" ] && break
  [ "$attempt" -lt "$max_attempts" ] && sleep "$retry_interval"
  attempt=$((attempt + 1))
done
if [ "$resolved_ip" != "$expected_ip" ]; then
  echo "PROXY_GATE_FAILED item=target_ip alias=$alias_name attempts=$max_attempts expected=target-container-ip actual=different" >&2
  exit 1
fi

attempt=1
http_status='unavailable'
while [ "$attempt" -le "$max_attempts" ]; do
  http_status=$(docker exec "$nginx_container" curl -fsS -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 10 "http://$alias_name:80/" 2>/dev/null || true)
  [ "$http_status" = 200 ] && break
  [ "$attempt" -lt "$max_attempts" ] && sleep "$retry_interval"
  attempt=$((attempt + 1))
done
if [ "$http_status" != 200 ]; then
  case "$http_status" in
    [0-9][0-9][0-9]) actual_status=$http_status ;;
    *) actual_status=unavailable ;;
  esac
  echo "PROXY_GATE_FAILED item=http alias=$alias_name attempts=$max_attempts expected=200 actual=$actual_status" >&2
  exit 1
fi

echo "PROXY_GATE_SUCCESS alias=$alias_name attempts_limit=$max_attempts"
