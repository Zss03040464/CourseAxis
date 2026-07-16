#!/usr/bin/env bash
set -euo pipefail
set +x
umask 022

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MACOS_DIR="$ROOT/macos"
PROJECT_FILE="$MACOS_DIR/project.yml"

: "${COURSEAXIS_DEVELOPMENT_TEAM:?Set COURSEAXIS_DEVELOPMENT_TEAM to the Apple Developer Team ID.}"
: "${COURSEAXIS_MACOS_SIGNING_IDENTITY:?Set COURSEAXIS_MACOS_SIGNING_IDENTITY to a Developer ID Application identity.}"
: "${COURSEAXIS_NOTARY_PROFILE:?Set COURSEAXIS_NOTARY_PROFILE to a notarytool Keychain profile name.}"

if [[ "$COURSEAXIS_MACOS_SIGNING_IDENTITY" != "Developer ID Application:"* ]]; then
    echo "COURSEAXIS_MACOS_SIGNING_IDENTITY 必须是 Developer ID Application 身份。" >&2
    exit 1
fi

for tool in xcodebuild xcrun codesign spctl ditto shasum; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "缺少发行工具：$tool" >&2
        exit 1
    fi
done

XCODEGEN="${COURSEAXIS_XCODEGEN:-$(command -v xcodegen || true)}"
if [[ -z "$XCODEGEN" || ! -x "$XCODEGEN" ]]; then
    echo "未找到 xcodegen；可显式设置 COURSEAXIS_XCODEGEN。" >&2
    exit 1
fi

VERSION="$(sed -n 's/^[[:space:]]*MARKETING_VERSION: \([^[:space:]]*\)/\1/p' "$PROJECT_FILE" | head -n 1)"
BUILD_NUMBER="$(sed -n 's/^[[:space:]]*CURRENT_PROJECT_VERSION: \([0-9][0-9]*\)/\1/p' "$PROJECT_FILE" | head -n 1)"
if [[ -z "$VERSION" || -z "$BUILD_NUMBER" ]]; then
    echo "无法从 macOS 项目配置读取版本。" >&2
    exit 1
fi

OUTPUT_ROOT="${COURSEAXIS_OUTPUT_DIR:-$ROOT/artifacts}"
OUTPUT_DIR="$OUTPUT_ROOT/macos/$VERSION-$BUILD_NUMBER"
ARCHIVE="$OUTPUT_DIR/CourseAxis.xcarchive"
APP="$ARCHIVE/Products/Applications/CourseAxis.app"
ARTIFACT_NAME="CourseAxis-$VERSION-$BUILD_NUMBER-macos.zip"
ARTIFACT="$OUTPUT_DIR/$ARTIFACT_NAME"
SUBMISSION="$OUTPUT_DIR/.CourseAxis-notarization.zip"
SIGNATURE_INFO="$OUTPUT_DIR/MACOS-SIGNATURE.txt"

mkdir -p "$OUTPUT_DIR"
rm -rf "$ARCHIVE" "$ARTIFACT" "$SUBMISSION"

(cd "$MACOS_DIR" && "$XCODEGEN" generate)

XCODEBUILD_ARGUMENTS=(
    -project "$MACOS_DIR/CourseAxis.xcodeproj"
    -scheme CourseAxis
    -configuration Release
    -destination "generic/platform=macOS"
    -archivePath "$ARCHIVE"
)
if [[ "${COURSEAXIS_ALLOW_PROVISIONING_UPDATES:-0}" == "1" ]]; then
    XCODEBUILD_ARGUMENTS+=("-allowProvisioningUpdates")
fi

xcodebuild \
    "${XCODEBUILD_ARGUMENTS[@]}" \
    DEVELOPMENT_TEAM="$COURSEAXIS_DEVELOPMENT_TEAM" \
    CODE_SIGN_STYLE=Automatic \
    CODE_SIGN_IDENTITY="$COURSEAXIS_MACOS_SIGNING_IDENTITY" \
    archive

if [[ ! -d "$APP" ]]; then
    echo "Archive 中没有 CourseAxis.app：$APP" >&2
    exit 1
fi

codesign --verify --deep --strict --verbose=2 "$APP"
codesign --display --verbose=4 "$APP" 2> "$SIGNATURE_INFO"
if ! grep -Fq "TeamIdentifier=$COURSEAXIS_DEVELOPMENT_TEAM" "$SIGNATURE_INFO"; then
    echo "签名 TeamIdentifier 与 COURSEAXIS_DEVELOPMENT_TEAM 不一致。" >&2
    exit 1
fi
if ! grep -Eq '^flags=.*runtime' "$SIGNATURE_INFO"; then
    echo "主应用签名没有启用 Hardened Runtime。" >&2
    exit 1
fi

ditto -c -k --sequesterRsrc --keepParent "$APP" "$SUBMISSION"
xcrun notarytool submit "$SUBMISSION" \
    --keychain-profile "$COURSEAXIS_NOTARY_PROFILE" \
    --wait
rm -f "$SUBMISSION"

xcrun stapler staple "$APP"
xcrun stapler validate "$APP"
codesign --verify --deep --strict --verbose=2 "$APP"
spctl --assess --type execute --verbose=4 "$APP"

ditto -c -k --sequesterRsrc --keepParent "$APP" "$ARTIFACT"
(cd "$OUTPUT_DIR" && shasum -a 256 "$ARTIFACT_NAME" > "$ARTIFACT_NAME.sha256")

printf 'CourseAxis macOS Release 已签名、公证并生成：\n%s\n' "$ARTIFACT"
