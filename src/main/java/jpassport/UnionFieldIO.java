package jpassport;

/**
 * When you make a record/Union, the record must have a member of this type.
 * This class is used by the union to determine the single field in the union
 * that is valid.
 */
public class UnionFieldIO
{
    public static final int NO_TO_NATIVE = -1;
    public static final int NO_FROM_NATIVE = -1;

    private final int toNativeIdx;
    private final String toNativeName;

    private final int fromNativeIdx;
    private final String fromNativeName;

    private UnionFieldIO(int toIdx, String toName, int fromIdx, String fromName)
    {
        toNativeIdx = toIdx;
        toNativeName = toName;
        fromNativeIdx = fromIdx;
        fromNativeName = fromName;
    }

    public static UnionFieldIO nativeIO(int to, int from)
    {
        return new UnionFieldIO(to, null, from, null);
    }

    public static UnionFieldIO nativeIO(String to, String from)
    {
        return new UnionFieldIO(NO_TO_NATIVE, to, NO_FROM_NATIVE, from);
    }

    public static UnionFieldIO toNativeOnly(int idx)
    {
        return new UnionFieldIO(idx, null, NO_FROM_NATIVE, null);
    }

    public static UnionFieldIO fromNativeOnly(int idx)
    {
        return new UnionFieldIO(NO_TO_NATIVE, null, idx, null);
    }

    public static UnionFieldIO toNativeOnly(String name)
    {
        return new UnionFieldIO(NO_TO_NATIVE, name, NO_FROM_NATIVE, null);
    }

    public static UnionFieldIO fromNativeOnly(String name)
    {
        return new UnionFieldIO(NO_TO_NATIVE, null, NO_FROM_NATIVE, name);
    }

    public static UnionFieldIO noIO()
    {
        return new UnionFieldIO(NO_TO_NATIVE, null, NO_FROM_NATIVE, null);
    }

    public boolean toNative(int idx, String name)
    {
        return idx == toNativeIdx || (toNativeName != null && toNativeName.equals(name));
    }

    public boolean fromNative(int idx, String name)
    {
        return idx == fromNativeIdx || (fromNativeName != null && fromNativeName.equals(name));
    }

    public boolean fromOrigRec(int idx, String name)
    {
        return !fromNative(idx, name);
    }
}
