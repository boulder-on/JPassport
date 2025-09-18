package jpassport.test.performance;

import jpassport.test.extracted.library_h;
import jpassport.test.structs.TestStruct;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;

public class UseExtracted implements PerfTest {
    public static void main(String[] args)
    {
        var ret = library_h.sumD(1, 2);
        System.out.println(ret);
    }

    @Override
    public double sumD(double d, double d2) {
        return library_h.sumD(d, d2);
    }

    @Override
    public double sumArrD(double[] d, int len) {
        try (var a = Arena.ofConfined()) {
            var aa = a.allocateFrom(ValueLayout.JAVA_DOUBLE, d);
            return library_h.sumArrD(aa, len);
        }
    }
}
