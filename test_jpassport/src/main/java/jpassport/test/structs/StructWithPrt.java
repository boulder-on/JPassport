package jpassport.test.structs;

import java.lang.foreign.Addressable;

public record StructWithPrt(int n, Addressable addr, float f) {
}
