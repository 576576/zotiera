cmake_minimum_required(VERSION 3.22)

project(ZeroTierOneJNI C CXX)

# ZeroTier 核心子模块根目录（core/src/main/cpp -> 仓库根 -> externals/core）
get_filename_component(ZT_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../externals/core" ABSOLUTE)

if(NOT EXISTS "${ZT_DIR}/include/ZeroTierOne.h")
    message(FATAL_ERROR "ZeroTier 子模块缺失：${ZT_DIR}（先执行 git submodule update --init --recursive）")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# 目录级 glob：上游 1.16.2 的 java/CMakeLists.txt 写死了三个已删除的源文件，
# 不能复用。这里按目录收集，新增/删除 node 源文件不需要改本文件。
file(GLOB ZT_NODE_SRC CONFIGURE_DEPENDS "${ZT_DIR}/node/*.cpp")

set(ZT_JNI_SRC
    "${ZT_DIR}/java/jni/com_zerotierone_sdk_Node.cpp"
    "${ZT_DIR}/java/jni/ZT_jniutils.cpp"
    "${ZT_DIR}/java/jni/ZT_jnicache.cpp"
)

set(ZT_EXT_SRC
    "${ZT_DIR}/ext/http-parser/http_parser.c"
)

set(ZT_OSDEP_SRC
    "${ZT_DIR}/osdep/OSUtils.cpp"
)

add_library(ZeroTierOneJNI SHARED
    ${ZT_NODE_SRC}
    ${ZT_JNI_SRC}
    ${ZT_EXT_SRC}
    ${ZT_OSDEP_SRC}
)

target_include_directories(ZeroTierOneJNI PRIVATE
    "${ZT_DIR}/include"
    "${ZT_DIR}/node"
    "${ZT_DIR}/ext"
    "${ZT_DIR}/ext/prometheus-cpp-lite-1.0/core/include"
    "${ZT_DIR}/ext/prometheus-cpp-lite-1.0/simpleapi/include"
)

# Android 15+ 要求原生库按 16KB 页对齐
target_link_options(ZeroTierOneJNI PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(ZeroTierOneJNI log)
