package jpassport;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;

public interface DebugPassport {
    /**
     * Called immediately after a record is converted to native memory as a struct or union.
     * @param struct   The native memory that was filled in.
     * @param layout   The layout of the struct or union used to fill native memory
     * @param structName The name of the record class that was converted
     * @param inputsRecord The record that was converted to native memory
     */
    void structBuilt(MemorySegment struct, MemoryLayout layout, String structName, Object[] inputsRecord);

    /**
     * Called immediately before the native function call.
     * @param functionName The name of the function being called.
     * @param arguments An array of the arguments to the function.
     */
    void preNativeCall(String functionName, Object ... arguments);

    /**
     * Called immediately after returning from the native function call.
     * @param functionName The name of the function that was called.
     * @param retVal The return value of the function
     * @param arguments The arguments of the function
     */
    void postNativeCall(String functionName, Object retVal, Object ... arguments);

    /**
     * Called immediately after native memory is converted back into a record. This will not be called if
     * the record was not an @RefArg, since in that case nothing is read back.
     *
     * @param struct The native memory that was read.
     * @param layout The layout of the struct or union
     * @param structName The name of the record class that was created
     * @param outputRecord The record that was created
     */
    void structReadBack(MemorySegment struct, MemoryLayout layout, String structName, Object outputRecord);
}
