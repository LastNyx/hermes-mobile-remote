#!/usr/bin/env bash
# Build Hermes Remote. Usage: ./build.sh            -> unit tests + APK (HermesRemote.apk)
#                             ./build.sh install    -> also install over USB (adb)
#                             ./build.sh test       -> unit tests only
#
# Requires JDK 17+ ($JAVA_HOME or `java` on PATH) and the Android SDK (sdk.dir in
# local.properties, or $ANDROID_HOME / $ANDROID_SDK_ROOT).
# With a keystore.properties the APK is release-signed; without one a debug-signed build is
# produced instead, because an unsigned release APK cannot be installed.
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"

die() { echo "error: $*" >&2; exit 1; }

if [ -n "${JAVA_HOME:-}" ]; then
  JAVA="$JAVA_HOME/bin/java"
  [ -x "$JAVA" ] || die "JAVA_HOME=$JAVA_HOME does not contain bin/java. Point it at a JDK 17+."
else
  JAVA="$(command -v java || true)"
  [ -n "$JAVA" ] || die "no JDK found. Install JDK 17+ and set JAVA_HOME or put java on PATH."
fi
JAVA_VERSION="$("$JAVA" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
if [ -z "$JAVA_VERSION" ] || [ "$JAVA_VERSION" -lt 17 ]; then
  die "JDK 17+ required (found ${JAVA_VERSION:-unknown version} at $JAVA)."
fi

SDK=""
if [ -f local.properties ]; then
  SDK="$(sed -n 's/^sdk\.dir=//p' local.properties)"
fi
SDK="${SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
if [ -z "$SDK" ]; then
  die "Android SDK not found. Create android/local.properties containing
  sdk.dir=/path/to/Android/Sdk
or set ANDROID_HOME (or ANDROID_SDK_ROOT)."
fi
export ANDROID_HOME="$SDK"
ADB="$SDK/platform-tools/adb"

case "${1:-}" in
  test) exec ./gradlew -q testDebugUnitTest ;;
esac

# Same condition as hasReleaseKey in app/build.gradle.kts.
if [ -f keystore.properties ] && grep -q '^storeFile=' keystore.properties; then
  ./gradlew -q testDebugUnitTest assembleRelease
  cp app/build/outputs/apk/release/app-release.apk HermesRemote.apk
else
  ./gradlew -q testDebugUnitTest assembleDebug
  cp app/build/outputs/apk/debug/app-debug.apk HermesRemote.apk
  cat <<'EOF'
Note: no release key configured, so this is a debug-signed build.
For a release-signed APK, create android/keystore.properties with:
  storeFile=/path/to/release.jks
  storePassword=...
  keyAlias=...
  keyPassword=...
EOF
fi
echo "Built $(pwd)/HermesRemote.apk ($(du -h HermesRemote.apk | cut -f1))"

if [ "${1:-}" = "install" ]; then
  "$ADB" install -r HermesRemote.apk
fi
