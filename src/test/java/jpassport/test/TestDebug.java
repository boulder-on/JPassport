package jpassport.test;

import jpassport.DebugPassport;
import jpassport.Passport;
import jpassport.PassportFactory;
import jpassport.Utils;
import jpassport.annotations.Ptr;
import jpassport.annotations.RefArg;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;

import static jpassport.test.TestLinkHelp.getLibName;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDebug {

    public record TestStruct(
            int s_int,
            long s_long,
            float s_float,
            double s_double) {}

    public record ComplexStruct(
            int ID,
            TestStruct ts,
            @Ptr TestStruct tsPtr,
            String string) { }


    public interface TestDebugging extends Passport
    {
        double passComplex(@RefArg ComplexStruct[] complexStruct);
        double sumD(double d, double d2);
    }

    static TestDebugging[] links;

    @BeforeAll
    public static void startup() throws Throwable
    {
        System.setProperty("jpassport.build.home", "out/testing");
        System.setProperty("jna.library.path", System.getProperty("java.library.path"));
        links = new TestDebugging[]{
                PassportFactory.link(getLibName(), TestDebugging.class, true),
                PassportFactory.link_written(getLibName(), TestDebugging.class, true)
        };

    }


    @Test
    public void testDebugStruct() throws Throwable {

        for (var testDebugging : links) {
            int[] debugCalls = new int[4];
            testDebugging.setDebugHook(new DebugPassport() {
                @Override
                public void structBuilt(MemorySegment struct, MemoryLayout layout, String structName, Object[] inputsRecord) {
                    System.out.println("Built: " + Utils.memToString(struct, layout, structName));
                    debugCalls[0]++;
                }

                @Override
                public void preNativeCall(String functionName, Object... arguments) {
                    System.out.println("Pre-native: " + functionName);
                    debugCalls[1]++;
                }

                @Override
                public void postNativeCall(String functionName, Object retVal, Object... arguments) {
                    System.out.println("Post-native: " + functionName);
                    debugCalls[2]++;
                }

                @Override
                public void structReadBack(MemorySegment struct, MemoryLayout layout, String structName, Object outputRecord) {
                    System.out.println("Read back: " + Utils.memToString(struct, layout, structName));
                    debugCalls[3]++;
                }
            });

            var complex = new ComplexStruct(
                    1,
                    new TestStruct(1, 2, 3, 4),
                    new TestStruct(5, 6, 7, 8),
                    "abcd");

            var passComplex = new ComplexStruct[]{complex};
            testDebugging.passComplex(passComplex);
            assertArrayEquals(new int[] {3, 1, 1, 3}, debugCalls);
        }
    }

    @Test
    public void testDebugSimple() throws Throwable {

        for (var testDebugging : links) {
            int[] debugCalls = new int[4];
            testDebugging.setDebugHook(new DebugPassport() {
                @Override
                public void structBuilt(MemorySegment struct, MemoryLayout layout, String structName, Object[] inputsRecord) {
                    System.out.println("Built: " + Utils.memToString(struct, layout, structName));
                    debugCalls[0]++;
                }

                @Override
                public void preNativeCall(String functionName, Object... arguments) {
                    System.out.println("Pre-native: " + functionName);
                    debugCalls[1]++;
                }

                @Override
                public void postNativeCall(String functionName, Object retVal, Object... arguments) {
                    System.out.println("Post-native: " + functionName);
                    debugCalls[2]++;
                }

                @Override
                public void structReadBack(MemorySegment struct, MemoryLayout layout, String structName, Object outputRecord) {
                    System.out.println("Read back: " + Utils.memToString(struct, layout, structName));
                    debugCalls[3]++;
                }
            });

            testDebugging.sumD(1, 2);
            assertArrayEquals(new int[] {0, 1, 1, 0}, debugCalls);
        }
    }
}
