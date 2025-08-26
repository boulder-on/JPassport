package jpassport.test.callback;

import jpassport.pointers.FunctionPtr;
import jpassport.PassportFactory;


import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

public class CallbackObj {
    public int calls = 0;

    public int callback(int n, double m) {
        calls++;
        return (int) (n + m);
    }

    public FunctionPtr getAsFunctionPtr()
    {
        return PassportFactory.createCallback(this, "callback");
    }

    public int sum = 0;

//    public void callbackArr(MemorySegment ptr, int count) {
//        var vals = Utils.toArr(ValueLayout.JAVA_INT, ptr, ptr.address(), count);
//        sum = Arrays.stream(vals).sum();
//    }

    public void callbackArr(MemorySegment ptr, int count) {
        var vals = toArr(ValueLayout.JAVA_INT, ptr, count);
        sum = Arrays.stream(vals).sum();
    }

    public FunctionPtr getAsFunctionArrPtr()
    {
        return PassportFactory.createCallback(this, "callbackArr");
    }

    public static int[] toArr(ValueLayout.OfInt layout, MemorySegment addr, int count) {
        if (MemorySegment.NULL.equals(addr))
            return null;

        if (addr.byteSize() == 0)
        {
            return  MemorySegment.ofAddress(addr.address()).
                    reinterpret((long)Integer.BYTES * count).toArray(ValueLayout.JAVA_INT);
        }

        return addr.asSlice(0, (long) count * Integer.BYTES).toArray(layout);
    }

}
