#!/bin/sh
# Builds TidyTV Home (our own launcher, launcher/), signs it with the TidyTV release key and copies it into the
# app as assets/launcher/tidytv-home.apk. Fails if the APK is 1 MB or more.
set -e
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
./gradlew -q :launcher:assembleRelease --console=plain
APK="$(ls launcher/build/outputs/apk/release/*.apk | head -1)"
OUT=app/src/main/assets/launcher/tidytv-home.apk
WORK="$(mktemp -d)"
"$BT/zipalign" -f -p 4 "$APK" "$WORK/aligned.apk"
if [ -f keys/signing.properties ]; then
  storePassword=$(sed -n 's/^storePassword=//p' keys/signing.properties)
  keyAlias=$(sed -n 's/^keyAlias=//p' keys/signing.properties)
  keyPassword=$(sed -n 's/^keyPassword=//p' keys/signing.properties)
  "$BT/apksigner" sign --ks keys/tidytv-release.jks --ks-key-alias "$keyAlias" \
    --ks-pass "pass:$storePassword" --key-pass "pass:$keyPassword" --out "$OUT" "$WORK/aligned.apk"
else
  echo "keys/signing.properties missing: signing with the debug key" >&2
  "$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$OUT" "$WORK/aligned.apk"
fi
"$BT/apksigner" verify "$OUT"
SIZE=$(wc -c < "$OUT" | tr -d ' ')
echo "tidytv-home.apk: $SIZE bytes"
rm -rf "$WORK"
if [ "$SIZE" -ge 1048576 ]; then echo "FAIL: launcher must stay under 1 MB" >&2; rm -f "$OUT"; exit 1; fi
