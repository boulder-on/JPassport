package jpassport.test;

import jpassport.ErrorCapture;
import jpassport.PassportFactory;
import jpassport.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static jpassport.test.TestLinkHelp.getLibName;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJPassportByteCode {
    static TestLinkByteCode testClass;

    @BeforeAll
    public static void startup() throws Throwable
    {
        System.setProperty("jpassport.build.home", "out/testing");
        System.setProperty("jna.library.path", System.getProperty("java.library.path"));
        testClass = PassportFactory.link(getLibName(), TestLinkByteCode.class);

//        new PassportBuilder<TestLink>(TestLink.class, "none.none", "testlinkImpl");
    }

    @Test
    public void testErrorCapture()
    {
        ErrorCapture ec = new ErrorCapture();
        testClass.setAnError(ec, 10);

        if (Utils.getPlatform() != Utils.Platform.Windows)
            assertEquals(10, ec.getError("errno"));
    }

}
