package jpassport.parser.clang.types;

import jpassport.annotations.Array;
import jpassport.annotations.StructReturnMemory;

import java.lang.foreign.MemorySegment;

public record CXSourceRange(
        @StructReturnMemory MemorySegment origMem,
        @Array(length = 2) int[] ptr_data,
        int begin_int_data,
        int end_int_data) {
}
