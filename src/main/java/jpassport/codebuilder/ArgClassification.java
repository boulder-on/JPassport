package jpassport.codebuilder;

import jpassport.MemoryBlock;
import jpassport.PassportException;
import jpassport.annotations.Ptr;

import java.lang.annotation.Annotation;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;

import static jpassport.PassportWriter.*;
import static jpassport.PassportWriter.isPtrPtrArg;

public enum ArgClassification {
    primitive,
    primitive_array,
    primitive_array_ptr,
    primitive_array2D,
    primitive_array2D_ptr2ptrs,
    record_,
    record_ptr,
    record_array,
    record_array_ptr,
    memsegment,
    string_,
    string_array,
    generic_ptr,
    generic_ptr_array,
    memory_block;

    public static ArgClassification classify(Class<?> arg, Annotation[] paramAnnotations)
    {
        if (arg.isPrimitive())
            return primitive;
        if (isArrayOfPrimitives(arg))
            return primitive_array;
        if (is2DArrayOfPrimitives(arg) && isPtrPtrArg(paramAnnotations))
            return primitive_array2D_ptr2ptrs;
        if (is2DArrayOfPrimitives(arg))
            return primitive_array2D;
        if (arg.isArray() && isGenericPtr(arg.getComponentType()))
            return generic_ptr_array;
        if (arg.isArray() && String.class.equals(arg.getComponentType()))
            return string_array;

        if (arg.isRecord())
            return record_;
        if (arg.isArray() && arg.getComponentType().isRecord())
            return record_array;
        if (MemorySegment.class.equals(arg))
            return memsegment;
        if (MemoryBlock.class.equals(arg))
            return memory_block;
        if (String.class.equals(arg))
            return string_;
        if (isGenericPtr(arg))
            return generic_ptr;

        throw new PassportException("Unhandled type: " + arg.getName());

    }

    static ArgClassification classify(Field f)
    {
        boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;
        Class<?> arg = f.getType();

        if (arg.isPrimitive())
            return primitive;
        if (isArrayOfPrimitives(arg))
            return isPointer ? primitive_array_ptr : primitive_array;
        if (is2DArrayOfPrimitives(arg))
            return isPointer ? primitive_array2D_ptr2ptrs : primitive_array2D;

        if (arg.isRecord())
            return isPointer ? record_ptr : record_;
        if (arg.isArray() && arg.getComponentType().isRecord())
            return isPointer ? record_array_ptr : record_array;

        if (MemorySegment.class.equals(arg))
            return memsegment;
        if (MemoryBlock.class.equals(arg))
            return memory_block;
        if (String.class.equals(arg))
            return string_;
        if (isGenericPtr(arg))
            return generic_ptr;

        throw new PassportException("Unhandled type: " + arg.getName());
    }

}
