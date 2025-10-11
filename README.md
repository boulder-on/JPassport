# JPassport - Java 24

JPassport works like [Java Native Access (JNA)](https://github.com/java-native-access/jna) but uses the 
[Foreign Function and Memory API](https://openjdk.java.net/jeps/393) (FFM) instead of JNI. 
Similar to JNA, you declare a Java interface that is bound to the external C library using method names.  
The goal of this project is to provide a JNA-like experience for anyone wanting to access native code.

JNA is a much more mature project than this one and, it supports Java 8 and earlier. I am NOT a JNA expert.
JPassport was able to build on new language features to make this library (hopefully) simpler to use 
(ex. I think my struct support is cleaner because I could rely on the formal structure of records). 
If you cannot use a recent Java version then JNA is your best bet. However, if you can use a recent JRE then 
this library is much lighter weight than JNA (100 kb vs 3+ MB) and the programing should be simpler
for many cases. I also hope that in simple cases, changing to JPassport from existing JNA code shouldn't be
an onerous task.

The FFM team maintain a tool called [JExtract](https://github.com/openjdk/panama-foreign/blob/foreign-jextract/doc/panama_jextract.md). Given a header file (.h), JExtract will write the required
Java code to access the native code described in the header. In order to use the code it generates you need 
to be somewhat familiar with FFM and, if I understand correctly,
for proper struct support you need to have it generate code for each platform you want to support. 
In terms of performance, JExtract and JPassport are nearly identical. Since JExtract leaves you dealing
directly with FFM calls it could get performance gains by directly optimizing the calls you make (ex.
you do not need to read back all fields in a struct). 

Whether jextract or JPassport is a better tool for you depends on a) how large is your C API, b) where do 
you want the cognitive load. For large header files, jextract will create the code very quickly and
it will be correct (i.e. argument ordering will always be right). Since you need to build the interface
file and records yourself in JPassport, you could make a mistake. Jextract puts the cognitive load on every
function call you make, since you need to know lots about FFM to use the generated code. JPassport puts
the cognitive load on building the interfaces and records. When using JPassport you do not need to know
any FFM, it should look like bland Java code.

**Java 24 and later** are required to use this library. There are separate branches for Java 17 to 22.

[FFM](https://docs.oracle.com/en/java/javase/22/core/foreign-function-and-memory-api.html) is final in 
Java 22. The [Class-file API](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/lang/classfile/package-summary.html) is final in Java 24.

# Getting Started

### Source
Download the source and run the maven build, or use the maven dependency:

        <dependency>
            <groupId>io.github.boulder-on</groupId>
            <artifactId>JPassport</artifactId>
            <version>1.2.0-24</version>
        </dependency>

If you would like to see the Java code or byte code created use:
```java
System.setProperty("jpassport.build.home", [folder location]);
```

When using JPassport you will get a warning from the JVM like:

```
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by jpassport.PassportFactory in an unnamed module
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled
```

In order to avoid this warning, start your java process with the command line argument:

```
--enable-native-access=ALL-UNNAMED

or 

--enable-native-access=jpassport
```

If you get an __java.lang.UnsatisfiedLinkError__ then you will need to provide the path to your library
either as a command line argument or in your PassportFactory.link call.

__-Djava.library.path=[path to lib]__

# Calling a native library example

The native api refers to these as "down calls".

C, compiled into libforeign.dll or libforeign.so:
```
int string_length(const char* string)
{
    return strlen(string);
}

double sumArrD(const double *arr, const int count)
{
    double r = 0;
    for (int n = 0; n < count; ++n)
        r += arr[n];
    return r;
}
```

Java Interface:
```Java
public interface Linked extends Passport {
   int string_length(String s);
   double sumArrD(double[] arr, int count);
}
```
Standard usage - creates a class in memory using the Classfile API:
```Java
Linked L = PassportFactory.link("libforeign", Linked.class); 
int n = L.string_length("hello");
double sum = L.sumArrD(new double[] {1, 2, 3}, 3);
```

Static usage - writes a .java file to disk that you can include in your codebase:
```java
PassportWriter pw = new PassportWriter(Linked.class);
pw.writeModule(Path.of('output_location'));
```
Once the class is compiled, to use it:
```java
Linked l = new Linked_Impl(PassportFactory.loadMethodHandles("libforeign", Linked.class));
```

JPassport works in one of 2 modes:

1. Using the Class-file API to build a class that implements the given interface. 
   1. PassportFactory.link()
   2. This is a fast method that creates generally fast code (in my example code it takes about 20ms to generate the class)
2. Writing a class that implements your interface, compiling it and passing it back to you. 
   1. PassportFactory.link_written()
   2. The process to write, compile and load the class is relatively slow, but that is a one-time cost. (in my example code it takes about 2s to generate the class)
   3. This method creates code you can see and hand optimize (see jpassport.build.home)

# Callback example

The native API refers to these as "up calls". It's common in native programming to pass a function
as a pointer into another function. This technique is used to create call-backs.

```java
import jpassport.pointers.FunctionPtr;

public interface CallbackNative extends Passport {
    void passMethod(FunctionPtr functionPtr);
}

public class MyCallback {
    public void callbackMethod(int value, String name) {
        System.out.println(value + ". " + name);
    }
}

MyCallback cb = new MyCallback();
FunctionPtr functionPtr = PassportFactory.createCallback(cb, "callbackMethod");

CallbackNative cbn = PassportFactory.link("libforeign", CallbackNative.class);
cbn.passMethod(functionPtr);
```

At the moment, this does not work for static java methods.

__NOTE:__ If your callback method uses Java synchronization, or interacts with object member variables
then the thread must be a Java thread. In testing I've done, if a callback is called from a 
normal Linux Thread then synchronized blocks do not work.

# Performance
Performance was tested as Java vs JNA Direct vs JPassport vs JPassport using critical functions.
The Java implementation of the called methods is identical to the C version.
The Java comparison is included purely to demonstrate that calling native code isn't always beneficial
for performance. The time difference from pure java to the fastest alternative shows the minimum
speed up that C code requires in order to make using a native function worthwhile (if performance is your
only metric).

In the metrics below, lower is better (i.e. shorter time per call).
```
Passing 2 doubles

Benchmark                      Mode  Cnt     Score     Error  Units
java                           avgt   30     0.646 ±   0.158  ns/op
JNA Direct                     avgt   30    74.724 ±   3.808  ns/op
jextract                       avgt   30     8.024 ±   0.400  ns/op
JPassport                      avgt   30     7.919 ±   0.041  ns/op
JPassport Critical             avgt   30     2.685 ±   0.020  ns/op
```
With a simple native method, you can see the big jump in performance from JNA (74 ns/op) 
to FFM (8 ns/op). When the FFM method call is marked as critical there is a notable performance boost - 7 ns to 2.6 ns. Before you use the @Critical annotation on a method,
be sure to read the FFM documentation on it so you know the implications.

[Linker.Option.critical](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/lang/foreign/Linker.Option.html#critical(boolean))

```
Passing array of 2048 doubles and reading back the changes.

Benchmark                      Mode  Cnt     Score     Error  Units
java                           avgt   30  1733.553 ±   8.366  ns/op
JNA Direct                     avgt   30  3410.523 ±  22.081  ns/op
jextract                       avgt   30  2593.223 ±  49.704  ns/op
JPassport                      avgt   30  2492.028 ±  24.256  ns/op
JPassport Critical             avgt   30  1751.971 ±  12.745  ns/op
```
JPassport is optimized to recognize a critical function passing an array of primitives.
Critical functions can access the Java heap, which means the primitive array is passed
directly to native code with no extra data copies. It's very impressive to see that
the pure Java and FFM critical implementations are nearly identical.

This is a good time to note that the @Critical annotation should not be used everywhere.
The native method must be extremely short running. Also, if the native method changes
the primitive array contents, those changes are seen when the call returns, as though
the parameter was marked as @RefArg. 
```
Invalid - throws an exception
@Critical
void doSomething(double[] arr)

Valid
@Critical
void doSomething(@RefArg double[] arr)
```
Because, whether you like it or not, the argument will be treated as a @RefArg
```
Passing a struct with 4 primitive fields and reading back the changes.

Benchmark                      Mode  Cnt     Score     Error  Units
java                           avgt   30   1.934   ±   0.094  ns/op
jextract                       avgt   30   108.757 ±   1.691  ns/op
JPassport                      avgt   30   110.747 ±   6.986  ns/op
```
I'm a little surprised at the cost of sending a simple struct to native code vs
keeping it in Java. Maybe there are ways to optimize this? Either way, I'm glad 
that JPassport is keeping pace with jextract. Of course, a jextract user could
selectively initialize or read back only parts of the struct if they needed
better performance.

```
Passing a struct with 4 array fields and reading back the changes.

Benchmark                      Mode  Cnt     Score     Error  Units
java                           avgt   30    15.124 ±  0.077  ns/op
jextract                       avgt   30   609.397 ±  56.744  ns/op
JPassport                      avgt   30   654.924 ±  11.542  ns/op
```
JPassport is a little slower than jextract using this more complex struct.
I think there are ways to narrow this gap. As a starting point, a 7%
gap in performance is not bad if you consider the difference in complexity
of the code to the programmer.

(Tests were run on Windows 11 with an i7-10850H.)

# C Data Types Handled Automatically

| C Data Type       | Java Data Type                                              |
|-------------------|-------------------------------------------------------------|
| double            | double                                                      |
| double*, double[] | double[]                                                    |
| double**          | @PtrPtrArg double[][]                                       |
| double[][]        | double[][]                                                  |
| float             | float                                                       |
| float*, float[]   | float[]                                                     |
| float**           | @PtrPtrArg float[][]                                        |
| float[][]         | float[][]                                                   |
| long              | long                                                        |
| long*, long[]     | long[]                                                      |
| long**            | @PtrPtrArg long[][]                                         |
| long[][]          | long[][]                                                    |
| int               | int                                                         |
| int*, int[]       | int[]                                                       |
| int**             | @PtrPtrArg int[][]                                          |
| int[][]           | int[][]                                                     |
| short             | short                                                       |
| short*, short[]   | short[]                                                     |
| short**           | @PtrPtrArg short[][]                                        |
| short[][]         | short[][]                                                   |
| char              | byte                                                        |
| char*             | byte[] or String                                            |
| char[]            | byte[] or String                                            |
| char**            | @PtrPtrArg byte[][]                                         |
| char[][]          | byte[][]                                                    |
| structs           | Records                                                     |
| structs[]         | Records[]                                                   |
| structs[n]        | @Array(length=n) Records[]  (when part of a struct)         |
| structs**         | @PtrPtrArg Records[]                                        |
| char*, void *     | MemoryBlock                                                 |
| char*, void *     | GenericPtr (Useful if a native method returns a pointer)    |
| char*, void *     | MemorySegment (if you are doing your own memory magagement) |
| n/a               | Arena (see below)                                           |
| n/a               | ErrorCapture - must always be the first arg (see below)     |

Any C argument that is defined with ** must be annotated with @PTrPtrArg in your Java interface.

An **Arena** object can be added to any interface method signature. JPassport will use
that Arena to allocate memory instead of creating its own Arena. This can help with
efficiency by allowing you to hold a large block of memory open longer, rather
than regularly re-allocating it. Only one Arena can be passed.

Return types can be:
1. double
2. float
3. long
4. int
5. short
6. char
7. void
8. char* (maps to a Java String)
9. any pointer (see limitations)

If an argument is changed by the C library call then the @RefArg annotation is required for that argument. 
The argument also needs to be passed as an array of length one. Ex.

C:
```
void setInt(int *val, int set)
{
    *val = set;
}
```

Java:
```Java
public interface Test extends Passport {
  void setInt(@RefArg int[] d, int set);
}

Linked lib = PassportFactory.link("foreign_link", Test.class);
int[] ref = new int[1];
lib.setInt(ref, 10);
```

Without the @RefArg, when ref[] is returned it will not have been updated.

@RefArg can be used to annotate your entire interface. In that case, all methods
that use arrays will be handled as reference arguments.

## Structs and Records
In order to handle C Structs you must make an equivalent Java Record. For example
```
struct PassingData
{
    int s_int;
    long long s_long;
    float s_float;
    double s_double;
};

struct ComplexPassing
{
    int s_ID;
    struct PassingData s_passingData;
    struct PassingData* s_ptrPassingData;
    char* s_string;
};

double passSimple(struct PassingData* complex)
{
...
}

double passComplex(struct ComplexPassing* complex)
{
...
}
```

```java
import jpassport.annotations.RefArg;

public record PassingData(
        long s_long,
        double s_double) {
}

public record ComplexPassing(
        int ID,
        PassingData ts,
        @Ptr TestStruct tsPtr,
        String string) {
}

public interface PerfTest extends Passport {
    double passStruct(PassingData structData);
    double passComplex(@RefArg ComplexPassing[] complexStruct);
}
```

The @Ptr annotation lets JPassport know to treat the member of the struct as
a pointer to another struct.

Arrays of Records can only be 1 element long. Longer arrays of Records are not supported.

Records can contain primitives, arrays of primitives, GenericPtr, arrays of GenericPtr, pointers to arrays of primitives, Strings, or pointers
to other Records.

Records are used to model structs mainly for convenience in the library. Records have a 
well-defined constructor and set of automatically generated methods. This means that the 
library can make assumptions about how to pull data out of the class and how to pull data
out of memory and create a new class. Without these built in assumptions, the complexity 
of the code would be terrible, and using MiscUnsafe might be required.

Structs often require padding bytes to align on 4 or 8 byte boundaries. JPassport
calculates this padding automatically based on your platform. However, if it's done
wrong then you can use the  @StructPadding annotation to implement custom padding.
You can also you this annotation to ignore sections of a struct you don't care about.

## Unions
There is no natural analog of C's unions in Java. A union is effectively a struct where only
one member of the struct can have a value at any time. 

```C
union SimpleUnion
{
    int u_i;
    long u_l;
    short u_s;
};

void useSimpleUnion(int idx, short value, union SimpleUnion* simple)
{
    if (idx == 0)
        simple->u_i = value;
    if (idx == 1)
        simple->u_l = value;
    if (idx == 2)
        simple->u_s = value;
}
```
The above C code simply writes a value into the union. The JPassport code looks like:

```Java
public record SimpleUnion (
    short u_s,
    int u_i,
    long u_l,
    UnionFieldIO unionIO   //One union field must be of this type
) implements Union {}

public interface UnionCalls extends Passport {
    boolean useSimpleUnion(int idx, short value, @RefArg SimpleUnion[] simple);
}

import static jpassport.UnionFieldIO.fromNativeOnly;

UnionCalls uc = PassportFactory.link(getLibName(), UnionCalls.class);
SimpleUnion[] suArr  = new SimpleUnion[] {new SimpleUnion(0, 0, 0, fromNativeOnly("u_i"))};
uc.useSimpleUnion(1, (short)4, suArr);
```
Tha above code needs some explanation. Since there is no analog in Java for a union you need to give
JPassport hints on how to behave. UnionFieldIO contains those hints. You can use a UnionFieldIO class
to specify either:
 - The name of the field to write to or read from native memory
 - The zero based index of the field in the union to write to or read from native memory
The UnionFieldIO member of the record is NOT part of the real union, it is treated specially by JPassport.
The call fromNativeOnly("u_i") specifies that when reading the union back from memory, only read the field named "u_i".
i.e. the call:

```Java
new SimpleUnion(0, 0, 0, fromNativeOnly(1));
```
Means: do not write any union fields to native memory, but when reading back,
get the int u_i field (the 1 in the call is zero based, so the second field).

The same rules and annotations for records/structs work for records/unions.

# Capturing Errors
FFM has the ability to capture errors that occurred during a call. For instance,
some C methods will set "errno" during a call. Or in windows GetLastError() can contain 
valuable information. Those values need to be collected by the JVM as soon as the native
call is done. JPassport automates this for you with the ErrorCapture class.

```Java
import jpassport.ErrorCapture;

import java.lang.foreign.MemorySegment;

public interface ErrCapTest extends Passport {
    MemorySegment malloc(ErrorCapture err, int size);
}

ErrCapTest errCapTest = ...
ErrorCapture errors = new ErrorCapture();
MemorySegment mem = errCapTest.malloc(errors, Integer.MAX_VALUE);

if (mem.equals(MemorySegment.NULL)) {
    System.out.println(errors);
    int errno = errors.getError("errno");
    if (errno != 0)
        System.out.println("The error encountered was: " + errno);
}

```
Notice that ErrorCapture is not a real argument of the malloc function.
We just inject the ErrorCapture class here to grab the
errors that the JVM found before returning from the native call.

NOTE: The errors that are returned are done by name. Each platform will
have different names available. Calling MemorySegment.toString() will show
you all that are available.

# Debugging

Since JPassport is doing most of the heavy lifting when communicating with native code, it can be hard to diagnose 
errors and JVM crashes. If you need to debug things there are 2 main options:

1. Use PassportFactory.link_written() to write the java code, then use your debugger to step through each of the calls.
2. Use PassportFactory.link("lib_name", interface.class, **true**) - to build debuggable versions of the generated code.

Here is an example of building and using a debuggable class:

```java
public interface TestDebugging extends Passport
{
    double passComplex(@RefArg ComplexStruct[] complexStruct);
    double sumD(double d, double d2);
}

TestDebugging ffmImpl = PassportFactory.link(getLibName(), TestDebugging.class, true);

ffmImpl.setDebugHook(new DebugPassport() {
    
    //Called after each record is turned into a struct or union
    public void structBuilt(MemorySegment struct, MemoryLayout layout, String structName, Object[] inputsRecord) {
        //Utils.memToString() is a special function that tries to convert a MemorySegment to a meaningful string 
        //based on the given MemoryLayout
        System.out.println("Built: " + Utils.memToString(struct, layout, structName));
    }
    
    //Called immediately before the native call
    public void preNativeCall(String functionName, Object... arguments) {}
    //Called immediately after the native call
    public void postNativeCall(String functionName, Object retVal, Object... arguments) {}

    //Called after each MemorySegment is converted back into a record
    public void structReadBack(MemorySegment struct, MemoryLayout layout, String structName, Object outputRecord) {}
});

ffmImpl.passComplex(passComplex);
```
The DebugPassport interface allows you to set break points in code, or just print statements, so you can see where
things might be going wrong. Utils.memToString() can be a handy function to see what it in the native memory.

# Annotations
JPassport uses annotations as code generation hints. The available annotations are:

| Annotation                   | Usage          | Meaning                                                                                                                                                                |
|------------------------------|----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Array                        | Record members | If a C Struct takes a pointer to a primative array, this allows you to say what the size of the primative array is for RefArgs.                                        |
| NotRequired                  | Methods | If a function could not be found in the native library then no exception will be thrown. Use Passport.hasMethod("") to determine if the function was found.            |                    |
| Ptr                          | Record members | If a C Struct takes a pointer to a primative or another struct then use this annotation.                                                                               |
| PtrPtrArg                    | Function argument| Any C function that takes a **<arg> must be annotated with this.                                                                                                       |
| RefArg                       | Function argument | Any C function that changes the contents of a pointer must be annotated with this to force the read back of the parameter                                              |
| RefArg (read_back_only=true) | Function argument | If you only need to pass a blank memory space for a method to fill, use this optimization, otherwise the values in the array are copied to memory that is passed to C. |
| StructPadding                | Record members | See the Javadoc or the above section on structs and records.                                                                                                           |
| Critical                     | Methods  | Removes some overhead for calling a native method. Cannot be used when callbacks are used. See the JDK's Linker.Option.critical for more details.                      |
NOTE: Methods marked with @Critical and that pass primitive arrays will pass the Java heap version of the array
directly to native code. Any changes to the primitive array in native code will be mirrored in Java. As such, @Critical methods with 
a primitive array MUST mark the array as @RefArg, otherwise an exception will be thrown.

# Limitations

* Only 1D and 2D arrays of primitives are supported, deeper nestings do not work.
* The interface file passed to PassportFactory and all required Records must be exported by your module.

Pointers as function returns only work in a limited fashion. Based on a C 
function declaration, there isn't a way to tell exactly what a method is returning.
For example, returning int* could return any number of ints. There is
little a library like JPassport can do to handle returned pointers automatically. 
The work-around is for your interface function to return MemorySegment. From there
it would be up to you to decipher the return. 

Declaring your interface method to take MemorySegment objects allows you to
manage all the data yourself (like jextract).

```
double* mallocDoubles(const int count)
{
    double* ret = malloc(count * sizeof(double ));

    for (int n = 0; n < count; ++n)
        ret[n] = (double)n;

    return ret;
}

void freeMemory(void *memory)
{
    free(memory);
}
```

```Java
public interface TestLink extends Passport {
    MemorySegment mallocDoubles(int count);
    void freeMemory(MemorySegment addr);
}

double[] testReturnPointer(int count) {
    MemorySegment address = linked_lib.mallocDoubles(count);
    double[] values = Utils.toArrDouble(address, count);
    linked_lib.freeMemory(address);
    return values;
}
```
# Future work

If JPassport does not appear to meet your needs, or you're not sure how to accomplish what you
want with JPassport, please send me a message or open an issue. 

# Dependencies

JPassport itself only requires **Java 24 or later** to build and run. There are separate Java 17-22 branches. 


# Release Notes
- 1.3.0-24 (unreleased)
  - Union support
  - DebugPassport added so that you can set breakpoints in the generated code.
  - Improved efficiency of arrays of structs
  - For critical methods, arrays are passed as java heap memory
  - Added direct jextract and JNA Direct performance comparison
  - Made all method handles static final
  - Fixed handling of nulls in structs/records
  - Improved efficiency of null handling
  - Removed dead code
  - Updated code generation to work with inner classes
  - Removed m_ from generated code variable names
- 1.2.0-24
  - Moved all record/struct reading and writing to the Classfile API instead of reflection (for speed)
  - Added MemoryBlock as a valid struct member
  - Code reorganization to hide classes that are not part of the API that a programmer needs to care about.
  - Fixed some issue passing booleans.
  - Added ErrorCapture
  - Cleanup the code that writes a java class (PassportFactory.link_written())
  - Deprecated the proxy implementation
  - Added support for arrays of structs > length 1
  - Added support for arrays of pointers to structs.
  - Fixed an issue calculating the size of a struct
- 1.1.0-24
  - Add support for building classes with the Classfile API
- 1.0.1-22
  - Fixed an issue where System libraries could not be loaded (ex. malloc).
- 1.0.0-22
  - Full 1.0 since Java 22 has gone GA and the foreign function API is now official
  - Added MemoryBlock as a method argument to pass allocated memory to a foreign function.
  - An Arena can now be an argument to a method. The Arena will be used for allocations during the call. In some cases this may be an optimization. 
- 0.7.0-22
  - Support Java 22
  - Added support for arrays of GenericPointer
  - Added Pointer as a sub-class of GenericPointer for better JNA compatability
  - Added the ability to use a Proxy object rather than writing a full new class
    - Using a Proxy is faster to create, but slower to invoke. Proxies are much slower than invoking a normal method, but the code to handle the native call is much less optimized as well.  
  - The RefArg annotation can be added to an interface to indicate that all arrays should be read back after a call.
- 0.6.0-21
  - Support Java 21
  - Make specifying byte padding in records/structs optional.
- 0.6 
  - Added the version of Java the library uses to the version (0.6.0-[java version])
  - Added GenericPointer returns and method arguments.
  - Added @NotRequired annotation for methods that may not exist.
  - Default functions in the interface are now ignored.
- 0.5
  - Added the GenericPointer class to help with returning things like win32 HANDLEs
  - Added RefArg(read_back_only = true) to optimize the returning of reference arguments.
- 0.5
  - Fixed and issue where zero argument methods would not compile
  - Fixed issues where passing and receiving null values caused their own NullPointerExceptions
- 0.4
  - Original release
