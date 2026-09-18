package jpassport.parser.clang.types;

import jpassport.annotations.Array;
import jpassport.annotations.Ptr;
import jpassport.annotations.StructReturnMemory;
import jpassport.pointers.MemoryBlock;

import java.lang.foreign.MemorySegment;

public record CXCursor(
        @StructReturnMemory MemorySegment origMem,
        CXCursorKind kind,
        int xdata,
        MemorySegment data0,
        MemorySegment data1,
        MemorySegment data2){
//        @Ptr @Array(length = 3) int[] data) {{
}
