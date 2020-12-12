#!/bin/bash

# ndk路径
echo $NDK_HOME
# /Users/senrsl/android/android-ndk-r14b
#更新新版NDK，从r14b 到21.3
NDK_HOME=/Users/senrsl/android/sdkas/ndk/21.3.6528147
# 编译工具链目录，ndk17版本以上用的是clang，以下是gcc
TOOLCHAIN=$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64
# 版本号
API=21
# 交叉编译树的根目录(查找相应头文件和库用)
SYSROOT="${TOOLCHAIN}/sysroot"
# SYSROOT=$NDK_HOME/platforms/android-21/arch-arm
# SYSROOT=$NDK_HOME/sysroot

# armv7-a
OUTPUT_FOLDER="armeabi-v7a"
ARCH="arm"
CPU="armv7-a"
TOOL_CPU_NAME=armv7a
TOOL_PREFIX="$TOOLCHAIN/bin/${TOOL_CPU_NAME}-linux-androideabi"
OPTIMIZE_CFLAGS="-march=$CPU"

# # arm64-v8a，这个指令集最低支持api21
# OUTPUT_FOLDER="arm64-v8a"
# ARCH="aarch64"
# CPU="armv8-a"
# TOOL_CPU_NAME=aarch64
# TOOL_PREFIX="$TOOLCHAIN/bin/${TOOL_CPU_NAME}-linux-android"
# OPTIMIZE_CFLAGS="-march=$CPU"

# # x86
# OUTPUT_FOLDER="x86"
# ARCH="x86"
# CPU="x86"
# TOOL_CPU_NAME="i686"
# TOOL_PREFIX="$TOOLCHAIN/bin/${TOOL_CPU_NAME}-linux-android"
# OPTIMIZE_CFLAGS="-march=i686 -mtune=intel -mssse3 -mfpmath=sse -m32"

# 这个执行完竟然没有输出
# # x86_64，这个指令集最低支持api21
# OUTPUT_FOLDER="x86_64"
# ARCH="x86_64"
# CPU="x86_64"
# TOOL_CPU_NAME="x86_64"
# TOOL_PREFIX="$TOOLCHAIN/bin/${TOOL_CPU_NAME}-linux-android"
# OPTIMIZE_CFLAGS="-march=$CPU"

# 输出目录
PREFIX="${PWD}/android/$OUTPUT_FOLDER"
# so的输出目录， --libdir=$LIB_DIR 可以不用指定，默认会生成在$PREFIX/lib目录中
#LIB_DIR="${PWD}/android/libs/$OUTPUT_FOLDER"
# 编译器
CC="${TOOL_PREFIX}${API}-clang" #armv7a-linux-androideabi21-clang
CXX="${TOOL_PREFIX}${API}-clang++"
# CC="$TOOLCHAIN/bin/clang"
# CXX="$TOOLCHAIN/bin/clang++"
# CC="$NDK_HOME/toolchains/arm-linux-androideabi-4.9/prebuilt/darwin-x86_64/bin/arm-linux-androideabi-gcc"
# CXX="$NDK_HOME/toolchains/arm-linux-androideabi-4.9/prebuilt/darwin-x86_64/bin/arm-linux-androideabi-g++"

# 定义执行configure的shell方法
function build_android() {
    ./configure \
        --prefix=$PREFIX \
        --enable-shared \
        --disable-static \
        --enable-jni \
        --disable-doc \
        --disable-programs \
        --disable-symver \
        --target-os=android \
        --arch=$ARCH \
        --cpu=$CPU \
        --cc=$CC \
        --cxx=$CXX \
        --enable-cross-compile \
        --sysroot=$SYSROOT \
        --extra-cflags="-Os -fpic $OPTIMIZE_CFLAGS" \
        --extra-ldflags="" \
        --disable-asm \
        $COMMON_FF_CFG_FLAGS
    make clean
    make -j8
    make install
}
build_android
