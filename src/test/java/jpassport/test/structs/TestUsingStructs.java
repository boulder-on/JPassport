package jpassport.test.structs;

import jpassport.pointers.MemoryBlock;
import jpassport.PassportFactory;
import jpassport.Utils;
import jpassport.test.PassType;
import jpassport.test.TestJPassport;
import jpassport.test.TestLink;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
                new Link(PassType.written, PassportFactory.link_written(getLibName(), TestStructCalls.class)),
                new Link(PassType.byte_code, PassportFactory.link(getLibName(), TestStructCalls.class))
        };
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
}
