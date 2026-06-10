cmake_minimum_required(VERSION 3.20)
project(lumen_native_rt VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)
find_package(Vulkan REQUIRED)

add_library(lumen_native_rt SHARED
    src/lumen_jni_bridge.cpp
    src/lumen_device.cpp
    src/lumen_as.cpp
    src/lumen_rt_pipeline.cpp
    src/lumen_output_image.cpp
    src/lumen_scene.cpp
    src/lumen_postprocess.cpp
)

target_include_directories(lumen_native_rt PRIVATE
    ${JNI_INCLUDE_DIRS}
    src
)

target_link_libraries(lumen_native_rt PRIVATE
    Vulkan::Vulkan
)

set_target_properties(lumen_native_rt PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/build/lib"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/build/lib"
)
