cmake_minimum_required(VERSION 3.22.1)
project(sklauncher LANGUAGES C CXX)

# skgfx.c / skgfx.h come from the external/skgfx submodule (SKonstruct/skgfx).
get_filename_component(SKGFX_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../external/skgfx/mobile" ABSOLUTE)

add_library(sklauncher SHARED
    sklauncher.c
    ${SKGFX_DIR}/skgfx.c
)
target_include_directories(sklauncher PRIVATE ${SKGFX_DIR})

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(glesv2-lib GLESv2)

target_link_libraries(sklauncher
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${glesv2-lib}
)

add_executable(xdgopen xdgopen.c)

set_target_properties(xdgopen PROPERTIES
    PREFIX ""
    SUFFIX ""
    OUTPUT_NAME "libxdgopen.so"
)
