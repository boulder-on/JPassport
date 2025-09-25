package jpassport.test.comparison;

import jpassport.Passport;
import jpassport.annotations.Critical;
import jpassport.annotations.RefArg;

import jpassport.test.structs.PassingArrays;

public interface struct_passer extends Passport {

    double sumD(double d, double d2);
    double sumArrD(@RefArg double[] d, int len);

    @Critical
    double sumDCritical(double d, double d2);
    @Critical
    double sumArrDCritical(@RefArg double[] d, int len);

    double passStruct(@RefArg PassingDataJP[] address);
    double passStructWithArrays(@RefArg PassingArrays[] arrays);
}
