package jpassport.test.callback;

import jpassport.Passport;

import java.lang.foreign.Addressable;

public interface CallbackNative extends Passport {
    int call_CB(Addressable fn, int v, double v2);
    void call_CBArr(Addressable fn, int[] vals, int count);
}
