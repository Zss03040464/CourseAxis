#!/usr/bin/env bash
set -euo pipefail
set +x
umask 022

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/android"
BUILD_FILE="$ANDROID_DIR/app/build.gradle.kts"

: "${COURSEAXIS_KEYSTORE_FILE:?Set COURSEAXIS_KEYSTORE_FILE to the absolute path of the offline release JKS.}"
: "${COURSEAXIS_KEYSTORE_PASSWORD:?Set COURSEAXIS_KEYSTORE_PASSWORD.}"
: "${COURSEAXIS_KEY_ALIAS:?Set COURSEAXIS_KEY_ALIAS.}"
: "${COURSEAXIS_KEY_PASSWORD:?Set COURSEAXIS_KEY_PASSWORD.}"

JAVA_HOME="${COURSEAXIS_JAVA_HOME:-${JAVA_HOME:-}}"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
: "${JAVA_HOME:?Set COURSEAXIS_JAVA_HOME or JAVA_HOME to JDK 17.}"
: "${ANDROID_HOME:?Set ANDROID_HOME or ANDROID_SDK_ROOT.}"

if [[ "$COURSEAXIS_KEYSTORE_FILE" != /* ]]; then
    echo "COURSEAXIS_KEYSTORE_FILE 必须是源码树外的绝对路径。" >&2
    exit 1
fi
if [[ "$COURSEAXIS_KEYSTORE_FILE" == "$ROOT"/* ]]; then
    echo "签名文件必须位于 CourseAxis 源码树之外。" >&2
    exit 1
fi
if [[ ! -s "$COURSEAXIS_KEYSTORE_FILE" ]]; then
    echo "签名文件不存在或为空：$COURSEAXIS_KEYSTORE_FILE" >&2
    exit 1
fi
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "未找到可执行的 JDK：$JAVA_HOME" >&2
    exit 1
fi
if [[ ! -d "$ANDROID_HOME/platforms/android-36" ]]; then
    echo "未找到 Android SDK Platform 36：$ANDROID_HOME" >&2
    exit 1
fi

VERSION="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)"/\1/p' "$BUILD_FILE" | head -n 1)"
VERSION_CODE="$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\)/\1/p' "$BUILD_FILE" | head -n 1)"
if [[ -z "$VERSION" || -z "$VERSION_CODE" ]]; then
    echo "无法从 Android 构建配置读取版本。" >&2
    exit 1
fi

OUTPUT_ROOT="${COURSEAXIS_OUTPUT_DIR:-$ROOT/artifacts}"
ARCHIVE_DIR="$OUTPUT_ROOT/android/$VERSION-$VERSION_CODE"
ARTIFACT_NAME="CourseAxis-$VERSION-$VERSION_CODE-release.apk"
ARTIFACT="$ARCHIVE_DIR/$ARTIFACT_NAME"

export JAVA_HOME ANDROID_HOME
cd "$ANDROID_DIR"
./gradlew \
    --no-daemon \
    --dependency-verification=strict \
    :app:testDebugUnitTest \
    :app:lintRelease \
    :app:assembleRelease \
    --console=plain

APK="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"
if [[ ! -s "$APK" ]]; then
    echo "Release APK 未生成：$APK" >&2
    exit 1
fi
unset COURSEAXIS_KEYSTORE_PASSWORD COURSEAXIS_KEY_PASSWORD

APKSIGNER="${COURSEAXIS_APKSIGNER:-}"
if [[ -z "$APKSIGNER" ]]; then
    APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner -perm -111 -print 2>/dev/null | sort -V | tail -n 1)"
fi
if [[ -z "$APKSIGNER" || ! -x "$APKSIGNER" ]]; then
    echo "未找到 apksigner；可显式设置 COURSEAXIS_APKSIGNER。" >&2
    exit 1
fi

mkdir -p "$ARCHIVE_DIR"
install -m 0644 "$APK" "$ARTIFACT"
CERTIFICATE_OUTPUT="$("$APKSIGNER" verify --verbose --print-certs "$ARTIFACT")"
CERTIFICATE_SHA256="$(printf '%s\n' "$CERTIFICATE_OUTPUT" | sed -n 's/^Signer #[0-9][0-9]* certificate SHA-256 digest: //p' | head -n 1)"
if [[ -z "$CERTIFICATE_SHA256" ]]; then
    echo "apksigner 未返回签名证书 SHA-256。" >&2
    exit 1
fi
printf 'Android signing certificate SHA-256: %s\n' "$CERTIFICATE_SHA256" \
    | tee "$ARCHIVE_DIR/ANDROID-SIGNING-CERTIFICATE.txt"
(cd "$ARCHIVE_DIR" && shasum -a 256 "$ARTIFACT_NAME" > "$ARTIFACT_NAME.sha256")

printf 'CourseAxis Android Release 已生成：\n%s\n' "$ARTIFACT"
