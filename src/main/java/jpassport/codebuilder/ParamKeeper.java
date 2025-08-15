package jpassport.codebuilder;


import jpassport.MemoryBlock;
import jpassport.Utils;

import java.lang.annotation.Annotation;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static jpassport.PassportWriter.isGenericPtr;

public class ParamKeeper {
    public Class<?> classtype;
    public ParamType type;
    public int storedOrig, stored;
    public Annotation[] annotations;

    public ParamKeeper(Class<?> c, ParamType t, int slot, Annotation[] a) {
        classtype = c;
        type = t;
        storedOrig = slot;
        stored = slot;
        annotations = a;
    }

    public String toString() {
        return type + "(" + stored + ")";
    }

    public int getSlotCount()
    {
        return type.slot_count;
    }

    public ClassDesc typeForInterfaceMethod()
    {
        if (classtype.isArray())
        {
            var atype = classtype.getComponentType();
            var pt = ParamType.toType(atype);
            if (pt != ParamType.addressType && pt.toDesc().isPresent())
                return pt.toDesc().get().arrayType();

            // 2D array of primitives
            if (atype.isArray() && atype.getComponentType().isPrimitive())
            {
                pt = ParamType.toType(atype.getComponentType());
                if (pt.toDesc().isPresent())
                    return pt.toDesc().get().arrayType(2);
            }
//            else if (isGenericPtr(atype))
//                return Utils.toDesc(GenericPointer.class).arrayType();
            return Utils.toDesc(atype).arrayType();
        }

        if (type.toDesc().isPresent())
            return type.toDesc().get();
        return Utils.toDesc(classtype);
    }

    public ClassDesc typeForVirtualCall()
    {
        Class<?> c = classtype;

        if (c.isArray() || c.isRecord() ||
                c.equals(MemorySegment.class) || c.equals(String.class) || c.equals(MemoryBlock.class) ||
                isGenericPtr(c))
        {
            return Utils.toDesc(MemorySegment.class);
        }
        return type.desc;
//
//        if (c.equals(void.class))
//            return ConstantDescs.CD_void;
//        if (c.equals(double.class))
//            return ConstantDescs.CD_double;
//        else if (c.equals(float.class))
//            return ConstantDescs.CD_float;
//        else if (c.equals(long.class))
//            return ConstantDescs.CD_long;
//        else if (c.equals(int.class))
//            return ConstantDescs.CD_int;
//        else if (c.equals(short.class))
//            return ConstantDescs.CD_short;
//        else if (c.equals(byte.class))
//            return ConstantDescs.CD_byte;
//        else if (c.equals(char.class))
//            return ConstantDescs.CD_char;
//        return ClassDesc.of(c.getName());

    }

    public boolean requiredForVirtualCall()
    {
        return !isArena();
    }

    public boolean isArena()
    {
        return classtype.equals(Arena.class);
    }

    public void loadParam(CodeBuilder cob)
    {
        switch(type)
        {
            case doubleType:
                cob.dload(stored);
                break;
            case longType:
                cob.lload(stored);
                break;
            case floatType:
                cob.fload(stored);
                break;
            case intType, shortType, byteType:
                cob.iload(stored);
                break;
            case addressType:
                if (classtype.equals(Arena.class))
                    break;
                cob.aload(stored);
                break;
        }
    }

    public static ParamKeeper classify(Class<?> c)
    {
        return new ParamKeeper(c, ParamType.toType(c), -1, new Annotation[0]);
    }
}
