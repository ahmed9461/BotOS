#!/usr/bin/env bash
# Reproducible inputs; upstream JSONJava bridge, no opaque third-party binaries.
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
abi="${1:?ABI required}"
mode="${2:---build}"
case "$abi" in arm64-v8a) openssl_target=android-arm64;; x86_64) openssl_target=android-x86_64;; *) echo 'Unsupported ABI' >&2; exit 2;; esac
mapfile -t pins < <(python3 - "$repo/native/dependencies.lock.json" <<'PY'
import json, sys
x=json.load(open(sys.argv[1]))
for s in (x['tdlib']['commit'], x['openssl']['commit'], x['ndk'], str(x['min_sdk'])): print(s)
PY
)
td_commit="${pins[0]}"; ssl_commit="${pins[1]}"; ndk_version="${pins[2]}"; api="${pins[3]}"
work="${RUNNER_TEMP:-/tmp}/botos-native-$abi"
out="$repo/native/output/$abi"
mkdir -p "$work" "$out/metadata"
fetch_source() {
  local path="$1" url="$2" sha="$3"
  if [[ ! -d "$path/.git" ]]; then
    git init -q "$path"
    git -C "$path" fetch --quiet --depth=1 "$url" "$sha"
    git -C "$path" checkout --quiet --detach FETCH_HEAD
  fi
  [[ "$(git -C "$path" rev-parse HEAD)" == "$sha" ]] || { echo 'Source identity mismatch' >&2; exit 1; }
}
fetch_source "$work/td" https://github.com/tdlib/td.git "$td_commit"
cp "$work/td/td/generate/scheme/td_api.tl" "$out/metadata/td_api.tl"
cp "$work/td/example/java/org/drinkless/tdlib/JsonClient.java" "$out/metadata/JsonClient.java"
cp "$work/td/LICENSE_1_0.txt" "$out/metadata/TDLib-LICENSE.txt"
cp "$repo/native/dependencies.lock.json" "$out/metadata/dependencies.lock.json"
[[ "$mode" == '--prepare' ]] && exit 0
[[ "$mode" == '--build' ]] || exit 2
root="${ANDROID_HOME:?Android SDK must be configured}"
export ANDROID_NDK_ROOT="$root/ndk/$ndk_version"
toolchain="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake"
[[ -f "$toolchain" ]] || { echo 'Pinned NDK missing' >&2; exit 1; }
export PATH="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
fetch_source "$work/openssl" https://github.com/openssl/openssl.git "$ssl_commit"
ssl_prefix="$work/openssl-install"
(
 cd "$work/openssl"
 ./Configure "$openssl_target" -D__ANDROID_API__="$api" no-shared no-tests no-module --prefix="$ssl_prefix" --libdir=lib
 make -j2 build_libs
 make install_dev
)
cp "$work/openssl/LICENSE.txt" "$out/metadata/OpenSSL-LICENSE.txt"
cmake -S "$work/td/example/android" -B "$work/host" -GNinja -DTD_ANDROID_JSON_JAVA=ON -DTD_GENERATE_SOURCE_FILES=ON -DCMAKE_BUILD_TYPE=Release
cmake --build "$work/host" --parallel 2
cmake -S "$work/td/example/android" -B "$work/android" -GNinja \
 -DCMAKE_TOOLCHAIN_FILE="$toolchain" -DANDROID_ABI="$abi" -DANDROID_PLATFORM="android-$api" \
 -DANDROID_STL=c++_static -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
 -DOPENSSL_ROOT_DIR="$ssl_prefix" -DOPENSSL_USE_STATIC_LIBS=TRUE \
 -DTD_ANDROID_JSON_JAVA=ON -DCMAKE_BUILD_TYPE=Release \
 '-DCMAKE_CXX_FLAGS_RELEASE=-O2 -DNDEBUG' '-DCMAKE_C_FLAGS_RELEASE=-O2 -DNDEBUG' \
 '-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384'
cmake --build "$work/android" --target tdjni --parallel 2
mkdir -p "$out/jniLibs/$abi"
cp "$work/android/libtdjsonjava.so" "$out/jniLibs/$abi/"
llvm-readelf -lW "$out/jniLibs/$abi/libtdjsonjava.so" > "$out/metadata/elf-headers.txt"
python3 - "$out/metadata/elf-headers.txt" <<'PY'
from pathlib import Path
import sys
loads=[line.split() for line in Path(sys.argv[1]).read_text().splitlines() if line.strip().startswith('LOAD ')]
assert loads and all(int(parts[-1], 16) >= 16384 for parts in loads), '16KB ELF alignment failed'
PY
(cd "$out" && sha256sum jniLibs/*/*.so metadata/td_api.tl metadata/JsonClient.java > SHA256SUMS.txt)
