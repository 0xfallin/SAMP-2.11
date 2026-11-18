cmake_minimum_required(VERSION 3.12)
project(samp)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# SAMP_OPTIMIZE: force -O3 even for Debug builds. The NDK toolchain adds -O0 for
# Debug *after* CMAKE_CXX_FLAGS, so the old "-O3" in CMAKE_CXX_FLAGS was ignored
# whenever the app was built with assembleDebug (native code ran unoptimised).
option(SAMP_OPTIMIZE "Always compile with -O3" ON)
# SAMP_LTO: ThinLTO. Smaller/faster .so but much slower and heavier to link, so
# it is OFF by default (building inside Termux/proot can run out of RAM).
option(SAMP_LTO "Enable ThinLTO" OFF)

if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    add_definitions(-DVER_x32=true)
    set(GLOSSHOOK_ABI "ARM")
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_definitions(-DVER_x32=false)
    set(GLOSSHOOK_ABI "ARM64")
endif()

# One recursive glob instead of ten hand-written depth patterns. Same file set
# (*.c and *.cpp); CONFIGURE_DEPENDS re-globs when files are added or removed.
file(GLOB_RECURSE SOURCES CONFIGURE_DEPENDS
        ${CMAKE_CURRENT_LIST_DIR}/*.c
        ${CMAKE_CURRENT_LIST_DIR}/*.cpp
)

include_directories(
        ${CMAKE_CURRENT_LIST_DIR}
        ${CMAKE_CURRENT_LIST_DIR}/util/
        ${CMAKE_CURRENT_LIST_DIR}/vendor/armhook
        ${CMAKE_CURRENT_LIST_DIR}/vendor/imgui
        ${CMAKE_CURRENT_LIST_DIR}/vendor/
        ${CMAKE_CURRENT_LIST_DIR}/game/RW/
        ${CMAKE_CURRENT_LIST_DIR}/game/Core/
)

add_library(samp SHARED ${SOURCES})
target_link_libraries(samp
        log
        android
        EGL
        GLESv3
        opus
        shadowhook
        ${CMAKE_CURRENT_SOURCE_DIR}/vendor/bass/libs/${ANDROID_ABI}/libbass.so
        ${CMAKE_CURRENT_SOURCE_DIR}/vendor/bass/libs/${ANDROID_ABI}/libbass_ssl.so
        ${CMAKE_CURRENT_SOURCE_DIR}/vendor/GlossHook/libs/${GLOSSHOOK_ABI}/libGlossHook.a
        ${CMAKE_CURRENT_SOURCE_DIR}/vendor/GlossHook/libs/${GLOSSHOOK_ABI}/libGlossHook.so
)

# -w silences warnings (the old "-Wall" was cancelled by it anyway); "-g" and
# "-s" are gone from the global flags: -g only bloated the objects.
target_compile_options(samp PRIVATE
        -w
        -pthread
        -fexceptions
        -ffunction-sections
        -fdata-sections
        $<$<CONFIG:Debug>:-fvisibility=default>
        $<$<CONFIG:Release>:-fvisibility=hidden>
        $<$<CONFIG:Release>:-fvisibility-inlines-hidden>
        $<$<CONFIG:Release>:-DNDEBUG>
)

if(SAMP_OPTIMIZE)
    # Target options come after CMAKE_CXX_FLAGS_<CONFIG>, so this wins over -O0.
    # The stack protector is dropped: it costs a canary check on every function
    # with a local array, on hot render/network paths.
    target_compile_options(samp PRIVATE -O3 -fno-stack-protector)
endif()

if(SAMP_LTO)
    target_compile_options(samp PRIVATE -flto=thin)
    target_link_options(samp PRIVATE -flto=thin)
endif()

target_link_options(samp PRIVATE
        -Wl,-z,max-page-size=16384
        -Wl,--gc-sections
        -Wl,--icf=safe
        -Wl,--build-id=none
        $<$<CONFIG:Release>:-Wl,--strip-all>
)
