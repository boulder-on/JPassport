package jpassport.test.callback;

import jpassport.pointers.FunctionPtr;
import jpassport.Passport;

public interface CallbackNative extends Passport {
    int call_CB(FunctionPtr fn, int v, double v2);
    void call_CBArr(FunctionPtr fn, int[] vals, int count);
}
