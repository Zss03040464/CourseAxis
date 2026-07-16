#!/usr/bin/env bash
set -euo pipefail
set +x
umask 022

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ $# -lt 3 || "$1" != "--output" || -z "$2" ]]; then
    echo "用法：$0 --output <目录> <制品> [制品...]" >&2
    exit 2
fi

OUTPUT_DIR="$2"
shift 2
ARTIFACTS=("$@")

VERSION="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)"/\1/p' "$ROOT/android/app/build.gradle.kts" | head -n 1)"
VERSION_CODE="$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\)/\1/p' "$ROOT/android/app/build.gradle.kts" | head -n 1)"
MACOS_VERSION="$(sed -n 's/^[[:space:]]*MARKETING_VERSION: \([^[:space:]]*\)/\1/p' "$ROOT/macos/project.yml" | head -n 1)"
MACOS_BUILD="$(sed -n 's/^[[:space:]]*CURRENT_PROJECT_VERSION: \([0-9][0-9]*\)/\1/p' "$ROOT/macos/project.yml" | head -n 1)"
if [[ -z "$VERSION" || -z "$VERSION_CODE" || -z "$MACOS_VERSION" || -z "$MACOS_BUILD" ]]; then
    echo "无法读取平台版本。" >&2
    exit 1
fi
if [[ "$VERSION" != "$MACOS_VERSION" ]]; then
    echo "Android 与 macOS 版本不一致：$VERSION / $MACOS_VERSION" >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR"
SUMS="$OUTPUT_DIR/SHA256SUMS"
METADATA="$OUTPUT_DIR/RELEASE-METADATA.txt"
: > "$SUMS"

NAMES=()
for artifact in "${ARTIFACTS[@]}"; do
    if [[ ! -s "$artifact" ]]; then
        echo "制品不存在或为空：$artifact" >&2
        exit 1
    fi
    name="$(basename "$artifact")"
    for existing_name in "${NAMES[@]-}"; do
        if [[ "$existing_name" == "$name" ]]; then
            echo "制品文件名重复：$name" >&2
            exit 1
        fi
    done
    NAMES+=("$name")
    digest="$(shasum -a 256 "$artifact" | awk '{print $1}')"
    printf '%s  %s\n' "$digest" "$name" >> "$SUMS"
done

GIT_COMMIT="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || printf 'unavailable')"
GRADLE_VERSION="$(sed -n 's#.*gradle-\([0-9][0-9.]*\)-bin\.zip#\1#p' "$ROOT/android/gradle/wrapper/gradle-wrapper.properties")"
GO_VERSION="$(sed -n 's/^go //p' "$ROOT/server/go.mod")"
XCODE_VERSION="$(xcodebuild -version 2>/dev/null | paste -sd ' ' - || printf 'unavailable')"
SWIFT_VERSION="$(swift --version 2>/dev/null | head -n 1 || printf 'unavailable')"

{
    printf 'CourseAxis version: %s\n' "$VERSION"
    printf 'Android versionCode: %s\n' "$VERSION_CODE"
    printf 'macOS version: %s\n' "$MACOS_VERSION"
    printf 'macOS build: %s\n' "$MACOS_BUILD"
    printf 'Git commit: %s\n' "$GIT_COMMIT"
    printf 'Gradle: %s\n' "$GRADLE_VERSION"
    printf 'Go: %s\n' "$GO_VERSION"
    printf 'Xcode: %s\n' "$XCODE_VERSION"
    printf 'Swift: %s\n' "$SWIFT_VERSION"
    printf 'Artifacts:\n'
    for artifact in "${ARTIFACTS[@]}"; do
        printf -- '- %s\n' "$(basename "$artifact")"
    done
} > "$METADATA"

printf '发行元数据已生成：\n%s\n%s\n' "$SUMS" "$METADATA"
