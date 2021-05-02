/* Copyright (c) 2021 Duncan McLean, All Rights Reserved
 *
 * The contents of this file is dual-licensed under the
 * Apache License 2.0.
 *
 * You may obtain a copy of the Apache License at:
 *
 * http://www.apache.org/licenses/
 *
 * A copy is also included in the downloadable source code.
 */
package jpassport.test.performance;

import jpassport.annotations.RefArg;
import jpassport.test.ComplexStruct;
import jpassport.test.TestStruct;

public class PureJavaPerf implements PerfTest{
    @Override
    public double sumD(double d, double d2) {
        return d + d2;
    }

    @Override
    public double sumArrD(double[] d, int len)
    {
        double ret = 0;
        for (int n = 0; n < len; ++n)
            ret += d[n];
        return ret;
    }

    @Override
    public int sumArrI(int[] d, int len)
    {
        int ret = 0;
        for (int n = 0; n < len; ++n)
            ret += d[n];
        return ret;
    }

    @Override
    public float sumArrF(float[] d, int len)
    {
        float ret = 0;
        for (int n = 0; n < len; ++n)
            ret += d[n];
        return ret;
    }

    public double passStruct(TestStruct simpleStruct) {
        return simpleStruct.s_int() + simpleStruct.s_long() + simpleStruct.s_float() + simpleStruct.s_double();
    }

    @Override
    public double passComplex(ComplexStruct[] complexStruct)
    {
        double ret = passStruct(complexStruct[0].ts()) + passStruct(complexStruct[0].tsPtr());
        TestStruct ts = new TestStruct(complexStruct[0].ts().s_int() + 10, complexStruct[0].ts().s_long(), complexStruct[0].ts().s_float(), complexStruct[0].ts().s_double());
        TestStruct tsPtr = new TestStruct(complexStruct[0].tsPtr().s_int() + 20, complexStruct[0].tsPtr().s_long(), complexStruct[0].tsPtr().s_float(), complexStruct[0].tsPtr().s_double());
        complexStruct[0] = new ComplexStruct(complexStruct[0].ID() + 10, ts, tsPtr, complexStruct[0].string().toUpperCase());
        return ret;
    }

}
