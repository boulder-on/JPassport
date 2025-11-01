package jpassport.test;

import jpassport.*;
import jpassport.annotations.Array;
import jpassport.annotations.Ptr;
import jpassport.annotations.RefArg;
import jpassport.enums.EnumInt;
import jpassport.enums.EnumLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static jpassport.test.TestEnums.longEnum.SATURDAY;
import static jpassport.test.TestEnums.longEnum.SUNDAY;
import static jpassport.test.TestLinkHelp.getLibName;
import static org.junit.jupiter.api.Assertions.*;

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
    }

    public enum errCodes implements EnumInt
    {
        no_err( 0),
        no_file(10),
        no_permission(200),
        no_socket(2100);

        private final int  value;
        errCodes(int v)
        {
            value = v;
        }

        @Override
        public int getCValue() {
            return value;
        }

        public static errCodes[] ptr() {return new errCodes[1];}
    }

    public record EnumStruct(
        int ivalue,
        intEnum eiValue,
        long lvalue,
        longEnum elValue){

        public static EnumStruct[] ptr(int i, long l)
        {
            return new EnumStruct[]{new EnumStruct(i, intEnum.MONDAY, l, SATURDAY)};
        }
    };

    public record EnumSimpleArraysStruct (

            @Array(length = 3) intEnum[] allDays,
            int countDays,
            @Ptr intEnum[] allDaysPtr
    ){};

    public record EnumArraysStruct(

        @Array(length = 3) intEnum[] allDays,
        int countDays,
        @Ptr intEnum[] allDaysPtr,
        errCodes error,
        longEnum weekend,
        @Array(length = 2) longEnum[] weekends,
        int countWeekends,
        @Ptr longEnum[] weekendPtr
        ){}

    public record EnumUnion
            (int u_i,
            errCodes u_err,
            intEnum u_weekday,
            longEnum u_weekend,
             UnionFieldIO unionIO
            )implements Union{

        public static EnumUnion[] ptr(UnionFieldIO io)
        {
            return new EnumUnion[] {new EnumUnion(0, errCodes.no_err, intEnum.MONDAY, SATURDAY, io)};
        }
    }

    public interface EnumLink extends Passport
    {
        long passEnum(intEnum ie, longEnum le);
        intEnum todayInt(@RefArg intEnum[] ie);
        longEnum todayLong(@RefArg longEnum[] ie);
        void todayTransfer(intEnum ie, @RefArg intEnum[] ie2, longEnum le, @RefArg longEnum[] le2);
        retEnum toRetEnum(int v);
        void readBackErrCode(int setErr, @RefArg errCodes[] code);
        long passSimpleEnumStruct(@RefArg EnumStruct[] enums);
        void passEnumWArrStruct(@RefArg EnumSimpleArraysStruct[] enums);
        void passComplexStructEnum(@RefArg EnumArraysStruct[] enums);
        void enumwithUnion(int field, long value, @RefArg EnumUnion[] eu);
    }

    record Link (PassType type, EnumLink link) {}
    static Link[] PassingEnums;

    @BeforeAll
    public static void startup() throws Throwable
    {
        System.setProperty("jpassport.build.home", "out/testing");
//        PassportFactory.loadMethodHandles(getLibName(), EnumLink.class);
        PassingEnums = new Link[] {
//                new Link(PassType.written, new EnumLink_impl(PassportFactory.loadMethodHandles(getLibName(), EnumLink.class))),
                new Link(PassType.written, PassportFactory.link_written(getLibName(), EnumLink.class)),
                new Link(PassType.byte_code, PassportFactory.link(getLibName(), EnumLink.class))
        };
    }

    @Test
    public void testSimpleEnum()
    {
        for (var link : PassingEnums)
        {
            long ret = link.link.passEnum(intEnum.WEDNESDAY, SATURDAY);
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

    @Test
    public void testIntEnum()
    {
        for (var link : PassingEnums) {

            for (var ec : errCodes.values())
            {
                errCodes[] ptr = errCodes.ptr();
                link.link.readBackErrCode(ec.getCValue(), ptr);
                assertEquals(ec, ptr[0]);
            }
        }
    }

    @Test
    public void testSimpleEnumStruct()
    {
        for (var link : PassingEnums) {

            EnumStruct[] pass = EnumStruct.ptr(intEnum.THURSDAY.ordinal(), longEnum.SUNDAY.getCValue());
            link.link.passSimpleEnumStruct(pass);
            assertEquals(intEnum.THURSDAY, pass[0].eiValue);
            assertEquals(longEnum.SUNDAY, pass[0].elValue);

            var inArr1 = new intEnum[] {intEnum.MONDAY, intEnum.WEDNESDAY, intEnum.FRIDAY};
            var inArr2 = new intEnum[] {intEnum.TUESDAY, intEnum.THURSDAY, intEnum.TUESDAY};

            EnumSimpleArraysStruct esa = new EnumSimpleArraysStruct(inArr1, 3, inArr2);
            var ptr = new EnumSimpleArraysStruct[] {esa};
            link.link.passEnumWArrStruct(ptr);
            assertArrayEquals(inArr2, ptr[0].allDays);
            assertArrayEquals(inArr1, ptr[0].allDaysPtr);
        }
    }

    @Test
    public void testComplexEnumStruct()
    {
        for (var link : PassingEnums) {

            var inArr1 = new intEnum[] {intEnum.MONDAY, intEnum.FRIDAY, intEnum.WEDNESDAY};
            var inArr2 = new intEnum[] {intEnum.TUESDAY, intEnum.THURSDAY, intEnum.MONDAY};

            var arg = new EnumArraysStruct(inArr1, 3, inArr2,
                    errCodes.no_err, SATURDAY, new longEnum[2], 1, new longEnum[]{SATURDAY});

            EnumArraysStruct[] pass = new EnumArraysStruct[] {arg};
            link.link.passComplexStructEnum(pass);

            assertArrayEquals(inArr2, pass[0].allDays);
            assertArrayEquals(inArr1, pass[0].allDaysPtr);
            assertEquals(errCodes.no_file, pass[0].error);
            assertEquals(SATURDAY, pass[0].weekends()[0]);
            assertEquals(SUNDAY, pass[0].weekends()[1]);
            assertNull(pass[0].weekendPtr);
        }
    }

    @Test
    public void testUnionEnum()
    {
        for (var link : PassingEnums) {
            var ptr = EnumUnion.ptr(UnionFieldIO.nativeIO(0, 1));
            link.link.enumwithUnion(1, errCodes.no_file.getCValue(), ptr);
            assertEquals(errCodes.no_file, ptr[0].u_err());

            ptr = EnumUnion.ptr(UnionFieldIO.nativeIO(0, 2));
            link.link.enumwithUnion(2, intEnum.THURSDAY.ordinal(), ptr);
            assertEquals(intEnum.THURSDAY, ptr[0].u_weekday());

            ptr = EnumUnion.ptr(UnionFieldIO.nativeIO(0, 3));
            link.link.enumwithUnion(3, SUNDAY.getCValue(), ptr);
            assertEquals(SUNDAY, ptr[0].u_weekend());
        }
    }

}
