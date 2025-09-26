package jpassport.test.testformats;

import jpassport.Passport;
import jpassport.annotations.Array;
import jpassport.annotations.Ptr;
import jpassport.annotations.RefArg;

public interface SmallInterfaceWithStruct extends Passport {
    record PassingArraysSimple(
            @Array(length = 5) double[] s_double,
            @Array(length = 8) long[] s_long,
            long s_doublePtrCount,
            @Ptr double[] s_doublePtr,
            long s_longPtrCount,
            @Ptr long[] s_longPtr)
    {
    }

    double passStructWithArrays(@RefArg PassingArraysSimple[] arrays);
}
