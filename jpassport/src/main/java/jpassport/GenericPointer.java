package jpassport;

import java.lang.foreign.Addressable;
import java.lang.foreign.MemoryAddress;

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
    protected MemoryAddress ptr;

    public GenericPointer(MemoryAddress addr)
    {
        ptr = addr;
    }

    public Addressable getPtr()
    {
        return ptr;
    }

    public boolean isNull()
    {
        return ptr.equals(MemoryAddress.NULL);
    }

    /**
     * A convenience method for a NULL value.
     */
    public static GenericPointer NULL() {
        return new GenericPointer(MemoryAddress.NULL);
    }
}
