module jpassport {
    requires jdk.compiler;
    requires java.desktop;


    exports jpassport;
    exports jpassport.annotations;
    exports jpassport.pointers;
    exports jpassport.enums;
    exports jpassport.parser;
    exports jpassport.parser.clang.types;
}