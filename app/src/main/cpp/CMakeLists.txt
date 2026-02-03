cmake_minimum_required(VERSION 3.4.1)

# C/C++ standards
set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 17)

# Define library version for Opus
set(OPUS_LIBRARY_VERSION "1.3.1")
set(OPUS_LIBRARY_VERSION_MAJOR "1")
set(PACKAGE_VERSION "1.3.1")

# Configure Opus options
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "Build programs" FORCE)
set(OPUS_BUILD_TESTING OFF CACHE BOOL "Build tests" FORCE)
set(OPUS_CUSTOM_MODES OFF CACHE BOOL "Disable custom modes" FORCE)
set(OPUS_USE_NEON OFF CACHE BOOL "Disable NEON" FORCE)

# Add opus subdirectory
add_subdirectory(opus-1.3.1)

# ============================================================================
# libfvad (WebRTC VAD) - Voice Activity Detection
# ============================================================================
set(LIBFVAD_DIR ${CMAKE_SOURCE_DIR}/libfvad)

set(LIBFVAD_SOURCES
    ${LIBFVAD_DIR}/src/fvad.c
    ${LIBFVAD_DIR}/src/vad/vad_core.c
    ${LIBFVAD_DIR}/src/vad/vad_filterbank.c
    ${LIBFVAD_DIR}/src/vad/vad_gmm.c
    ${LIBFVAD_DIR}/src/vad/vad_sp.c
    ${LIBFVAD_DIR}/src/signal_processing/division_operations.c
    ${LIBFVAD_DIR}/src/signal_processing/energy.c
    ${LIBFVAD_DIR}/src/signal_processing/resample_48khz.c
)

# Build static library for libfvad
add_library(fvad STATIC ${LIBFVAD_SOURCES})

target_include_directories(fvad PUBLIC
    ${LIBFVAD_DIR}/include
    ${LIBFVAD_DIR}/src
)

# ============================================================================
# Main JNI Library (Opus + VAD)
# ============================================================================
add_library(app SHARED
            opus_decoder.cpp
            opus_recorder.cpp
            vad_jni.cpp)

# Include headers
target_include_directories(app PRIVATE
    opus-1.3.1/include
    ${LIBFVAD_DIR}/include
)

# Link libraries
target_link_libraries(app
                      opus
                      fvad
                      log)

# Force hash-style to both for Android 5.0+ compatibility
set_target_properties(app PROPERTIES LINK_FLAGS "-Wl,--hash-style=both")
