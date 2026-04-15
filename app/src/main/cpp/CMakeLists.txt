cmake_minimum_required(VERSION 3.22)
project(novaterm-bootstrap)

# Build the bootstrap library that embeds the ZIP via .incbin
add_library(novaterm-bootstrap SHARED
    novaterm-bootstrap-zip.S
    novaterm-bootstrap.c
)

# 16KB page size alignment (Android 15+ Play Store requirement)
# Ensures LOAD segments are aligned to 16KB instead of default 4KB
target_link_options(novaterm-bootstrap PRIVATE "-Wl,-z,max-page-size=16384")

# Link Android log library for potential debugging
target_link_libraries(novaterm-bootstrap log)
