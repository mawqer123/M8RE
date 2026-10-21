#!/bin/bash
# M8RE Android 移植版 —— 手工调用 aapt2/javac/d8/apksigner 构建。
# 完全不依赖 Gradle：离线可用，几秒出包。
set -e

# 脚本自身所在目录(可在任意位置 clone 后直接构建)
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Android SDK 定位顺序：环境变量 > local.properties > 常见默认路径
if [ -n "$ANDROID_HOME" ]; then
  SDK=$ANDROID_HOME
elif [ -n "$ANDROID_SDK_ROOT" ]; then
  SDK=$ANDROID_SDK_ROOT
elif [ -f "$ROOT/local.properties" ]; then
  SDK=$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | head -1)
else
  SDK=/usr/lib/android-sdk
fi
if [ ! -d "$SDK" ]; then
  echo "!! 找不到 Android SDK,请设置 ANDROID_HOME 或写 local.properties 的 sdk.dir"; exit 1
fi

# build-tools:取版本号最高的一个(必须含 aapt2,跳过 debian/ 之类的非版本目录)
BT=""
for d in $(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V); do
  v=$(basename "$d")
  case "$v" in *[!0-9.]*) continue;; esac   # 只接受形如 35.0.0 的版本目录
  [ -x "${d}aapt2" ] && BT=${d%/}
done
if [ -z "$BT" ]; then
  echo "!! $SDK/build-tools 下没有可用的 aapt2"; exit 1
fi

# 平台版本：优先 android-35，没有就取版本号最高的可用平台
PLATFORM=$SDK/platforms/android-35/android.jar
if [ ! -f "$PLATFORM" ]; then
  PLATFORM=""
  for d in $(ls -d "$SDK"/platforms/android-*/ 2>/dev/null | sort -V); do
    v=$(basename "$d"); v=${v#android-}
    case "$v" in *[!0-9.]*) continue;; esac
    [ -f "${d}android.jar" ] && PLATFORM=${d%/}/android.jar
  done
fi
if [ -z "$PLATFORM" ] || [ ! -f "$PLATFORM" ]; then
  echo "!! 找不到 android.jar,请安装 platform"; exit 1
fi

# 版本号:只在这里定义,其余位置一律引用,避免多处漂移
APP_VERSION_NAME="1.0"
APP_VERSION_CODE=1

APP=$ROOT/app/src/main
BUILD=$ROOT/build
OUT=$ROOT/out

# ------------------------------------------------------------------
# 引擎同步：本 fork 的上游扩展源码在仓库根的 content/（git 里只存一份）。
# assets/m8re/ 只存放移植层自己写的文件；每次构建前把 content/*.js 复制过去，
# 保证 Android 版永远和同一棵树的引擎一致，不会出现两份互相分歧的副本。
# ------------------------------------------------------------------
UPSTREAM_CONTENT="$ROOT/../content"
ASSETS_M8RE="$APP/assets/m8re"
if [ -d "$UPSTREAM_CONTENT" ]; then
  echo "==> [0/6] 从上游 content/ 同步引擎"
  ENGINE="00_ns 01_scanner 02_expr 03_codegen 04_parser 05_vm 06_tween 07_bytearray 08_graphics 09_elements 10_motion 11_hostapi 12_protobuf 13_fetcher 14_main"
  for f in $ENGINE; do
    if [ ! -f "$UPSTREAM_CONTENT/$f.js" ]; then
      echo "!! 上游缺少 content/$f.js"; exit 1
    fi
    cp "$UPSTREAM_CONTENT/$f.js" "$ASSETS_M8RE/$f.js"
  done
  echo "    已同步 15 个引擎文件"
else
  echo "==> [0/6] 未找到 $UPSTREAM_CONTENT,使用 assets 内现有引擎副本"
fi

rm -rf "$BUILD" "$OUT"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$OUT"

echo "==> [1/6] aapt2 编译资源"
"$BT/aapt2" compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "==> [2/6] aapt2 链接（生成 R.java 与基础 APK）"
"$BT/aapt2" link \
  -o "$BUILD/base.apk" \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code "$APP_VERSION_CODE" --version-name "$APP_VERSION_NAME" \
  -A "$APP/assets" \
  "$BUILD/res.zip"

echo "==> [2.5/6] 校验注入分块:每块必须能单独通过 node --check"
python3 "$ROOT/tools/check_chunks.py" || { echo "!! 分块语法检查失败,拒绝打包"; exit 1; }

echo "==> [3/6] javac 编译"
find "$APP/java" "$BUILD/gen" -name "*.java" > "$BUILD/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$PLATFORM" \
  -classpath "$PLATFORM:$BT/core-lambda-stubs.jar" \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt" 2>&1 | grep -v "bootstrap class path" || true

echo "==> [4/6] d8 生成 dex"
"$BT/d8" --min-api 24 --lib "$PLATFORM" \
  --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name "*.class")

echo "==> [5/6] 将 classes.dex 打包进 APK"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
(cd "$BUILD/dex" && "$BT/aapt" add "$BUILD/unsigned.apk" classes.dex >/dev/null) 2>/dev/null || \
  (cd "$BUILD/dex" && zip -q -j "$BUILD/unsigned.apk" classes.dex)

echo "==> [6/6] zipalign 对齐并签名"
# 签名:优先用你自备的密钥(自己填路径与密码),没有才回退到本地 debug 密钥。
#
# 自己签名(发布用):
#   export M8_KEYSTORE=/path/to/your.jks
#   export M8_KS_PASS='你的密钥库密码'
#   export M8_KEY_ALIAS='你的密钥别名'
#   export M8_KEY_PASS='你的密钥密码'
#   ./build.sh
#
# 不设置这些变量时，回退到本地 debug.keystore（仅限个人本地测试；
# 别人拿到源码也能用它签,所以发布版请务必用你自己的密钥重签)。
KEYSTORE=${M8_KEYSTORE:-$ROOT/debug.keystore}
KS_PASS=${M8_KS_PASS:-}
KEY_ALIAS=${M8_KEY_ALIAS:-}
KEY_PASS=${M8_KEY_PASS:-}

if [ -n "${M8_KEYSTORE:-}" ]; then
  if [ -z "$KS_PASS" ] || [ -z "$KEY_ALIAS" ]; then
    echo "!! M8_KEYSTORE 已设置,但 M8_KS_PASS / M8_KEY_ALIAS 未提供"; exit 1
  fi
else
  # 本地测试密钥:只含 CN,不含任何组织/公司字段
  if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -alias m8re -keyalg RSA \
      -keysize 2048 -validity 10000 -storepass android -keypass android \
      -dname "CN=m8re-debug" >/dev/null 2>&1
  fi
  KS_PASS=android
  KEY_ALIAS=m8re
  KEY_PASS=android
fi

"$BT/zipalign" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" \
  --ks-key-alias "$KEY_ALIAS" --key-pass "pass:${KEY_PASS:-$KS_PASS}" \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$OUT/M8RE-$APP_VERSION_NAME.apk" "$BUILD/aligned.apk"
"$BT/apksigner" verify --print-certs "$OUT/M8RE-$APP_VERSION_NAME.apk" | head -3

echo
echo "==> BUILD OK: $OUT/M8RE-$APP_VERSION_NAME.apk"
ls -la "$OUT/M8RE-$APP_VERSION_NAME.apk"
