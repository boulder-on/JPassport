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
package jpassport.test;

import java.util.Collection;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import jpassport.PassportFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static jpassport.test.TestLinkHelp.getLibName;
import static org.junit.jupiter.api.Assertions.*;


public class TestJPassport
{
    static TestLink testClass[];

    @BeforeAll
    public static void startup() throws Throwable
    {
        System.setProperty("jpassport.build.home", "out/testing");
        System.setProperty("jna.library.path", System.getProperty("java.library.path"));
        testClass = new TestLink[] {PassportFactory.link(getLibName(), TestLink.class),
                                PassportFactory.proxy(getLibName(), TestLink.class)};
    }

    @Test
    public void testNoPresent()
    {
        for (TestLink testLink : testClass) {
            assertFalse(testLink.hasMethod("functionDoesNotExist"));
            assertThrows(Error.class, () -> testLink.functionDoesNotExist(1));
        }
    }
    @Test
    public void testAllocString()
    {
        for (TestLink testLink : testClass) {
            String orig = "hello";
            String ret = testLink.mallocString(orig);
            assertEquals(orig, ret);
        }
    }

    @Test
    public void testNulls()
    {
        for (TestLink testFL : testClass) {
            assertNull(testFL.mallocString(null));
            assertEquals(0, testFL.sumArrD(null, 10));
            assertTrue(TestLinkHelp.testMallocDouble(testFL));
        }
    }

    @Test
    public void testD()
    {
        for (TestLink testFL : testClass) {
            assertEquals(4 + 5, testFL.sumD(4, 5));
            assertEquals(1 + 2 + 3, testFL.sumArrD(new double[]{1, 2, 3}, 3));
            assertEquals(1 + 2 + 3 + 4 + 5 + 6, testFL.sumArrDD(new double[]{1, 2, 3}, new double[]{4, 5, 6}, 3));

            double[] v = new double[1];
            testFL.readD(v, 5);
            assertEquals(5, v[0]);
        }
    }

    @Test
    public void testF()
    {
        for (TestLink testFL : testClass) {
            assertEquals(1 + 2 + 3, testFL.sumArrF(new float[]{1, 2, 3}, 3));

            float[] v = new float[1];
            testFL.readF(v, 5);
            assertEquals(5, v[0]);
        }
    }


    @Test
    public void testL()
    {
        for (TestLink testFL : testClass) {
            assertEquals(1 + 2 + 3, testFL.sumArrL(new long[]{1, 2, 3}, 3));

            long[] v = new long[1];
            testFL.readL(v, 5);
            assertEquals(5, v[0]);
        }
    }


    @Test
    public void testI()
    {
        int[] testRange = IntStream.range(1, 5).toArray();
        int correct = IntStream.range(1, 5).sum();

        for (TestLink testFL : testClass) {
            assertEquals(correct, testFL.sumArrI(testRange, testRange.length));

            int[] v = new int[1];
            testFL.readI(v, 5);
            assertEquals(5, v[0]);
        }
    }


    @Test
    public void testS()
    {
        for (TestLink testFL : testClass) {
            assertEquals(1 + 2 + 3, testFL.sumArrS(new short[]{1, 2, 3}, (short) 3));

            short[] v = new short[1];
            testFL.readS(v, (short) 5);
            assertEquals(5, v[0]);
        }
    }


    @Test
    public void testB()
    {
        for (TestLink testFL : testClass) {
            assertEquals(1 + 2 + 3, testFL.sumArrB(new byte[]{1, 2, 3}, (byte) 3));

            byte[] v = new byte[1];
            testFL.readB(v, (byte) 5);
            assertEquals(5, v[0]);
        }
    }

    @Test
    public void testSumMatD()
    {
        for (TestLink testFL : testClass) {
            double[][] mat = new double[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}};
            int correct = IntStream.range(1, 13).sum();
            assertEquals(correct, testFL.sumMatD(mat.length, mat[0].length, mat));
            assertEquals(correct, testFL.sumMatDPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    public void testSumMatF()
    {
        for (TestLink testFL : testClass) {
            float[][] mat = new float[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}};
            int correct = IntStream.range(1, 13).sum();
            assertEquals(correct, testFL.sumMatF(mat.length, mat[0].length, mat));
            assertEquals(correct, testFL.sumMatFPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    public void testSumMatL()
    {
        for (TestLink testFL : testClass) {
            long[][] mat = new long[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}};
            int correct = IntStream.range(1, 13).sum();
            assertEquals(correct, testFL.sumMatL(mat.length, mat[0].length, mat));
            assertEquals(correct, testFL.sumMatLPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    public void testSumMatI()
    {
        for (TestLink testFL : testClass) {
            int[][] mat = new int[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}};
            int correct = IntStream.range(1, 13).sum();
            assertEquals(correct, testFL.sumMatI(mat.length, mat[0].length, mat));
            assertEquals(correct, testFL.sumMatIPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    public void testSumMatS()
    {
        for (TestLink testFL : testClass) {
            short[][] mat = new short[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}};
            int correct = IntStream.range(1, 13).sum();
            assertEquals(correct, testFL.sumMatS(mat.length, mat[0].length, mat));
            assertEquals(correct, testFL.sumMatSPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    public void testSumMatB()
    {
        for (TestLink testFL : testClass) {
            byte[][] mat = new byte[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}};
            int correct = IntStream.range(1, 13).sum();
            assertEquals(correct, testFL.sumMatB(mat.length, mat[0].length, mat));
            assertEquals(correct, testFL.sumMatBPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    public void testStrLen()
    {
        for (TestLink testFL : testClass) {
            assertEquals(5, testFL.cstringLength("12345"));
        }
    }

//    @Test
//    public void testReturnPointer()
//    {
//        TestLink.calling(testFL);
//    }


}
