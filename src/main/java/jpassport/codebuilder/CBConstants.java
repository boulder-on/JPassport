package jpassport.codebuilder;

import jpassport.*;
import jpassport.annotations.*;
import jpassport.enums.EnumInt;
import jpassport.enums.EnumLong;
import jpassport.pointers.GenericPointer;

import java.lang.annotation.Annotation;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.foreign.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

import static jpassport.Utils.Platform.Windows;

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
    ClassDesc CD_ErrorCapture = toDesc(ErrorCapture.class);
    ClassDesc CD_UnionFieldIO = toDesc(UnionFieldIO.class);
    ClassDesc CD_HashMap = toDesc(HashMap.class);
    ClassDesc CD_EnumLong = toDesc(EnumLong.class);
    ClassDesc CD_EnumInt = toDesc(EnumInt.class);

    String INIT_STRUCTS_METHOD_NAME = "initStructs";

    static ClassDesc toDesc(Class<?> c)
    {
        return Utils.toDesc(c);
    }

    static void loadParam(CodeBuilder cob, int idx, Class<?> c)
    {
        if (c == null)
            throw new IllegalArgumentException("When loading a value from the memory the type cannot be null");

        if (c.equals(void.class))
            return;

        if (c.equals(double.class))
            cob.dload(idx);
        else if (c.equals(long.class))
            cob.lload(idx);
        else if (c.equals(float.class))
            cob.fload(idx);
        else if (c.isPrimitive()) //int, short, byte, boolean, char
            cob.iload(idx );
        else
            cob.aload(idx);
    }

    static int storeLocalVar(CodeBuilder cob, int idx, Class<?> c)
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
        else if (c.isPrimitive()) //int, short, byte, boolean, char
            cob.istore(idx);
        else
            cob.astore(idx);
        return idx+1;
    }

    static void returnParam(CodeBuilder cob, Class<?> c)
    {
        if (c == null)
            throw new IllegalArgumentException("When returning a value from a function the type cannot be null");

        if (c.equals(void.class))
            cob.return_();
        else if (c.equals(double.class))
            cob.dreturn();
        else if (c.equals(long.class))
            cob.lreturn();
        else if (c.equals(float.class))
            cob.freturn();
        else if (c.isPrimitive()) //int, short, byte, boolean, char
            cob.ireturn();
        else
            cob.areturn();
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
        if (c.equals(boolean.class))
            return Integer.BYTES;
        if (c.equals(char.class))
            return Character.BYTES;
        return Integer.BYTES;
    }

    static boolean isRefArgReadBackOnly(Parameter methodArg)
    {
        var ref = methodArg.getAnnotationsByType(RefArg.class);
        if (ref.length > 0)
            return ref[0].read_back_only();
        return false;
    }

    static boolean isRefArgReadBackOnly(Annotation[] annotations)
    {
        for (var a : annotations)
        {
            if (a.annotationType().equals(RefArg.class))
            {
                return ((RefArg)a).read_back_only();
            }
        }
        return false;
    }

    static boolean isRefArg(Annotation[] paramAnnotations)
    {
        return Arrays.stream(paramAnnotations).map(Annotation::annotationType).anyMatch(RefArg.class::equals);
    }

    static boolean isPtrPtrArg(Annotation[] paramAnnotations)
    {
        return Arrays.stream(paramAnnotations).map(Annotation::annotationType).anyMatch(PtrPtrArg.class::equals);
    }

    static boolean hasAnnotation(Field f, Class<? extends Annotation> annotationType)
    {
        return f.getAnnotation(annotationType) != null;
    }

    static boolean skipUnionField(Class<?> c, Field f)
    {
//        return isUnion(c) && (hasAnnotation(f, UnionToNativeIdx.class) || hasAnnotation(f, UnionFromNativeIdx.class));
        return isUnion(c) && f.getType().equals(UnionFieldIO.class);
    }

    static void verifyUnion(Class<?> c)
    {
        if (!isUnion(c))
            return;

        long ioFields = Arrays.stream(c.getDeclaredFields()).filter(f -> f.getType().equals(UnionFieldIO.class)).count();
        if (ioFields > 1)
            throw new PassportException("Only one UnionFieldIO field per Union: " + c.getSimpleName());
        if (ioFields == 0)
            throw new PassportException("Unions must have one UnionFieldIO field: " + c.getSimpleName());

//        int unionToNativeCount = 0;
//        int unionFromNativeCount = 0;
//
//        for (Field f : c.getDeclaredFields()) {
//            if (hasAnnotation(f, UnionToNativeIdx.class)) {
//                if (f.getType() != int.class)
//                    throw new PassportException("@UnionToNativeIdx must be an int: " + c.getSimpleName() + "." + f.getName());
//                unionToNativeCount++;
//            }
//            if (hasAnnotation(f, UnionFromNativeIdx.class)) {
//                if (f.getType() != int.class)
//                    throw new PassportException("@UnionFromNativeIdx must be an int: " + c.getSimpleName() + "." + f.getName());
//                unionFromNativeCount++;
//            }
//        }
//
//        if (unionToNativeCount == 0)
//            throw new PassportException("Unions must have at least one @UnionToNativeIdx field: " + c.getSimpleName());
//        if (unionToNativeCount > 1)
//            throw new PassportException("Unions can only have one @UnionToNativeIdx field: " + c.getSimpleName());
//        if (unionFromNativeCount == 0)
//            throw new PassportException("Unions must have at least one @UnionFromNativeIdx field: " + c.getSimpleName());
//        if (unionFromNativeCount > 1)
//            throw new PassportException("Unions can only have one @UnionFromNativeIdx field: " + c.getSimpleName());

    }

    static boolean isArrayOfPrimitives(Class<?> c)
    {
        return c.isArray() && c.getComponentType().isPrimitive();
    }

    static boolean is2DArrayOfPrimitives(Class<?> c)
    {
        return c.isArray() && c.getComponentType().isArray() && isArrayOfPrimitives(c.getComponentType());
    }

    static boolean isGenericPtr(Class<?> c)
    {
        while (!c.equals(GenericPointer.class) && c.getSuperclass() != null)
            c = c.getSuperclass();
        return c.equals(GenericPointer.class);
    }

    static boolean isUnion(Class<?> c)
    {
        return Arrays.asList(c.getInterfaces()).contains(Union.class);
    }

    static boolean isLongEnum(Class<?> c)
    {
        return Arrays.asList(c.getInterfaces()).contains(EnumLong.class);
    }

    static boolean isIntEnum(Class<?> c)
    {
        return Arrays.asList(c.getInterfaces()).contains(EnumInt.class);
    }

    static int getPaddingBytes(Field field)
    {
        Annotation[] annotations = field.getAnnotationsByType(StructPadding.class);
        int paddingBytes = 0;

        if (annotations.length > 0)
        {
            StructPadding sp = ((StructPadding) annotations[0]);
            paddingBytes = sp.bytes();

            Utils.Platform p = Utils.getPlatform();
            if (Windows.equals(p) && sp.windowsBytes() != StructPadding.NO_VALUE)
                paddingBytes = sp.windowsBytes();
            else if (Utils.Platform.Mac.equals(p) && sp.macBytes() != StructPadding.NO_VALUE)
                paddingBytes = sp.macBytes();
            else if (Utils.Platform.Linux.equals(p) && sp.linuxBytes() != StructPadding.NO_VALUE)
                paddingBytes = sp.linuxBytes();
        }

        return paddingBytes;
    }

    /**
     * This will search the interface method for return types and arguments that should be imported.
     * These will all be Records.
     *
     * @param interfaceMethods All of the methods in the interfacee
     * @return The list of Record types that should be imported.
     */
    static Set<Class<?>> findAllExtraImports(List<Method> interfaceMethods) {
        Set<Class<?>> extraImports = new HashSet<>();
        for (Method m : interfaceMethods) {
            Class<?> retType = m.getReturnType();
            Class<?>[] params = m.getParameterTypes();

            if (!isValidArgType(retType))
                throw new PassportException(m.getName() + ". Types in the interface must by primitive, arrays of primitives, String, Records or Enum. " + retType.getSimpleName() + " not supported.");

            List<Class<?>> invalid = Arrays.stream(params).filter(p -> !isValidArgType(p)).toList();
            if (!invalid.isEmpty())
                throw new PassportException(m.getName() + ". Types in the interface must by primitive, arrays of primitives, String, Records or Enum. " + invalid.get(0).getSimpleName() + " not supported.");

            if (retType.isRecord() || (retType.isArray() && retType.getComponentType().isRecord()) || isGenericPtr(retType))
                extraImports.add(retType);
            Arrays.stream(params).filter(c -> c.isRecord() || c.isEnum()).forEach(extraImports::add);
            Arrays.stream(params).filter(Class::isArray).map(Class::getComponentType).filter(c -> c.isRecord() || c.isEnum()).forEach(extraImports::add);
            Arrays.stream(params).filter(CBConstants::isGenericPtr).forEach(extraImports::add);

            if (retType.isEnum())
                extraImports.add(retType);
        }

        extraImports.remove(String.class);
        extraImports.remove(MemorySegment.class);
        List<Class<?>> importLst = new ArrayList<>(extraImports);

        //In case any of the Records are made up of Records then this will pick those up to
        for (int n = 0; n < importLst.size(); ++n)
        {
            Class<?> c = importLst.get(n);
            if (c.isRecord())
            {
                var subset = findSubRecords(c);
                importLst.addAll(subset);
                extraImports.addAll(subset);
            }
        }

        return extraImports;
    }

    /**
     * Search all Record types recursively to make sure we import and handle all Record types needed.
     * @param record A record class to search for other records
     * @return All of the sub-Records.
     */
    static Set<Class<?>> findSubRecords(Class<?> record)
    {
        Set<Class<?>> subRecords = new HashSet<>();
        for (Field f : record.getDeclaredFields()) {
            if (f.getType().isRecord())
            {
                subRecords.add(f.getType());
                subRecords.addAll(findSubRecords(f.getType()));
            }
        }
        return subRecords;
    }

    /**
     * At the moment the only argument types that are supported are:
     * Primitive
     * Primitive[]
     * Primitive[][]
     * Record
     * String
     * MemorySegment
     *
     * @param c The type to check
     * @return Is the type something we can work with
     */
    private static boolean isValidArgType(Class<?> c)
    {
        try {
            //any failure to classify the parameter type means that we don't support it.
            ArgClassification.classify(c, null);
            return true;

        }
        catch (PassportException ex)
        {
            return false;
        }
    }

}
