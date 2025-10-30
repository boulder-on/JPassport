package jpassport.test;

import jpassport.ErrorCapture;
import jpassport.Passport;
import jpassport.PassportFactory;
import jpassport.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static jpassport.test.TestLinkHelp.getLibName;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestErrCapture {
    public interface ErrCap extends Passport {
        void setAnError(ErrorCapture errs, int errval);
    }


    record Link (PassType type, ErrCap link) {}
    static Link[] testClass;

    @BeforeAll
    public static void startup() throws Throwable
    {
//        System.setProperty("jpassport.build.home", "out/testing");
//        System.setProperty("jna.library.path", System.getProperty("java.library.path"));
        testClass = new Link[] {new Link(PassType.byte_code, PassportFactory.link(getLibName(), ErrCap.class)),
                new Link(PassType.written, PassportFactory.link_written(getLibName(), ErrCap.class))};
    }

    @Test
    public void testErrorCapture()
    {
        for (var testFL : testClass) {
            ErrorCapture ec = new ErrorCapture();
            testFL.link.setAnError(ec, 10);

            if (Utils.getPlatform() == Utils.Platform.Windows)
                assertEquals(10, ec.getError("GetLastError"));
            else
                assertEquals(10, ec.getError("errno"));
        }
    }

}
