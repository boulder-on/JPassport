package jpassport.test.callback;

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.MemoryAddress;
import jpassport.PassportFactory;
import jpassport.Utils;

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

    public void callbackArr(MemoryAddress ptr, int count) {
        var vals = Utils.toArrInt(ptr, count);
        sum = Arrays.stream(vals).sum();
    }

    public Addressable getAsFunctionArrPtr()
    {
        return PassportFactory.createCallback(this, "callbackArr");
    }
}
