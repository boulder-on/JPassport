cmake_minimum_required(VERSION 3.17)
project(foreign_link C)

set(CMAKE_C_STANDARD 99)

add_library(foreign_link SHARED library.c library.h)