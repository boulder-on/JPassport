# JPassport - Java 20

JPassport works like [Java Native Access (JNA)](https://github.com/java-native-access/jna) but uses the 
[Foreign Linker API](https://openjdk.java.net/jeps/393) instead of JNI. 
Similar to JNA, you declare a Java interface that is bound to the external C library using method names.  
The goal of this project is to a) start working with the Foreign Linker, b) provide a drop in replacement
for JNA in simple applications.

As part of the Foreign Linker API a tool called [JExtract](https://github.com/openjdk/panama-foreign/blob/foreign-jextract/doc/panama_jextract.md) 
is available. Given a header file JExtract will build the classes needed to access a C library. If you have
a large header file then JExtract is likely an easier tool for you to use if you don't already have interfaces
defined for JNA.

**Java 20** is required to use this library. There are separate branches for Java 17, 18, 19 support.

The Foreign Linker API is in preview in Java 20, so you need to use --enable-preview to use this library.

# Getting Started

Download the source and run the maven build.

# Calling a native library example

The native api refers to these a "down calls".

C:
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
Java Usage for dynamic class creation:
```Java
Linked L = PassportFactory.link("libforeign", Linked.class);
int n = L.string_length("hello");
double sum = L.sumArrD(new double[] {1, 2, 3}, 3);
```

Java Usage to create a .java file for inclusion in your codebase:
```java
PassportWriter pw = new PassportWriter(Linked.class);
pw.writeModule(Path.of('output_location'));
```
Once the class is compiled, to use it:
```java
Linked l = new Linked_Impl(PassportFactory.loadMethodHandles("libforeign", Linked.class));
```

In order to use this library you will need to provide the VM these arguments:

__-Djava.library.path=[path to lib] --enable-native-access jpassport --enable-preview__

JPassport works by writing a class that implements your interface, compiling it and passing it back to you.
By default, the classes are written to the folder specified by System.getProperty("java.io.tmpdir").
If you provide the system property __"jpassport.build.home"__ then the classes will be written and
compiled there.

# Passing function pointers to native code example

The native API refers to these as "up calls". It's common in native programming to pass a function
as a pointer into another function. This technique is used to create call-backs. 

```java
public interface CallbackNative extends Passport
{
    void passMethod(MemoryAddress functionPtr);
}

public class MyCallback
{
    public void callbackMethod(int value, String name)
    {
        System.out.println(value + ". " + name);
    }
}

MyCallback cb = new MyCallback();
MemoryAddress functionPtr = PassportFactory.createCallback(cb, "callbackMethod");

CallbackNative cbn = PassportFactory.link("libforeign", CallbackNative.class);
cbn.passMethod(functionPtr);
```

At the moment this does not work for static method - that will be an easy enhancement.

# Performance
Performance was tested vs JNA, JNA Direct, and pure Java.

Performance of a method that passes 2 doubles:

![primative performance](passing_doubles.png)

Performance of a method that passes an array of doubles

![array performance](passing_double_arr.png)

(Tests were run on Windows 10 with an i7-10850H.)

# C Data Types Handled Automatically

| C Data Type       | Java Data Type        |
|-------------------|-----------------------|
| double            | double                |
| double*, double[] | double[]              |
| double**          | @PtrPtrArg double[][] |
| double[][]        | double[][]            |
| float             | float                 |
| float*, float[]   | float[]               |
| float**           | @PtrPtrArg float[][]  |
| float[][]         | float[][]             |
| long              | long                  |
| long*, long[]     | long[]                |
| long**            | @PtrPtrArg long[][]   |
| long[][]          | long[][]              |
| int               | int                   |
| int*, int[]       | int[]                 |
| int**             | @PtrPtrArg int[][]    |
| int[][]           | int[][]               |
| short             | short                 |
| short*, short[]   | short[]               |
| short**           | @PtrPtrArg short[][]  |
| short[][]         | short[][]             |
| char              | byte                  |
| char*             | byte[] or String      |
| char[]            | byte[] or String      |
| char**            | @PtrPtrArg byte[][]   |
| char[][]          | byte[][]              |
| structs           | Records               |

Any C argument that is defined with ** must be annotated with @PTrPtrArg in your Java interface.

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
void readB(int *val, int set)
{
    *val = set;
}
```

Java:
```Java
public interface Test extends Passport {
  void readD(@RefArg int[] d, int set);
}

Linked lib = PassportFactory.link("foreign_link", Test.class);
int[] ref = new int[1];
lib.readD(ref, 10);
```

Without the @RefArg, when ref[] is returned it will not have been updated.
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
        @StructPadding(bytes = 4) int s_int,
        long s_long,
        @StructPadding(bytes = 4) float s_float,
        double s_double) {
}

public record ComplexPassing(
        @StructPadding(bytes = 4) int ID,
        PassingData ts,
        @Ptr TestStruct tsPtr,
        String string) {
}

public interface PerfTest extends Passport {
    double passStruct(PassingData structData);
    double passComplex(@RefArg ComplexPassing[] complexStruct);
}
```
The most important thing to note here is the @StructPadding annotation. When a C compiler compiles a 
struct it will insert bytes of padding. It is critical for you to tell JPassport how much padding is either
before or after a structure member (negative numbers indicate pre-member padding). There is no standard about what padding will be used in any situation
so JPassport can't figure this out on its own (at least not that I'm aware of!). There are separate
annotation values for different platforms (windowsBytes, macBytes, linuxBytes).

The other important annotation is @Ptr, this lets JPassport know to treat the member of the struct as
a pointer to another struct.

Arrays of Records can only be 1 element long. Longer arrays of Records are not supported.

Records can contain primitives, arrays of primitives, pointers to arrays of primitives, Strings, or pointers
to other Records.

# Annotations
JPassport uses annotations as code generation hints. The available annotations are:

| Annotation                   | Usage          | Meaning                                                                                                                                                                |
|------------------------------|----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Array                        | Record members | If a C Struct takes a pointer to a primative array, this allows you to say what the size of the primative array is for RefArgs.                                        |
| NotRequired                  | Functions | If a function could not be found in the native library then no exception will be thrown. Use hasMethod("") to determine if the function was found. |                    |
| Ptr                          | Record members | If a C Struct takes a pointer to a primative or another struct then use this annotation.                                                                               |
| PtrPtrArg                    | Function argument| Any C function that takes a **<arg> must be annotated with this.                                                                                                       |
| RefArg                       | Function argument | Any C function that changes the contents of a pointer must be annotated with this to force the read back of the parameter                                              |
| RefArg (read_back_only=true) | Function argument | If you only need to pass a blank memory space for a method to fill, use this optimization, otherwise the values in the array are copied to memory that is passed to C. |
| StructPadding                | Record members | See the Javadoc or the above section on structs and records.                                                                                                           |

# Limitations

* Only arrays of Records of length 1 work.
* Only 1D and 2D arrays of primitives are supported, deeper nestings do not work.
* The interface file passed to PassportFactory and all required Records must be exported by your module.

Pointers as function returns only work in a limited fashion. Based on a C 
function declaration there isn't a way to tell exactly what a method is returning.
For example, returning int* could return any number of ints. There is
little a library like JPassport can do to handle returned pointers automatically. 
The work-around is for your interface function to return MemoryAddress. From there
it would be up to you to decipher the return. 

Declaring your interface method to take MemoryAddress objects allow you to
manage all of the data yourself (like JExtract).

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
    MemoryAddress mallocDoubles(int count);
    void freeMemory(MemoryAddress addr);
}

double[] testReturnPointer(int count) {
    MemoryAddress address = linked_lib.mallocDoubles(count);
    double[] values = Utils.toArrDouble(address, count);
    linked_lib.freeMemory(address);
    return values;
}
```
# Dependencies

JPassport itself only requires **Java 19** to build and run. There are separate Java 17 and 18 branches. 

The testing classes require:

* JNA 5.8.0
* JUnit 5.4.2 (later versions of JUnit do not play nice with modules yet)
* Apache Commons CSV 1.8 (only used to output performance data)

# Work To-Do
Roughly in order of importance

1. Support arrays of Records 
2. Support returning a Record
3. Use the Java Micro-benchmarking harness.
4. Compile classes in memory instead of from disk

# Release Notes
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