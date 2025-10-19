package jpassport.test;

import jpassport.Passport;
import jpassport.PassportFactory;
import jpassport.annotations.RefArg;
import org.junit.jupiter.api.Test;

import static jpassport.test.TestLinkHelp.getLibName;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class TestInheritance {

    public interface TopClass extends Passport{
        double sumD(double d, double d2);
        double sumArrD(@RefArg double[] d, int len);
    }

    public enum intEnum
    {
        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY;
    };

    public interface MiddleClass extends Passport, TopClass {
        intEnum todayInt(@RefArg intEnum[] ie);
    }

    public record TestStruct(
            int s_int,
            long s_long,
            float s_float,
            double s_double) {
    }

    public interface MiddleClass2 extends Passport
    {
        double passStruct(TestStruct address);

    }

    public interface BottomClass extends Passport, MiddleClass, MiddleClass2
    {
        float sumArrF(float[] i, int len);
    }

    @Test
    public void testInheritanceWritten() throws Throwable {
        System.setProperty("jpassport.build.home", "out/testing");
        BottomClass impl = PassportFactory.link_written(getLibName(), BottomClass.class);

        assertEquals(3, impl.sumD(1, 2));

        var ptrToday = new intEnum[1];
        var today = impl.todayInt(ptrToday);
        assertEquals(today, ptrToday[0]);
        assertEquals(intEnum.FRIDAY, today);
        assertEquals(10, impl.passStruct(new TestStruct(1, 2, 3, 4)));
        assertEquals(10, impl.sumArrF(new float[]{1, 2, 3, 4}, 4));
    }

    @Test
    public void testInheritanceByteCode() throws Throwable {
        System.setProperty("jpassport.build.home", "out/testing");
        BottomClass impl = PassportFactory.link(getLibName(), BottomClass.class);
        assertEquals(3, impl.sumD(1, 2));

        var ptrToday = new intEnum[1];
        var today = impl.todayInt(ptrToday);
        assertEquals(today, ptrToday[0]);
        assertEquals(intEnum.FRIDAY, today);
        assertEquals(10, impl.passStruct(new TestStruct(1, 2, 3, 4)));
        assertEquals(10, impl.sumArrF(new float[]{1, 2, 3, 4}, 4));
    }
}
