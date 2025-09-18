package jpassport.test.comparison;

import jpassport.Passport;
import jpassport.annotations.Critical;
import jpassport.annotations.RefArg;

import jpassport.test.structs.PassingArrays;

public interface struct_passer extends Passport {
    @Critical
    double sumD(double d, double d2);
    @Critical
    double sumArrD(double[] d, int len);

    @Critical
    double passStruct(@RefArg PassingDataJP[] address);
    double passStructWithArrays(@RefArg PassingArrays[] arrays);
}
