package jpassport.test.structs;

import jpassport.PassportFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUsingStructs {

//    public static void main(String[] str) throws Throwable {
//        System.setProperty("jpassport.build.home", "out/testing");
//        System.setProperty("jna.library.path", System.getProperty("java.library.path"));
//
////        PassStructWithArray pswa = PassportFactory.link("libforeign_link", PassStructWithArray.class);
//
//        int expected = IntStream.range(1, 21).sum();
//        PassingArrays pa = new PassingArrays(new double[] {1, 2, 3, 4, 5}, new long[] {6, 7, 8, 9, 10, 11, 12, 13}, 3, new double[] {14, 15, 16}, 4, new long[] {17,18,19,20});
//        PassingArrays[] regArg = new PassingArrays[] {pa};
//        double r = pswa.passStructWithArrays(regArg);
//        System.out.println(r);
//
//    }

    static TestStructCalls PassingStructs;

    @BeforeAll
    public static void startup() throws Throwable
    {
        System.setProperty("jpassport.build.home", "out/testing");
        System.setProperty("jna.library.path", System.getProperty("java.library.path"));

        PassingStructs = PassportFactory.link("libpassport_test", TestStructCalls.class);
    }

    @Test
    void testSimpleStruct()
    {
        assertEquals(2+3+4+5, PassingStructs.passStruct(new TestStruct(2, 3, 4, 5)));
    }

    @Test
    void testComplexStruct()
    {
        TestStruct ts = new TestStruct(1, 2, 3, 4);
        TestStruct tsPtr = new TestStruct(5, 6, 7, 8);
        ComplexStruct[] complex = new ComplexStruct[] {new ComplexStruct(55, ts, tsPtr, "hello")};

        double d = PassingStructs.passComplex(complex);
        assertEquals(IntStream.range(1, 9).sum(), d);
        assertEquals(65, complex[0].ID());
        assertEquals(11, complex[0].ts().s_int());
        assertEquals(25, complex[0].tsPtr().s_int());
        assertEquals("HELLO", complex[0].string());
    }

    @Test
    void testStructsWithArrays()
    {
        int expected = IntStream.range(1, 21).sum();

        var doublesArr = new double[] {1, 2, 3, 4, 5};
        var longArr = new long[] {6, 7, 8, 9, 10, 11, 12, 13};
        var doublePtr =  new double[] {14, 15, 16};
        var longPtr = new long[] {17,18,19,20};

        PassingArrays pa = new PassingArrays(doublesArr, longArr, doublePtr.length, doublePtr, longPtr.length, longPtr);
        PassingArrays[] regArg = new PassingArrays[] {pa};
        assertEquals(expected, PassingStructs.passStructWithArrays(regArg));

        assertArrayEquals(new double[] {14,15,16,4,5}, regArg[0].s_double());
        assertArrayEquals(new long[] {6,7,8,9}, regArg[0].s_longPtr());
        assertArrayEquals(longArr, regArg[0].s_long());
        assertArrayEquals(doublePtr, regArg[0].s_doublePtr());
    }
}
