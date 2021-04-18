package jfa.test.performance;

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
}
