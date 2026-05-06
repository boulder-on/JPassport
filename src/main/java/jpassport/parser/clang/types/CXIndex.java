package jpassport.parser.clang.types;

import jpassport.pointers.GenericPointer;

import java.lang.foreign.MemorySegment;

public class CXIndex extends GenericPointer {
    public CXIndex(MemorySegment addr) {
        super(addr);
    }
}
