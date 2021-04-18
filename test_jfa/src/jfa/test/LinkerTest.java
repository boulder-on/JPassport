package jfa.test;

import com.sun.jna.Native;

import java.util.List;
import java.util.stream.IntStream;


import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import jfa.LinkFactory;

import org.junit.jupiter.api.Test;

public class LinkerTest
{
    static TestLink testFL;
    static TestLink testJNA;
    static TestLink testJava;

    static List<TestLink> allLinks;
    static List<TestLink> allLinksPtrPtr;

    @BeforeAll
    public static void startup() throws Throwable
    {
//        System.out.println(new java.io.File(".").getAbsolutePath());
//        System.setProperty("java.library.path", "fl_dll\\cmake-build-debug");

        testFL = LinkFactory.link("libforeign_link", TestLink.class);
        testJNA =  Native.load("libforeign_link.dll", TestLink.class);
        testJava = new PureJava();

        allLinks = List.of(testJava,  testFL, testJNA);
        allLinksPtrPtr = List.of(testJava,  testFL);
    }

//    public static void main(String[] str)
//    {
//
//        testSumD(allLinks);
//        testSumArrD(allLinks);
//        testSumArrDD(allLinks);
//        testReadD(allLinks);
//
//        testSumArrF(allLinks);
//        testReadF(allLinks);
//
//        testSumArrL(allLinks);
//        testReadL(allLinks);
//
//        testSumArrI(allLinks);
//        testReadI(allLinks);
//
//        testSumArrS(allLinks);
//        testReadS(allLinks);
//
//        testSumArrB(allLinks);
//        testReadB(allLinks);
//
//        testSumMatI(allLinks);
//        testSumMatIPtrPtr(allLinks);
////        for (int loops = 1000; loops < 100000; loops += 1000)
////        {
////            double j = pj.sumTest(loops);
////            double jna = TestLib.sumTest(testLib, loops);
////            double clink = linked.sumTest(loops);
////            double clink2 = TestLibLinked2.sumTest(linked2, loops);
////
////            System.out.printf("sum, %d, %f, %f, %f, %f\n", loops, j, jna, clink, clink2);
////        }
//    }

    @Test
    void testD()
    {
        for (TestLink test : allLinks)
        {
            assertEquals(4 + 5, test.sumD(4, 5));
            assertEquals(1+2+3, test.sumArrD(new double[] {1, 2, 3}, 3));
            assertEquals(1+2+3+4+5+6, test.sumArrDD(new double[] {1, 2, 3}, new double[] {4, 5, 6}, 3));

            double[] v = new double[1];
            test.readD(v, 5);
            assertEquals(5, v[0]);
        }
    }

    @Test
    void testF()
    {
        for (TestLink test : allLinks)
        {
            assertEquals(1+2+3, test.sumArrF(new float[] {1, 2, 3}, 3));

            float[] v = new float[1];
            test.readF(v, 5);
            assertEquals(5, v[0]);
        }
    }


    @Test
    void testL()
    {
        for (TestLink test : allLinks)
        {
            assertEquals(1+2+3, test.sumArrL(new long[] {1, 2, 3}, 3));

            long[] v = new long[1];
            test.readL(v, 5);
            assertEquals(5, v[0]);
        }
    }


    @Test
    void testI()
    {
        int[] testRange = IntStream.range(1, 5).toArray();
        int correct = IntStream.range(1, 5).sum();

        for (TestLink test : allLinks)
        {
            assertEquals(correct, test.sumArrI(testRange, testRange.length));

            int v[] = new int[1];
            test.readI(v, 5);
            assertEquals(5, v[0]);
        }
    }


    @Test
    void testS()
    {
        for (TestLink test : allLinks)
        {
            assertEquals(1+2+3, test.sumArrS(new short[] {1, 2, 3}, (short)3));

            short v[] = new short[1];
            test.readS(v, (short)5);
            assertEquals(5, v[0]);
        }
    }


    @Test
    void testB()
    {
        for (TestLink test : allLinks)
        {
            assertEquals(1+2+3, test.sumArrB(new byte[] {1, 2, 3}, (byte)3));

            byte v[] = new byte[1];
            test.readB(v, (byte)5);
            assertEquals(5, v[0]);
        }
    }

    @Test
    void testSumMatD()
    {
        double[][] mat = new double[][] {{1,2,3}, {4,5,6}, {7,8,9}, {10,11,12}};
        int correct = IntStream.range(1,13).sum();
        for (TestLink test : allLinksPtrPtr)
        {
            assertEquals(correct, test.sumMatD(mat.length, mat[0].length, mat));
            assertEquals(correct, test.sumMatDPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    void testSumMatF()
    {
        float[][] mat = new float[][] {{1,2,3}, {4,5,6}, {7,8,9}, {10,11,12}};
        int correct = IntStream.range(1,13).sum();
        for (TestLink test : allLinksPtrPtr)
        {
            assertEquals(correct, test.sumMatF(mat.length, mat[0].length, mat));
            assertEquals(correct, test.sumMatFPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    void testSumMatL()
    {
        long[][] mat = new long[][] {{1,2,3}, {4,5,6}, {7,8,9}, {10,11,12}};
        int correct = IntStream.range(1,13).sum();
        for (TestLink test : allLinksPtrPtr)
        {
            assertEquals(correct, test.sumMatL(mat.length, mat[0].length, mat));
            assertEquals(correct, test.sumMatLPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    void testSumMatI()
    {
        int[][] mat = new int[][] {{1,2,3}, {4,5,6}, {7,8,9}, {10,11,12}};
        int correct = IntStream.range(1,13).sum();
        for (TestLink test : allLinksPtrPtr)
        {
            assertEquals(correct, test.sumMatI(mat.length, mat[0].length, mat));
            assertEquals(correct, test.sumMatIPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    void testSumMatS()
    {
        short[][] mat = new short[][] {{1,2,3}, {4,5,6}, {7,8,9}, {10,11,12}};
        int correct = IntStream.range(1,13).sum();
        for (TestLink test : allLinksPtrPtr)
        {
            assertEquals(correct, test.sumMatS(mat.length, mat[0].length, mat));
            assertEquals(correct, test.sumMatSPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    void testSumMatB()
    {
        byte[][] mat = new byte[][] {{1,2,3}, {4,5,6}, {7,8,9}, {10,11,12}};
        int correct = IntStream.range(1,13).sum();
        for (TestLink test : allLinksPtrPtr)
        {
            assertEquals(correct, test.sumMatB(mat.length, mat[0].length, mat));
            assertEquals(correct, test.sumMatBPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    void testStrLen()
    {
        for (TestLink test : allLinksPtrPtr)
        {
            assertEquals(5, test.cstringLength("12345"));
        }
    }
}
