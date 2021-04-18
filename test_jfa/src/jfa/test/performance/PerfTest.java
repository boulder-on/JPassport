package jfa.test.performance;

import com.sun.jna.Library;
import jfa.Foreign;

public interface PerfTest extends Foreign, Library {
    double sumD(double d, double d2);
    double sumArrD(double[] d, int len);
    float sumArrF(float[] d, int len);
    int sumArrI(int[] d, int len);
}
