#!/bin/bash
# NCM Lite 构建：普通 APK（不需要 Xposed）
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
BT="$SDK/build-tools/${BUILD_TOOLS_VERSION:-34.0.0}"
AJ="$SDK/platforms/${PLATFORM_VERSION:-android-34}/android.jar"
OUT="$ROOT/build"
KS="$ROOT/lite.jks"
PASS="ncm123456"
NAME="${1:-NCMLite-v1.0}"

AAPT2="${AAPT2:-$(command -v aapt2 || echo "$BT/aapt2")}"
ZIPALIGN="${ZIPALIGN:-$(command -v zipalign || echo "$BT/zipalign")}"
APKSIGNER="${APKSIGNER:-$(command -v apksigner || echo "$BT/apksigner")}"
D8="$BT/d8"

echo ">>> 清理"
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex"

echo ">>> javac"
find "$ROOT/src" -name "*.java" > "$OUT/sources.txt"
javac -encoding UTF-8 --release 8 -nowarn \
      -cp "$AJ" -d "$OUT/classes" @"$OUT/sources.txt"

echo ">>> d8"
(cd "$OUT/classes" && jar cf "$OUT/app.jar" .)
"$D8" --release --min-api 26 --lib "$AJ" --output "$OUT/dex" "$OUT/app.jar"

echo ">>> aapt2"
"$AAPT2" link -o "$OUT/base.apk" -I "$AJ" --manifest "$ROOT/AndroidManifest.xml"

echo ">>> 打包 dex"
python3 - "$OUT" <<'PY'
import zipfile, shutil, sys, os
out = sys.argv[1]
shutil.copy(os.path.join(out,'base.apk'), os.path.join(out,'unsigned.apk'))
with zipfile.ZipFile(os.path.join(out,'unsigned.apk'),'a',zipfile.ZIP_DEFLATED) as z:
    z.write(os.path.join(out,'dex','classes.dex'),'classes.dex')
print('  条目:', zipfile.ZipFile(os.path.join(out,'unsigned.apk')).namelist())
PY

echo ">>> 签名"
"$ZIPALIGN" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias lite -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$PASS" -keypass "$PASS" \
    -dname "CN=NCM Lite, OU=Whale, O=Sea, C=CN" >/dev/null 2>&1
fi
"$APKSIGNER" sign --ks "$KS" --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$ROOT/$NAME.apk" "$OUT/aligned.apk"

echo ">>> 完成：$ROOT/$NAME.apk"
ls -lh "$ROOT/$NAME.apk"
