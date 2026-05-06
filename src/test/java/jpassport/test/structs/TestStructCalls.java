package jpassport.test.structs;

import jpassport.Passport;
import jpassport.annotations.NotRequired;
import jpassport.annotations.Ptr;
import jpassport.annotations.PtrPtrArg;
import jpassport.annotations.RefArg;

public interface TestStructCalls extends Passport {
    double passStruct(TestStruct address);
    double passComplex(@RefArg ComplexStruct[] complexStruct);
    double passStructWithArrays(@RefArg PassingArrays[] arrays);
    @NotRequired
    void testAddrCall(StructWithPrt test);

    void passMemoryBlock(@RefArg PassMemoryBlockStruct[] memoryBlock);

    double passStructArrBlock(@RefArg TestStruct[] data, int count, int multiply);
    double passStructArrPtr(@RefArg @PtrPtrArg TestStruct[] data, int count, int multiply);
    double passStructOfStructs(@RefArg PassingStructs[] data);
    TestStruct returnStruct();
}
