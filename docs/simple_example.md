# Calling a native library example

FFM refers to calling native code as "down calls".

Here is some basic C code that we are going to try to call. This code
must be compiled into a DLL on Windows or .so file on Linux.
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

For the C code above you would create the following Java Interface. Note the
matching method names and similar arguments and returns.
```Java
//You must extend Passport
public interface Linked extends Passport {
   int string_length(String s);  //char* in C is equivalent to a Java String
   double sumArrD(double[] arr, int count); //java does not have pointers like the C function, so we use arrays.
}
```

Using your interface to call native code looks like this:
```Java
//Get JPassport to creae the native bindings for you. "libforeign" is the name of the DLL or shared library you want to link to
Linked L = PassportFactory.link("libforeign", Linked.class); 

//These are the calls into native code. No FFM calls required, the bindings from the link() call do it all for you.
int n = L.string_length("hello");   
double sum = L.sumArrD(new double[] {1, 2, 3}, 3);
```
## Writing the code ahead of time

You can get JPassport to write out a java file for you that you can hand tweak and put in your codebase.

If you are using GraalVM to build native code then you will need this method - pre-writing the code avoids
all runtime reflection. Also, by avoiding runtime reflection, this is technique has the shortest start-up time.

```java
var details = new PassportFactory.WritingDetails(Linked.class,  //the interface to build FFM bindings for
        "Linked_impl",    //The name of the class to build
        "jpassport.test",   //The package the class should be built in
        false,              //Should there be debug bindings?
        getLibName(),       //The name of the library
        Path.of("src/test/java/jpassport/test") );  //The folder to write the .java file into

PassportFactory.write(details);
```

Once the class is written to your source tree, to use it:
```java
var linked = new Linked_Impl();
```
## Example code for primitives and memory blocks
- [primitive_examples.c](../fl_dll/primitive_examples.c)
- [primitive_examples.h](../fl_dll/primitive_examples.h)
- [Java interface - TestLink.java](../src/test/java/jpassport/test/TestLink.java)
- [JUnit tests - TestJPassport.java](../src/test/java/jpassport/test/TestJPassport.java)