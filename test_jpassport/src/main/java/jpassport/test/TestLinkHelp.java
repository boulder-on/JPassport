package jpassport.test;

import jdk.incubator.foreign.MemoryAddress;

public class TestLinkHelp {

    public static boolean testMallocDouble(TestLink link)
    {
        return link.mallocDoubles(0) == MemoryAddress.NULL;
    }
}
