package jpassport.test.structs;

import jpassport.annotations.StructPadding;
import jpassport.annotations.StructReturnMemory;

import java.lang.foreign.MemorySegment;

public record TestStruct(
        @StructReturnMemory MemorySegment origMemory,
        @StructPadding(bytes = 4) int s_int,
        long s_long,
        @StructPadding(bytes = 4) float s_float,
        double s_double) {

    public TestStruct(int i, long l, float f, double d)
    {
        this(MemorySegment.NULL, i, l, f, d);
    }
}
