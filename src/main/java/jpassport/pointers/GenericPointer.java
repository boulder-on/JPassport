package jpassport.pointers;


import java.lang.foreign.MemorySegment;

/**
 * This class should be extended if you have a method that works with an opaque pointer
 * class. You could just declare that your interface returns a MemoryAddress, but this
 * allows you to give a type to the address, which aids in readability.
 * ex.
 * <pre>
 * C
 * GATEWAY* openGateway();
 * void closeGateway(GATEWAY* g);
 *
 * Java
 * class Gateway extends GenericPointer
 * {}
 *
 * interface MyGateway
 * {
 *     Gateway openGateway();
 *     void closeGateway(Gateway g);
 * }
 * </pre>
 */
public class GenericPointer {
    public MemorySegment ptr;

    public GenericPointer(MemorySegment addr)
    {
        ptr = addr;
    }

    /**
     * The native pointer to the underlying memory.
     * @return
     */
    public MemorySegment getPtr()
    {
        return ptr;
    }

    /**
     * IS the pointer this holds a C NUL?
     * @return Is the pointer NULL?
     */
    public boolean isNull()
    {
        return ptr.equals(MemorySegment.NULL);
    }

    /**
     * A convenience method for a NULL value.
     * @return a pointer that represents NULL in C.
     */
    public static GenericPointer NULL() {
        return new GenericPointer(MemorySegment.NULL);
    }
}
