package jpassport.test.structs;

import java.lang.foreign.MemorySegment;

public record StructWithPrt(int n, MemorySegment addr, float f) {
}
