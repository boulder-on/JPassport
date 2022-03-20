package jpassport.test;

import jdk.incubator.foreign.MemoryAddress;

import java.util.Locale;

public class TestLinkHelp {

    public static boolean testMallocDouble(TestLink link)
    {
        return link.mallocDoubles(0) == MemoryAddress.NULL;
    }


    public static String getLibName()
    {
        if (System.getProperty("os.name").equalsIgnoreCase("linux"))
            return "libpassport_test.so";
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows"))
            return "libpassport_test";

        throw new IllegalArgumentException("Unknown OS: " + System.getProperty("os.name"));
    }
}
