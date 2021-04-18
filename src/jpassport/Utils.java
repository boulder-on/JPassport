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

public class Utils
{
/* Double ///////////////////////////////////////////////////////////////// */
    public static MemorySegment toMS(double[] arr) {
        MemorySegment iseg = MemorySegment.ofArray(arr);
        MemorySegment segment = MemorySegment.allocateNative(iseg.byteSize());
        segment.copyFrom(iseg);
        return segment;
    }

    public static MemorySegment toMS(double[][] arr) {
        MemorySegment segment = MemorySegment.allocateNative((long) arr.length * arr[0].length * Double.BYTES);
        int n = 0;
        for (double[] row : arr) {
            for (double i : row)
                MemoryAccess.setDoubleAtIndex(segment, n++, i);
        }

        return segment;
    }

    public static MemorySegment toPtrPTrMS(double[][] arr) {
        PtrPtrMemorySegment segment = new PtrPtrMemorySegment(arr.length);
        for (double[] a : arr)  segment.addNative(a);
        return segment;
    }

    public static void toArr(double[] arr, MemorySegment segment) {
        for (int n = 0; n < arr.length; ++n)
            arr[n] = MemoryAccess.getDoubleAtIndex(segment, n);
    }


    /* Float ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(float[] arr) {
        MemorySegment iseg = MemorySegment.ofArray(arr);
        MemorySegment segment = MemorySegment.allocateNative(iseg.byteSize());
        segment.copyFrom(iseg);
        return segment;
    }

    public static MemorySegment toMS(float[][] arr) {
        MemorySegment segment = MemorySegment.allocateNative((long) arr.length * arr[0].length * Float.BYTES);
        int n = 0;
        for (float[] row : arr) {
            for (float i : row)
                MemoryAccess.setFloatAtIndex(segment, n++, i);
        }

        return segment;
    }

    public static MemorySegment toPtrPTrMS(float[][] arr) {
        PtrPtrMemorySegment segment = new PtrPtrMemorySegment(arr.length);
        for (float[] a : arr)
            segment.addNative(a);
        return segment;
    }

    public static void toArr(float[] arr, MemorySegment segment) {
        for (int n = 0; n < arr.length; ++n)
            arr[n] = MemoryAccess.getFloatAtIndex(segment, n);
    }

/* Long ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(long[] arr) {
        MemorySegment iseg = MemorySegment.ofArray(arr);
        MemorySegment segment = MemorySegment.allocateNative(iseg.byteSize());
        segment.copyFrom(iseg);
        return segment;
    }

    public static MemorySegment toPtrPTrMS(long[][] arr) {
        PtrPtrMemorySegment segment = new PtrPtrMemorySegment(arr.length);
        for (long[] a : arr)  segment.addNative(a);
        return segment;
    }

    public static MemorySegment toMS(long[][] arr) {
        MemorySegment segment = MemorySegment.allocateNative((long) arr.length * arr[0].length * Long.BYTES);
        int n = 0;
        for (long[] row : arr) {
            for (long i : row)
                MemoryAccess.setLongAtIndex(segment, n++, i);
        }

        return segment;
    }

    public static void toArr(long[] arr, MemorySegment segment) {
        for (int n = 0; n < arr.length; ++n)
            arr[n] = MemoryAccess.getLongAtIndex(segment, n);
    }

/* Int ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(int[] arr) {
        MemorySegment iseg = MemorySegment.ofArray(arr);
        MemorySegment segment = MemorySegment.allocateNative(iseg.byteSize());
        segment.copyFrom(iseg);
        return segment;
    }

    public static MemorySegment toPtrPTrMS(int[][] arr) {
        PtrPtrMemorySegment segment = new PtrPtrMemorySegment(arr.length);
        for (int[] a : arr)  segment.addNative(a);
        return segment;
    }

    public static MemorySegment toMS(int[][] arr) {
        MemorySegment segment = MemorySegment.allocateNative((long) arr.length * arr[0].length * Integer.BYTES);
        int n = 0;
        for (int[] row : arr) {
            for (int i : row)
                MemoryAccess.setIntAtIndex(segment, n++, i);
        }

        return segment;
    }

    public static void toArr(int[] arr, MemorySegment segment) {
        for (int n = 0; n < arr.length; ++n)
            arr[n] = MemoryAccess.getIntAtIndex(segment, n);
    }

/* Short ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(short[] arr) {
        MemorySegment iseg = MemorySegment.ofArray(arr);
        MemorySegment segment = MemorySegment.allocateNative(iseg.byteSize());
        segment.copyFrom(iseg);
        return segment;
    }

    public static MemorySegment toPtrPTrMS(short[][] arr) {
        PtrPtrMemorySegment segment = new PtrPtrMemorySegment(arr.length);
        for (short[] a : arr)
            segment.addNative(a);
        return segment;
    }

    public static MemorySegment toMS(short[][] arr) {
        MemorySegment segment = MemorySegment.allocateNative((long) arr.length * arr[0].length * Short.BYTES);
        int n = 0;
        for (short[] row : arr) {
            for (short i : row)
                MemoryAccess.setShortAtIndex(segment, n++, i);
        }

        return segment;
    }

    public static void toArr(short[] arr, MemorySegment segment) {
        for (int n = 0; n < arr.length; ++n)
            arr[n] = MemoryAccess.getShortAtIndex(segment, n);
    }

/* Byte ///////////////////////////////////////////////////////////////// */

    public static MemorySegment toMS(byte[] arr) {
        MemorySegment iseg = MemorySegment.ofArray(arr);
        MemorySegment segment = MemorySegment.allocateNative(iseg.byteSize());
        segment.copyFrom(iseg);
        return segment;
    }

    public static MemorySegment toPtrPTrMS(byte[][] arr) {
        PtrPtrMemorySegment segment = new PtrPtrMemorySegment(arr.length);
        for (byte[] a : arr)
            segment.addNative(a);
        return segment;
    }

    public static MemorySegment toMS(byte[][] arr) {
        MemorySegment segment = MemorySegment.allocateNative((long) arr.length * arr[0].length * Byte.BYTES);
        int n = 0;
        for (byte[] row : arr) {
            for (byte i : row)
                MemoryAccess.setByteAtOffset(segment, n++, i);
        }

        return segment;
    }

    public static void toArr(byte[] arr, MemorySegment segment) {
        for (int n = 0; n < arr.length; ++n)
            arr[n] = MemoryAccess.getByteAtOffset(segment, n);
    }

/*///////////////////////////////////////////////////////////////// */


    static class PtrPtrMemorySegment implements MemorySegment {


        MemorySegment m_axis1Segment;
        List<MemorySegment> m_axis2Segments = new ArrayList<>();

        PtrPtrMemorySegment(int ptrCount)
        {
            m_axis1Segment = MemorySegment.allocateNative((long)ptrCount * Long.BYTES);
        }

        void addNative(int[] arr)
        {
            addSegment(toMS(arr));
        }

        void addNative(long[] arr)
        {
            addSegment(toMS(arr));
        }

        void addNative(double[] arr)
        {
            addSegment(toMS(arr));
        }

        void addNative(float[] arr)
        {
            addSegment(toMS(arr));
        }

        void addNative(short[] arr)
        {
            addSegment(toMS(arr));
        }

        void addNative(byte[] arr)
        {
            addSegment(toMS(arr));
        }

        private void addSegment(MemorySegment segment)
        {
            MemoryAccess.setLongAtIndex(m_axis1Segment, m_axis2Segments.size(), segment.address().toRawLongValue());
            m_axis2Segments.add(segment);
        }

        @Override
        public MemoryAddress address() {
            return m_axis1Segment.address();
        }

        @Override
        public Spliterator<MemorySegment> spliterator(SequenceLayout layout) {
            return null;
        }

        @Override
        public Thread ownerThread() {
            return null;
        }

        @Override
        public long byteSize() {
            return m_axis1Segment.byteSize();
        }

        @Override
        public MemorySegment withAccessModes(int accessModes) {
            return m_axis1Segment.withAccessModes(accessModes);
        }

        @Override
        public boolean hasAccessModes(int accessModes) {
            return m_axis1Segment.hasAccessModes(accessModes);
        }

        @Override
        public int accessModes() {
            return m_axis1Segment.accessModes();
        }

        @Override
        public MemorySegment asSlice(long offset, long newSize) {
            return null;
        }

        @Override
        public boolean isMapped() {
            return m_axis1Segment.isMapped();
        }

        @Override
        public boolean isAlive() {
            return m_axis1Segment.isAlive();
        }

        @Override
        public void close() {
            m_axis2Segments.forEach(MemorySegment::close);
            m_axis1Segment.close();
        }

        @Override
        public MemorySegment handoff(Thread thread) {
            return m_axis1Segment.handoff(thread);
        }

        @Override
        public MemorySegment handoff(NativeScope nativeScope) {
            return m_axis1Segment.handoff(nativeScope);
        }

        @Override
        public MemorySegment share() {
            return m_axis1Segment.share();
        }

        @Override
        public MemorySegment registerCleaner(Cleaner cleaner) {
            return m_axis1Segment.registerCleaner(cleaner);
        }

        @Override
        public MemorySegment fill(byte value) {
            return null;
        }

        @Override
        public void copyFrom(MemorySegment src) {

        }

        @Override
        public long mismatch(MemorySegment other) {
            return 0;
        }

        @Override
        public ByteBuffer asByteBuffer() {
            return null;
        }

        @Override
        public byte[] toByteArray() {
            return new byte[0];
        }

        @Override
        public short[] toShortArray() {
            return new short[0];
        }

        @Override
        public char[] toCharArray() {
            return new char[0];
        }

        @Override
        public int[] toIntArray() {
            return new int[0];
        }

        @Override
        public float[] toFloatArray() {
            return new float[0];
        }

        @Override
        public long[] toLongArray() {
            return new long[0];
        }

        @Override
        public double[] toDoubleArray() {
            return new double[0];
        }
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
}
