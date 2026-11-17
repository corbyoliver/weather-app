#!/usr/bin/env bash
# Saves your Pirate Weather API key in local.properties, which git ignores.
#
# - The key is typed (or pasted) with nothing shown on screen.
# - It never goes on a command line, so it does not land in shell history
#   or in the process list.
# - local.properties is made readable by your user only.
# - One test call checks the key works; only the HTTP status is printed.
#
# Run from anywhere:  ./scripts/set-pirate-key.sh
set -euo pipefail

cd "$(dirname "$0")/.."
PROPS="local.properties"

if ! git check-ignore -q "$PROPS"; then
  echo "Stopping: $PROPS is not ignored by git, so the key could be committed." >&2
  exit 1
fi

printf 'Paste your Pirate Weather API key, then press Return (nothing will show): '
IFS= read -rs KEY
echo
# Trim spaces and stray line endings from the paste.
KEY="$(printf '%s' "$KEY" | tr -d '[:space:]')"

if [[ -z "$KEY" ]]; then
  echo "No key entered. Nothing changed." >&2
  exit 1
fi
if [[ ! "$KEY" =~ ^[A-Za-z0-9_-]{10,200}$ ]]; then
  echo "That does not look like an API key (expected letters, digits, - or _). Nothing changed." >&2
  exit 1
fi

# Write a new copy without any old key line, then add the new one.
umask 077
# Temp file in your private temp folder, never in the repo.
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
if [[ -f "$PROPS" ]]; then
  grep -v '^pirateWeatherApiKey=' "$PROPS" > "$TMP" || true
fi
printf 'pirateWeatherApiKey=%s\n' "$KEY" >> "$TMP"
chmod 600 "$TMP"
mv "$TMP" "$PROPS"
trap - EXIT
echo "Saved to $PROPS (readable by you only; ignored by git)."

# One test call. The URL goes to curl on stdin, never on the command line.
echo "Testing the key with one call to Pirate Weather…"
STATUS="$(printf 'url = "https://api.pirateweather.net/forecast/%s/45.8901,-122.8071?units=us&exclude=currently,hourly,daily,alerts"\n' "$KEY" \
  | curl -sS -m 30 -o /dev/null -w '%{http_code}' -K - 2>/dev/null || echo "000")"
case "$STATUS" in
  200) echo "Key works (HTTP 200). Tell Claude the key is in." ;;
  401) echo "Pirate Weather says the key is not accepted yet (HTTP 401). A new key can take up to 20 minutes to start working. The key is saved; try again later." ;;
  429) echo "Pirate Weather says this key's monthly limit is used up (HTTP 429). The key is saved." ;;
  000) echo "Could not reach Pirate Weather (network problem). The key is saved." ;;
  *)   echo "Pirate Weather answered HTTP $STATUS. The key is saved." ;;
esac
