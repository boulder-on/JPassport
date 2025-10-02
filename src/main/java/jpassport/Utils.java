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
import static jpassport.codebuilder.CBConstants.getPaddingBytes;

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

        long size = 0;
        for (Field f : c.getDeclaredFields())
        {
            size += getPaddingBytes(f);

            Class<?> type = f.getType();
            if (type.isPrimitive())
                size += typeToSize(type);
            else if (type.isRecord())
            {
                boolean isPtr = f.getAnnotationsByType(Ptr.class).length > 0;
                if (isPtr)
                    size += ValueLayout.ADDRESS.byteSize();
                else
                    size += size_of(type);
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
            if (curSize + nextItemSize > nextBarrier && nextBarrier - curSize > 0)
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

//    private static final Map<Class<?>, MemoryLayout> typeToCName = new HashMap<>()
//    {
//        {
//            put(byte.class, ValueLayout.JAVA_CHAR);
//            put(short.class, ValueLayout.JAVA_SHORT);
//            put(int.class, ValueLayout.JAVA_INT);
//            put(long.class, ValueLayout.JAVA_LONG);
//            put(float.class, ValueLayout.JAVA_FLOAT);
//            put(double.class, ValueLayout.JAVA_DOUBLE);
//        }
//    };

//    record StructField(Field field, Method accessor, MethodHandle mhandle, String name, Class<?> type, MemoryLayout layout, long offset, boolean isPtr){
//        public Object get(Object rec)
//        {
//            try {
//                return accessor.invoke(rec);
//            } catch (IllegalAccessException | InvocationTargetException e) {
//                throw new RuntimeException(e);
//            }
//        }
//
//        public MemorySegment ofArray(Object rec)
//        {
//            if (!type.isArray())
//                throw new IllegalArgumentException("Field is not an array");
//
//            Class<?> arrType = type.getComponentType();
//
//           if (arrType.equals(byte.class))
//               return MemorySegment.ofArray((byte[])get(rec));
//           else if (arrType.equals(short.class))
//                return MemorySegment.ofArray((short[])get(rec));
//           else if (arrType.equals(int.class))
//               return MemorySegment.ofArray((int[])get(rec));
//           else if (arrType.equals(long.class))
//               return MemorySegment.ofArray((long[])get(rec));
//           else if (arrType.equals(float.class))
//               return MemorySegment.ofArray((float[])get(rec));
//           else if (arrType.equals(double.class))
//               return MemorySegment.ofArray((double[])get(rec));
//
//           throw new IllegalArgumentException("Unknown array type for conversion.");
//        }
//
//        public MemorySegment toPointer(SegmentAllocator scope, Object rec)
//        {
//            if (!type.isArray())
//                throw new IllegalArgumentException("Field is not an array");
//
//            Class<?> arrType = type.getComponentType();
//
//            if (arrType.equals(byte.class))
//                return Utils.toMS(scope, (byte[])get(rec), false);
//            else if (arrType.equals(short.class))
//                return Utils.toMS(scope, (short[])get(rec), false);
//            else if (arrType.equals(int.class))
//                return Utils.toMS(scope, (int[])get(rec), false);
//            else if (arrType.equals(long.class))
//                return Utils.toMS(scope, (long[])get(rec), false);
//            else if (arrType.equals(float.class))
//                return Utils.toMS(scope, (float[])get(rec), false);
//            else if (arrType.equals(double.class))
//                return Utils.toMS(scope, (double[])get(rec), false);
//
//            throw new IllegalArgumentException("Unknown array type for conversion.");
//        }
//    }
//
//    static class StructConversionDetails
//    {
//        final Class<?>  recordType;
//        final GroupLayout layout;
//        List<StructField> fields = new ArrayList<>();
//        Constructor<?> constructor = null;
//
//        StructConversionDetails(Class<?> t)
//        {
//            recordType = t;
//
//            List<MemoryLayout> memLayout = new ArrayList<>();
//            for (Field f : recordType.getDeclaredFields()) {
//                MemoryLayout layoutType = null;
//                boolean isPtr = f.getAnnotationsByType(Ptr.class).length > 0;
//
//                int paddingBytes = getPaddingBytes(f);
//                if (paddingBytes < 0)
//                    memLayout.add(MemoryLayout.paddingLayout(paddingBytes));
//
//                Class<?> type = f.getType();
//                if (type.isPrimitive())
//                {
//                    layoutType = typeToCName.get(type).withName(f.getName());
//                    memLayout.add(layoutType);
//                }
//                else if (type.isRecord())
//                {
//                    var subStruct = getStructDetails(type);
//                    if (isPtr)
//                    {
//                        layoutType = ValueLayout.ADDRESS.withName(f.getName());
//                        memLayout.add(layoutType);
//                    }
//                    else
//                    {
//                        layoutType = subStruct.layout.withName(f.getName());
//                        memLayout.add(layoutType);
//                    }
//                }
//                else if (String.class.equals(type) || MemorySegment.class.equals(type) || isGenericPtr(type))
//                {
//                    layoutType = ValueLayout.ADDRESS.withName(f.getName());
//                    memLayout.add(layoutType);
//                }
//                else if (type.isArray())
//                {
//                    Annotation[] arrays = f.getAnnotationsByType(Array.class);
//
//                    if (arrays.length > 0)
//                    {
//                        int length = ((Array) arrays[0]).length();
//                        Class<?> arrType = type.getComponentType();
//                        layoutType = MemoryLayout.sequenceLayout(length, typeToCName.get(arrType)).withName(f.getName());
//                        memLayout.add(layoutType);
//                    }
//                    else if (isPtr)
//                    {
//                        layoutType = ValueLayout.ADDRESS.withName(f.getName());
//                        memLayout.add(layoutType);
//                    }
//                    else
//                        throw new PassportException("Record arrays must be defined with either an Array or Ptr annotation");
//                }
//
//                if (paddingBytes > 0)
//                    memLayout.add(MemoryLayout.paddingLayout(paddingBytes));
//
//                var accessor = Arrays.stream(recordType.getDeclaredMethods()).filter(m -> m.getName().equals(f.getName())).findFirst();
//                MethodHandle mh = null;
//                try {
//                    mh = MethodHandles.publicLookup().findVirtual(recordType, f.getName(), MethodType.methodType(f.getType()));
//                } catch (NoSuchMethodException e) {
//                    throw new RuntimeException(e);
//                } catch (IllegalAccessException e) {
//                    throw new RuntimeException(e);
//                }
//
//                fields.add(new StructField(f, accessor.orElseGet(null), mh, f.getName(), f.getType(), layoutType, 0, isPtr));
//            }
//            layout = makeStruct(memLayout.toArray(new MemoryLayout[0]));
//
//            List<StructField> cpy = new ArrayList<>();
//            for (var f : fields)
//                cpy.add(new StructField(f.field(), f.accessor(), f.mhandle(), f.name(), f.type(), f.layout(), layout.byteOffset(groupElement(f.name())), f.isPtr()));
//            fields = cpy;
//        }
//        MemorySegment toNative(SegmentAllocator scope, Object rec)
//        {
//            return toNative(scope, new Object[]{rec});
//        }
//
//        MemorySegment toNative(SegmentAllocator scope, Object[] recs)
//        {
//            long size = layout.byteSize();
//            MemorySegment memStruct = scope.allocate(size * recs.length);
//            long offset = 0;
//            for (Object rec : recs) {
//                for (var f : fields)
//                {
////                    try
//                    {
//                        if (f.type().isPrimitive()) {
//                            try {
//                                if (f.type.equals(byte.class))
//                                    memStruct.set(ValueLayout.JAVA_BYTE, f.offset + offset, (byte)f.mhandle.invoke(rec));
//                                else if (f.type.equals(short.class))
//                                    memStruct.set(ValueLayout.JAVA_SHORT, f.offset + offset, (short)f.mhandle.invoke(rec));
//                                else if (f.type.equals(int.class))
//                                    memStruct.set(ValueLayout.JAVA_INT, f.offset + offset, (int)f.mhandle.invoke(rec));
//                                else if (f.type.equals(long.class))
//                                    memStruct.set(ValueLayout.JAVA_LONG, f.offset + offset, (long)f.mhandle.invoke(rec));
//                                else if (f.type.equals(float.class))
//                                    memStruct.set(ValueLayout.JAVA_FLOAT, f.offset + offset, (float)f.mhandle.invoke(rec));
//                                else if (f.type.equals(double.class))
//                                    memStruct.set(ValueLayout.JAVA_DOUBLE, f.offset + offset, (double)f.mhandle.invoke(rec));
//                                else if (f.type.equals(boolean.class))
//                                    memStruct.set(ValueLayout.JAVA_BOOLEAN, f.offset + offset, (boolean)f.mhandle.invoke(rec));
//                            }
//                            catch (Throwable ex)
//                            {
//                                throw new RuntimeException(ex);
//                            }
//                        }
//                        else if (f.type().isRecord())
//                        {
//                            if (f.isPtr())
//                                memStruct.set(ValueLayout.ADDRESS, f.offset + offset, getStructDetails(f.type()).toNative(scope, f.get(rec)));
//                            else
//                                memStruct.asSlice(f.offset + offset).copyFrom(getStructDetails(f.type()).toNative(scope, f.get(rec)));
//                        }
//                        else if (MemorySegment.class.equals(f.type()))
//                            memStruct.set(ValueLayout.ADDRESS,f.offset + offset, (MemorySegment) f.get(rec));
//                        else if (String.class.equals(f.type()))
//                            memStruct.set(ValueLayout.ADDRESS, f.offset + offset, Utils.toCString((String)f.get(rec), scope));
//                        else if (f.type().isArray())
//                        {
//                            Class<?> arrType = f.type().getComponentType();
//                            Annotation[] arrays = f.field().getAnnotationsByType(Array.class);
//                            boolean isPointer = f.field().getAnnotationsByType(Ptr.class).length > 0;
//
//                            if (arrType.isPrimitive())
//                            {
//                                if (arrays.length > 0)
//                                    memStruct.asSlice(f.offset + offset).copyFrom(f.ofArray(rec));
//                                else if (isPointer)
//                                    memStruct.set(ValueLayout.ADDRESS, f.offset + offset, f.toPointer(scope, rec));
//                            }
//                            else if (arrType.isRecord())
//                            {
//                                //todo: implement array records
//                            }
//
//                        }
//                    }
////                    catch (IllegalAccessException e) {
////                        throw new RuntimeException(e);
////                    }
//                }
//                offset += size;
//            }
//            return memStruct;
//        }
//
//        private Object fromNative(MemorySegment memStruct, Object recArr) {
//            memStruct = Utils.resize(memStruct, layout.byteSize());
//            List args = new ArrayList();
//            long offset = 0;
//
//            Object rec = recArr;
//            if (rec.getClass().isArray())
//                rec = ((Object[])recArr)[0];
//
//            for (var f : fields)
//            {
//                if (f.type().isPrimitive()) {
//                    if (f.type.equals(byte.class))
//                        args.add(memStruct.get(ValueLayout.JAVA_BYTE, f.offset + offset));
//                    else if (f.type.equals(short.class))
//                        args.add(memStruct.get(ValueLayout.JAVA_SHORT, f.offset + offset));
//                    else if (f.type.equals(int.class))
//                        args.add(memStruct.get(ValueLayout.JAVA_INT, f.offset + offset));
//                    else if (f.type.equals(long.class))
//                        args.add(memStruct.get(ValueLayout.JAVA_LONG, f.offset + offset));
//                    else if (f.type.equals(float.class))
//                        args.add(memStruct.get(ValueLayout.JAVA_FLOAT, f.offset + offset));
//                    else if (f.type.equals(double.class))
//                        args.add(memStruct.get(ValueLayout.JAVA_DOUBLE, f.offset + offset));
//                    else if (f.type.equals(boolean.class))
//                        args.add(memStruct.get(ValueLayout.JAVA_BOOLEAN, f.offset + offset));
//                }
//                else if (f.type.isRecord())
//                {
//                    var subStruct = getStructDetails(f.type());
//                    if (f.isPtr())
//                        args.add(subStruct.fromNative(Utils.slice(memStruct, memStruct.get(ValueLayout.ADDRESS,f.offset + offset), subStruct.layout.byteSize()), subStruct.recordType));
//                    else
//                        args.add(subStruct.fromNative(memStruct.asSlice(f.offset + offset), subStruct.recordType));
//                }
//                else if (MemorySegment.class.equals(f.type))
//                    args.add(memStruct.get(ValueLayout.ADDRESS,f.offset + offset));
//                else if (isGenericPtr(f.type))
//                {
//                    var mem = memStruct.get(ValueLayout.ADDRESS,f.offset + offset);
//                    //todo: WTF?
////                    sb.append(String.format("\t\tvar %1$s = new %2$s(mem_%1$s);\n", f.getName(), type.getName()));
//                }
//                else if (String.class.equals(f.type))
//                    args.add(Utils.readString(memStruct.get(ValueLayout.ADDRESS,f.offset + offset)));
//                else if (f.type.isArray())
//                {
//                    Class<?> arrType = f.type.getComponentType();
//                    Annotation[] arrays = f.field.getAnnotationsByType(Array.class);
//
//                    if (arrType.isPrimitive())
//                    {
//                        if (arrays.length > 0) {
//                            int length = ((Array) arrays[0]).length();
//                            if (arrType.equals(byte.class))
//                                args.add(memStruct.asSlice(f.offset + offset, length * Integer.BYTES).toArray(ValueLayout.JAVA_BYTE));
//                            else if (arrType.equals(short.class))
//                                args.add(memStruct.asSlice(f.offset + offset, length * Short.BYTES).toArray(ValueLayout.JAVA_SHORT));
//                            else if (arrType.equals(int.class))
//                                args.add(memStruct.asSlice(f.offset + offset, length * Integer.BYTES).toArray(ValueLayout.JAVA_INT));
//                            else if (arrType.equals(long.class))
//                                args.add(memStruct.asSlice(f.offset + offset, length * Long.BYTES).toArray(ValueLayout.JAVA_LONG));
//                            else if (arrType.equals(float.class))
//                                args.add(memStruct.asSlice(f.offset + offset, length * Float.BYTES).toArray(ValueLayout.JAVA_FLOAT));
//                            else if (arrType.equals(double.class))
//                                args.add(memStruct.asSlice(f.offset + offset, length * Double.BYTES).toArray(ValueLayout.JAVA_DOUBLE));
//                        }
//                        else if (f.isPtr())
//                        {
//                            if (arrType.equals(byte.class)) {
//                                int size = ((byte[])f.get(rec)).length;
//                                args.add(Utils.toArr(ValueLayout.JAVA_BYTE, memStruct, memStruct.get(ValueLayout.ADDRESS, f.offset + offset), size));
//                            }
//                            else if (arrType.equals(short.class))
//                            {
//                                int size = ((short[])f.get(rec)).length;
//                                args.add(Utils.toArr(ValueLayout.JAVA_SHORT, memStruct, memStruct.get(ValueLayout.ADDRESS, f.offset + offset), size));
//                            }
//                            else if (arrType.equals(int.class))
//                            {
//                                int size = ((int[])f.get(rec)).length;
//                                args.add(Utils.toArr(ValueLayout.JAVA_INT, memStruct, memStruct.get(ValueLayout.ADDRESS, f.offset + offset), size));
//                            }
//                            else if (arrType.equals(long.class))
//                            {
//                                int size = ((long[])f.get(rec)).length;
//                                args.add(Utils.toArr(ValueLayout.JAVA_LONG, memStruct, memStruct.get(ValueLayout.ADDRESS, f.offset + offset), size));
//                            }
//                            else if (arrType.equals(float.class))
//                            {
//                                int size = ((float[])f.get(rec)).length;
//                                args.add(Utils.toArr(ValueLayout.JAVA_FLOAT, memStruct, memStruct.get(ValueLayout.ADDRESS, f.offset + offset), size));
//                            }
//                            else if (arrType.equals(double.class))
//                            {
//                                int size = ((double[])f.get(rec)).length;
//                                args.add(Utils.toArr(ValueLayout.JAVA_DOUBLE, memStruct, memStruct.get(ValueLayout.ADDRESS, f.offset + offset), size));
//                            }
//                        }
//                    }
//                }
//            }
//
//            //caching the constructor saves significant time
//            if (constructor == null) {
//                try {
//                    Class<?>[] argList = new Class[fields.size()];
//                    for (int n = 0; n < argList.length; ++n)
//                        argList[n] = fields.get(n).type();
//                    constructor = recordType.getConstructor(argList);
//                }
//                catch (NoSuchMethodException ex)
//                {
//                    throw new RuntimeException(ex);
//                }
//            }
//
//            try {
//                return constructor.newInstance(args.toArray());
//            } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
//                throw new RuntimeException(e);
//            }
//
//        }
//
//    }
//
//    static final HashMap<Class<?>, StructConversionDetails> StructDetails = new HashMap<>();
//
//    private static StructConversionDetails getStructDetails(Class<?> rec)
//    {
//        return StructDetails.computeIfAbsent(rec, k -> new StructConversionDetails(rec));
//    }
//
//    public static MemorySegment storeStruct(Arena scope, Object rec)
//    {
//        return storeStruct(scope, new Object[] {rec});
//    }
//
//    public static MemorySegment storeStruct(Arena scope, Object[] rec)
//    {
//        var sd = getStructDetails(rec[0].getClass());
//        return sd.toNative(scope, rec);
//    }
//
//    public static void readBackStruct(MemorySegment memorySegment, Object[] rec)
//    {
//        var sd = getStructDetails(rec[0].getClass());
//        rec[0] =  sd.fromNative(memorySegment, rec);
//    }

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

}
