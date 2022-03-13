package jpassport.test.callback;

import jpassport.PassportFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCallback {

    @Test
    public void testCallback() throws Throwable {
        var callBack = PassportFactory.link("libpassport_test", CallbackNative.class);

        var myCB = new CallbackObj();

        int ret = callBack.call_CB(myCB.getAsFunctionPtr(), 5, 1);
        assertEquals(5, myCB.calls);
        assertEquals(30, ret);
    }
}
