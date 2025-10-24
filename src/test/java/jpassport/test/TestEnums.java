package jpassport.test;

import jpassport.Utils;
import jpassport.annotations.RefArg;
import jpassport.enums.EnumLong;
import jpassport.Passport;
import jpassport.PassportFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static jpassport.test.TestLinkHelp.getLibName;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEnums {

    public enum intEnum
    {
        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY;

        public static intEnum[] ptr() {return new intEnum[1];}
        public static intEnum[] ptr(intEnum e) {return new intEnum[]{e};}
    };

    public enum longEnum implements EnumLong
    {
        SATURDAY(0L),
        SUNDAY(0xFFFFFFFFFL);

        private final long value;
        longEnum(long v)
        {
            value = v;
        }

        @Override
        public long getCValue() {
            return value;
        }

        public static longEnum[] ptr() {return new longEnum[1];}
        public static longEnum[] ptr(longEnum e) {return new longEnum[]{e}; }
    };

    public enum retEnum
    {
        value0, value1, value2
    };

    public interface EnumLink extends Passport
    {
        long passEnum(intEnum ie, longEnum le);
        intEnum todayInt(@RefArg intEnum[] ie);
        longEnum todayLong(@RefArg longEnum[] ie);
        void todayTransfer(intEnum ie, @RefArg intEnum[] ie2, longEnum le, @RefArg longEnum[] le2);
        retEnum toRetEnum(int v);
    }

    record Link (PassType type, EnumLink link) {}
    static Link[] PassingEnums;

    @BeforeAll
    public static void startup() throws Throwable
    {
        System.setProperty("jpassport.build.home", "out/testing");
        PassingEnums = new Link[] {
                new Link(PassType.written, PassportFactory.link_written(getLibName(), EnumLink.class)),
                new Link(PassType.byte_code, PassportFactory.link(getLibName(), EnumLink.class))
        };
    }

    @Test
    public void testSimpleEnum()
    {
        for (var link : PassingEnums)
        {
            long ret = link.link.passEnum(intEnum.WEDNESDAY, longEnum.SATURDAY);
            assertEquals(2, ret);
        }
    }

    @Test
    public void testEnumReturns()
    {
        for (var link : PassingEnums)
        {
            var ptrToday = intEnum.ptr();
            var today = link.link.todayInt(ptrToday);
            assertEquals(today, ptrToday[0]);
            assertEquals(intEnum.FRIDAY, today);

            var ptrTodayL = longEnum.ptr();
            var todayL = link.link.todayLong(ptrTodayL);
            assertEquals(todayL, ptrTodayL[0]);
            assertEquals(longEnum.SUNDAY, todayL);

            ptrToday = intEnum.ptr();
            ptrTodayL = longEnum.ptr();
            link.link.todayTransfer(intEnum.THURSDAY, ptrToday, longEnum.SUNDAY, ptrTodayL);
            assertEquals(intEnum.THURSDAY, ptrToday[0]);
            assertEquals(longEnum.SUNDAY, ptrTodayL[0]);
        }
    }

    @Test
    public void testReturnEnum()
    {
        for (var link : PassingEnums)
        {
            var ret = link.link.toRetEnum(0);
            assertEquals(retEnum.value0, ret);
            ret = link.link.toRetEnum(2);
            assertEquals(retEnum.value2, ret);
        }
    }

}
