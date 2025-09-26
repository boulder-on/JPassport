package jpassport.test.testformats;

import jpassport.Passport;
import jpassport.PassportFactory;
import jpassport.annotations.Array;
import jpassport.annotations.Ptr;
import jpassport.annotations.RefArg;
import org.junit.jupiter.api.Test;

import static jpassport.test.TestLinkHelp.getLibName;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSimplifiedLayout {

    public record PassingArraysSimple(
            @Array(length = 5) double[] s_double,
            @Array(length = 8) long[] s_long,
            long s_doublePtrCount,
            @Ptr double[] s_doublePtr,
            long s_longPtrCount,
            @Ptr long[] s_longPtr)
    {
    }

    public interface SmallInterface extends Passport {
        double passStructWithArrays(@RefArg PassingArraysSimple[] arrays);
    }

    @Test
    void fullyContained() throws Throwable {
        SmallInterface si = PassportFactory.link_written(getLibName(), SmallInterface.class);

        var pa = new PassingArraysSimple(
                new double[]{1, 2, 3, 4, 5},
                new long[]{10, 20, 30, 40, 50, 6, 7, 8},
                3,
                new double[]{10, 20, 30},
                4,
                new long[]{1, 2, 3, 4}
        );

        var pass = new PassingArraysSimple[]{pa};
        assertEquals(256,  si.passStructWithArrays(pass));
    }

    @Test
    void testInterfaceWithStruct() throws Throwable {
        System.setProperty("jpassport.build.home", "out/testing");

        SmallInterfaceWithStruct si = PassportFactory.link_written(getLibName(), SmallInterfaceWithStruct.class);

        var pa = new SmallInterfaceWithStruct.PassingArraysSimple(
                new double[] {1, 2, 3, 4, 5},
                new long[] {10, 20, 30, 40, 50, 6,7 ,8},
                3,
                new double[] {10, 20, 30},
                4,
                new long[] {1, 2, 3, 4}
        );

        var pass  = new SmallInterfaceWithStruct.PassingArraysSimple[] {pa};
        assertEquals(256,  si.passStructWithArrays(pass));
    }

}
