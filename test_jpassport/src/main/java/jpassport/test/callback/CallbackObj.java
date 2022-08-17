package jpassport.test.callback;

import jpassport.PassportFactory;
import jpassport.Utils;

import java.lang.foreign.Addressable;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

public class CallbackObj {
    public int calls = 0;

    public int callback(int n, double m) {
        calls++;
        return (int) (n + m);
    }

    public Addressable getAsFunctionPtr()
    {
        return PassportFactory.createCallback(this, "callback");
    }

    public int sum = 0;

//    public void callbackArr(MemorySegment ptr, int count) {
//        var vals = Utils.toArr(ValueLayout.JAVA_INT, ptr, ptr.address(), count);
//        sum = Arrays.stream(vals).sum();
//    }

    public void callbackArr(MemoryAddress ptr, int count) {
        var vals = Utils.toArr(ValueLayout.JAVA_INT, ptr, count);
        sum = Arrays.stream(vals).sum();
    }

    public Addressable getAsFunctionArrPtr()
    {
        return PassportFactory.createCallback(this, "callbackArr");
    }
}
