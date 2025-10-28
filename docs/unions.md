# Unions
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
    UnionFieldIO unionIO   //A Union must have a field of this type
) implements Union {}  //A union must extend Union

public interface UnionCalls extends Passport {
    boolean useSimpleUnion(int idx, short value, @RefArg SimpleUnion[] simple);
}

import static jpassport.UnionFieldIO.fromNativeOnly;

UnionCalls uc = PassportFactory.link(getLibName(), UnionCalls.class);
SimpleUnion[] suArr  = new SimpleUnion[] {new SimpleUnion(0, 0, 0, fromNativeOnly("u_i"))};
uc.useSimpleUnion(1, (short)4, suArr);
```
The above code needs some explanation. Since there is no analog in Java for a union you need to give
JPassport hints on how to behave. UnionFieldIO contains those hints. You can use a UnionFieldIO class
to specify either:
- The name of the field to write to or read from native memory
- Or the zero based index of the field in the union to write to or read from native memory
  
The UnionFieldIO member of the record is NOT part of the real union, it is treated specially by JPassport.
The call fromNativeOnly("u_i") specifies that when reading the union back from memory, only read the field named "u_i".
i.e. the call:

```Java
new SimpleUnion(0, 0, 0, fromNativeOnly(1));
```
Means: do not write any union fields to native memory, but when reading back,
get the int u_i field (the 1 in the call is zero based, so the second field).

The same rules and annotations for records/structs work for records/unions.
