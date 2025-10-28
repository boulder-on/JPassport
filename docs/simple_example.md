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

You can get JPassport to write out a java file for you that you can hand tweak and put in your codebase.
```java
PassportWriter pw = new PassportWriter(Linked.class);

//writes out the java code to the given folder
pw.writeModule(Path.of('output_location'));
```

Once the class is compiled, to use it:
```java
Linked l = new Linked_Impl(PassportFactory.loadMethodHandles("libforeign", Linked.class));
```