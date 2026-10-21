#!/usr/bin/env bash
#
# 本地构建测试包，产物命名与 CI 一致，拷到 zt-playground。
#
# 默认出三种：arm64-v8a / x86_64 / all（两个 ABI 合一）。
# 走 .pr 通道（包名 net.sumitm.zotiera.pr，仓库内固定测试签名），不能覆盖正式包。
#
#   scripts/build-local.sh                  # 三种都出
#   scripts/build-local.sh arm64-v8a        # 只出一种
#   scripts/build-local.sh --out D:/tmp/apk # 换输出目录
#   scripts/build-local.sh --install        # 出完之后装到已连的设备
#   scripts/build-local.sh -c release       # 正式签名，需要 4 个 ANDROID_* 变量
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

CHANNEL=dev
OUT_DIR="${ZOTIERA_PLAYGROUND:-$ROOT/../zt-playground}"
INSTALL=0
ARCHES=()

usage() {
    sed -n '4,13p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help) usage 0 ;;
        -c|--channel) CHANNEL="$2"; shift 2 ;;
        -o|--out) OUT_DIR="$2"; shift 2 ;;
        -i|--install) INSTALL=1; shift ;;
        arm64-v8a|x86_64|all) ARCHES+=("$1"); shift ;;
        *) echo "未知参数：$1" >&2; usage 1 ;;
    esac
done
[ ${#ARCHES[@]} -gt 0 ] || ARCHES=(arm64-v8a x86_64 all)

# ---- 工具链 ----

# 系统默认 JDK 25 跑 AGP 走不通，优先用 Android Studio 自带的 JBR
for candidate in "${ZOTIERA_JAVA_HOME:-}" "C:/Program Files/Android/Android Studio/jbr"; do
    if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]; then
        export JAVA_HOME="$candidate"
        break
    fi
done

if [ ! -f externals/core/version.h ]; then
    echo "缺 externals/core：先跑 git submodule update --init --recursive" >&2
    exit 1
fi

# ---- 版本号：与 release.yml 的推导保持一致 ----

major=$(sed -n 's/^#define ZEROTIER_ONE_VERSION_MAJOR //p' externals/core/version.h)
minor=$(sed -n 's/^#define ZEROTIER_ONE_VERSION_MINOR //p' externals/core/version.h)
patch=$(sed -n 's/^#define ZEROTIER_ONE_VERSION_REVISION //p' externals/core/version.h)
revision=$(tr -d '[:space:]' < version.txt)
engine="${major}.${minor}.${patch}"
commits=$(git rev-list --count HEAD)
version_code=$(( major * 1000000 + minor * 10000 + patch * 100 + revision ))

case "$CHANNEL" in
    release)
        version_name="${engine}-${revision}"
        task=":app:assembleRelease"
        apk_dir="app/build/outputs/apk/release"
        for name in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
            if [ -z "${!name:-}" ]; then
                echo "release 通道需要环境变量 $name" >&2
                exit 1
            fi
        done
        ;;
    dev|alpha|beta)
        version_name="${engine}-r${commits}"
        task=":app:assemblePr"
        apk_dir="app/build/outputs/apk/pr"
        ;;
    *)
        echo "未知通道：$CHANNEL（dev / alpha / beta / release）" >&2
        exit 1
        ;;
esac

mkdir -p "$OUT_DIR"
log="$ROOT/build/local-build.log"
mkdir -p "$ROOT/build"
git_dirty=""
[ -z "$(git status --porcelain)" ] || git_dirty="（工作区有未提交改动）"

echo "版本 ${version_name} / versionCode ${version_code} / 通道 ${CHANNEL}${git_dirty}"
echo "输出 $OUT_DIR"
echo

built=()

for abi in "${ARCHES[@]}"; do
    if [ "$abi" = all ]; then
        abi_args=()
        apk_abi="all"
    else
        abi_args=(-PzotieraAbi="$abi")
        apk_abi="$abi"
    fi

    # 每次清掉产物目录，靠 find 取到的一定是这一次打的包
    rm -rf "$apk_dir"
    printf '构建 %-10s ... ' "$apk_abi"
    if ! ./gradlew "$task" "${abi_args[@]}" \
            -PzotieraVersionName="$version_name" \
            -PzotieraVersionCode="$version_code" \
            --console=plain > "$log" 2>&1; then
        echo "失败"
        tail -40 "$log" >&2
        exit 1
    fi

    src=$(find "$apk_dir" -name '*.apk' | head -1)
    if [ -z "$src" ]; then
        echo "失败：$apk_dir 里没有 apk"
        exit 1
    fi

    libs=$(unzip -l "$src" | grep -o 'lib/[^/]*/' | sort -u | tr '\n' ' ')
    if [ "$apk_abi" = all ]; then
        want=(arm64-v8a x86_64)
    else
        want=("$apk_abi")
    fi
    for one in "${want[@]}"; do
        if ! printf '%s' "$libs" | grep -q "$one"; then
            echo "失败：包里没有 $one（$libs）"
            exit 1
        fi
    done

    dest="$OUT_DIR/Zotiera-${version_name}-${apk_abi}.apk"
    cp -f "$src" "$dest"
    built+=("$dest")
    echo "ok  $(du -h "$dest" | cut -f1)"
done

echo
for path in "${built[@]}"; do
    printf '  %s\n' "$(basename "$path")"
done

if [ "$INSTALL" = 1 ]; then
    if ! command -v adb > /dev/null 2>&1; then
        echo "没有 adb，跳过安装" >&2
        exit 0
    fi
    for path in "${built[@]}"; do
        echo "安装 $(basename "$path")"
        adb install -r "$path" > /dev/null
    done
fi
