# JPassport

JPassport works like [Java Native Access (JNA)](https://github.com/java-native-access/jna) but uses the 
[Foreign Linker API](https://openjdk.java.net/jeps/393) instead of JNI. 
Similar to JNA, you declare a Java interface that is bound to the external C library using method names.  
JPassport is not as full featured as JNA at this time (see the Limitations below) but for simple 
applications it would be a near drop in replacement.

As part of the Foreign Linker API a tool called [JExtract](https://github.com/openjdk/panama-foreign/blob/foreign-jextract/doc/panama_jextract.md) 
is available. Given a header file JExtract will build the classes needed to access a C library. The
main differences with JPassport and JExtract are:

* JExtract requires a header file, JPassport does not.
* JExtract requires you to convert Java objects to MemoryAddress objects manually, JPassport will handle some conversions for you.
* JExtract builds the classes ahead of time, JPassport builds classes at run time.

Which tool is right for you will greatly depend on your situation.

The Foreign Linker API is still an incubator at this time and Java 16 at least is required to use this library.

# Getting Started

Download the source and run the maven build, or run the ant build.

# Example

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

Java:
```Java
public interface Linked extends Passport {
   int string_length(String s);
   double sumArrD(double[] arr, int count);
}
```
Java Usage:
```Java
Linked L = PassportFactory.link("libforeign", Linked.class);
int n = L.string_length("hello");
double sum = L.sumArrD(new double[] {1, 2, 3}, 3);
```

In order to use this library you will need to provide the VM these arguments:

__-Djava.library.path=[path to lib] -Dforeign.restricted=permit__

By default, the classes are written to the folder specified by System.getProperty("java.io.tmpdir").
If you provide the system property __"jpassport.build.home"__ then the classes will be written and
compiled there.

# Performance
Performance was tested vs JNA, JNA Direct, and pure Java.

Performance of a method that passes 2 doubles:

![primative performance](passing_doubles.png)

Performance of a method that passes an array of doubles

![array performance](passing_double_arr.png)

(Tests were run on Windows 10 with an i7-10850H.)

# C Data Types Handled Automatically

C Data Type | Java Data Type
------------|---------------
double|double
double*, double[] | double[]
double** | @PtrPtrArg double[][]
double[][] | double[][]
float|float
float*, float[] | float[]
float** | @PtrPtrArg float[][]
float[][] | float[][]
long|long
long*, long[] | long[]
long** | @PtrPtrArg long[][]
long[][] | long[][]
int|int
int*, int[] | int[]
int** | @PtrPtrArg int[][]
int[][] | int[][]
short|short
short*, short[] | short[]
short** | @PtrPtrArg short[][]
short[][] | short[][]
char|byte
char*| byte[] or String
char[] | byte[] or String
char** | @PtrPtrArg byte[][]
char[][] | byte[][]

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

If an argument is changed by the C library call then the @RefArg annotation is required for that argument. Ex.

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

Linked lib = PassportFactory.link("libforeign_link", Test.class);
int ref[] = new int[1];
lib.readD(ref, 10);
```

Without the @RefArg, when ref[] is returned it will not have been updated.

# Limitations

* Struct arguments to C functions do not work.
* The interface file passed to LinkFactory must be exported by your module.

Pointers as function returns only work in a limited fashion. Based on a C 
function declaration there isn't a way to tell exactly what a method is returning.
For example, returning int* could return any number of ints. There is
little a library like JPassport can do to handle returned pointers automatically. 
The work-around is for your interface function return MemoryAddress. From there
it would be up to you to decipher the return. 

Declaring your interface method to take MemoryAddress objects allow you to
manage passing your own structs as well.

```
double* mallocDoubles(const int count)
{
    double* ret = malloc(count *sizeof(double ));

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
    double[] values = new double[count];
    // Use the provided Util function to copy data out of the MemorySegment
    Utils.toArr(values, address.asSegmentRestricted(count * Double.BYTES));
    linked_lib.freeMemory(address);
    return values;
}

```
# Dependencies

JPassport itself only requires at least Java 16 to build and run.

The testing classes require:

* JNA 5.8.0
* JUnit 5.4.2 (later versions of JUnit do not play nice with modules yet)
* Apache Commons CSV 1.8 (only used to output performance data)

# Work To-Do
Roughly in order of importance

1. Support struct arguments.
2. Use the Java Micro-benchmarking harness.
3. Compile classes in memory instead of from disk
