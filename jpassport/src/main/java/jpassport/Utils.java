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
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;

import static jdk.incubator.foreign.CLinker.*;
import static jdk.incubator.foreign.CLinker.TypeKind.DOUBLE;

public class Utils
{
/* Double ///////////////////////////////////////////////////////////////// */
    public static MemorySegment toMS(SegmentAllocator scope, double[] arr) {
        return scope.allocateArray(C_DOUBLE, arr);
    }

    public static MemorySegment toMS(SegmentAllocator scope, double[][] arr) {
        MemorySegment segment =  scope.allocate((long) arr.length * arr[0].length * Double.BYTES);
        int n = 0;
        for (double[] row : arr) {
            segment.asSlice(n, (long)row.length * Double.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Double.BYTES;
        }

        return segment;
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, double[][] arr) {
        MemorySegment segment =  scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (double[] a : arr)
        {
            MemorySegment subSeg = scope.allocateArray(C_DOUBLE, a);
            MemoryAccess.setLongAtIndex(segment, n++, subSeg.address().toRawLongValue());
        }
        return segment;
    }

    public static void toArr(double[] arr, MemorySegment segment) {
        for (int n = 0; n < arr.length; ++n)
            arr[n] = MemoryAccess.getDoubleAtIndex(segment, n);
    }

    public static double[] toArrDouble(MemoryAddress addr, int count)
    {
        return addr.asSegment((long)count * Double.BYTES, MemorySegment.globalNativeSegment().scope()).toDoubleArray();
    }

    /* Float ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, float[] arr) {
        return scope.allocateArray(C_FLOAT, arr);
    }

    public static MemorySegment toMS(SegmentAllocator scope, float[][] arr) {
        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Float.BYTES);
        int n = 0;
        for (float[] row : arr) {
            segment.asSlice(n, (long)row.length * Float.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Float.BYTES;
        }

        return segment;
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, float[][] arr) {
        MemorySegment segment =  scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (float[] a : arr)
        {
            MemorySegment subSeg = scope.allocateArray(C_FLOAT, a);
            MemoryAccess.setLongAtIndex(segment, n++, subSeg.address().toRawLongValue());
        }
        return segment;
    }

    public static void toArr(float[] arr, MemorySegment segment) {
        for (int n = 0; n < arr.length; ++n)
            arr[n] = MemoryAccess.getFloatAtIndex(segment, n);
    }

    public static float[] toArrFloat(MemoryAddress addr, int count)
    {
        return addr.asSegment((long)count * Float.BYTES, MemorySegment.globalNativeSegment().scope()).toFloatArray();
    }

    /* Long ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, long[] arr) {
        return scope.allocateArray(C_LONG_LONG, arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, long[][] arr) {
        MemorySegment segment =  scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (long[] a : arr)
        {
            MemorySegment subSeg = scope.allocateArray(C_LONG_LONG, a);
            MemoryAccess.setLongAtIndex(segment, n++, subSeg.address().toRawLongValue());
        }
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, long[][] arr) {
        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Long.BYTES);
        int n = 0;
        for (long[] row : arr) {
            segment.asSlice(n, (long)row.length * Long.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Long.BYTES;
        }

        return segment;
    }

    public static void toArr(long[] arr, MemorySegment segment) {
        for (int n = 0; n < arr.length; ++n)
            arr[n] = MemoryAccess.getLongAtIndex(segment, n);
    }

    public static long[] toArrLong(MemoryAddress addr, int count)
    {
        return addr.asSegment((long)count * Long.BYTES, MemorySegment.globalNativeSegment().scope()).toLongArray();
    }

    /* Int ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, int[] arr) {
        return scope.allocateArray(C_INT, arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, int[][] arr) {
        MemorySegment segment =  scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (int[] a : arr)
            MemoryAccess.setLongAtIndex(segment, n++, scope.allocateArray(C_INT, a).address().toRawLongValue());
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, int[][] arr) {
        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Integer.BYTES);
        int n = 0;
        for (int[] row : arr) {
            segment.asSlice(n, (long)row.length * Integer.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Integer.BYTES;
        }

        return segment;
    }

    public static void toArr(int[] arr, MemorySegment segment) {
        for (int n = 0; n < arr.length; ++n)
            arr[n] = MemoryAccess.getIntAtIndex(segment, n);
    }

    public static int[] toArrInt(MemoryAddress addr, int count)
    {
        return addr.asSegment((long)count * Integer.BYTES, MemorySegment.globalNativeSegment().scope()).toIntArray();
    }

/* Short ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, short[] arr) {
        return scope.allocateArray(C_SHORT, arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, short[][] arr) {
        MemorySegment segment =  scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (short[] a : arr)
            MemoryAccess.setLongAtIndex(segment, n++, scope.allocateArray(C_SHORT, a).address().toRawLongValue());
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, short[][] arr) {
        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Short.BYTES);
        int n = 0;
        for (short[] row : arr) {
            segment.asSlice(n, (long)row.length * Short.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Short.BYTES;
        }

        return segment;
    }

    public static void toArr(short[] arr, MemorySegment segment) {
        for (int n = 0; n < arr.length; ++n)
            arr[n] = MemoryAccess.getShortAtIndex(segment, n);
    }

    public static short[] toArrShort(MemoryAddress addr, int count)
    {
        return addr.asSegment((long)count * Short.BYTES, MemorySegment.globalNativeSegment().scope()).toShortArray();
    }

/* Byte ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(SegmentAllocator scope, byte[] arr) {
        return scope.allocateArray(C_CHAR, arr);
    }

    public static MemorySegment toPtrPTrMS(SegmentAllocator scope, byte[][] arr) {
        MemorySegment segment =  scope.allocate((long) arr.length * Long.BYTES);
        int n = 0;
        for (byte[] a : arr)
            MemoryAccess.setLongAtIndex(segment, n++, scope.allocateArray(C_CHAR, a).address().toRawLongValue());
        return segment;
    }

    public static MemorySegment toMS(SegmentAllocator scope, byte[][] arr) {
        MemorySegment segment = scope.allocate((long) arr.length * arr[0].length * Byte.BYTES);
        int n = 0;
        for (byte[] row : arr) {
            segment.asSlice(n, row.length * Byte.BYTES).copyFrom(MemorySegment.ofArray(row));
            n += row.length * Byte.BYTES;
        }

        return segment;
    }

    public static void toArr(byte[] arr, MemorySegment segment) {
        for (int n = 0; n < arr.length; ++n)
            arr[n] = MemoryAccess.getByteAtOffset(segment, n);
    }

    public static byte[] toArrByte(MemoryAddress addr, int count)
    {
        return addr.asSegment((long)count * Byte.BYTES, MemorySegment.globalNativeSegment().scope()).toByteArray();
    }

/*///////////////////////////////////////////////////////////////// */

    public static MemorySegment slice(ResourceScope scope, MemoryAddress addr, long bytes)
    {
        return addr.asSegment(bytes, scope);
    }

    public static String readString(MemoryAddress addr)
    {
        return toJavaString(addr);
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
