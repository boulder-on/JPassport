package jpassport.test.unions;

import jpassport.Passport;
import jpassport.PassportFactory;
import jpassport.Union;
import jpassport.UnionFieldIO;
import jpassport.annotations.*;


import jpassport.test.PassType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


import static jpassport.UnionFieldIO.fromNativeOnly;
import static jpassport.UnionFieldIO.nativeIO;
import static jpassport.test.TestLinkHelp.getLibName;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUnions {

    public record SimpleUnion (
            short u_s,
            int u_i,
            long u_l,
            float u_f,
            double u_d,
            UnionFieldIO unionIO
    ) implements Union {}

    public record PassingData(    int s_int,
            long s_long,
            float s_float,
            double s_double
    ){}

    public record UnionWithStruct (
            long u_i,
            PassingData u_pd,
            short u_s,
            UnionFieldIO unionIO
    ) implements Union {}

    public record UnionWithArrays (
          @Array(length = 5) long[] u_i,
          @Ptr long[] u_ip,
          UnionFieldIO unionIO
    ) implements Union {}

    public interface UnionCalls extends Passport {
        boolean useSimpleUnion(int idxSrc, int idxDest, @RefArg SimpleUnion[] simple);
        boolean useUnionWithStruct(PassingData srcVals, @RefArg UnionWithStruct[] withStruct);
        boolean useUnionWithArray(int direction,  @RefArg UnionWithArrays[] withArrays);
    }

    record Link (PassType type, UnionCalls link) {}
    static Link[] PassingUnions;

    @BeforeAll
    public static void startup() throws Throwable
    {
        System.setProperty("jpassport.build.home", "out/testing");
        PassingUnions = new Link[] {
                new Link(PassType.written, PassportFactory.link_written(getLibName(), UnionCalls.class)),
                new Link(PassType.byte_code, PassportFactory.link(getLibName(), UnionCalls.class))
        };
    }

    @Test
    public void testPrimitivesUnion()
    {
        for (Link link : PassingUnions)
        {
            SimpleUnion[] suArr  = new SimpleUnion[] {new SimpleUnion((short)7, 8, 0, 0, 0, nativeIO(0, 4))};
            link.link.useSimpleUnion(0, 4, suArr);

            assertEquals(7, suArr[0].u_s);
            assertEquals(7, suArr[0].u_d);
            assertEquals(8, suArr[0].u_i);
            assertEquals(0, suArr[0].u_l);
        }
    }

    @Test
    void testUnionWithStruct()
    {
        for (Link link : PassingUnions) {
            var pd = new PassingData(1, 2, 3, 4);
            var uws = new UnionWithStruct[] {new UnionWithStruct(0, new PassingData(0,0,0,0), (short)0, fromNativeOnly(1))};
            link.link.useUnionWithStruct(pd, uws);

            assertEquals(0, uws[0].u_i);
            assertEquals(0, uws[0].u_s);
            assertEquals(pd.s_int, uws[0].u_pd.s_int);
            assertEquals(pd.s_long, uws[0].u_pd.s_long);
            assertEquals(pd.s_float, uws[0].u_pd.s_float);
            assertEquals(pd.s_double, uws[0].u_pd.s_double);
        }
    }

    @Test
    void testUnionWithArrays()
    {
        for (Link link : PassingUnions)
        {
            var wa = new UnionWithArrays(new long[] {1,2,3,4,5}, new long[5],nativeIO(0, 1));
            var uwa = new UnionWithArrays[] {wa};
            link.link.useUnionWithArray(1, uwa);
            assertArrayEquals(wa.u_i, uwa[0].u_ip);

            wa = new UnionWithArrays(new long[5], new long[] {10,11,12,13,14},nativeIO(1, 0));
            uwa = new UnionWithArrays[] {wa};
            link.link.useUnionWithArray(-1, uwa);
            assertArrayEquals(wa.u_ip, uwa[0].u_i);
        }
    }
}
