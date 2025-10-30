# Mapping C data types to Java

All primitive types are treated the same by JPassport.

In the table, primitive refers to: boolean, byte, short, int, long, float, double.

| C Data Type    | Java Data Type                                                     |
|----------------|--------------------------------------------------------------------|
| primitive      | primitive. ex. a C float maps to a Java float                      |
| primitive*,    | primitive[]                                                           |
| primitive[],   | primitive[]                                                           |
| primitive**    | @PtrPtrArg primitive[][]                                           |
| primitive[][]  | primitive[][]                                                      |
| struct         | Records  (see [Structs](structs.md))                               |
| structs[]      | Records[]                                                          |
| structs[n]     | @Array(length=n) Records[]  (when part of a struct)                |
| structs**      | @PtrPtrArg Records[]                                               |
| union          | Records extending Union  (see [Unions](unions.md))                 |
| union[]        | Records[] extending Union                                          |
| union[n]       | @Array(length=n) Records[]  (when part of a struct)                |
| union**        | @PtrPtrArg Records[] extending Union                               |
| enum           | Enum or Enum extending EnumInt or EnumLong (see [Enums](enums.md)) |
| enum*          | Enum[] or Enum[] extending EnumInt or EnumLong                     |
| char*, void *  | MemoryBlock                                                        |
| char*, void *  | GenericPtr (Useful if a native method returns a pointer)           |
| char*, void *  | MemorySegment (if you are doing your own memory management)        |
| n/a            | Arena (see below)                                                  |
| n/a            | ErrorCapture - must always be the first arg (see below)            |

An **Arena** object can be added to any interface method signature. JPassport will use
that Arena to allocate memory instead of creating its own Arena. This can help with
efficiency by allowing you to hold a large block of memory open longer, rather
than regularly re-allocating it. Only one Arena can be passed.

Return types can be:
1. Primitive
2. void
3. char* (maps to a Java String)
4. any pointer (see limitations)
10. Java Enums (see Enums)

If an argument is changed by the C library call then the @RefArg annotation is required for that argument.
The argument also needs to be passed as an array. Ex.

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
    //instead of a pointer to ints, the Java version requires an array
  void setInt(@RefArg int[] d, int set);
}

Linked lib = PassportFactory.link("foreign_link", Test.class);
int[] ref = new int[1];
lib.setInt(ref, 10);
```

Without the @RefArg, when ref[] is returned it will not have been updated.

@RefArg can be used to annotate your entire interface. In that case, all methods
that use arrays will be handled as reference arguments.

## Example code for primitives and memory blocks
- [primitive_examples.c](../fl_dll/primitive_examples.c)
- [primitive_examples.h](../fl_dll/primitive_examples.h)
- [Java interface - TestLink.java](../src/test/java/jpassport/test/TestLink.java)
- [JUnit tests - TestJPassport.java](../src/test/java/jpassport/test/TestJPassport.java)