# Code generation

For small header files it's easy enough to generate the Java code you need by hand. For larger
projects with hundreds of native calls that you need to map it might be easier to start with
automatic header -> java interface generation.

The JPassport jar file is runnable and will create the java interface, records and enums you need.

```
java -jar JPassport-1.3.1-24.jar [full path to header file] [destination folder for generated code] [package name to use] [delimited list of include folders]
```

The parser will:

- Parse all #includes to make a single giant, ordered, temporary header file
- If a preprocessor is detected (Windows: clang, Linux or Mac: clang, gcc, cpp) then the temporary header file is preprocessed
  - IF no preprocessor is found then this step is skipped
  - standard includes are always skipped. ex #include <sdtio.h>, or anything using <>.
- The proprocessed version of the header is parsed and turned into
  - [header name]_h.java - containing the JPassport interface
  - The required records for mapping structs and unions
  - The required enums for mapping enums

The generated interface can be used normally as per the instructions in this guide.

## Limitations

The parsing and interpretation process can only make guesses about the code that should be generated.
The generated code will not be optimized perfectly for any given native API.

- All pointer parameters are given @RefArg, which means the contents will be written to native memory before a native call and read back from native memory after
  - This can be expensive if all you meant was to send a native method blank memory and read back a resonse
- Things like void* may be better handled with a MemoryBlock, but instead default to byte[]
- char* are always converted to String arguments. Maybe they should be byte[]?

It's really important to look at the documentation for the native code you are calling and make sure that the
generated interface function is reasonable