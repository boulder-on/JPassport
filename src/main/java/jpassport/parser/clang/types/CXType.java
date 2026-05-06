package jpassport.parser.clang.types;

import jpassport.annotations.Array;
import jpassport.annotations.PtrPtrArg;
import jpassport.annotations.StructReturnMemory;

import java.lang.foreign.MemorySegment;

public record CXType(
        CXTypeKind kind,
        @PtrPtrArg @Array(length=2) long[] data,
        @StructReturnMemory MemorySegment origMem) {
}
