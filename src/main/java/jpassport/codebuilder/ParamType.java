package jpassport.codebuilder;

import jpassport.PassportBuilder;
import jpassport.Utils;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum ParamType {
    voidType(0, ConstantDescs.CD_void),
    doubleType(2, ConstantDescs.CD_double), longType(2, ConstantDescs.CD_long),
    floatType(1, ConstantDescs.CD_float),
    intType(1, ConstantDescs.CD_int),
    shortType(1, ConstantDescs.CD_short),
    byteType(1, ConstantDescs.CD_byte),
    charType(1, ConstantDescs.CD_char),
    addressType(1, null);

    final int slot_count;
    final ClassDesc desc;

    ParamType(int slots, ClassDesc cd)
    {
        slot_count = slots;
        desc = cd;
    }

    public int requiredSlots()
    {
        return slot_count;
    }

    public Optional<ClassDesc> toDesc()
    {
        return desc == null ? Optional.empty() : Optional.of(desc);
    }

    private static final Map<Class<?>, ParamType> classToType = new HashMap<>();

    static {
        classToType.put(void.class, ParamType.voidType);
        classToType.put(double.class, ParamType.doubleType);
        classToType.put(float.class, ParamType.floatType);
        classToType.put(long.class, ParamType.longType);
        classToType.put(int.class, ParamType.intType);
        classToType.put(short.class, ParamType.shortType);
        classToType.put(byte.class, ParamType.byteType);
        classToType.put(char.class, ParamType.charType);
    }

    public static ParamType toType(Class<?> c)
    {
        return classToType.getOrDefault(c, ParamType.addressType);
    }
}
