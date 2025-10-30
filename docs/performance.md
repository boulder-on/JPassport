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

## Code
- [PassportJMH](../src/test/java/jpassport/test/comparison/CPassportJMH.java)
