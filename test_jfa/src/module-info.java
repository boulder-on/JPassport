module test.passport {
    requires jfa;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires jdk.incubator.foreign;

    requires org.junit.jupiter.api;
    requires org.junit.platform.engine;

    exports jfa.test;
    exports jfa.test.performance;
}