# Structs

The most reasonable analog of a struct in C is a Java record. The analogy is not
perfect because records are immutable. But for JPassport, records are very
handy because of their well-defined format. Using records to model structs allows
them to be handled fully automatically with very few extra hints required from you.

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
        @Ptr TestStruct tsPtr,  //@Ptr indicates that it is a pointer to memory holding the struct
        String string) {
}

public interface PerfTest extends Passport {
    double passStruct(PassingData structData);
    double passComplex(@RefArg ComplexPassing[] complexStruct);
}
```

The @Ptr annotation lets JPassport know to treat the member of the struct as
a pointer to another struct. If the C struct uses '*' to define a member then the record will required @Ptr for
that member.

Records can contain primitives, arrays of primitives, GenericPtr, arrays of GenericPtr, pointers to arrays of primitives, Strings, or pointers
to other Records.

Structs often require padding bytes to align on 4 or 8 byte boundaries. JPassport
calculates this padding automatically based on your platform. However, if it's done
wrong then you can use the  @StructPadding annotation to implement custom padding.
You can also you this annotation to ignore sections of a struct you don't care about.

## Example code
- [struct_examples.c](../fl_dll/struct_examples.c)
- [struct_examples.h](../fl_dll/struct_examples.h)
- [Java Interface - TestStructCalls.java](../src/test/java/jpassport/test/structs/TestStructCalls.java)
- [JUnit tests - TestUsingStructs.java](../src/test/java/jpassport/test/structs/TestUsingStructs.java)