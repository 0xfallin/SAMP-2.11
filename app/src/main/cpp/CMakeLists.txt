cmake_minimum_required(VERSION 3.12)

include_directories(
        ${CMAKE_CURRENT_LIST_DIR}/opus/include
)

# shadowhook is linked from a vendored prebuilt copy rather than through
# prefab: prefab outputs live in Gradle's artifact-transform cache and can be
# unusable on this host, which made :app:buildCMakeDebug fail intermittently.
add_library(shadowhook SHARED IMPORTED)
set_target_properties(shadowhook PROPERTIES
        IMPORTED_LOCATION "${CMAKE_CURRENT_LIST_DIR}/vendor/shadowhook/lib/${ANDROID_ABI}/libshadowhook.so"
        INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_LIST_DIR}/vendor/shadowhook/include"
)

add_subdirectory(opus)
add_subdirectory(samp)

target_compile_options(opus PRIVATE -O3 -ffunction-sections -fdata-sections)
