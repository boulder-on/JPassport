package jpassport.test.callback;

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.MemoryAddress;
import jpassport.Passport;

public interface CallbackNative extends Passport {
    int call_CB(Addressable fn, int v, double v2);
    void call_CBArr(Addressable fn,  int[] vals, int count);
}
