#!/bin/bash
# 手动构建 APK（不依赖 Gradle）：aapt2 -> javac -> d8 -> zipalign -> apksigner
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
APP_ID="com.youyoudezhuzhu.quizsearch"
VERSION_NAME="${VERSION_NAME:-1.0.2}"
VERSION_CODE="${VERSION_CODE:-3}"
OUT="build/quiz-search-${VERSION_NAME}.apk"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$PLATFORM"; do
  [ -e "$f" ] || { echo "缺少 SDK 组件: $f" >&2; exit 2; }
done

rm -rf build/compiled build/gen build/classes build/dex build/app-unsigned.apk build/app-dex.apk build/app-aligned.apk
mkdir -p build/compiled build/gen build/classes build/dex

echo "==> 编译资源"
"$BT/aapt2" compile --dir res -o build/compiled/res.zip
"$BT/aapt2" link \
  -o build/app-unsigned.apk \
  -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  -A assets \
  --java build/gen \
  --min-sdk-version 26 \
  --target-sdk-version 33 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  build/compiled/res.zip

echo "==> 编译 Java"
javac --release 8 -encoding UTF-8 -classpath "$PLATFORM" -d build/classes \
  $(find src build/gen -name '*.java')

echo "==> dex"
"$BT/d8" --release --lib "$PLATFORM" --min-api 26 --output build/dex \
  $(find build/classes -name '*.class')

cp build/app-unsigned.apk build/app-dex.apk
(cd build/dex && zip -q ../app-dex.apk classes.dex)

echo "==> 对齐/签名"
"$BT/zipalign" -f 4 build/app-dex.apk build/app-aligned.apk
if [ ! -f build/quiz-search.keystore ]; then
  keytool -genkeypair -v \
    -keystore build/quiz-search.keystore \
    -storepass quizsearch -keypass quizsearch \
    -alias quizsearch -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=AnGui SouTi, OU=Dev, O=youyoudezhuzhu, L=CN, C=CN" >/dev/null 2>&1
fi
"$BT/apksigner" sign \
  --ks build/quiz-search.keystore --ks-key-alias quizsearch \
  --ks-pass pass:quizsearch --key-pass pass:quizsearch \
  --out "$OUT" build/app-aligned.apk
"$BT/apksigner" verify --print-certs "$OUT" | head -3
echo "==> 完成: $OUT ($(du -h "$OUT" | cut -f1))"
