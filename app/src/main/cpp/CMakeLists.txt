# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)

# Declares and names the project.

project("livepushmine")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

# 添加 define  -DNO_CRYPTO，在c文件可使用，
#1. CMAKE_C_FLAGS介绍：https://cloud.tencent.com/developer/article/1433578
#2. define介绍：https://blog.csdn.net/chouhuan1877/article/details/100808689
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DNO_CRYPTO")
#或者使用 add_definitions(-DTEST_DEBUG)，这样在cxx_flags，c_flags都有

AUX_SOURCE_DIRECTORY(${CMAKE_SOURCE_DIR} SRC_LIST)
AUX_SOURCE_DIRECTORY(${CMAKE_SOURCE_DIR}/librtmp RTMP_LIST)

add_library( # Sets the name of the library.
             live-push

             # Sets the library as a shared library.
             SHARED

             ${RTMP_LIST}

             ${SRC_LIST}

             # Provides a relative path to your source file(s).
            native-lib.cpp
            DZPacketQueue.cpp
            DZJNICall.cpp
            DZLivePush.cpp
            )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        live-push
        # 链接 android ndk 自带的一些库
        android
        # included in the NDK.
        ${log-lib} )