# Enums
Enums in C and Java are quite similar, but they have differences that need to be accounted for.
Primarily, in java, enums are only allowed to have serial values, where in C the members of an enum
can have any value. Secondarily, java enums only have int values, but in C they can be int or long.
Technically, in C they can be byte or short as well, but that's less common in modern compilers.

JPassport gives you 3 options for handling enums: basic, custom ints, custom longs. Here are some
examples:

```C
//basic
enum weekdays
{
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY
};

//custom longs
enum weekends
{
    SATURDAY = 0l,
    SUNDAY = 0xFFFFFFFFF  //NOTE, this value is larger than an int, so must be stored as a float
};

//custom ints
enum computer
{
    INTEL = 86,
    AMD = 64
}
```
The JPassport mappings look like:
```java
//This standard enum will use the ordinal() values of each enum.
public enum weekdays {
  MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY
}

// implements EnumLong so that long values are expected
public enum weekends implements EnumLong {
    SATURDAY(0L),
    SUNDAY(0xFFFFFFFFFL);
    private final long value;
    weekends(long v) {
        value = v;
    }

    @Override
    public long getCValue() {
        return value;
    }
}

//implementes EnumInt so that custom integers are used for each value, instead of ordinal()
public enum computer implements EnumInt {
    INTEL(86),
    AMD(64);
    private final int value;
    computer(long v) {
        value = v;
    }

    @Override
    public int getCValue() {
        return value;
    }
}
```

Enums can be passed as method arguments or received as method return values.

```C
//C method declaration
extern enum weekdays todayLong(enum weekdays* ie);
```

```Java
//Jpassport equivalent
public interface EnumLink extends Passport
{
    /**
     * 
     * @param ie A variable to capture the current weekday in
     * @return The current weekday
     */
    weekdays todayLong(@RefArg weekdays[] ie);
}
```

## Example code
- [enum_examples.c](../fl_dll/enum_examples.c)
- [enum_examples.h](../fl_dll/enum_examples.h)
- [JUnit tests - TestEnums.java](../src/test/java/jpassport/test/TestEnums.java)