package jpassport.codebuilder;


import jpassport.ErrorCapture;
import jpassport.pointers.MemoryBlock;
import jpassport.Utils;

import java.lang.annotation.Annotation;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static jpassport.codebuilder.CBConstants.*;


public class ParamKeeper {
    public Class<?> classtype;
    public ParamType type;
    public int storedOrig, stored;
    public Annotation[] annotations;
    public ArgClassification classification;

    public ParamKeeper(Class<?> c, ParamType t, int slot, Annotation[] a) {
        classtype = c;
        type = t;
        storedOrig = slot;
        stored = slot;
        annotations = a;
        classification = ArgClassification.classify(c, a);
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
                isGenericPtr(c) || c.equals(ErrorCapture.class))
        {
            return CD_MemorySegment;
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
//        return !isArena() && !isErrorCapture();
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
            case intType, shortType, byteType, boolType:
                cob.iload(stored);
                break;
            case addressType:
                if (classtype.equals(Arena.class))
                    break;
                cob.aload(stored);
                break;
        }
    }

    public void loadAutoBoxed(CodeBuilder cob)
    {
        autoBox(cob, classtype, stored);
    }

    public static void autoBox(CodeBuilder cob, Class<?> ctype, int slot)
    {
        switch(ParamType.toType(ctype))
        {
            case voidType:
                cob.aconst_null();
                break;
            case doubleType:
                cob.dload(slot);
                cob.invokestatic(toDesc(Double.class), "valueOf", MethodTypeDesc.of(toDesc(Double.class), ConstantDescs.CD_double));
                break;
            case longType:
                cob.lload(slot);
                cob.invokestatic(toDesc(Long.class), "valueOf", MethodTypeDesc.of(toDesc(Long.class), ConstantDescs.CD_long));
                break;
            case floatType:
                cob.fload(slot);
                cob.invokestatic(toDesc(Float.class), "valueOf", MethodTypeDesc.of(toDesc(Float.class), ConstantDescs.CD_float));
                break;
            case intType, shortType, byteType, boolType:
                cob.iload(slot);
                cob.invokestatic(toDesc(Integer.class), "valueOf", MethodTypeDesc.of(toDesc(Integer.class), ConstantDescs.CD_int));
                break;
            default:
                if (ctype.equals(Arena.class))
                    break;
                cob.aload(slot);
                break;
        }
    }


    public static ParamKeeper classify(Class<?> c)
    {
        return new ParamKeeper(c, ParamType.toType(c), -1, new Annotation[0]);
    }
}
