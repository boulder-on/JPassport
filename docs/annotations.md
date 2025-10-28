# Annotations
Annotations are a very important part of JPassport. JPassport uses annotations as code generation hints. The available annotations are:

| Annotation                   | Usage          | Meaning                                                                                                                                                                |
|------------------------------|----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Array                        | Record members | If a C Struct takes a pointer to a primative array, this allows you to say what the size of the primative array is                                                     |
| NotRequired                  | Methods | If a function could not be found in the native library then no exception will be thrown. Use Passport.hasMethod("") to determine if the function was found.            |                    |
| Ptr                          | Record members | If a C Struct has a member that is a pointer of any type then use this annotation.                                                                                     |
| PtrPtrArg                    | Function argument| Any C function that takes a **<arg> must be annotated with this.                                                                                                       |
| RefArg                       | Function argument | Any C function that changes the contents of a pointer must be annotated with this to force the read back of the parameter                                              |
| RefArg (read_back_only=true) | Function argument | If you only need to pass a blank memory space for a method to fill, use this optimization, otherwise the values in the array are copied to memory that is passed to C. |
| StructPadding                | Record members | See the Javadoc or the above section on structs and records.                                                                                                           |
| Critical                     | Methods  | Removes some overhead for calling a native method. Cannot be used when callbacks are used. See the JDK's Linker.Option.critical for more details.                      |
| NativeName                   | Methods | Allows you to specify the name of the native function your interface method maps to, in the case that it does not simply map to the interface method name              |
NOTE: Methods marked with @Critical and that pass primitive arrays will pass the Java heap version of the array
directly to native code. Any changes to the primitive array in native code will be mirrored in Java. As such, @Critical methods with
a primitive array MUST mark the array as @RefArg, otherwise an exception will be thrown.
