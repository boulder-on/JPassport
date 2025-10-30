# Capturing Errors
FFM has the ability to capture errors that occurred during a call. For instance,
some C methods will set "errno" during a call. Or in windows GetLastError() can contain
valuable information. Those values need to be collected by the JVM as soon as the native
call is done. JPassport automates this for you with the ErrorCapture class.

```Java
import jpassport.ErrorCapture;

import java.lang.foreign.MemorySegment;

public interface ErrCapTest extends Passport {
    MemorySegment malloc(ErrorCapture err, int size);
}

ErrCapTest errCapTest = PassportFactory.link("libforeign", ErrCapTest.class);
ErrorCapture errors = new ErrorCapture();
MemorySegment mem = errCapTest.malloc(errors, Integer.MAX_VALUE);

if (mem.equals(MemorySegment.NULL)) {
    System.out.println(errors);
    int errno = errors.getError("errno");
    if (errno != 0)
        System.out.println("The error encountered was: " + errno);
}

```
Notice that ErrorCapture is not a real argument of the malloc function.
We just inject the ErrorCapture class here to grab the
errors that the JVM found before returning from the native call.

NOTE: The errors that are returned are done by name. Each platform will
have different names available. Calling ErrorCapture.toString() will show
you all that are available.

## Example code
- [error_capture_example.c](../fl_dll/error_capture_example.c)
- [error_capture_example.h](../fl_dll/error_capture_example.h)
- [JUnit tests - TestErrCapture.java](../src/test/java/jpassport/test/TestErrCapture.java)