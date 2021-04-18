module test.passport {
    requires passport;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires jdk.incubator.foreign;

    requires org.junit.jupiter.api;
    requires org.junit.platform.engine;

    exports jfa.test;
}