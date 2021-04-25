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

import com.sun.jna.Native;
import jpassport.LinkFactory;
import jpassport.test.TestLinkJNADirect;
import jpassport.test.util.CSVOutput;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;


public class PerformanceTest
{
    static PerfTest testFL;
    static PerfTest testJNA;
    static PerfTest testJNADirect;
    static PerfTest testJava;


    public static void startup() throws Throwable
    {
        System.setProperty("jpassport.build.home", "out/testing");
        testFL = LinkFactory.link("libforeign_link", PerfTest.class);
        testJNA =  Native.load("libforeign_link.dll", PerfTest.class);
        testJNADirect =  new TestLinkJNADirect.JNADirect();
        testJava = new PureJavaPerf();
    }

    public static void main(String[] str) throws Throwable
    {
        startup();

        try(var csv = new CSVOutput(Path.of("performance", "doubles_opt.csv")))
        {
            csv.add("iteration", "pure java", "JNA", "JNA Direct", "JPassport").endLine();

            for (int loops = 1000; loops < 100000; loops += 1000) {
                double j = sumTest(testJava, loops);
                double jlink = sumTest(testFL, loops);
                double jna = sumTest(testJNA, loops);
                double jnaDirect = sumTest(testJNADirect, loops);

                csv.addF(loops, j, jna, jnaDirect, jlink).endLine();
                System.out.println("loops: " + loops);
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }

        try(var csv = new CSVOutput(Path.of("performance", "double_arr_opt.csv")))
        {
            csv.add("array size", "pure java", "JNA", "JNA Direct", "JPassport").endLine();
            for (int size = 1024; size <= 1024*256; size += 1024)
            {
                double j = sumTestArrD(testJava, 100, size);
                double jlink = sumTestArrD(testFL, 100, size);
                double jna = sumTestArrD(testJNA, 100, size);
                double jnaDirect = sumTestArrD(testJNADirect, 100, size);

                csv.addF(size, j, jna, jnaDirect, jlink).endLine();
                System.out.println("array size: " + size);
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
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
