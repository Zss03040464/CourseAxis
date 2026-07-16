#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || -z "$1" ]]; then
    echo "用法：$0 <工具输出目录>" >&2
    exit 2
fi

XCODEGEN_COMMIT="8d3d3476a69ae3e5d68e1adccc701c410c05eb36"
DESTINATION="$1"
SOURCE="$DESTINATION/source"

case "$DESTINATION" in
    /|.|..)
        echo "工具输出目录不能是根目录或当前目录。" >&2
        exit 2
        ;;
esac

if [[ -e "$DESTINATION" ]]; then
    echo "工具输出目录已存在；为避免覆盖现有文件，请使用一个全新目录。" >&2
    exit 2
fi

mkdir -p "$SOURCE"
git -C "$SOURCE" init --quiet
git -C "$SOURCE" remote add origin https://github.com/yonaskolb/XcodeGen.git
git -C "$SOURCE" fetch --quiet --depth=1 origin "$XCODEGEN_COMMIT"
git -C "$SOURCE" checkout --quiet --detach FETCH_HEAD

if [[ "$(git -C "$SOURCE" rev-parse HEAD)" != "$XCODEGEN_COMMIT" ]]; then
    echo "XcodeGen checkout 与固定提交不一致。" >&2
    exit 1
fi

swift build --package-path "$SOURCE" --configuration release
BIN_PATH="$(swift build --package-path "$SOURCE" --configuration release --show-bin-path)"
install -m 0755 "$BIN_PATH/xcodegen" "$DESTINATION/xcodegen"
"$DESTINATION/xcodegen" --version
