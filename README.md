# JPassport

JPassport is intended to be a replacement for Java Native Access (JNA) but uses the Foreign Linker API instead of JNI. 
Similar to JNA, you can create an interface with the method definitions that exist in your library then JPassport does 
the rest. To the caller the library is just an interface.

The Foreign Linker API is still an incubator at this time and Java 16 at least is required to use this library.

# Getting started

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
```
public interface Linked extends Foreign {
   int string_length(String s);
   double sumArrD(double[] arr, int count);
}
```
Java Usage:
```
Linked L = LinkFactory.link("libforeign", Linked.class);
int n = L.string_length("hello");
double sum = L.sumArrD(new double[] {1, 2, 3}, 3);
```

In order to use this library you will need to provide the VM these arguments:

__-Djava.library.path=[path to lib] -Dforeign.restricted=permit__

# Performance
The testing classes I have use JNA, JNA Direct, JPassport and pure Java. The performance difference break down as:

Passing primatives: 
1. Java - About 4.5 times faster than JPassport
2. JPassport - About 5.5x faster than JNA Direct
3. JNA Direct - Very good, about 7-8x faster than JAN
4. JNA - Slowest by a significant margin

Passing Arrays:

There was less of a difference here.
1. Java - About 4 times faster than JPassport
2. JPassport - About 1.3x faster than JNA Direct (better at larger array sizes)
3. JNA Direct - About the same as JNA
4. JNA

NOTE: I tried without success to use th jextract tool. I was able to get it to generate code and I saw little substantive difference it what it tried to generate over what I generated.

Performance of method that passes 2 doubles:

![primative performance](passing_doubles.png)

Performance of method that passes an array of doubles

![array performance](passing_double_arr.png)


# How it works

There are 2 stages to make the foreign linking to work:

1. The interface is scanned for non-static methods. All non-static methods are found by name in the given library
2. A new class is built using the given interface and then compiled.

Using compiled classes rather than interface proxy objects makes the solution fairly efficient.

By default the classes are written to the folder specified by System.getProperty("java.io.tmpdir").
If you provide the system property "jpassport.build.home" then the classes will be written and
compiled there.

# Library Data Types that work

Methods with the following data types for arguments can be called:
1. double, double*, double[], double**, double[][]
2. float, float*, float[], float**, float[][]
3. long, long*, long[], long**, long[][]
4. int, int*, int[], int**, int[][]
5. short, short*, short[], short**, short[][]
6. char, char*, char[], char**, char[][]

Return types can be:
1. double
2. float
3. long
4. int
5. short
6. char
7. void
8. char*

If an argument is changed by the library call then an annotation is required. Ex

C:
```
void readB(int *val, int set)
{
    *val = set;
}
```

Java:
```
public interface Test extends Foreign {
  void readD(@RefArg int[] d, int set);
}

Linked L = LinkFactory.link("libforeign_link", Test.class);
int ref[] = new int[1];
L.readD(ref, 10);
```

Without the @RefArg, when ref[] is returned it will not have been updated.

# Limitations

* Struct arguments to C functions do not work.
* Pointers as function returns do not work
* The interface file passed to LinkFactory must be exported by your module.

# Dependencies

JPassport itself only requires at least Java 16 to build and run.

The testing classes require:

* JNA 5.8.0
* JUnit 5.4.2 (later versions of JUnit do not play nice with modules yet)

# Work To-Do
Roughly in order of importance

1. Support struct arguments 
2. Use the Java Micro-benchmarking harness
