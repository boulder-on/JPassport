package jpassport.test.comparison;

import jpassport.PassportFactory;
import jpassport.test.extracted.PassingData;
import jpassport.test.extracted.library_h;
import jpassport.test.structs.PassingArrays;
import org.openjdk.jmh.annotations.*;

import java.io.File;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Random;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED" })
public class CPassportJMH {

    public static void main(String[] args) throws Throwable {

        URLClassLoader classLoader = (URLClassLoader) CPassportJMH.class.getClassLoader();
        StringBuilder classpath = new StringBuilder();
        for (URL url : classLoader.getURLs()) {
            classpath.append(url.getPath()).append(File.pathSeparator);
        }
        System.setProperty("java.class.path", classpath.toString());        org.openjdk.jmh.Main.main(args);
    }


    @Setup
    public void init() throws Throwable
    {
        System.setProperty("jpassport.build.home", "out/testing");
        sp =  PassportFactory.link("libpassport_test", struct_passer.class);
        passStruct = new PassingDataJP(1, 2, 3, 4);

        var doublesArr = new double[]{1, 2, 3, 4, 5};
        var longArr = new long[]{6, 7, 8, 9, 10, 11, 12, 13};
        var doublePtr = new double[]{14, 15, 16};
        var longPtr = new long[]{17, 18, 19, 20};

        passArrays = new PassingArrays(doublesArr, longArr, doublePtr.length, doublePtr, longPtr.length, longPtr);

        var rand = new Random();
        passArr = rand.doubles(2048).toArray();
    }

    static struct_passer sp;
    private static PassingDataJP passStruct;
    private static PassingArrays passArrays;
    private static double[] passArr;

    @Benchmark
    public double usePassportSumD()
    {
        return sp.sumD(1.0, 2.0);
    }

    @Benchmark
    public double usePassportSumArrD()
    {
        return sp.sumArrD(passArr, passArr.length);
    }

    @Benchmark
    public double usePassportStruct()
    {
        PassingDataJP[] pd = new PassingDataJP[] {passStruct};
        double sum = sp.passStruct(pd);
        sum += pd[0].s_int() + pd[0].s_long() + pd[0].s_float() + pd[0].s_double();
        return sum;
    }

    @Benchmark
    public double usePassportArrays()
    {
        PassingArrays[] regArg = new PassingArrays[]{passArrays};
        double sum = sp.passStructWithArrays(regArg);
        sum += regArg[0].s_long().length + regArg[0].s_double().length +
                regArg[0].s_doublePtrCount() + regArg[0].s_longPtrCount() +
                regArg[0].s_doublePtr().length + regArg[0].s_longPtr().length;
        return sum;
    }

    @Benchmark
    public double useExtractSumD()
    {
        return library_h.sumD(1.0, 2.0);
    }

    @Benchmark
    public double useExtractSumArrD()
    {
        try (Arena a = Arena.ofConfined()){
            var memseg = a.allocateFrom(ValueLayout.JAVA_DOUBLE, passArr);
            return library_h.sumArrD(memseg, passArr.length);
        }
    }

    @Benchmark
    public double useExtractStruct()
    {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment struct = PassingData.allocate(a);
            PassingData.s_int(struct, passStruct.s_int());
            PassingData.s_long(struct, passStruct.s_long());
            PassingData.s_float(struct, passStruct.s_float());
            PassingData.s_double(struct, passStruct.s_double());
            double s = library_h.passStruct(struct);
            int i = PassingData.s_int(struct);
            long l = PassingData.s_long(struct);
            float f = PassingData.s_float(struct);
            double d = PassingData.s_double(struct);
            return s + i + l + f + d;
        }
    }

    @Benchmark
    public static double useExtractArrays()
    {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment struct = jpassport.test.extracted.PassingArrays.allocate(a);

            jpassport.test.extracted.PassingArrays.s_double(struct, MemorySegment.ofArray(passArrays.s_double()));
            jpassport.test.extracted.PassingArrays.s_long(struct, MemorySegment.ofArray(passArrays.s_long()));
            jpassport.test.extracted.PassingArrays.s_doublePtrCount(struct, passArrays.s_doublePtrCount());
            jpassport.test.extracted.PassingArrays.s_longPtrCount(struct, passArrays.s_longPtrCount());

            var ptrDoubles = a.allocateFrom(ValueLayout.JAVA_DOUBLE, passArrays.s_doublePtr());
            jpassport.test.extracted.PassingArrays.s_doublePtr(struct, ptrDoubles);
            var ptrLongs = a.allocateFrom(ValueLayout.JAVA_LONG, passArrays.s_longPtr());
            jpassport.test.extracted.PassingArrays.s_longPtr(struct, ptrLongs);

            double sum = library_h.passStructWithArrays(struct);
            var dd1 = jpassport.test.extracted.PassingArrays.s_double(struct).toArray(ValueLayout.JAVA_DOUBLE);
            var ll1 = jpassport.test.extracted.PassingArrays.s_long(struct).toArray(ValueLayout.JAVA_LONG);
            var lc = jpassport.test.extracted.PassingArrays.s_longPtrCount(struct);
            var dc = jpassport.test.extracted.PassingArrays.s_doublePtrCount(struct);
            var dd = ptrDoubles.toArray(ValueLayout.JAVA_DOUBLE);
            var ll = ptrLongs.toArray(ValueLayout.JAVA_LONG);
            return sum + dd1.length + ll1.length + lc + dc + dd.length + ll.length;
        }
    }

}
