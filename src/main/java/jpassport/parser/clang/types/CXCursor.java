package jpassport.parser.clang.types;

import jpassport.annotations.StructReturnMemory;

import java.lang.foreign.MemorySegment;

public record CXCursor(
        @StructReturnMemory MemorySegment origMem,
        CXCursorKind kind,
        int xdata,
        MemorySegment data) {}
