package jpassport.codebuilder;

import jpassport.Utils;

import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.foreign.*;

/**
 * A set of constants that are helpful when building code.
 */
public interface CBConstants {
    ClassDesc CD_MemorySegment = toDesc(MemorySegment.class);
    ClassDesc CD_MemoryLayout =  toDesc(MemoryLayout.class);
    ClassDesc CD_Arena = toDesc(Arena.class);
    ClassDesc CD_SegmentAllocator = toDesc(SegmentAllocator.class);
    ClassDesc CD_Utils = toDesc(Utils.class);
    ClassDesc CD_ValueLayout = toDesc(ValueLayout.class);
    ClassDesc CD_AddressLayout = toDesc(AddressLayout.class);

    String INIT_STRUCTS_METHOD_NAME = "initStructs";

    static ClassDesc toDesc(Class<?> c)
    {
        return Utils.toDesc(c);
    }

    static void loadParam(CodeBuilder cob, int idx, Class<?> c)
    {
        if (c.equals(void.class))
            return;

        if (c.equals(double.class))
            cob.dload(idx);
        else if (c.equals(long.class))
            cob.lload(idx);
        else if (c.equals(float.class))
            cob.fload(idx);
        else if (c.equals(int.class) || c.equals(short.class) || c.equals(byte.class))
            cob.iload(idx );
        else
            cob.aload(idx);
    }

    static int storeParam(CodeBuilder cob, int idx, Class<?> c)
    {
        if (c.equals(void.class))
            return idx;
        else if (c.equals(double.class))
        {
            cob.dstore(idx);
            return idx + 2;
        }
        else if (c.equals(long.class))
        {
            cob.lstore(idx);
            return idx + 2;
        }
        else if (c.equals(float.class))
            cob.fstore(idx);
        else if (c.equals(int.class) || c.equals(short.class) || c.equals(byte.class))
            cob.istore(idx);
        else
            cob.astore(idx);
        return idx+1;
    }

    static int byteSize(Class<?> c)
    {
        if (c.equals(double.class))
            return Double.BYTES;
        if (c.equals(float.class))
            return Float.BYTES;
        if (c.equals(int.class))
            return Integer.BYTES;
        if (c.equals(long.class))
            return Long.BYTES;
        if (c.equals(short.class))
            return Short.BYTES;
        if (c.equals(byte.class))
            return Byte.BYTES;
        return Integer.BYTES;
    }

}
