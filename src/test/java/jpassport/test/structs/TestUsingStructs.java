package jpassport.test.structs;

import jpassport.pointers.MemoryBlock;
import jpassport.PassportFactory;
import jpassport.Utils;
import jpassport.test.PassType;
import jpassport.test.TestJPassport;
import jpassport.test.TestLink;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

//import java.io.IO;
import java.util.stream.IntStream;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.*;
import static jpassport.test.TestLinkHelp.getLibName;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUsingStructs {

    record Link (PassType type, TestStructCalls link) {}
    static Link[] PassingStructs;

    @BeforeAll
    public static void startup() throws Throwable
    {
        System.setProperty("jpassport.build.home", "out/testing");
        PassingStructs = new Link[] {
//                new Link(PassType.byte_code, new TestStructCalls_impl()),
                new Link(PassType.written, PassportFactory.link_written(getLibName(), TestStructCalls.class)),
                new Link(PassType.byte_code, PassportFactory.link(getLibName(), TestStructCalls.class))
        };

//        PassingStructs = new Link[] {
//                new Link(PassType.written, new TestStructCalls_impl(PassportFactory.loadMethodHandles(getLibName(), TestStructCalls.class)))
//        };

    }

    @Test
    public void testSimpleStruct()
    {
        assertEquals(4 * JAVA_LONG.byteSize(), Utils.size_of(TestStruct.class));
        assertEquals(JAVA_INT.byteSize() + Utils.size_of(TestStruct.class) +
                ADDRESS.byteSize() * 2, Utils.size_of(ComplexStruct.class));

        for (int n = 0; n < PassingStructs.length; ++n)
            assertEquals(2+3+4+5, PassingStructs[n].link.passStruct(new TestStruct(2, 3, 4, 5)));
    }

    @Test
    public void testComplexStruct()
    {
        for (int n = 0; n < PassingStructs.length; ++n)
        {
            TestStruct ts = new TestStruct(1, 2, 3, 4);
            TestStruct tsPtr = new TestStruct(5, 6, 7, 8);
            ComplexStruct[] complex = new ComplexStruct[] {new ComplexStruct(55, ts, tsPtr, "hello")};

            double d = PassingStructs[n].link.passComplex(complex);
            assertEquals(IntStream.range(1, 9).sum(), d);
            assertEquals(65, complex[0].ID());
            assertEquals(11, complex[0].ts().s_int());
            assertEquals(25, complex[0].tsPtr().s_int());
            assertEquals("HELLO", complex[0].string());
        }
    }

    @Test
    public void testStructsWithArrays()
    {
        int expected = IntStream.range(1, 21).sum();

        for (int n = 0; n < PassingStructs.length; ++n) {

            var doublesArr = new double[]{1, 2, 3, 4, 5};
            var longArr = new long[]{6, 7, 8, 9, 10, 11, 12, 13};
            var doublePtr = new double[]{14, 15, 16};
            var longPtr = new long[]{17, 18, 19, 20};

            PassingArrays pa = new PassingArrays(doublesArr, longArr, doublePtr.length, doublePtr, longPtr.length, longPtr);
            PassingArrays[] regArg = new PassingArrays[]{pa};
            assertEquals(expected, PassingStructs[n].link.passStructWithArrays(regArg));

            assertArrayEquals(new double[]{14, 15, 16, 4, 5}, regArg[0].s_double());
            assertArrayEquals(new long[]{6, 7, 8, 9}, regArg[0].s_longPtr());
            assertArrayEquals(longArr, regArg[0].s_long());
            assertArrayEquals(doublePtr, regArg[0].s_doublePtr());
        }
    }

    @Test
    public void testStructsWithArraysAndNulls()
    {
        int expected = IntStream.range(1, 14).sum();

        for (int n = 0; n < PassingStructs.length; ++n) {

            var doublesArr = new double[]{1, 2, 3, 4, 5};
            var longArr = new long[]{6, 7, 8, 9, 10, 11, 12, 13};

            PassingArrays pa = new PassingArrays(doublesArr, longArr, 10, null, 12, null);
            PassingArrays[] regArg = new PassingArrays[]{pa};
            assertEquals(expected, PassingStructs[n].link.passStructWithArrays(regArg));

            assertArrayEquals(doublesArr, regArg[0].s_double());
            assertArrayEquals(null, regArg[0].s_longPtr());
            assertArrayEquals(longArr, regArg[0].s_long());
            assertArrayEquals(null, regArg[0].s_doublePtr());

            ComplexStruct[] complex = new ComplexStruct[] {new ComplexStruct(55, null, null, null)};
            assertEquals(-2, PassingStructs[n].link.passComplex(complex));
            assertEquals(65, complex[0].ID());
            complex = new ComplexStruct[] {new ComplexStruct(55, null, null, "hello")};
            assertEquals(-1, PassingStructs[n].link.passComplex(complex));
        }
    }

    @Test
    public void testMemoryBlockMember()
    {
        for (int m = 0; m < PassingStructs.length; ++m) {
            var mb = new MemoryBlock(1024);
            var mbs = new PassMemoryBlockStruct[]{new PassMemoryBlockStruct(mb, (int) mb.size())};

            PassingStructs[m].link.passMemoryBlock(mbs);

            byte[] expected = new byte[1024];
            for (int n = 0; n < expected.length; ++n)
                expected[n] = (byte) (n % 10);

            assertArrayEquals(expected, mbs[0].mem().getBytes(), "Comparison using implementation " + m);
        }
    }

    @Test
    public void testPassingArrayBlock()
    {
        for (int m = 0; m < PassingStructs.length; ++m) {

            int ii = m;
            TestStruct[]  pass = new TestStruct[5];
            for (int n = 0; n < pass.length; ++n)
            {
                pass[n] = new TestStruct(ii++, ii++, ii++, ii++);
            }

            int expected = IntStream.range(m, ii).sum();
            double answer = PassingStructs[m].link.passStructArrBlock(pass, pass.length, 2);

            assertEquals(expected, answer);
            assertEquals((ii-1) * 2, pass[pass.length - 1].s_double());
        }
    }

    @Test
    public void testPassingArrayPtr()
    {
        for (int m = 0; m < PassingStructs.length; ++m) {

            int ii = m;
            TestStruct[]  pass = new TestStruct[5];
            for (int n = 0; n < pass.length; ++n)
            {
                pass[n] = new TestStruct(ii++, ii++, ii++, ii++);
            }

            int expected = IntStream.range(m, ii).sum();
            double answer = PassingStructs[m].link.passStructArrPtr(pass, pass.length, 2);

            assertEquals(expected, answer);
            assertEquals((ii-1) * 2, pass[pass.length - 1].s_double());
        }
    }

    @Test
    public void testPassingStructWithArrays()
    {
        for (int m = 0; m < PassingStructs.length; ++m) {

            int ii = m;
            var arg1 = new TestStruct(ii++, ii++, ii++, ii++);

            TestStruct[]  arg2 = new TestStruct[4];
            for (int n = 0; n < arg2.length; ++n)
                arg2[n] = new TestStruct(ii++, ii++, ii++, ii++);

            TestStruct[]  arg3 = new TestStruct[3];
            for (int n = 0; n < arg3.length; ++n)
                arg3[n] = new TestStruct(ii++, ii++, ii++, ii++);

            jpassport.test.structs.PassingStructs p = new PassingStructs(arg1, arg3, arg2.length, arg2);

            jpassport.test.structs.PassingStructs[] pass = new jpassport.test.structs.PassingStructs[1];
            pass[0] = p;

            int expected = IntStream.range(m, ii).sum();

            double response = PassingStructs[m].link.passStructOfStructs(pass);
            assertEquals(expected, response);
        }
    }

    @Test
    public void testStructReturn() {
        for (int m = 0; m < PassingStructs.length; ++m) {
            var r = PassingStructs[m].link.returnStruct();
            assertEquals(3, r.s_int() + r.s_long());
        }
    }
}
