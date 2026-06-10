# Limitations

* Only 1D and 2D arrays of primitives are supported, deeper nestings do not work.
* The interface file passed to PassportFactory and all required Records must be exported by your module.

Pointers as function returns only work in a limited fashion. Based on a C
function declaration, there isn't a way to tell exactly what a method is returning.
For example, returning int* could return any number of ints. There is
little a library like JPassport can do to handle returned pointers automatically.
The work-around is for your interface function to return MemorySegment. From there
it would be up to you to decipher the return.

Declaring your interface method to take MemorySegment objects allows you to
manage all the data yourself (like jextract).

```
double* mallocDoubles(const int count)
{
    double* ret = malloc(count * sizeof(double ));

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
    MemorySegment mallocDoubles(int count);
    void freeMemory(MemorySegment addr);
}

double[] testReturnPointer(int count) {
    MemorySegment address = linked_lib.mallocDoubles(count);
    double[] values = Utils.toArrDouble(address, count);
    linked_lib.freeMemory(address);
    return values;
}
```

Functions that return a struct must be linked using PassportFactory.link_written().