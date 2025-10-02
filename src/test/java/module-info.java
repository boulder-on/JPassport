module test.passport {
    requires jpassport;
    requires com.sun.jna;
    requires com.sun.jna.platform;

    requires org.junit.jupiter.api;
    requires org.junit.platform.engine;
    requires jmh.core;
    requires jmh.generator.annprocess;

    requires commons.csv;
    requires java.desktop;
    requires jdk.jdi;

    exports jpassport.test;
    exports jpassport.test.performance;
    exports jpassport.test.structs;
    exports jpassport.test.callback;
    exports jpassport.test.comparison;
    exports jpassport.test.testformats;
    opens jpassport.test.testformats;
    exports jpassport.test.unions;
    opens jpassport.test.unions;
}
