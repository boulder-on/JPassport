package jfa.test.performance;

import com.sun.jna.Native;
import jfa.LinkFactory;

import java.util.stream.IntStream;


public class PerformanceTest
{
    static PerfTest testFL;
    static PerfTest testJNA;
    static PerfTest testJava;


    public static void startup() throws Throwable
    {
        testFL = LinkFactory.link("libforeign_link", PerfTest.class);
        testJNA =  Native.load("libforeign_link.dll", PerfTest.class);
        testJava = new PureJavaPerf();
    }

    public static void main(String[] str) throws Throwable
    {
        startup();

//        System.out.println(",iteration, pure java, JNA, JFA");

//        for (int loops = 1000; loops < 100000; loops += 1000)
//        {
//            double j = sumTest(testJava, loops);
//            double jlink = sumTest(testFL, loops);
//            double jna = sumTest(testJNA, loops);
//
//            System.out.printf("sum, %d, %f, %f, %f\n", loops, j, jna, jlink);
//        }

        System.out.println(",size , pure java, JNA, JFA");
        for (int size = 1024; size <= 1024*256; size += 1024)
        {
            double j = sumTestArrD(testJava, 100, size);
            double jlink = sumTestArrD(testFL, 100, size);
            double jna = sumTestArrD(testJNA, 100, size);

            System.out.printf("sum, %d, %f, %f, %f\n", size, j, jna, jlink);
        }
    }


    static double sumTest(PerfTest testLib, int count) {
        long start = System.nanoTime();
        double m = 0;
        for (double n = 0; n < count; ++n) {
            m = testLib.sumD(n, m);
        }
        return (System.nanoTime() - start) / 1e9;
    }

    static double sumTestArrD(PerfTest testLib, int count, int arrSize) {
        double[] d = IntStream.range(0, arrSize).mapToDouble(i -> i).toArray();
        long start = System.nanoTime();
        double m = 0;
        for (double n = 0; n < count; ++n) {
            m = testLib.sumArrD(d, arrSize);
        }
        return (System.nanoTime() - start) / 1e9;
    }

    static double sumTestArrF(PerfTest testLib, int count, int arrSize) {
        float[] d = new float[arrSize];
        IntStream.range(0, arrSize).forEach(i -> d[i] = i);

        long start = System.nanoTime();
        double m = 0;
        for (double n = 0; n < count; ++n) {
            m = testLib.sumArrF(d, arrSize);
        }
        return (System.nanoTime() - start) / 1e9;
    }

    static double sumTestArrI(PerfTest testLib, int count, int arrSize) {
        int[] d = IntStream.range(0, arrSize).toArray();

        long start = System.nanoTime();
        double m = 0;
        for (double n = 0; n < count; ++n) {
            m = testLib.sumArrI(d, arrSize);
        }
        return (System.nanoTime() - start) / 1e9;
    }
}
