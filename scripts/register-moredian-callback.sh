#!/usr/bin/env bash
set -euo pipefail

: "${MOREDIAN_CALLBACK_URL:?Set MOREDIAN_CALLBACK_URL to the public HTTPS callback URL}"
MOREDIAN_CALLBACK_TAGS="${MOREDIAN_CALLBACK_TAGS:-REC_SUCCESS}"

if [[ "${MOREDIAN_CALLBACK_URL}" != https://* ]]; then
  echo "MOREDIAN_CALLBACK_URL must use HTTPS." >&2
  exit 1
fi
if ((${#MOREDIAN_CALLBACK_URL} >= 128)); then
  echo "MOREDIAN_CALLBACK_URL must be shorter than 128 characters." >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

if [[ -z "${MOREDIAN_ACCESS_TOKEN:-}" ]]; then
  read -r -s -p 'Moredian AccessToken: ' MOREDIAN_ACCESS_TOKEN
  echo
fi
if [[ -z "${MOREDIAN_ACCESS_TOKEN}" ]]; then
  echo "Moredian AccessToken is empty." >&2
  exit 1
fi

payload="$(jq -nc \
  --arg callbackUrl "${MOREDIAN_CALLBACK_URL}" \
  --arg callbackTag "${MOREDIAN_CALLBACK_TAGS}" \
  '{callbackUrl:$callbackUrl,callbackTag:$callbackTag}')"
response="$(curl -fsS -X POST \
  "https://toapi.moredian.com/callback/addOrgCallback?accessToken=${MOREDIAN_ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  --data "${payload}")"

printf '%s\n' "${response}" | jq .
printf '%s' "${response}" | jq -e '(.result | tostring) == "0"' >/dev/null || {
  echo "Moredian callback registration failed." >&2
  exit 1
}
echo "Moredian callback registration succeeded."
