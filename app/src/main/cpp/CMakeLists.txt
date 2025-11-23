# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name
project("realtimeedgedetection")

# Set C++ standard to C++11 with RTTI and exceptions enabled
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -frtti -fexceptions")

# Find and link OpenCV
set(OpenCV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../sdk/native/jni")
find_package(OpenCV REQUIRED PATHS "${OpenCV_DIR}" NO_DEFAULT_PATH)

# Include OpenCV header directories
include_directories(${OpenCV_INCLUDE_DIRS})
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Create shared library
add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp
        image_processor.cpp)

# Find system libraries
find_library(jnigraphics-lib jnigraphics)
find_library(log-lib log)

# Link libraries to target
target_link_libraries(${CMAKE_PROJECT_NAME}
        ${jnigraphics-lib}
        EGL
        GLESv2
        android
        ${log-lib}
        ${OpenCV_LIBS})

# Set compiler flags for optimization and debugging
if(CMAKE_BUILD_TYPE MATCHES Release)
    target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE -O2)
else()
    target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE -g)
endif()