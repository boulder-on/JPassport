package jpassport.test.structs;

import jpassport.annotations.Array;
import jpassport.annotations.PtrPtrArg;
import jpassport.annotations.StructPadding;

public record PassingStructs(
        TestStruct simple,
//        @Array(length = 3) TestStruct[] arr,
        int countOfPtrs,
        @PtrPtrArg TestStruct[] array_of_ptrs
//        @Array(length = 3) TestStruct[] array_block
) {
}
