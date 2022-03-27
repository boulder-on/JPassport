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

import jdk.incubator.foreign.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;


import static jdk.incubator.foreign.CLinker.*;
//import static jdk.incubator.foreign.CLinker.TypeKind.DOUBLE;

public class Utils
{

    public static Addressable toAddr(MemorySegment seg)
    {
        if (seg == null)
            return MemoryAddress.NULL;

        return seg;
    }
    /* Double ///////////////////////////////////////////////////////////////// */
    public static MemorySegment toMS(SegmentAllocator scope, double[] arr) {
        if (arr == null)
            return null;
        return scope.allocateArray(ValueLayout.JAVA_DOUBLE, arr);
    }

    public static MemorySegment toMS(SegmentAllocator scope, double[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment =  scope.allocate((long) arr.length * arr[0].length * Double.BYTES);
        int n = 0;
        for (double[] row : arr) {
            segment.asSlice(n, (long)row.length * Double.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Double.BYTES;
        }

        return segment;
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, double[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment =  scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (double[] a : arr)
        {
            MemorySegment subSeg = scope.allocateArray(ValueLayout.JAVA_DOUBLE, a);
            segment.setAtIndex(ValueLayout.ADDRESS, n++, subSeg.address());
        }
        return segment;
    }

    public static void toArr(double[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        var s = segment.toArray(ValueLayout.JAVA_DOUBLE);
        System.arraycopy(s, 0, arr, 0, arr.length);
    }

    public static double[] toArrDouble(MemoryAddress addr, int count)
    {
        if (addr == MemoryAddress.NULL)
            return null;

        //todo: fix all of these to this line!!
//        return MemorySegment.ofAddress(addr, count * Long.BYTES, scope).toArray(ValueLayout.JAVA_LONG);

        var ret = new double[count];
        for (int n = 0; n < count; ++n)
            ret[n] = addr.getAtIndex(ValueLayout.JAVA_DOUBLE, n);
        return ret;
    }

    /* Float ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, float[] arr) {
        if (arr == null)
            return null;

        return scope.allocateArray(ValueLayout.JAVA_FLOAT, arr);
    }

    public static MemorySegment toMS(SegmentAllocator scope, float[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Float.BYTES);
        int n = 0;
        for (float[] row : arr) {
            segment.asSlice(n, (long)row.length * Float.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Float.BYTES;
        }

        return segment;
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, float[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment =  scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (float[] a : arr)
        {
            MemorySegment subSeg = scope.allocateArray(ValueLayout.JAVA_FLOAT, a);
            segment.setAtIndex(ValueLayout.ADDRESS, n++, subSeg.address());
        }
        return segment;
    }

    public static void toArr(float[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        var s = segment.toArray(ValueLayout.JAVA_FLOAT);
        System.arraycopy(s, 0, arr, 0, arr.length);
    }

    public static float[] toArrFloat(MemoryAddress addr, int count)
    {
        if (addr == MemoryAddress.NULL)
            return null;

        //todo: fix all of these to this line!!
//        return MemorySegment.ofAddress(addr, count * Float.BYTES, scope).toArray(ValueLayout.JAVA_FLOAT);

        var ret = new float[count];
        for (int n = 0; n < count; ++n)
            ret[n] = addr.getAtIndex(ValueLayout.JAVA_FLOAT, n);
        return ret;
    }

    /* Long ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, long[] arr) {
        if (arr == null)
            return null;
        return scope.allocateArray(ValueLayout.JAVA_LONG, arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, long[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment =  scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (long[] a : arr)
        {
            MemorySegment subSeg = scope.allocateArray(ValueLayout.JAVA_LONG, a);
            segment.setAtIndex(ValueLayout.ADDRESS, n++, subSeg.address());
        }
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, long[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Long.BYTES);
        int n = 0;
        for (long[] row : arr) {
            segment.asSlice(n, (long)row.length * Long.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Long.BYTES;
        }

        return segment;
    }

    public static void toArr(long[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        var s = segment.toArray(ValueLayout.JAVA_LONG);
        System.arraycopy(s, 0, arr, 0, arr.length);
    }

    public static long[] toArrLong(MemoryAddress addr, int count)
    {
        if (addr == MemoryAddress.NULL)
            return null;

        //todo: fix all of these to this line!!
//        return MemorySegment.ofAddress(addr, count * Long.BYTES, scope).toArray(ValueLayout.JAVA_LONG);

        var ret = new long[count];
        for (int n = 0; n < count; ++n)
            ret[n] = addr.getAtIndex(ValueLayout.JAVA_LONG, n);
        return ret;
    }

    /* Int ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, int[] arr) {
        if (arr == null)
            return null;

        return scope.allocateArray(ValueLayout.JAVA_INT, arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, int[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment =  scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (int[] a : arr)
        {
            MemorySegment subSeg = scope.allocateArray(ValueLayout.JAVA_INT, a);
            segment.setAtIndex(ValueLayout.ADDRESS, n++, subSeg.address());

        }
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, int[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Integer.BYTES);
        int n = 0;
        for (int[] row : arr) {
            segment.asSlice(n, (long)row.length * Integer.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Integer.BYTES;
        }

        return segment;
    }

    public static void toArr(int[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        var s = segment.toArray(ValueLayout.JAVA_INT);
        System.arraycopy(s, 0, arr, 0, arr.length);
    }

    public static int[] toArrInt(MemoryAddress addr, int count)
    {
        if (addr == MemoryAddress.NULL)
            return null;

        //todo: fix all of these to this line!!
//        return MemorySegment.ofAddress(addr, count * Long.BYTES, scope).toArray(ValueLayout.JAVA_LONG);

        var ret = new int[count];
        for (int n = 0; n < count; ++n)
            ret[n] = addr.getAtIndex(ValueLayout.JAVA_INT, n);
        return ret;
    }

    /* Short ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, short[] arr) {
        if (arr == null)
            return null;

        return scope.allocateArray(ValueLayout.JAVA_SHORT, arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, short[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment =  scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (short[] a : arr)
        {
            MemorySegment subSeg = scope.allocateArray(ValueLayout.JAVA_SHORT, a);
            segment.setAtIndex(ValueLayout.ADDRESS, n++, subSeg.address());
        }
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, short[][] arr) {
        if (arr == null)
            return null;

        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Short.BYTES);
        int n = 0;
        for (short[] row : arr) {
            segment.asSlice(n, (long)row.length * Short.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Short.BYTES;
        }

        return segment;
    }

    public static void toArr(short[] arr, MemorySegment segment) {
        if (arr == null)
            return;

        var s = segment.toArray(ValueLayout.JAVA_SHORT);
        System.arraycopy(s, 0, arr, 0, arr.length);
    }

    public static short[] toArrShort(MemoryAddress addr, int count)
    {
        if (addr == MemoryAddress.NULL)
            return null;

        //todo: fix all of these to this line!!
//        return MemorySegment.ofAddress(addr, count * Long.BYTES, scope).toArray(ValueLayout.JAVA_LONG);

        var ret = new short[count];
        for (int n = 0; n < count; ++n)
            ret[n] = addr.getAtIndex(ValueLayout.JAVA_SHORT, n);
        return ret;
    }

    /* Byte ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, byte[] arr) {
        if (arr == null)
            return null;

        return scope.allocateArray(ValueLayout.JAVA_BYTE, arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, byte[][] arr) {
        if (arr == null)
            return null;


        MemorySegment segment =  scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (byte[] a : arr)
        {
            MemorySegment subSeg = scope.allocateArray(ValueLayout.JAVA_BYTE, a);
            segment.setAtIndex(ValueLayout.ADDRESS, n++, subSeg.address());
        }
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, byte[][] arr) {
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

        var s = segment.toArray(ValueLayout.JAVA_BYTE);
        System.arraycopy(s, 0, arr, 0, arr.length);
    }

    public static byte[] toArrByte(MemoryAddress addr, int count)
    {
        if (addr == MemoryAddress.NULL)
            return null;

        //todo: fix all of these to this line!!
//        return MemorySegment.ofAddress(addr, count * Long.BYTES, scope).toArray(ValueLayout.JAVA_LONG);

        var ret = new byte[count];
        for (int n = 0; n < count; ++n)
            ret[n] = addr.get(ValueLayout.JAVA_BYTE, n);
        return ret;
    }

    /*///////////////////////////////////////////////////////////////// */

    public static MemorySegment slice(ResourceScope scope, MemoryAddress addr, long bytes)
    {
        return MemorySegment.ofAddress(addr, bytes, scope);
    }

    public static String readString(MemoryAddress addr)
    {
        if (addr == MemoryAddress.NULL)
            return null;
        return addr.getUtf8String(0);
    }

    public static MemorySegment toCString(String s, ResourceScope scope)
    {
        var seg = MemorySegment.allocateNative(s.getBytes(StandardCharsets.UTF_8).length + 1, scope);
        seg.setUtf8String(0, s);
        return seg;
    }

    public static MemorySegment toCString(String s, SegmentAllocator allocator)
    {
        var seg = allocator.allocate(s.getBytes(StandardCharsets.UTF_8).length + 1);
        seg.setUtf8String(0, s);
        return seg;
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

    public static Path getBuildFolder()
    {
        if (System.getProperty("jpassport.build.home") != null)
            return Path.of(System.getProperty("jpassport.build.home"));
        return Path.of(System.getProperty("java.io.tmpdir"), "jpassport");
    }

    public enum Platform {Windows, Mac, Linux, Unknown}

    public static Platform getPlatform()
    {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win"))
            return Platform.Windows;
        if (os.contains("mac"))
            return Platform.Mac;
        if ((os.contains("nix") || os.contains("nux") || os.contains("aix")))
            return Platform.Linux;

        return Platform.Unknown;
    }
}
