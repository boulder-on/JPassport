package jpassport.codebuilder;

import jpassport.GenericPointer;
import jpassport.MemoryBlock;
import jpassport.Utils;

import java.lang.annotation.Annotation;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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

    public static List<ParamKeeper> classifyParams(CodeBuilder cob, Method iMethod)
    {
        ArrayList<ParamKeeper> keepers = new ArrayList<>();
        Annotation[][] annotations = iMethod.getParameterAnnotations();
        int slot = 0;
        for (Class<?> c : iMethod.getParameterTypes())
        {
//            keepers.add(new ParamKeeper(c, ParamType.toType(c), cob.parameterSlot(slot), annotations[slot]));
            keepers.add(classify(c, slot, annotations[slot]));
            slot++;
        }
        return keepers;
    }

    public static ParamKeeper classify(Class<?> c, int slot, Annotation[] annotations)
    {
        return new ParamKeeper(c, ParamType.toType(c), slot, annotations);
    }

    public static ParamKeeper classify(Class<?> c)
    {
        return new ParamKeeper(c, ParamType.toType(c), -1, new Annotation[0]);
    }

    public static ClassDesc toDesc(Class<?> c, boolean forMethodSig)
    {
//        if (c.equals(void.class) && forMethodSig)
        if (c.equals(void.class))
            return ConstantDescs.CD_void;

        if (c.isArray())
        {
            var atype = c.getComponentType();
            if (atype.equals(double.class))
                return ConstantDescs.CD_double.arrayType();
            else if (atype.equals(float.class))
                return ConstantDescs.CD_float.arrayType();
            else if (atype.equals(long.class))
                return ConstantDescs.CD_long.arrayType();
            else if (atype.equals(int.class))
                return ConstantDescs.CD_int.arrayType();
            else if (atype.equals(short.class))
                return ConstantDescs.CD_short.arrayType();
            else if (atype.equals(byte.class))
                return ConstantDescs.CD_byte.arrayType();
            else if (atype.equals(char.class))
                return ConstantDescs.CD_char.arrayType();
            else if (atype.equals(boolean.class))
                return ConstantDescs.CD_boolean.arrayType();
            else if (isGenericPtr(atype))
            {
                if (forMethodSig)
                    return Utils.toDesc(atype).arrayType();
                return Utils.toDesc(GenericPointer.class).arrayType();
            }

            if (forMethodSig) {
                //2 dimensional array of primitives
                if (atype.isArray() && atype.getComponentType().isPrimitive())
                {
                    return toDesc(atype.getComponentType(), false).arrayType(2);
                }
                return Utils.toDesc(atype).arrayType();
            }

            return ConstantDescs.CD_Object.arrayType();
        }
        if (c.equals(double.class))
            return ConstantDescs.CD_double;
        else if (c.equals(float.class))
            return ConstantDescs.CD_float;
        else if (c.equals(long.class))
            return ConstantDescs.CD_long;
        else if (c.equals(int.class))
            return ConstantDescs.CD_int;
        else if (c.equals(short.class))
            return ConstantDescs.CD_short;
        else if (c.equals(byte.class))
            return ConstantDescs.CD_byte;
        else if (c.equals(char.class))
            return ConstantDescs.CD_char;
        else if (c.equals(MemorySegment.class))
            return Utils.toDesc(MemorySegment.class);
        else if (c.equals(String.class) && !forMethodSig)
            return Utils.toDesc(MemorySegment.class);
        else if (c.equals(MemoryBlock.class) && !forMethodSig)
            return Utils.toDesc(MemorySegment.class);
        else if (isGenericPtr(c))
        {
            if (forMethodSig)
                return Utils.toDesc(c);
            return Utils.toDesc(MemorySegment.class);
        }
        if (forMethodSig)
            return Utils.toDesc(c);
        return ConstantDescs.CD_Object;
    }

}
