#!/usr/bin/env bash
# Creates the personal key that signs Rain Frog's APK, and records it in
# local.properties (git-ignored) so the build uses it.
#
# Why it matters: Android only installs an UPDATE of an app if it is signed
# with the same key as the installed copy. Keep this key and its password
# for as long as you use the app. Back up both (for example in your
# password manager). If they are lost, the app must be uninstalled and
# installed fresh, which only loses cached weather.
#
# - The password is typed with nothing shown and never goes on a command line.
# - The key file is made readable by your user only, outside the repo.
# - The script refuses to overwrite an existing key.
#
# Run from anywhere:  ./scripts/create-signing-key.sh
set -euo pipefail

cd "$(dirname "$0")/.."
PROPS="local.properties"
KEY_DIR="$HOME/.rain-frog"
KEY_FILE="$KEY_DIR/rain-frog-release.p12"
ALIAS="rainfrog"

if ! git check-ignore -q "$PROPS"; then
  echo "Stopping: $PROPS is not ignored by git, so the password could be committed." >&2
  exit 1
fi
if [[ -e "$KEY_FILE" ]]; then
  echo "A key already exists at $KEY_FILE. Nothing changed (never replace it: updates would stop installing)." >&2
  exit 1
fi
if ! command -v keytool >/dev/null; then
  echo "keytool not found. It comes with the Java JDK used to build the app." >&2
  exit 1
fi

printf 'Choose a password for the signing key (8+ characters, nothing will show): '
IFS= read -rs PW1; echo
printf 'Type it again: '
IFS= read -rs PW2; echo
if [[ "$PW1" != "$PW2" ]]; then echo "The two passwords differ. Nothing changed." >&2; exit 1; fi
if (( ${#PW1} < 8 )); then echo "Use at least 8 characters. Nothing changed." >&2; exit 1; fi
if [[ "$PW1" == *$'\n'* || "$PW1" == *'\'* ]]; then echo "Please avoid backslashes in the password. Nothing changed." >&2; exit 1; fi

umask 077
mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"
RF_PW="$PW1" keytool -genkeypair -noprompt \
  -keystore "$KEY_FILE" -storetype PKCS12 -storepass:env RF_PW -keypass:env RF_PW \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=Rain Frog personal build" >/dev/null
chmod 600 "$KEY_FILE"
echo "Created $KEY_FILE (readable by you only)."

# Record it in local.properties, replacing any older signing lines.
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
if [[ -f "$PROPS" ]]; then
  grep -vE '^signing(StoreFile|StorePassword|KeyAlias)=' "$PROPS" > "$TMP" || true
fi
{
  printf 'signingStoreFile=%s\n' "$KEY_FILE"
  printf 'signingStorePassword=%s\n' "$PW1"
  printf 'signingKeyAlias=%s\n' "$ALIAS"
} >> "$TMP"
chmod 600 "$TMP"
mv "$TMP" "$PROPS"
trap - EXIT
unset PW1 PW2
echo "Saved the key's location and password in $PROPS (readable by you only; ignored by git)."
echo
echo "Now back up two things, for as long as you use the app:"
echo "  1. the file $KEY_FILE"
echo "  2. the password you just typed"
echo "Then tell Claude the key is made."
