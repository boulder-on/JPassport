# Creating Callbacks

It's common in native programming to pass a function as a pointer into another function such that
the native code can "call back" into your code. FFM refers to these as "up calls".  

This technique is used to create call-backs.

```java
import jpassport.pointers.FunctionPtr;

public interface CallbackNative extends Passport {
    void passMethod(FunctionPtr functionPtr);
}

public class MyCallback {
    public void callbackMethod(int value, String name) {
        System.out.println(value + ". " + name);
    }
}

MyCallback cb = new MyCallback();
//creates a function pointer that can be passed to native code. The function pointer
//is the method named "callbackMethod" in the cb object.
FunctionPtr functionPtr = PassportFactory.createCallback(cb, "callbackMethod");

CallbackNative cbn = PassportFactory.link("libforeign", CallbackNative.class);
//pass the callback to the native code.
cbn.passMethod(functionPtr);
```

At the moment, this does not work for static java methods.

__NOTE:__ If your callback method uses Java synchronization, or interacts with object member variables
then the thread must be a Java thread. In testing I've done, if a callback is called from a
normal Linux Thread then synchronized blocks do not work.

## Example code
 - [callback_examples.c](../fl_dll/callback_examples.c)
 - [callback_examples.h](../fl_dll/callback_examples.h)
 - [Java interface - CallbackNative.java](../src/test/java/jpassport/test/callback/CallbackNative.java)
 - [JUnit tests - TestCallback.java](../src/test/java/jpassport/test/callback/TestCallback.java)