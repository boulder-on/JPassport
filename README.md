# JPassport - Java 24

JPassport works like [Java Native Access (JNA)](https://github.com/java-native-access/jna) but uses the 
[Foreign Function and Memory API](https://openjdk.java.net/jeps/393) (FFM) instead of JNI. 
Similar to JNA, you declare a Java interface that is bound to the external C library using method names.  
The goal of this project is to provide a JNA-like experience for anyone wanting to access native code.

JNA is a much more mature project than this one and, it supports Java 8 and earlier. 
JPassport was able to build on new language features to make this library (hopefully) simpler to use.
If you cannot use a recent Java version then JNA is your best bet. However, if you can use a recent JRE then 
this library is much lighter weight than JNA (100 kb vs 3+ MB) and the programing should be simpler
for many cases. I also hope that in simple cases, changing to JPassport from existing JNA code shouldn't be
an onerous task.

The FFM team maintain a tool called [JExtract](https://github.com/openjdk/panama-foreign/blob/foreign-jextract/doc/panama_jextract.md). Given a header file (.h), JExtract will write the required
Java code to access the native code described in the header. In order to use the code it generates you need 
to be somewhat familiar with FFM and, if I understand correctly,
for proper struct support you need to have it generate code for each platform you want to support. 
In terms of [performance](docs/performance.md), JExtract and JPassport are nearly identical. Since JExtract leaves you dealing
directly with FFM calls it could get performance gains by directly optimizing the calls you make (ex.
you do not need to read back all fields in a struct). 

Whether jextract or JPassport is a better tool for you depends on a) how large is your C API, b) where do 
you want the cognitive load. For large header files, jextract will create the code very quickly and
it will be correct (i.e. argument ordering will always be right). However, in my experiment using jextract
on [blis](https://github.com/flame/blis) I found that jextract produced a truly unusable API (>125k lines of code). 
Since you need to build the interface
file and records yourself in JPassport, you could make a mistake. Jextract puts the cognitive load on every
function call you make, since you need to know lots about FFM to use the generated code. JPassport puts
the cognitive load on building the interfaces and records. When using JPassport you do not need to know
any FFM, it should look like bland Java code.

**Java 24 and later** are required to use this library. There are separate branches for Java 17 to 22.

[FFM](https://docs.oracle.com/en/java/javase/22/core/foreign-function-and-memory-api.html) is final in 
Java 22. The [Class-file API](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/lang/classfile/package-summary.html) is final in Java 24.

# Contents
* **[Getting Started](#Getting-Started)**
* **[Automatically generating JPassport interfaces](docs/code_generation.md)**
* **[Calling native code](docs/simple_example.md)**
* **[Callbacks](docs/creating_callbacks.md)**
* **[Data type mapping](docs/data_type_mapping.md)**
  * **[Structs](docs/structs.md)**
  * **[Unions](docs/unions.md)**
  * **[Enums](docs/enums.md)**
* **[Annotations](docs/annotations.md)**
* **[Trouble Shooting](docs/trouble_shooting.md)**
  * **[Debugging](docs/debugging.md)** 
  * **[Capturing errors](docs/err_cap.md)**
* **[Performance](docs/performance.md)**
* **[Limitations](docs/limitations.md)**
* **[Release notes](docs/release_notes.md)**
----

# Getting Started

### Source
Download the source and run the maven build, or use the maven dependency:

        <dependency>
            <groupId>io.github.boulder-on</groupId>
            <artifactId>JPassport</artifactId>
            <version>1.5.0-24</version>
        </dependency>

### Dependencies

JPassport itself only requires **Java 24 or later** to build and run. There are separate Java 17-22 branches.

## Future work

If JPassport does not appear to meet your needs, or you're not sure how to accomplish what you
want with JPassport, please send me a message or open an issue. 
