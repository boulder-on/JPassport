# Code generation

For small header files it's easy enough to generate the Java code you need by hand. For larger
projects with hundreds of native calls that you need to map it might be easier to start with
automatic header java interface generation.

The JPassport jar file is runnable and will create the java interface, records and enums you need.

```
java -jar JPassport-1.3.1-24.jar [full path to header file] [destination folder for generated code] [package name to use] [preprocessor options]
```

You can also write code to generate the java files if that is easier

```java
String[] includeFolders = {
        "C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\um",
        "C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\shared",
        "C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\ucrt",
        "C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\VC\\Tools\\MSVC\\14.40.33807\\include"};

String[] genArgs = {
  "C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\um\\winbase.h", // the header file to parse
  "JWin32/src/main/java/jwin32b/winbase",  //the folder for output
  "jwin32b.winbase",    //the package name to use
};

List<String> allArgs = new ArrayList<>(Arrays.asList(genArgs));
for (int n = 0; n < includeFolders.length; n++)
    allArgs.add("-I" + includeFolders[n]);  //adds the include folders for the preprocessor

var progArgs = allArgs.toArray(new String[0]);
HeaderToPassport.main(progArgs);

```
In order to parse your header file a preprocessor is required:

- Windows
  - clang (install LLVM)
- Linux, Mac
  - clang
  - gcc
  - cpp

The parser will:

- Feed the header into the C preprocessor using the -E option which outputs only the preprocessed C code
- The proprocessed version of the header is parsed and turned into
  - [header name]_h.java - containing the JPassport interface
  - The required records for mapping structs and unions
  - The required enums for mapping enums

The generated interface can be used normally as per the instructions in this guide.

## Limitations
- Unions defined within structs are not properly parsed
- Some keywords are not handled: volatile, signed
- Variadic arguments (...) are not handled!

In general, you will get output code. There may be errors in it that you need to correct by hand.

The parsing and interpretation process can only make guesses about the code that should be generated.
The generated code will not be optimized perfectly for any given native API.

- All pointer parameters are given @RefArg, which means the contents will be written to native memory before a native call and read back from native memory after
  - This can be expensive if all you meant was to send a native method blank memory and read back a resonse
- Things like void* may be better handled with a MemoryBlock, but instead default to byte[]
- char* are always converted to String arguments. Maybe they should be byte[]?

It's really important to look at the documentation for the native code you are calling and make sure that the
generated interface function is reasonable

For example, here is a C method declaration from the win32 API:

```C
BOOL DnsHostnameToComputerNameExW(LPCWSTR Hostname, LPWSTR ComputerName, LPDWORD nSize);
```
This code will translate to this Java interface method

```Java
int DnsHostnameToComputerNameExW(String Hostname, String ComputerName, @RefArg long[] nSize);
```
In this case, ComputerName will be filled with the computer name when this method returns. There's no way
for that to happen in this Java interface. A more accurate translation of this interface is:

```Java
int DnsHostnameToComputerNameExW(String Hostname, @RefArg byte[] ComputerName, @RefArg long[] nSize);
```
After the call returns you would need to convert the bytes to a string yourself.

