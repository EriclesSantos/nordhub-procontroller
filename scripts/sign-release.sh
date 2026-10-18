#!/bin/sh
# Sign an already-built release with a stable private key kept OUTSIDE the repository.
set -eu
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK directory}"
: "${SIGNING_KEYSTORE:?Set SIGNING_KEYSTORE to your private PKCS12 keystore path}"
: "${SIGNING_PASSWORD_FILE:?Set SIGNING_PASSWORD_FILE to a private password file path}"
project=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
tools="$ANDROID_HOME/build-tools/35.0.0"
unsigned="$project/android-app/app/build/outputs/apk/release/app-release-unsigned.apk"
output=${RELEASE_APK_OUTPUT:-"$project/android-app/app/build/outputs/apk/release/switchphone-release.apk"}
if [ ! -r "$unsigned" ]; then
    echo 'Run ./gradlew assembleRelease lintRelease in android-app first.' >&2
    exit 1
fi
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$tools/zipalign" -f 4 "$unsigned" "$work/aligned.apk"
"$tools/apksigner" sign --ks "$SIGNING_KEYSTORE" --ks-key-alias switchphone --ks-type PKCS12 \
    --ks-pass "file:$SIGNING_PASSWORD_FILE" \
    --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
    --out "$output" "$work/aligned.apk"
"$tools/apksigner" verify --verbose "$output"
"$tools/zipalign" -c 4 "$output"
echo "Signed release: $output"
