package jpassport.parser.clang.types;

import jpassport.annotations.StructPadding;
import jpassport.annotations.StructReturnMemory;

import java.lang.foreign.MemorySegment;

public record CXString(@StructReturnMemory MemorySegment origData,
                       MemorySegment data,
                       @StructPadding(bytes = 4) int private_flags) {
}
