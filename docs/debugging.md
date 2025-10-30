# Debugging

Since JPassport is doing most of the heavy lifting when communicating with native code, it can be hard to diagnose
errors and JVM crashes. If you need to debug things there are 2 main options:

1. Use PassportFactory.link_written() to write the java code, then use your debugger to step through each of the calls.
2. Use PassportFactory.link("lib_name", interface.class, **true**) - to build debuggable versions of the generated code.

Here is an example of building and using a debuggable class:

```java
public interface TestDebugging extends Passport
{
    double passComplex(@RefArg ComplexStruct[] complexStruct);
    double sumD(double d, double d2);
}

TestDebugging ffmImpl = PassportFactory.link(getLibName(), TestDebugging.class, true);

ffmImpl.setDebugHook(new DebugPassport() {
    
    //Called after each record is turned into a struct or union
    public void structBuilt(MemorySegment struct, MemoryLayout layout, String structName, Object[] inputsRecord) {
        //Utils.memToString() is a special function that tries to convert a MemorySegment to a meaningful string 
        //based on the given MemoryLayout
        System.out.println("Built: " + Utils.memToString(struct, layout, structName));
    }
    
    //Called immediately before the native call
    public void preNativeCall(String functionName, Object... arguments) {}
    //Called immediately after the native call
    public void postNativeCall(String functionName, Object retVal, Object... arguments) {}

    //Called after each MemorySegment is converted back into a record
    public void structReadBack(MemorySegment struct, MemoryLayout layout, String structName, Object outputRecord) {}
});

ffmImpl.passComplex(passComplex);
```
The DebugPassport interface allows you to set break points in code, or just print statements, so you can see where
things might be going wrong. Utils.memToString() can be a handy function to see what it in the native memory.

## Example code
- [JUnit tests - TestDebug.java](../src/test/java/jpassport/test/TestDebug.java)