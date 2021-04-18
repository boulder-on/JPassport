# JFA
Java Foreign Access

JFA is intended to be a replacement for Java Native Access (JNA). Similar to JNA, you can create an interface with the method definitions that exist in your library then JFA does the rest. To the caller the library is just an interface.

Example:

C:
int string_length(const char* string)
{
    return strlen(string);
}

Java:
public interface Linked extends Foreign {
   int string_length(String s);
}

Java Usage:

Linked L = LinkFactory.link("libforeign_link", Linked.class);
int n = L.string_length("hello");

**How it works**

There are 2 stages to make the foreign linking to work:

1. The passed in interface is scanned for non-static methods. All non-static methods are found by name in the given library
2. A new class is built using the given interface and then compiled.

Using compiled classes rather than interface proxy objects makes the solution very efficient. Most of the real speed of the solution is from the Foreign Linker API.

**Library Data Types that work**

Methods with the following data types for arguments can be called:
1. double, double*, double[], double**, double[][]
2. float, float*, float[], float**, float[][]
3. long, long*, long[], long**, long[][]
4. int, int*, int[], int**, int[][]
5. short, short*, short[], short**, short[][]
6. char, char*, char[], char**, char[][]

If an argument is changed by the library call then an annotation is required. Ex

C:
void readB(int *val, int set)
{
    *val = set;
}

Java:

public interface Test extends Foreign {
  void readD(**@RefArg** int[] d, int set);
}

Linked L = LinkFactory.link("libforeign_link", Test.class);
int ref[] = new int[1];
L.readD(ref, 10);

Without the @RefArg, when ref[] is returned it will not have been updated.
