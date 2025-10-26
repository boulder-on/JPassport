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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.stream.IntStream;

import jpassport.ErrorCapture;
import jpassport.Utils;
import jpassport.pointers.MemoryBlock;
import jpassport.pointers.Pointer;
import org.junit.jupiter.api.BeforeAll;
import jpassport.PassportFactory;

import org.junit.jupiter.api.Test;

import static jpassport.test.TestLinkHelp.getLibName;
import static org.junit.jupiter.api.Assertions.*;


public class TestJPassport
{
    record Link (PassType type, TestLink link) {}
    static Link[] testClass;

    @BeforeAll
    public static void startup() throws Throwable
    {
        System.setProperty("jpassport.build.home", "out/testing");
        System.setProperty("jna.library.path", System.getProperty("java.library.path"));
        testClass = new Link[] {new Link(PassType.byte_code, PassportFactory.link(getLibName(), TestLink.class)),
                new Link(PassType.written, PassportFactory.link_written(getLibName(), TestLink.class))};
    }

    @Test
    public void testNamedLookup()
    {
        for (var testLink : testClass) {
            assertNotSame(testLink.link.named.addr(), MemorySegment.NULL);
            assertEquals(testLink.link.namedNotFound.addr(), MemorySegment.NULL);
        }
    }

    @Test
    public void TestPointerReturn()
    {
        try (Arena a = Arena.ofConfined())
        {
            var m = a.allocate(100);
            Pointer p = new Pointer(m);
            for (var testLink : testClass) {
                var mp = testLink.link.PassPointers(p);
                assertEquals(m.address(), mp.getPtr().address());
            }
        }
    }

    @Test
    public void testNoPresent()
    {
        for (var testLink : testClass) {
            assertFalse(testLink.link.hasMethod("functionDoesNotExist"));
            assertTrue(testLink.link.hasMethod("mallocString"));
            assertThrows(Error.class, () -> testLink.link.functionDoesNotExist(1));
        }
    }
    @Test
    public void testAllocString()
    {
        for (var testLink : testClass) {
            String orig = "hello";
            String ret = testLink.link.mallocString(orig);
            assertEquals(orig, ret);
        }
    }

    @Test
    public void testNulls()
    {
        for (var testFL : testClass) {
            assertNull(testFL.link.mallocString(null));
            assertEquals(0, testFL.link.sumArrD(null, 10));
            assertEquals(0, testFL.link.sumArrDCritical(null, 10));
            assertTrue(TestLinkHelp.testMallocDouble(testFL.link));
        }
    }

    @Test
    public void testD()
    {
        for (var testFL : testClass) {
            assertEquals(4 + 5, testFL.link.sumD(4, 5));
            assertEquals(4 + 5, testFL.link.sumDCritical(4, 5));
            double pass[] = new double[]{1, 2, 3};
            assertEquals(1 + 2 + 3, testFL.link.sumArrD(pass, pass.length));
            assertArrayEquals(new double[]{3, 2, 3}, pass);

            pass = new double[]{1, 2, 3};
            assertEquals(1 + 2 + 3, testFL.link.sumArrDCritical(pass, 3));
            assertArrayEquals(new double[]{3, 2, 3}, pass);

            assertEquals(1 + 2 + 3 + 4 + 5 + 6, testFL.link.sumArrDD(new double[]{1, 2, 3}, new double[]{4, 5, 6}, 3));

            double[] v = new double[1];
            testFL.link.readD(v, 5);
            assertEquals(5, v[0]);
        }
    }

    @Test
    public void testF()
    {
        for (var testFL : testClass) {
            assertEquals(1 + 2 + 3, testFL.link.sumArrF(new float[]{1, 2, 3}, 3));

            float[] v = new float[1];
            testFL.link.readF(v, 5);
            assertEquals(5, v[0]);
        }
    }


    @Test
    public void testL()
    {
        for (var testFL : testClass) {
            assertEquals(1 + 2 + 3, testFL.link.sumArrL(new long[]{1, 2, 3}, 3));

            long[] v = new long[1];
            testFL.link.readL(v, 5);
            assertEquals(5, v[0]);
        }
    }


    @Test
    public void testI()
    {
        int[] testRange = IntStream.range(1, 5).toArray();
        int correct = IntStream.range(1, 5).sum();

        for (var testFL : testClass) {
            assertEquals(correct, testFL.link.sumArrI(testRange, testRange.length));

            int[] v = new int[1];
            testFL.link.readI(v, 5);
            assertEquals(5, v[0]);
        }
    }


    @Test
    public void testS()
    {
        for (var testFL : testClass) {
            assertEquals(1 + 2 + 3, testFL.link.sumArrS(new short[]{1, 2, 3}, (short) 3));

            short[] v = new short[1];
            testFL.link.readS(v, (short) 5);
            assertEquals(5, v[0]);
        }
    }


    @Test
    public void testB()
    {
        for (var testFL : testClass) {
            assertEquals(1 + 2 + 3, testFL.link.sumArrB(new byte[]{1, 2, 3}, (byte) 3));

            byte[] v = new byte[1];
            testFL.link.readB(v, (byte) 5);
            assertEquals(5, v[0]);
        }
    }

    @Test
    public void testSumMatD()
    {
        for (var testFL : testClass) {
            double[][] mat = new double[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}};
            int correct = IntStream.range(1, 13).sum();
            assertEquals(correct, testFL.link.sumMatD(mat.length, mat[0].length, mat));
            assertEquals(correct, testFL.link.sumMatDPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    public void testSumMatF()
    {
        for (var testFL : testClass) {
            float[][] mat = new float[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}};
            int correct = IntStream.range(1, 13).sum();
            assertEquals(correct, testFL.link.sumMatF(mat.length, mat[0].length, mat));
            assertEquals(correct, testFL.link.sumMatFPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    public void testSumMatL()
    {
        for (var testFL : testClass) {
            long[][] mat = new long[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}};
            int correct = IntStream.range(1, 13).sum();
            assertEquals(correct, testFL.link.sumMatL(mat.length, mat[0].length, mat));
            assertEquals(correct, testFL.link.sumMatLPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    public void testSumMatI()
    {
        for (var testFL : testClass) {
            int[][] mat = new int[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}};
            int correct = IntStream.range(1, 13).sum();
            assertEquals(correct, testFL.link.sumMatI(mat.length, mat[0].length, mat));
            assertEquals(correct, testFL.link.sumMatIPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    public void testSumMatS()
    {
        for (var testFL : testClass) {
            short[][] mat = new short[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}};
            int correct = IntStream.range(1, 13).sum();
            assertEquals(correct, testFL.link.sumMatS(mat.length, mat[0].length, mat));
            assertEquals(correct, testFL.link.sumMatSPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    public void testSumMatB()
    {
        for (var testFL : testClass) {
            byte[][] mat = new byte[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}};
            int correct = IntStream.range(1, 13).sum();
            assertEquals(correct, testFL.link.sumMatB(mat.length, mat[0].length, mat));
            assertEquals(correct, testFL.link.sumMatBPtrPtr(mat.length, mat[0].length, mat));
        }
    }

    @Test
    public void testStrLen()
    {
        for (var testFL : testClass) {
            assertEquals(5, testFL.link.cstringLength("12345"));
        }
    }

//    @Test
//    public void testReturnPointer()
//    {
//        TestLink.calling(testFL);
//    }

    @Test
    public void testPointerPassing()
    {

        for (var testFL : testClass) {
            var pt = new Pointer[1];
            pt[0] = new Pointer();

            testFL.link.readPointer(pt, 5);
            assertEquals(5, pt[0].getPtr().address());

            try (var scope = Arena.ofConfined();) {
                var mem = scope.allocate(8);
                pt[0] = new Pointer();
                var ret =  testFL.link.getPointer (pt, mem.address());

                assertEquals(mem.address(), pt[0].getPtr().address());
                assertEquals(mem.address(), ret.getPtr().address());

            }
        }
    }

    @Test
    public void testStringArr()
    {
        for (var testFL : testClass) {
            String[]  var= new String[] {"hello", "Goodbye"};

            var len = testFL.link.swapStrings(var, 0, 1);
            assertEquals(var[0].length() + var[1].length(), len);
            assertEquals("hello".length(), var[1].length());
            assertEquals("Goodbye".length(), var[0].length());
        }
    }

    @Test
    public void testCharArgs()
    {
        String expected = "hello world";

        for (var testFL : testClass) {
            MemoryBlock fill = new MemoryBlock(100);
            try (Arena a = Arena.ofConfined()) {
                int ll = testFL.link.fillChars(a, fill, (int) fill.size());
                assertEquals(expected.length(), ll);
                assertEquals(expected, fill.toString());

                int s = 0;
                for (char c : expected.toCharArray())
                    s += c;

                assertEquals(s, testFL.link.passChars(fill.toString().toCharArray(), (int) expected.length() * 2));
            }
        }

    }

    @Test
    public void testErrorCapture()
    {
        for (var testFL : testClass) {
            ErrorCapture ec = new ErrorCapture();
            testFL.link.setAnError(ec, 10);

            if (Utils.getPlatform() != Utils.Platform.Windows)
                assertEquals(10, ec.getError("errno"));
        }
    }

    @Test
    public void testBooleans()
    {
        for (var testFL : testClass) {

            assertEquals(true, testFL.link.isTrue(true));
            assertEquals(false, testFL.link.isTrue(false));

            boolean[] arr1 = new boolean[4];
            testFL.link.stripe(arr1.length, arr1);
            assertArrayEquals(new boolean[]{true, false, true, false}, arr1);

            arr1 = new boolean[] {true, true, false};
            boolean[] arr2 = new boolean[] {true, true, false};
            boolean[] arr3 = new boolean[] {true, false, false};
            boolean[] ret = new boolean[1];

            testFL.link.compareBool(arr1.length, arr1, arr2, ret);
            assertTrue(ret[0]);
            testFL.link.compareBool(arr1.length, arr1, arr3, ret);
            assertFalse(ret[0]);
        }

    }

    @Test
    public void testFunctionRenaming()
    {
        for (var testFL : testClass) {
            assertEquals(testFL.link.sumD(4, 5), testFL.link.sumDoubles(4, 5));
        }

    }
}
