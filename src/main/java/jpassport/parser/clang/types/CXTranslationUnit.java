package jpassport.parser.clang.types;

import jpassport.pointers.GenericPointer;

import java.lang.foreign.MemorySegment;

public class CXTranslationUnit extends GenericPointer {
    public CXTranslationUnit(MemorySegment addr) {
        super(addr);
    }
}