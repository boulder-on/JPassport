package jpassport.pointers;

import jpassport.Utils;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

/**
 * A very common idiom in C is to pass a pointer to some allocated memory
 * and the function fills the memory. This class is used to handle this
 * for strings specifically. You can pass char[] if you want, but the
 * C function then needs to handle 2 byte chars.
 * NOTE: this class is always assumed to be a RefArg, i.e. it is always
 * read back after the native call.
 */
public class MemoryBlock {
    private long sizeInBytes;
    private MemorySegment ptr = null;
    private String readBack = null;
    private byte[] buffer = null;

    /**
     * Set the size in bytes to this value if you would like the returned pointer to be NULL.
     */
    private static final int NULL_SIZE = -1;

    public MemoryBlock(long bytes)
    {
        sizeInBytes = bytes;
    }

    public MemoryBlock(byte[] data)
    {
        buffer = data;
        sizeInBytes = data.length;
    }

    /**
     * IF you would like the pointer this creates to be NULL.
     */
    public void setToNull()
    {
        sizeInBytes = NULL_SIZE;
    }

    public static MemoryBlock recreate(MemorySegment ptr, MemoryBlock orig)
    {
        return new MemoryBlock(ptr, orig.size());
    }

    public MemoryBlock(MemorySegment ptr, long bytes)
    {
        sizeInBytes = bytes;
        this.ptr = ptr.reinterpret(bytes);
        readBack();
    }

    public long size()
    {
        return sizeInBytes;
    }

    public MemorySegment toPtr(SegmentAllocator scope)
    {
        if (ptr == null)
        {
            if (buffer != null)
                ptr = Utils.toMS(scope, buffer, false);
            else if (sizeInBytes == NULL_SIZE)
                ptr = MemorySegment.NULL;
            else
                ptr = scope.allocate(sizeInBytes);
        }
        return ptr;
    }

    public void readBack()
    {
        if (ptr != null)
        {
            String[] args = new String[1];
            args[0] = Utils.readString(ptr);
            readBack = args[0];

            var bb = ptr.asByteBuffer();
            buffer = new byte[bb.limit()];
            bb.get(buffer, 0, buffer.length);
        }
    }

    public byte[] getBytes()
    {
        return buffer;
    }

    public void setString(String testing_only)
    {
        readBack = testing_only;
    }

    public String toString()
    {
        if (readBack != null)
            return readBack;
        return "No memory allocated";
    }
}
