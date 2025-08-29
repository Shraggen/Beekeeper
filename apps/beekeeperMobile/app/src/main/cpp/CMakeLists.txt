# In apps/beekeeperMobile/app/src/main/cpp/CMakeLists.txt

cmake_minimum_required(VERSION 3.22.1)

project("beekeeper_whisper")

# Add whisper.cpp as a subdirectory. This tells CMake to look for its own CMakeLists.txt
# and build it as a static library we can link against.
add_subdirectory(whisper.cpp)

# Define our JNI bridge library
add_library(
        beekeeper_whisper
        SHARED
        jni_bridge.cpp)

# Find and link the Android logging library
find_library(
        log-lib
        log)

# Link our bridge against whisper and the Android log library
target_link_libraries(
        beekeeper_whisper
        whisper
        ${log-lib})