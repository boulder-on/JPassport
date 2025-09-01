package jpassport.test;

import jpassport.ErrorCapture;
import jpassport.Passport;

public interface TestLinkByteCode extends Passport {
    void setAnError(ErrorCapture errs, int errval);

}
