package jpassport.test.callback;

import jdk.incubator.foreign.MemoryAddress;
import jpassport.Passport;

public interface CallbackNative extends Passport {
    int call_CB(MemoryAddress fn, int v, double v2);
}
