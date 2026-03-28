cmake_minimum_required(VERSION 3.22)
project(novaterm-bootstrap)

# Build the bootstrap library that embeds the ZIP via .incbin
add_library(novaterm-bootstrap SHARED
    novaterm-bootstrap-zip.S
    novaterm-bootstrap.c
)

# Link Android log library for potential debugging
target_link_libraries(novaterm-bootstrap log)
