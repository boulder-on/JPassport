package jpassport.codebuilder;

import jpassport.PassportException;
import jpassport.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static jpassport.Utils.toDesc;

public class SampleCode {

    public static void main(String[] args)
    {
        var code = SampleCode.getCodeTemplate(TemplateFunction.store_arr);
        for (CodeElement ce : code.elementList())
        {
            System.out.println(ce);
        }

    }
    record SimpleRec(int i){}

    private static final GroupLayout SimpleRecLayout = Utils.makeStruct(
            JAVA_INT.withName("i"));

    public enum TemplateFunction {
        store_arr("storeArrSimpleRec"),
        store_ptr("storePtrsSimpleRec"),
        read_arr("readArrSimpleRec"),
        read_ptr("readPtrsSimpleRec");

        final String functionName;

        TemplateFunction(String name)
        {
            functionName = name;
        }
    }

    static CodeModel getCodeTemplate(TemplateFunction function)
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try {
            InputStream in = SampleCode.class.getResourceAsStream("SampleCode.class");
            if (in == null)
                throw new PassportException("SampleCode class is missing from JPassport jar.");

            byte[] data = new byte[4096];
            int len;

            while ((len = in.read(data)) > 0)
                bout.write(data, 0, len);
            in.close();
        }
        catch (IOException ex)
        {
            throw new PassportException("Could not find template code.");
        }

        ClassModel cm = ClassFile.of().parse(bout.toByteArray());

        for (var mm : cm.methods())
        {
            if (!mm.methodName().stringValue().equals(function.functionName))
                continue;

            var code = mm.code();
            if (code.isEmpty())
                throw new PassportException("Could not find code for " + function.functionName);
            return code.get();
        }

        throw new PassportException("Could not find code for " + function.functionName);
    }


    /// //////////////////////////////////////////////////////////////////////////////////////////
    private MemorySegment storeArrSimpleRec(SegmentAllocator scope, SimpleRec[] rec) {
        if (rec == null)
            return MemorySegment.NULL;

        long size = SimpleRecLayout.byteSize();
        MemorySegment ptr = scope.allocate(size * rec.length);
        for (int n = 0; n < rec.length; ++n)
        {
            MemorySegment struct = storeSimpleRec(scope, rec[n]);
            ptr.asSlice(size*n).copyFrom(struct);
        }
        return ptr;
    }
    /// //////////////////////////////////////////////////////////////////////////////////////////

    /// //////////////////////////////////////////////////////////////////////////////////////////
    private MemorySegment storePtrsSimpleRec(SegmentAllocator scope, SimpleRec[] rec) {
        if (rec == null)
            return MemorySegment.NULL;

        long addressBytes = ValueLayout.ADDRESS.byteSize();
        MemorySegment ptr = scope.allocate(addressBytes * rec.length);
        for (int n = 0; n < rec.length; ++n)
        {
            MemorySegment struct = storeSimpleRec(scope, rec[n]);
            ptr.set(ValueLayout.ADDRESS, addressBytes * n, struct);
        }
        return ptr;
    };
    /// //////////////////////////////////////////////////////////////////////////////////////////

    private MemorySegment storeSimpleRec(SegmentAllocator scope, SimpleRec rec) {
        return storeSimpleRec(scope, new SimpleRec[] {rec});
    };

    private MemorySegment storeSimpleRec(SegmentAllocator scope, SimpleRec[] recs) {
        MemorySegment memStruct = scope.allocate(1);
        return memStruct;
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////
    private void readArrSimpleRec(MemorySegment mem, SimpleRec[] rec) {
        if (MemorySegment.NULL.equals(mem) || rec == null)
            return;

        GroupLayout layout = SimpleRecLayout;
        long byteSize = layout.byteSize();

        for (int n = 0; n < rec.length; ++n)
        {
            MemorySegment ptrToStruct = mem.asSlice(n * byteSize, byteSize);
            rec[n] = readSimpleRec(ptrToStruct, rec[0]);
        }
    }
    /// //////////////////////////////////////////////////////////////////////////////////////////

    private void readPtrsSimpleRec(MemorySegment mem, SimpleRec[] rec) {
        if (MemorySegment.NULL.equals(mem) || rec == null)
            return;

        GroupLayout layout = SimpleRecLayout;
        long addressBytes = ValueLayout.ADDRESS.byteSize();
        mem = mem.reinterpret(addressBytes * rec.length);

        for (int n = 0; n < rec.length; ++n)
        {
            MemorySegment ptrToStruct = mem.get( ValueLayout.ADDRESS, n * addressBytes);
            ptrToStruct = ptrToStruct.reinterpret(layout.byteSize());
            rec[n] = readSimpleRec(ptrToStruct, rec[0]);
        }
    }
    /// //////////////////////////////////////////////////////////////////////////////////////////

    private SimpleRec readSimpleRec(MemorySegment memStruct, SimpleRec rec) {
         return new SimpleRec(1);
    }

}
