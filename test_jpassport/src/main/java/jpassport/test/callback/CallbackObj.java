package jpassport.test.callback;

import jdk.incubator.foreign.MemoryAddress;
import jpassport.PassportFactory;

public class CallbackObj {
    public int calls = 0;

    public int callback(int n, double m) {
        calls++;
        return (int) (n + m);
    }

    public MemoryAddress getAsFunctionPtr()
    {
        return PassportFactory.createCallback(this, "callback");
    }
}
