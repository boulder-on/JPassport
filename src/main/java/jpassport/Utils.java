/* Copyright (c) 2021 Duncan McLean, All Rights Reserved
 *
 * The contents of this file is dual-licensed under the
 * Apache License 2.0.
 *
 * You may obtain a copy of the Apache License at:
 *
 * http://www.apache.org/licenses/
 *
 * A copy is also included in the downloadable source code.
 */
package jpassport;


import jpassport.annotations.Array;
import jpassport.annotations.Ptr;
import jpassport.enums.EnumInt;
import jpassport.enums.EnumLong;
import jpassport.pointers.GenericPointer;
import jpassport.pointers.MemoryBlock;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.constant.ClassDesc;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static java.lang.foreign.ValueLayout.*;
import static jpassport.codebuilder.CBConstants.*;

/**
 * A set of methods for converting Java entities to native memory (mostly).
 */
public class Utils {


    /* Double ///////////////////////////////////////////////////////////////// */
    public static MemorySegment toMS(SegmentAllocator scope, double[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;
        return isReadBackOnly ? scope.allocate((long)arr.length * Double.BYTES) :
                scope.allocateFrom(JAVA_DOUBLE, arr);
    }

    public static MemorySegment toMS(double[] arr) {
        if (arr == null)
            return MemorySegment.NULL;
        return MemorySegment.ofArray(arr);
    }

    public static MemorySegment toMS(SegmentAllocator scope, double[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Double.BYTES);
        int n = 0;
        for (double[] row : arr) {
            segment.asSlice(n, (long) row.length * Double.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Double.BYTES;
        }

        return segment;
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, double[][] arr) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (double[] a : arr) {
            MemorySegment subSeg = scope.allocateFrom(JAVA_DOUBLE, a);
            segment.setAtIndex(ValueLayout.ADDRESS, n++, subSeg);
        }
        return segment;
    }

    public static void toArr(double[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        MemorySegment.copy(segment, JAVA_DOUBLE, 0, arr, 0, arr.length);
    }

    public static double[] toArr(MemorySegment seg, MemorySegment addr, double[] origArray) {
        if (MemorySegment.NULL.equals(addr) || origArray == null)
            return null;

        return slice(seg, addr, origArray.length * JAVA_DOUBLE.byteSize()).toArray(JAVA_DOUBLE);
    }

    /* Float ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, float[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        return isReadBackOnly ? scope.allocate(arr.length * Float.BYTES) :
                scope.allocateFrom(ValueLayout.JAVA_FLOAT, arr);
    }

    public static MemorySegment toMS(float[] arr) {
        if (arr == null)
            return MemorySegment.NULL;
        return MemorySegment.ofArray(arr);
    }

    public static MemorySegment toMS(SegmentAllocator scope, float[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Float.BYTES);
        int n = 0;
        for (float[] row : arr) {
            segment.asSlice(n, (long) row.length * Float.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Float.BYTES;
        }

        return segment;
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, float[][] arr) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (float[] a : arr) {
            MemorySegment subSeg = scope.allocateFrom(ValueLayout.JAVA_FLOAT, a);
            segment.setAtIndex(ValueLayout.ADDRESS, n++, subSeg);
        }
        return segment;
    }

    public static void toArr(float[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        MemorySegment.copy(segment, ValueLayout.JAVA_FLOAT, 0, arr, 0, arr.length);
    }

    public static float[] toArr(MemorySegment seg, MemorySegment addr, float[] origArray) {
        if (MemorySegment.NULL.equals(addr) || origArray == null)
            return null;

        return slice(seg, addr, origArray.length * JAVA_FLOAT.byteSize()).toArray(JAVA_FLOAT);
    }


    /* Pointers ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, GenericPointer[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        var pass = Arrays.stream(arr).mapToLong(gp -> gp == null ? MemorySegment.NULL.address() : gp.getPtr().address()).toArray();
        return isReadBackOnly ? scope.allocate((long)arr.length * Long.BYTES) :
                scope.allocateFrom(ValueLayout.JAVA_LONG, pass);
    }

    public static void toArr(GenericPointer[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        var asLong = new long[arr.length];
        toArr(asLong, segment);

        for (int n = 0; n < asLong.length; ++n)
        {
            var addr = MemorySegment.ofAddress(asLong[n]);
            if (arr[n] == null)
            {
                try {
                    var cons = arr.getClass().getComponentType().getConstructor(MemorySegment.class);
                    arr[n] = (GenericPointer) cons.newInstance(addr);
                }
                catch(Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            else
                arr[n].ptr = addr;
        }
    }

    /* Long ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, long[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;
        return isReadBackOnly ? scope.allocate((long)arr.length * Long.BYTES) :
                scope.allocateFrom(ValueLayout.JAVA_LONG, arr);
    }

    public static MemorySegment toMS(long[] arr) {
        if (arr == null)
            return MemorySegment.NULL;
        return MemorySegment.ofArray(arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, long[][] arr) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (long[] a : arr) {
            MemorySegment subSeg = scope.allocateFrom(ValueLayout.JAVA_LONG, a);
            segment.setAtIndex(ValueLayout.ADDRESS, n++, subSeg);
        }
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, long[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Long.BYTES);
        int n = 0;
        for (long[] row : arr) {
            segment.asSlice(n, (long) row.length * Long.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Long.BYTES;
        }

        return segment;
    }

    public static void toArr(long[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        MemorySegment.copy(segment, ValueLayout.JAVA_LONG, 0, arr, 0, arr.length);
    }

    public static long[] toArr(MemorySegment seg, MemorySegment addr, long[] origArray) {
        if (MemorySegment.NULL.equals(addr) || origArray == null)
            return null;

        return slice(seg, addr, origArray.length * JAVA_LONG.byteSize()).toArray(JAVA_LONG);
    }

    /* Int ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, int[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        return isReadBackOnly ? scope.allocate(arr.length * Integer.BYTES) :
                scope.allocateFrom(ValueLayout.JAVA_INT, arr);
    }

    public static MemorySegment toMS(int[] arr) {
        if (arr == null)
            return MemorySegment.NULL;
        return MemorySegment.ofArray(arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, int[][] arr) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (int[] a : arr) {
            segment.setAtIndex(ValueLayout.ADDRESS, n++, scope.allocateFrom(ValueLayout.JAVA_INT, a));
        }
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, int[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Integer.BYTES);
        int n = 0;
        for (int[] row : arr) {
            segment.asSlice(n, (long) row.length * Integer.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Integer.BYTES;
        }

        return segment;
    }

    public static void toArr(int[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        MemorySegment.copy(segment, ValueLayout.JAVA_INT, 0, arr, 0, arr.length);
    }

    public static int[] toArr(MemorySegment seg, MemorySegment addr, int[] origArray) {
        if (MemorySegment.NULL.equals(addr) || origArray == null)
            return null;

        return slice(seg, addr, origArray.length * JAVA_INT.byteSize()).toArray(JAVA_INT);
    }


    /* Short ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, short[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        return isReadBackOnly ? scope.allocate((int) (arr.length * Short.BYTES)) :
                scope.allocateFrom(ValueLayout.JAVA_SHORT, arr);
    }

    public static MemorySegment toMS(short[] arr) {
        if (arr == null)
            return MemorySegment.NULL;
        return MemorySegment.ofArray(arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, short[][] arr) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (short[] a : arr) {
            segment.setAtIndex(ValueLayout.ADDRESS, n++, scope.allocateFrom(ValueLayout.JAVA_SHORT, a));
        }
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, short[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Short.BYTES);
        int n = 0;
        for (short[] row : arr) {
            segment.asSlice(n, (long) row.length * Short.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Short.BYTES;
        }

        return segment;
    }

    public static void toArr(short[] arr, MemorySegment segment) {
        if (arr == null)
            return;
        MemorySegment.copy(segment, ValueLayout.JAVA_SHORT, 0, arr, 0, arr.length);
    }

    public static short[] toArr(MemorySegment seg, MemorySegment addr, short[] origArray) {
        if (MemorySegment.NULL.equals(addr) || origArray == null)
            return null;

        return slice(seg, addr, origArray.length * JAVA_SHORT.byteSize()).toArray(JAVA_SHORT);
    }

    /* Boolean ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, boolean[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;
        return toMS(scope, convert(arr), isReadBackOnly);
    }

    public static MemorySegment toMS(boolean[] arr) {
        if (arr == null)
            return MemorySegment.NULL;
        return MemorySegment.ofArray(convert(arr));
    }

    public static void toArr(boolean[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        byte[] data = new byte[arr.length];
        toArr(data, segment);
        for (int n = 0; n < arr.length; ++n)
            arr[n] = data[n] > 0;

//        MemorySegment.copy(segment, JAVA_BOOLEAN, 0, arr, 0, arr.length);
    }

    public static boolean[] toArr(MemorySegment seg, MemorySegment addr, boolean[] origArray) {
        if (MemorySegment.NULL.equals(addr) || origArray == null)
            return null;

        byte[] arr = slice(seg, addr, origArray.length * JAVA_BYTE.byteSize()).toArray(JAVA_BYTE);
        boolean[] ret = new boolean[arr.length];
        for (int n = 0; n < ret.length; ++n)
            ret[n] = arr[n] > 0;
        return ret;
    }

    private static byte[] convert(boolean[] arr)
    {
        if (arr == null)
            return null;

        byte[] converted = new byte[arr.length];
        for (int n = 0; n < arr.length; ++n)
            converted[n] = arr[n] ? (byte)1 : (byte)0;

        return converted;
    }

    /* Byte ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, byte[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        return isReadBackOnly ? scope.allocate(arr.length) : scope.allocateFrom(ValueLayout.JAVA_BYTE, arr);
    }

    public static MemorySegment toMS(byte[] arr) {
        if (arr == null)
            return MemorySegment.NULL;
        return MemorySegment.ofArray(arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, byte[][] arr) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (byte[] a : arr)
            segment.setAtIndex(ValueLayout.ADDRESS, n++, scope.allocateFrom(ValueLayout.JAVA_BYTE, a));
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, byte[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Byte.BYTES);
        int n = 0;
        for (byte[] row : arr) {
            segment.asSlice(n, row.length * Byte.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Byte.BYTES;
        }

        return segment;
    }

    public static void toArr(byte[] arr, MemorySegment segment) {
        if (arr == null)
            return;
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, 0, arr, 0, arr.length);
    }

    public static byte[] toArr(MemorySegment seg, MemorySegment addr, byte[] origArray) {
        if (MemorySegment.NULL.equals(addr) || origArray == null)
            return null;

        return slice(seg, addr, origArray.length * JAVA_BYTE.byteSize()).toArray(JAVA_BYTE);
    }

    /* Char ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, char[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        return isReadBackOnly ? scope.allocate(Character.BYTES * arr.length) : scope.allocateFrom(ValueLayout.JAVA_CHAR, arr);
    }

    public static MemorySegment toMS(char[] arr) {
        if (arr == null)
            return MemorySegment.NULL;
        return MemorySegment.ofArray(arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, char[][] arr) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (char[] a : arr)
            segment.setAtIndex(ValueLayout.ADDRESS, n++, scope.allocateFrom(ValueLayout.JAVA_CHAR, a));
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, char[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return MemorySegment.NULL;

        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Byte.BYTES);
        int n = 0;
        for (char[] row : arr) {
            segment.asSlice(n, (int)(row.length * Character.BYTES)).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Byte.BYTES;
        }

        return segment;
    }

    public static void toArr(char[] arr, MemorySegment segment) {
        if (arr == null)
            return;
        MemorySegment.copy(segment, ValueLayout.JAVA_CHAR, 0, arr, 0, arr.length);
    }

    public static char[] toArr(MemorySegment seg, MemorySegment addr, char[] origArray) {
        if (MemorySegment.NULL.equals(addr) || origArray == null)
            return null;

        return slice(seg, addr, origArray.length * JAVA_CHAR.byteSize()).toArray(JAVA_CHAR);
    }

    /*///////////////////////////////////////////////////////////////// */

    public static MemorySegment slice(MemorySegment scope, MemorySegment addr, long bytes) {
        if (addr.byteSize() == 0)
            return MemorySegment.ofAddress(addr.address()).reinterpret(bytes).asSlice(0, bytes);

        return MemorySegment.ofAddress(addr.address()).asSlice(0, bytes);
    }

    public static String readString(MemorySegment addr) {
        if (MemorySegment.NULL.equals(addr))
            return null;

        if (addr.byteSize() == 0)
        {
            // This is slightly horrible. I can't find a better way. Use C's strlen to figure out
            //how big the memory segment really is.
            return addr.reinterpret(strLen(addr)+1).getString(0);
        }

        return addr.getString(0);
    }

    public static MemorySegment resize(MemorySegment addr, long bytes)
    {
        if (addr.byteSize() == 0)
            addr = addr.reinterpret(bytes);
        return addr;
    }

    static MethodHandle strlen = null;

    private static long strLen(MemorySegment seg)
    {
        if (strlen == null)
        {
            Linker linker = Linker.nativeLinker();
            SymbolLookup stdlib = linker.defaultLookup();
            strlen = linker.downcallHandle(
                    stdlib.find("strlen").get(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
            );

        }
        try {
            return (long)strlen.invokeExact(seg);
        } catch (Throwable e) {
            return 0;
        }
    }

    public static MemorySegment toCString(String[] s, Arena scope) {
        var segment = scope.allocate(ValueLayout.JAVA_LONG.byteSize() * s.length);
        for (int i = 0; i < s.length; ++i)
        {
            segment.setAtIndex(ValueLayout.ADDRESS, i, toCString(s[i], scope));
        }
        return segment;
    }

    public static void fromCString(MemorySegment mem, String[] s)
    {
        for (int n = 0; n < s.length; ++n)
            s[n] = readString(mem.getAtIndex(ValueLayout.ADDRESS, n));
    }

    public static MemorySegment toCString(String s, Arena scope) {
        return s == null ? MemorySegment.NULL : scope.allocateFrom(s);
    }

    public static MemorySegment toCString(String s, SegmentAllocator scope) {
        return s == null ? MemorySegment.NULL : scope.allocateFrom(s);
    }

    /**
     * Given a folder or file this will recursively delete it.
     *
     * @param folder The root folder to recursively delete everything under.
     * @return True if everything was deleted.
     */
    public static boolean deleteFolder(Path folder) {
        if (!Files.exists(folder))
            return true;

        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null)
                        throw exc;

                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static Path getBuildFolder() {
        if (System.getProperty("jpassport.build.home") != null)
            return Path.of(System.getProperty("jpassport.build.home"));
        return Path.of(System.getProperty("java.io.tmpdir"), "jpassport");
    }

    public enum Platform {Windows, Mac, Linux, Unknown}

    public static Platform getPlatform() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win"))
            return Platform.Windows;
        if (os.contains("mac"))
            return Platform.Mac;
        if ((os.contains("nix") || os.contains("nux") || os.contains("aix")))
            return Platform.Linux;

        return Platform.Unknown;
    }

    private static long typeToSize(Class<?> type)
    {

        if (type.equals(byte.class)) return ValueLayout.JAVA_BYTE.byteSize();
        if (type.equals(char.class)) return ValueLayout.JAVA_CHAR.byteSize();
        if (type.equals(short.class)) return ValueLayout.JAVA_SHORT.byteSize();
        if (type.equals(int.class)) return ValueLayout.JAVA_INT.byteSize();
        if (type.equals(boolean.class)) return ValueLayout.JAVA_BOOLEAN.byteSize();
        if (type.equals(long.class)) return ValueLayout.JAVA_LONG.byteSize();
        if (type.equals(float.class)) return ValueLayout.JAVA_FLOAT.byteSize();
        if (type.equals(double.class)) return ValueLayout.JAVA_DOUBLE.byteSize();
        if (type.isRecord()) return size_of(type);
        throw new IllegalArgumentException("Cannot get size for non-primative");
    };

    public static long size_of(Class<?> c)
    {
        if (!c.isRecord())
            throw new IllegalArgumentException("Can only get size of records, not " + c.getName());

        if (isUnion(c))
            return size_of_union(c);

        long size = 0;
        for (Field f : c.getDeclaredFields())
        {
            size += getPaddingBytes(f);

            Class<?> type = f.getType();
            if (type.isPrimitive())
                size += typeToSize(type);
            else if (type.isRecord())
            {
                if (isUnion(type))
                    size += size_of_union(type);
                else {
                    boolean isPtr = f.getAnnotationsByType(Ptr.class).length > 0;
                    if (isPtr)
                        size += ValueLayout.ADDRESS.byteSize();
                    else
                        size += size_of(type);
                }
            }
            else if (String.class.equals(type) || MemoryBlock.class.equals(type))
                size += ValueLayout.ADDRESS.byteSize();
            else if (type.isArray())
            {
                Annotation[] arrays = f.getAnnotationsByType(Array.class);
                boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;

                if (arrays.length > 0)
                {
                    int length = ((Array) arrays[0]).length();
                    size += length * typeToSize(type.getComponentType());
                }
                else if (isPointer)
                    size += ValueLayout.ADDRESS.byteSize();
            }
        }
        return size;
    }

    private static long size_of_union(Class<?> c)
    {
        if (!c.isRecord())
            throw new IllegalArgumentException("Can only get size of records, not " + c.getName());

        // A union is as large as the largest field. This will recursively figure out all
        // field sizes and choose the largest one.
        long[] sizes = new long[c.getDeclaredFields().length - 1];
        int idx = 0;
        for (Field f : c.getDeclaredFields())
        {
            if (skipUnionField(c, f))
                continue;

            Class<?> type = f.getType();
            if (type.isPrimitive())
                sizes[idx++] = typeToSize(type);
            else if (type.isRecord())
            {
                boolean isPtr = f.getAnnotationsByType(Ptr.class).length > 0;
                if (isPtr)
                    sizes[idx++] = ValueLayout.ADDRESS.byteSize();
                else
                    sizes[idx++] = size_of(type);
            }
            else if (String.class.equals(type) || MemoryBlock.class.equals(type))
                sizes[idx++] = ValueLayout.ADDRESS.byteSize();
            else if (type.isArray())
            {
                Annotation[] arrays = f.getAnnotationsByType(Array.class);
                boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;

                if (arrays.length > 0)
                {
                    int length = ((Array) arrays[0]).length();
                    sizes[idx++] = length * typeToSize(type.getComponentType());
                }
                else if (isPointer)
                    sizes[idx++] = ValueLayout.ADDRESS.byteSize();
            }
        }
        var size = Arrays.stream(sizes).max();
        return size.orElse(0);
    }

    public static MemorySegment toPtr(Arena arena, MemoryBlock mb)
    {
        if (mb == null)
            return MemorySegment.NULL;
        return mb.toPtr(arena);
    }

    public static void readBack(MemoryBlock mb)
    {
        if (mb != null)
            mb.readBack();
    }

//    public static MemorySegment toMS(Passport passport, Object ob, Arena scope)
//    {
//        Class<?> c = ob.getClass();
//
//        if (!c.isRecord())
//            throw new IllegalArgumentException("Must be record to convert to a struct");
//
//
//    }

    /**
     * Called by generated code to build the memory layout for a struct. This automatically
     * tries to figure out where padding is needed in the struct in order to get proper byte
     * alignment.
     * @param layout The members of the struct
     * @return The full GroupLayout of the struct.
     */
    public static GroupLayout makeStruct(MemoryLayout ... layout)
    {
        int byteBarrier = System.getProperty("sun.arch.data.model").contains("64") ? 8 : 4;
        ArrayList<MemoryLayout> memLayout = new ArrayList<>();
        memLayout.add(layout[0]);
        long nextBarrier = byteBarrier;

        var curSize = memLayout.stream().mapToLong(MemoryLayout::byteSize).sum();
        while (nextBarrier < curSize) nextBarrier += byteBarrier;

        for (int n = 1; n < layout.length; ++n)
        {
            curSize = memLayout.stream().mapToLong(MemoryLayout::byteSize).sum();
            long nextItemSize = layout[n].byteSize();

            //if an array is next, then we only need to byte align the first element of the array
            if (layout[n] instanceof SequenceLayout seq)
                nextItemSize = seq.byteSize() / seq.elementCount();

            // If the next piece of memory we are adding crosses the byte alignement barrier
            // then we need to pad the struct to alow byte alignment
            if (curSize + nextItemSize > nextBarrier && (curSize + nextItemSize ) % byteBarrier != 0 && nextBarrier - curSize > 0)
                memLayout.add(MemoryLayout.paddingLayout(nextBarrier - curSize));

            memLayout.add(layout[n]);

            curSize = memLayout.stream().mapToLong(MemoryLayout::byteSize).sum();
            while (nextBarrier <= curSize) nextBarrier += byteBarrier;
        }


        return MemoryLayout.structLayout(memLayout.toArray(new MemoryLayout[0]));
    }

    public static UnionLayout makeUnion(MemoryLayout ... layout)
    {
        return MemoryLayout.unionLayout(layout);
    }


    public static ClassDesc toDesc(Class<?> c)
    {
        return ClassDesc.of(c.getName());
    }

    public void templatedMethod()
    {
        try (var scope = Arena.ofConfined();) {

        }
        catch(Throwable th)
        {
            throw new Error(th);
        }
    }

    public static HashMap<Long, Object> buildEnumMapLong(Class<? extends EnumLong> enumClass)
    {
        var enumValues = enumClass.getEnumConstants();
        HashMap<Long, Object> ret = new HashMap<>();

        for (var eVal: enumValues)
        {
            if (eVal instanceof EnumLong el)
                ret.put(el.getCValue(), eVal);
        }
        return ret;
    }

    public static long[] enumToPrimitiveLong(Object[] vals)
    {
        if (vals == null)
            return  null;
        long[] ret = new long[vals.length];
        for (int n = 0; n < vals.length; ++n)
        {
            switch (vals[n]) {
                case null -> ret[n] = -1;
                case EnumLong el -> ret[n] = el.getCValue();
                default ->
                        throw new PassportException("Unknown object cannot be converted to C Enum value. " + vals[n]);
            }
        }
        return ret;
    }

    public static void primitiveToEnumLong(long[] vals, Object[] ret, HashMap<Long, Object> enumMap)
    {
        if (ret == null)
            return;

        if (vals == null)
        {
            Arrays.fill(ret, null);
            return;
        }

        int len = Math.min(vals.length, ret.length);
        for (int n = 0; n < len; ++n)
            ret[n] = enumMap.getOrDefault(vals[n], null);
    }

    public static int[] enumToPrimitiveInteger(Object[] vals)
    {
        if (vals == null)
            return  null;
        int[] ret = new int[vals.length];
        for (int n = 0; n < vals.length; ++n)
        {
            switch (vals[n]) {
                case null -> ret[n] = -1;
                case EnumInt el -> ret[n] = el.getCValue();
                case Enum<?> e -> ret[n] = e.ordinal();
                default ->
                        throw new PassportException("Unknown object cannot be converted to C Enum value. " + vals[n]);
            }
        }
        return ret;
    }

    public static HashMap<Integer, Object> buildEnumMapInteger(Class<?> enumClass)
    {
        var enumValues = enumClass.getEnumConstants();
        HashMap<Integer, Object> ret = new HashMap<>();

        for (var eVal: enumValues)
        {
            if (eVal instanceof EnumInt ei)
                ret.put(ei.getCValue(), eVal);
            else if (eVal instanceof Enum<?> ee)
                ret.put(ee.ordinal(), ee);
        }
        return ret;
    }

    public static void primitiveToEnumInteger(int[] vals, Object[] ret, HashMap<Integer, Object> enumMap)
    {
        if (ret == null)
            return;
        if (vals == null)
        {
            Arrays.fill(ret, null);
            return;
        }

        int len = Math.min(vals.length, ret.length);
        for (int n = 0; n < len; ++n)
            ret[n] = enumMap.getOrDefault(vals[n], null);
    }

    public static void structBuilt(Passport p, MemorySegment mem, MemoryLayout layout, String name, Object arg)
    {
        var debug = p.getDebug();
        if (debug.isEmpty())
            return;

        debug.get().structBuilt(mem, layout, name, new Object[] {arg});
    }

    public static void structBuilt(Passport p, MemorySegment mem, MemoryLayout layout, String name, Object[] arg)
    {
        var debug = p.getDebug();
        if (debug.isEmpty())
            return;

        debug.get().structBuilt(mem, layout, name, arg);
    }

    public static void structReadBack(Passport p, MemorySegment mem, MemoryLayout layout, String name, Object rec)
    {
        var debug = p.getDebug();
        if (debug.isEmpty())
            return;

        debug.get().structReadBack(mem, layout, name, rec);
    }

    public static void preNativeCall(Passport p, String name, Object ... args)
    {
        var debug = p.getDebug();
        if (debug.isEmpty())
            return;

        debug.get().preNativeCall(name, args);
    }

    public static void postNativeCall(Passport p, String name, Object ret, Object ... args)
    {
        var debug = p.getDebug();
        if (debug.isEmpty())
            return;

        debug.get().postNativeCall(name, ret, args);
    }

    public static String memToString(MemorySegment mem, MemoryLayout layout, String name)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(": ");

        if (layout instanceof StructLayout structLayout) {
            for (MemoryLayout ml : structLayout.memberLayouts())
            {
                if (ml instanceof PaddingLayout pad)
                {
                    sb.append("pad bytes=").append(pad.byteSize());
                }
                else {
                    sb.append(ml.name().orElse("<no name>")).append("=0x");
                    long size = ml.byteSize();
                    MemorySegment subSeg = mem.asSlice(structLayout.byteOffset(PathElement.groupElement(ml.name().get())), size);
                    var bytes = subSeg.toArray(JAVA_BYTE);
                    for (int n = 0; n < bytes.length; ++n)
                    {
                        if (n > 0 && n % 4 == 0)
                            sb.append(",0x");
                        sb.append(String.format("%02x", bytes[n]));
                     }
                }
                sb.append(", ");
            }
        }
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }
}
