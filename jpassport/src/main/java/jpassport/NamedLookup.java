package jpassport;

import java.lang.foreign.Addressable;

/**
 * Variables of this type declared in your interface will be loaded by the SymbolLookup.
 *
 * <pre>
 *     public interface LoadNames extends Passport
 *     {
 *        NamedLookup loadedName = new NamedLookup("LoadName");
 *     }
 * </pre>
 *
 * The above code would load LoadName from the native library as a pointer.
 */
public class NamedLookup {
    final private String name;
    private Addressable addr;

    public NamedLookup(String name)
    {
        this.name = name;
    }

    protected void setAddress(Addressable addr)
    {
        this.addr = addr;
    }

    public Addressable addr()
    {
        return addr;
    }

    public String name()
    {
        return name;
    }

    public boolean equals(GenericPointer ptr)
    {
        return addr.equals(ptr.getPtr());
    }
}
