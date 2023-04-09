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

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.foreign.*;
import java.lang.ref.Cleaner;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.IntStream;


public class Utils {

    public static Addressable toAddr(MemorySegment seg) {
        if (seg == null)
            return MemoryAddress.NULL;

        return seg.address();
    }

    /* Double ///////////////////////////////////////////////////////////////// */
    public static MemorySegment toMS(SegmentAllocator scope, double[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return null;
        return isReadBackOnly ? scope.allocate(arr.length * Double.BYTES) :
                scope.allocateArray(ValueLayout.JAVA_DOUBLE, arr);
    }

    public static MemorySegment toMS(SegmentAllocator scope, double[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return null;

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
            return null;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (double[] a : arr) {
            MemorySegment subSeg = scope.allocateArray(ValueLayout.JAVA_DOUBLE, a);
            segment.setAtIndex(ValueLayout.ADDRESS, n++, subSeg.address());
        }
        return segment;
    }

    public static void toArr(double[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        MemorySegment.copy(segment, ValueLayout.JAVA_DOUBLE, 0, arr, 0, arr.length);
    }

    public static double[] toArr(ValueLayout.OfDouble layout, MemorySegment seg, MemoryAddress addr, int count) {
        if (MemoryAddress.NULL.equals(addr.address()))
            return null;

        return slice(seg, addr, count * layout.byteSize()).toArray(layout);
    }

    public static double[] toArr(ValueLayout.OfDouble layout, MemoryAddress addr, int count) {
        if (MemoryAddress.NULL.equals(addr.address()))
            return null;

        double[] ret = new double[count];
        for (int n = 0; n < count; ++n)
            ret[n] = addr.getAtIndex(layout, n);
        return ret;
    }

    /* Float ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, float[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return null;

        return isReadBackOnly ? scope.allocate(arr.length * Float.BYTES) :
                scope.allocateArray(ValueLayout.JAVA_FLOAT, arr);
    }

    public static MemorySegment toMS(SegmentAllocator scope, float[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return null;

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
            return null;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (float[] a : arr) {
            MemorySegment subSeg = scope.allocateArray(ValueLayout.JAVA_FLOAT, a);
            segment.setAtIndex(ValueLayout.ADDRESS, n++, subSeg.address());
        }
        return segment;
    }

    public static void toArr(float[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        MemorySegment.copy(segment, ValueLayout.JAVA_FLOAT, 0, arr, 0, arr.length);
    }

    public static float[] toArr(ValueLayout.OfFloat layout, MemorySegment seg, MemoryAddress addr, int count) {
        if (MemoryAddress.NULL.equals(addr.address()))
            return null;

        return slice(seg, addr, count * layout.byteSize()).toArray(layout);
    }

    public static float[] toArr(ValueLayout.OfFloat layout, MemoryAddress addr, int count) {
        if (MemoryAddress.NULL.equals(addr.address()))
            return null;

        float[] ret = new float[count];
        for (int n = 0; n < count; ++n)
            ret[n] = addr.getAtIndex(layout, n);
        return ret;
    }

    /* Long ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, long[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return null;
        return isReadBackOnly ? scope.allocate(arr.length * Long.BYTES) :
                scope.allocateArray(ValueLayout.JAVA_LONG, arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, long[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (long[] a : arr) {
            MemorySegment subSeg = scope.allocateArray(ValueLayout.JAVA_LONG, a);
            segment.setAtIndex(ValueLayout.ADDRESS, n++, subSeg.address());
        }
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, long[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return null;

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

    public static long[] toArr(ValueLayout.OfLong layout, MemorySegment seg, MemoryAddress addr, int count) {
        if (MemoryAddress.NULL.equals(addr.address()))
            return null;

        return slice(seg, addr, count * layout.byteSize()).toArray(layout);
    }

    public static long[] toArr(ValueLayout.OfLong layout, MemoryAddress addr, int count) {
        if (MemoryAddress.NULL.equals(addr.address()))
            return null;

        long[] ret = new long[count];
        for (int n = 0; n < count; ++n)
            ret[n] = addr.getAtIndex(layout, n);
        return ret;
    }

    /* Int ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, int[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return null;

        return isReadBackOnly ? scope.allocate(arr.length * Integer.BYTES) :
                scope.allocateArray(ValueLayout.JAVA_INT, arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, int[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (int[] a : arr) {
            segment.setAtIndex(ValueLayout.ADDRESS, n++, scope.allocateArray(ValueLayout.JAVA_INT, a).address());
        }
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, int[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return null;

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

    public static int[] toArr(ValueLayout.OfInt layout, MemorySegment seg, MemoryAddress addr, int count) {
        if (MemoryAddress.NULL.equals(addr.address()))
            return null;

        return slice(seg, addr, count * layout.byteSize()).toArray(layout);
    }

    public static int[] toArr(ValueLayout.OfInt layout, MemoryAddress addr, int count) {
        if (MemoryAddress.NULL.equals(addr.address()))
            return null;

        int[] ret = new int[count];
        for (int n = 0; n < count; ++n)
            ret[n] = addr.getAtIndex(layout, n);
        return ret;
    }


    /* Short ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, short[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return null;

        return isReadBackOnly ? scope.allocate((int) (arr.length * Short.BYTES)) :
                scope.allocateArray(ValueLayout.JAVA_SHORT, arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, short[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (short[] a : arr) {
            segment.setAtIndex(ValueLayout.ADDRESS, n++, scope.allocateArray(ValueLayout.JAVA_SHORT, a).address());
        }
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, short[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return null;

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

    public static short[] toArr(ValueLayout.OfShort layout, MemorySegment seg, MemoryAddress addr, int count) {
        if (MemoryAddress.NULL.equals(addr.address()))
            return null;

        return slice(seg, addr, count * layout.byteSize()).toArray(layout);
    }

    public static short[] toArr(ValueLayout.OfShort layout, MemoryAddress addr, int count) {
        if (MemoryAddress.NULL.equals(addr.address()))
            return null;

        short[] ret = new short[count];
        for (int n = 0; n < count; ++n)
            ret[n] = addr.getAtIndex(layout, n);
        return ret;
    }

    /* Byte ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, byte[] arr, boolean isReadBackOnly) {
        if (arr == null)
            return null;

        return isReadBackOnly ? scope.allocate(arr.length) : scope.allocateArray(ValueLayout.JAVA_BYTE, arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, byte[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment = scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (byte[] a : arr)
            segment.setAtIndex(ValueLayout.ADDRESS, n++, scope.allocateArray(ValueLayout.JAVA_BYTE, a).address());
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, byte[][] arr, boolean isReadBackOnly) {
        if (arr == null)
            return null;

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

    public static byte[] toArr(ValueLayout.OfByte layout, MemorySegment seg, MemoryAddress addr, int count) {
        if (MemoryAddress.NULL.equals(addr.address()))
            return null;

        return slice(seg, addr, count * layout.byteSize()).toArray(layout);
    }

    public static byte[] toArr(ValueLayout.OfByte layout, MemoryAddress addr, int count) {
        if (MemoryAddress.NULL.equals(addr.address()))
            return null;

        byte[] ret = new byte[count];
        for (int n = 0; n < count; ++n)
            ret[n] = addr.get(layout, n);
        return ret;
    }

    /*///////////////////////////////////////////////////////////////// */

    public static MemorySegment slice(MemorySegment scope, MemoryAddress addr, long bytes) {
        return MemorySegment.ofAddress(addr, bytes, scope.session());
    }

    public static String readString(Addressable addr) {
        if (addr == MemoryAddress.NULL)
            return null;
        return addr.address().getUtf8String(0);
    }

    public static Addressable toCString(String s, MemorySession scope) {
        return scope.allocateUtf8String(s);
    }

    public static Addressable toCString(String s, SegmentAllocator scope) {
        return scope.allocateUtf8String(s);
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

        if (type.equals(byte.class)) return ValueLayout.JAVA_CHAR.byteSize();
        if (type.equals(short.class)) return ValueLayout.JAVA_SHORT.byteSize();
        if (type.equals(int.class)) return ValueLayout.JAVA_INT.byteSize();
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

        int size = 0;
        for (Field f : c.getDeclaredFields())
        {
            size += PassportWriter.getPaddingBits(f) / 8;

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
            else if (String.class.equals(type))
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
}
