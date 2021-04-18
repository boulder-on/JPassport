# JPassport

JPassport is intended to be a replacement for Java Native Access (JNA). Similar to JNA, you can create an interface with the method definitions that exist in your library then JPassport does the rest. To the caller the library is just an interface.

The Foreign Linker API is still an incubator at this time and Java 15 at least is required to use this library.

The testing classes I have can be used to call JNA or JPassport. The Foreign Linker appears to be significantly faster than JNA for some circumstances. Passing primitives via the Foreign Linker looks like it has 
a performance benefit JNA. If you pass large arrays, there is a benefit to the Foreign Linker, but much less so.

Performance of method that passes 2 doubles:
![primative performance](passing_doubles.png)

Performance of method that passes an array of doubles
![array performance](passing_double_arr.png)

Example:

C:
```
int string_length(const char* string)
{
    return strlen(string);
}
```

Java:
```
public interface Linked extends Foreign {
   int string_length(String s);
}
```
Java Usage:
```
Linked L = LinkFactory.link("libforeign_link", Linked.class);
int n = L.string_length("hello");
```

In order to use this library you will need to provide the VM these arguments:

__-Djava.library.path=[path to lib] -Dforeign.restricted=permit__

# How it works

There are 2 stages to make the foreign linking to work:

1. The passed in interface is scanned for non-static methods. All non-static methods are found by name in the given library
2. A new class is built using the given interface and then compiled.

Using compiled classes rather than interface proxy objects makes the solution very efficient. Most of the real speed of the solution is from the Foreign Linker API.

# Library Data Types that work

Methods with the following data types for arguments can be called:
1. double, double*, double[], double**, double[][]
2. float, float*, float[], float**, float[][]
3. long, long*, long[], long**, long[][]
4. int, int*, int[], int**, int[][]
5. short, short*, short[], short**, short[][]
6. char, char*, char[], char**, char[][]

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
  void readD(**@RefArg** int[] d, int set);
}

Linked L = LinkFactory.link("libforeign_link", Test.class);
int ref[] = new int[1];
L.readD(ref, 10);
```

Without the @RefArg, when ref[] is returned it will not have been updated.

# Limitations

The interface file passed to LinkFactory must be exported by your module.

Struct arguments to C functions do not work, only basic types and C-strings.

# Dependencies

JPassport itself only requires at least Java 16 to build and run.

The testing classes require:

* JNA 5.8.0
* JUnit 5.4.2 (later versions of JUnit do not play nice with modules yet)

# Example
Review the test folder and the fl_dll C code to see how to make use of all of the parameters types.
