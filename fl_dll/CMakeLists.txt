cmake_minimum_required(VERSION 3.17)
project(foreign_link C)

set(CMAKE_C_STANDARD 99)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "../..")
add_library(passport_test SHARED primitive_examples.c
        enum_examples.c
        struct_examples.c
        union_examples.c
        union_examples.h
        callback_examples.c
        callback_examples.h
        error_capture_example.c
        error_capture_example.h)