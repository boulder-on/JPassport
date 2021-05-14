package jpassport.test.structs;

import jpassport.Passport;
import jpassport.annotations.RefArg;

public interface TestStructCalls extends Passport {
    double passStruct(TestStruct address);
    double passComplex(@RefArg ComplexStruct[] complexStruct);
    double passStructWithArrays(@RefArg PassingArrays[] arrays);
}
