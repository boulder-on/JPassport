package jpassport;

import jpassport.annotations.Array;
import jpassport.annotations.Ptr;
import jpassport.codebuilder.ParamKeeper;
import jpassport.codebuilder.ParamType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.TypeDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;

import static java.lang.foreign.ValueLayout.*;
import static jpassport.PassportWriter.*;

public class PassportBuilder<T extends Passport> extends ClassLoader{

    Class<T> interfaceClass;
    private final String fullName;
    private final byte[] classBytes;

    private static int Class_ID = 1; //Used to make unique package names

    static Map<Class<?>, ClassDesc> primativeToVLDescMap;
    static Map<Class<?>, String> primitiveToConstName = new HashMap<>();
    static Map<Class<?>, ClassDesc> primitiveToDescMap = new HashMap<>();
    static final String INIT_METHODS_METHOD_NAME = "initMethods";
    static final String INIT_STRUCTS_METHOD_NAME = "initStructs";

    static {
        primativeToVLDescMap = new HashMap<>();
        primativeToVLDescMap.put(int.class, toDesc(ValueLayout.OfInt.class));
        primativeToVLDescMap.put(long.class, toDesc(ValueLayout.OfLong.class));
        primativeToVLDescMap.put(short.class, toDesc(ValueLayout.OfShort.class));
        primativeToVLDescMap.put(byte.class, toDesc(ValueLayout.OfByte.class));
        primativeToVLDescMap.put(double.class, toDesc(ValueLayout.OfDouble.class));
        primativeToVLDescMap.put(float.class, toDesc(ValueLayout.OfFloat.class));

        primitiveToConstName.put(int.class, "JAVA_INT");
        primitiveToConstName.put(long.class, "JAVA_LONG");
        primitiveToConstName.put(short.class, "JAVA_SHORT");
        primitiveToConstName.put(byte.class, "JAVA_BYTE");
        primitiveToConstName.put(double.class, "JAVA_DOUBLE");
        primitiveToConstName.put(float.class, "JAVA_FLOAT");

        primitiveToDescMap.put(int.class, ConstantDescs.CD_int);
        primitiveToDescMap.put(long.class, ConstantDescs.CD_long);
        primitiveToDescMap.put(short.class, ConstantDescs.CD_short);
        primitiveToDescMap.put(byte.class, ConstantDescs.CD_byte);
        primitiveToDescMap.put(double.class, ConstantDescs.CD_double);
        primitiveToDescMap.put(float.class, ConstantDescs.CD_float);
    }

    record RecordVariables(FieldRefEntry layout, FieldRefEntry offsets) {}

    private final ClassDesc thisClassDesc;
    private final HashMap<String, FieldRefEntry> methodHandles = new HashMap<>();
    private final HashMap<Class<?>, RecordVariables> groupLayouts = new HashMap<>();

    private static final ClassDesc CD_MemorySegment = toDesc(MemorySegment.class);
    private static final ClassDesc CD_MemoryLayout =  toDesc(MemoryLayout.class);
    private static final ClassDesc CD_Arena = toDesc(Arena.class);
    private static final ClassDesc CD_SegmentAllocator = toDesc(SegmentAllocator.class);
    private static final ClassDesc CD_Utils = toDesc(Utils.class);

    public PassportBuilder(Class<T> interfaceClass)
    {
        this(interfaceClass, "jpassport.called_" + Class_ID++, interfaceClass.getSimpleName() + "_impl");
    }


    public PassportBuilder(Class<T> interfaceClass, String packageName, String className)
    {
        this.interfaceClass = interfaceClass;
        thisClassDesc = ClassDesc.of(packageName, className);
        List<Method> interfaceMethods = PassportFactory.getDeclaredMethods(interfaceClass);

        classBytes = ClassFile.of().build(thisClassDesc, clb ->
        {
            var entry = ConstantPoolBuilder.of().classEntry(toDesc(Passport.class));
            var mainInterface = ConstantPoolBuilder.of().classEntry(toDesc(interfaceClass));
            clb.withInterfaces(entry, mainInterface);
            clb.withFlags(ClassFile.ACC_PUBLIC);

            createRecordAccessors(clb);

            var classDescHM = toDesc(HashMap.class);
            var descMap = toDesc(Map.class);
            clb.withField("methods", classDescHM, ClassFile.ACC_PUBLIC);

            var nte = clb.constantPool().nameAndTypeEntry("methods", classDescHM);
            var methodsfield = clb.constantPool().fieldRefEntry(clb.constantPool().classEntry(thisClassDesc), nte);


            var desc = MethodTypeDesc.of(ConstantDescs.CD_void, classDescHM);
            var descputAll = MethodTypeDesc.of(ConstantDescs.CD_void, descMap);

            clb.withMethod(ConstantDescs.INIT_NAME, desc,
                    ClassFile.ACC_PUBLIC, mb -> mb.withCode(

                                    // ** call Object.<init>
                            cob ->  cob.aload(0)
                                    .invokespecial(ConstantDescs.CD_Object,
                                            ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                                    // **done Object.<init>
                                    .aload(0)
                                    .new_(classDescHM)
                                    .dup()
                                    .invokespecial(classDescHM, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, false)
                                    .putfield(methodsfield)
                                    .aload(0)
                                    .getfield(methodsfield)
                                    .aload(1)
                                    .invokevirtual(classDescHM, "putAll", descputAll)
                                    .aload(0)
                                    .invokevirtual(thisClassDesc, INIT_METHODS_METHOD_NAME, ConstantDescs.MTD_void)
                                    .aload(0)
                                    .invokevirtual(thisClassDesc, INIT_STRUCTS_METHOD_NAME, ConstantDescs.MTD_void)
                                    .return_()));

            //Create member variables for each of the method handles to the native methods.
            var methodTypeDesc = toDesc(MethodHandle.class);
            for (Method m : interfaceMethods)
            {
                String fieldName = "m_" + m.getName();
                clb.withField(fieldName, methodTypeDesc, ClassFile.ACC_PRIVATE);
                nte = clb.constantPool().nameAndTypeEntry(fieldName, methodTypeDesc);
                var fieldRefEntry = clb.constantPool().fieldRefEntry(clb.constantPool().classEntry(thisClassDesc), nte);
                methodHandles.put(fieldName, fieldRefEntry);
            }

            //Create a method that assigns all the method handles for the native methods
            var cdObject = toDesc(Object.class);
            clb.withMethod(INIT_METHODS_METHOD_NAME,  ConstantDescs.MTD_void,
                    ClassFile.ACC_PUBLIC, mb -> mb.withCode(
                       cob -> {
                           var start = cob.newBoundLabel();

                           for (String name : methodHandles.keySet())
                           {
                               cob.ldc(name.substring(2)) // clip off "m_"
                                       .astore(1)
                                       .aload(0)
                                       .aload(0)
                                       .getfield(methodsfield)
                                       .aload(1)
                                       .invokevirtual(classDescHM, "get", MethodTypeDesc.of(cdObject, cdObject))
                                       .checkcast(methodTypeDesc)
                                        .putfield(methodHandles.get(name));
                           }
                           var end = cob.endLabel();
                           cob.localVariable(1, "key", ConstantDescs.CD_String, start, end);
                           cob.return_();
                       }
                    ));

            for (Method m : interfaceMethods)
            {
                try {
                    if (needsArena(m))
                        addMethodWithArena(clb, m);
                    else
                        addMethod(clb, m);
                }
                catch (Throwable th)
                {
                    th.printStackTrace();
                }
            }

        }
        );

        if (System.getProperties().containsKey("jpassport.build.home"))
        {
            var dest = Path.of(System.getProperty("jpassport.build.home"));
            var destFile = dest.resolve(className + ".class");
            try {
                Files.write(destFile, classBytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        fullName = packageName + "." + className;
    }


    private void createRecordAccessors(ClassBuilder cbl)
    {
        var groupLayout = toDesc(GroupLayout.class);
        var offsets = ConstantDescs.CD_long.arrayType();
        List<Class<?>> orderedRecords = getOrderedListOfRecords();

        //Create the 2 fields that each record/struct type needs
        // [record name]Layout = new GroupLayout()
        // [record name]Offsets = new long[];  - offsets into memory for each struct field, since they are expensive to calculate each tieme
        for (Class<?> c : orderedRecords) {

            //create the GroupLayout for each struct/record we deal with
            String fieldName = recordToLayoutName(c);
            cbl.withField(fieldName, groupLayout, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC);
            var nte = cbl.constantPool().nameAndTypeEntry(fieldName, groupLayout);
            var fieldRefEntry = cbl.constantPool().fieldRefEntry(cbl.constantPool().classEntry(thisClassDesc), nte);

            //For reasons unclear to me, getting the offset for a member of a groupLayout is quite expensive.
            //This creates a long[] that holds all offsets for struct/record members, so we can cache the calls.
            fieldName = recordToOffsetName(c);
            cbl.withField(fieldName, offsets, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC);
            nte = cbl.constantPool().nameAndTypeEntry(fieldName, offsets);
            var offsetfieldRefEntry = cbl.constantPool().fieldRefEntry(cbl.constantPool().classEntry(thisClassDesc), nte);

            groupLayouts.put(c, new RecordVariables(fieldRefEntry, offsetfieldRefEntry));
        }

        //Create a method that will create all GroupStructs and cached offsets for all rec/structs
        cbl.withMethod(INIT_STRUCTS_METHOD_NAME,  ConstantDescs.MTD_void,
                ClassFile.ACC_PUBLIC, methodBuilder -> methodBuilder.withCode(
                cob -> {
                    for (Class<?> c : orderedRecords) {
                        int firstAvailableSlot = createGroupLayout(cob, c, 1);
                        createRecordOffsets(cob, c, firstAvailableSlot);
                    }
                    cob.return_();
                }
                ));

        createStoreMethods(cbl, orderedRecords);
        createReadMethods(cbl, orderedRecords);
    }

    /**
     * Create all methods for moving Records to native memory that is laid out as a struct would be.
     *
     * @param cbl The class builder for the class we're making
     * @param extraImports The complete list of structs we need support for
     */
    private void createStoreMethods(ClassBuilder cbl, List<Class<?>> extraImports) {

        for (Class<?> recordType : extraImports)
        {
            //Creates a method "private MemorySegment store[RecordName](Arena a, RecordName recToStore)".
            //The returned MemorySegment is a pointer to native memory that is laid out as a struct would be in C.
            cbl.withMethod("store" + recordType.getSimpleName(),
                    MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(recordType)),
                    ClassFile.ACC_PRIVATE, methodBuilder -> methodBuilder.withCode(cob -> {

                        int arenaSlot = cob.parameterSlot(0);
                        int inputRecSlot = cob.parameterSlot(1);
                        var recDesc = toDesc(recordType);

                        int slots = cob.parameterSlot(1) + 1; //start the local variables after the parameters
                        int sizeSlot = slots;
                        slots+=2;
                        var methodStart = cob.newLabel();
                        cob.labelBinding(methodStart);
                        var methodEnd = cob.newLabel();
                        cob.localVariable(sizeSlot, "size", ConstantDescs.CD_long, methodStart, methodEnd);
                        int memSegSlot = slots++;
                        cob.localVariable(memSegSlot, "memStruct", CD_MemorySegment, methodStart, methodEnd);

                        cob.getstatic(groupLayouts.get(recordType).layout());
                        cob.invokeinterface(toDesc(GroupLayout.class), "byteSize", MethodTypeDesc.of(ConstantDescs.CD_long));
                        cob.lstore(sizeSlot);

                        cob.aload(arenaSlot); //load the Arena
                        cob.lload(sizeSlot);
                        cob.invokeinterface(CD_SegmentAllocator, "allocate", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_long));
                        cob.astore(memSegSlot);


                        int ii = 0;
                        slots++;
                        for (Field f : recordType.getDeclaredFields()) {
                            Class<?> ftype = f.getType();

                            if (ftype.isPrimitive()) {
                                cob.aload(inputRecSlot);

                                cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(primitiveToDescMap.get(ftype)));
                                int fieldSlot = slots;
                                slots = storeParam(cob, fieldSlot, ftype);

                                cob.getstatic(groupLayouts.get(recordType).offsets);
                                cob.loadConstant(ii++).laload();
                                int offsetSlot = slots;
                                slots = storeParam(cob, offsetSlot, long.class);

                                cob.aload(memSegSlot);
                                cob.getstatic(toDesc(ValueLayout.class), primitiveToConstName.get(ftype), primativeToVLDescMap.get(ftype));
                                cob.lload(offsetSlot);
                                loadParam(cob, fieldSlot, ftype);

                                cob.invokeinterface(CD_MemorySegment, "set",
                                        MethodTypeDesc.of(ConstantDescs.CD_void, primativeToVLDescMap.get(ftype), ConstantDescs.CD_long, primitiveToDescMap.get(ftype)));
                            }
                            else if (ftype.isArray())
                            {
                                Annotation[] arrays = f.getAnnotationsByType(Array.class);
                                boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;

                                if (isArrayOfPrimitives(ftype))
                                {
                                    var arrDesc = primitiveToDescMap.get(ftype.getComponentType()).arrayType();

                                    //Some record fields may be annotated with @Array(length = N), this handles those
                                    //i.e. This will add all N elements directly into the memory space of the struct.
                                    if (arrays.length > 0)  //only works with 1D array
                                    {
                                        cob.aload(memSegSlot);
                                        cob.getstatic(groupLayouts.get(recordType).offsets);
                                        cob.loadConstant(ii++).laload();
                                        cob.invokeinterface(CD_MemorySegment, "asSlice", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_long));
                                        int sliceSlot = slots;
                                        slots = storeParam(cob, sliceSlot, MemorySegment.class);

                                        cob.aload(inputRecSlot);
                                        cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(arrDesc));
                                        int valueSlot = slots;
                                        slots = storeParam(cob, valueSlot, ftype);
                                        cob.aload(valueSlot);
                                        cob.invokestatic(CD_MemorySegment, "ofArray", MethodTypeDesc.of(CD_MemorySegment, arrDesc), true);

                                        int copySlot = slots;
                                        slots = storeParam(cob, copySlot, MemorySegment.class);

                                        cob.aload(sliceSlot).aload(copySlot);
                                        cob.invokeinterface(CD_MemorySegment, "copyFrom", MethodTypeDesc.of(CD_MemorySegment, CD_MemorySegment));
//                                        memStruct.asSlice(PassingArraysLayoutOffsets[0] + offset).copyFrom(MemorySegment.ofArray(rec.s_double()));
                                    }
                                    //For arrays annotated with @Ptr, this allocates the memory for the array, and places the pointer to that memory
                                    //in the Struct
                                    else if (isPointer)
                                    {
                                        cob.aload(inputRecSlot);
                                        cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(arrDesc));
                                        int valueSlot = slots;
                                        slots = storeParam(cob, valueSlot, ftype);

                                        cob.aload(arenaSlot).aload(valueSlot).iconst_0();
                                        cob.invokestatic(CD_Utils, "toMS", MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, arrDesc, ConstantDescs.CD_boolean));
                                        int memorySlot = slots;
                                        slots = storeParam(cob, memorySlot, MemorySegment.class);
                                        cob.aload(memSegSlot);
                                        cob.getstatic(toDesc(ValueLayout.class), "ADDRESS", toDesc(AddressLayout.class));
                                        cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                        cob.aload(memorySlot);
                                        cob.invokeinterface(CD_MemorySegment, "set",
                                                MethodTypeDesc.of(ConstantDescs.CD_void, toDesc(AddressLayout.class), ConstantDescs.CD_long, CD_MemorySegment));
                                        //memStruct.set(ADDRESS, PassingArraysLayoutOffsets[3] + offset, Utils.toMS(scope, rec.s_doublePtr(), false));
                                    }
                                }
                            }
                            else if (ftype.isRecord())
                            {
                                boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;
                                cob.aload(inputRecSlot);

                                cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                int fieldSlot = slots;
                                slots = storeParam(cob, fieldSlot, ftype);

                                cob.aload(0).aload(arenaSlot).aload(fieldSlot);
                                cob.invokevirtual(thisClassDesc, "store" + ftype.getSimpleName(), MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(ftype)));
                                int memorySlot = slots;
                                slots = storeParam(cob, memorySlot, MemorySegment.class);

                                if (isPointer)
                                {
                                    cob.aload(memSegSlot);
                                    cob.getstatic(toDesc(ValueLayout.class), "ADDRESS", toDesc(AddressLayout.class));
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    cob.aload(memorySlot);
                                    cob.invokeinterface(CD_MemorySegment, "set",
                                            MethodTypeDesc.of(ConstantDescs.CD_void, toDesc(AddressLayout.class), ConstantDescs.CD_long, CD_MemorySegment));
//                                    memStruct.set(ADDRESS, ComplexStructLayoutOffsets[2] + offset, storeTestStruct(scope, rec.tsPtr()));
                                }
                                else
                                {
                                    cob.aload(memSegSlot);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    cob.invokeinterface(CD_MemorySegment, "asSlice", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_long));
                                    int sliceSlot = slots;
                                    slots = storeParam(cob, sliceSlot, MemorySegment.class);

                                    cob.aload(sliceSlot).aload(memorySlot);
                                    cob.invokeinterface(CD_MemorySegment, "copyFrom", MethodTypeDesc.of(CD_MemorySegment, CD_MemorySegment));

                                    //memStruct.asSlice(ComplexStructLayoutOffsets[1] + offset).copyFrom(storeTestStruct(scope, rec.ts()));
                                }

                            }
                            else if (isGenericPtr(ftype))
                            {
                                throw new PassportException("Generic pointers are not supported in structs yet");
                                //todo
                            }
                            else if (MemoryBlock.class.equals(ftype))
                            {
                                throw new PassportException("Memory blocks are not supported in structs yet");
                                //todo
                            }
                            else if (MemorySegment.class.equals(ftype))
                            {
                                cob.aload(inputRecSlot);
                                cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                int addrSlot = slots;
                                slots = storeParam(cob, addrSlot, ftype);

                                cob.aload(memSegSlot);
                                cob.getstatic(toDesc(ValueLayout.class), "ADDRESS", toDesc(AddressLayout.class));
                                cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                cob.aload(addrSlot);
                                cob.invokeinterface(CD_MemorySegment, "set",
                                        MethodTypeDesc.of(ConstantDescs.CD_void, toDesc(AddressLayout.class), ConstantDescs.CD_long, CD_MemorySegment));
//                                memStruct.set(ADDRESS, StructWithPrtLayoutOffsets[1] + offset, rec.addr());
                            }
                            else if (String.class.equals(ftype))
                            {
                                cob.aload(inputRecSlot);
                                cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                int stringSlot = slots;
                                slots = storeParam(cob, stringSlot, ftype);

                                cob.aload(stringSlot).aload(arenaSlot);
                                cob.invokestatic(CD_Utils, "toCString", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_String, CD_SegmentAllocator));
                                int cstringSlot = slots;
                                slots = storeParam(cob, cstringSlot, MemorySegment.class);

                                cob.aload(memSegSlot);
                                cob.getstatic(toDesc(ValueLayout.class), "ADDRESS", toDesc(AddressLayout.class));
                                cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                cob.aload(cstringSlot);
                                cob.invokeinterface(CD_MemorySegment, "set",
                                        MethodTypeDesc.of(ConstantDescs.CD_void, toDesc(AddressLayout.class), ConstantDescs.CD_long, CD_MemorySegment));
//                                memStruct.set(ADDRESS, ComplexStructLayoutOffsets[3] + offset, Utils.toCString(rec.string(), scope));
                            }
                        }

                        cob.aload(memSegSlot).areturn();
                        cob.labelBinding(methodEnd);
                    }));

            //==========================================================================================
            // This version of the store method takes an array of the records, pulls out the first array element
            //and passes it to the store method above.
            // In order to support arrays > 1 element, you'd need to start work here.
            cbl.withMethod("store" + recordType.getSimpleName(),
                    MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(recordType).arrayType()),
                    ClassFile.ACC_PRIVATE, methodBuilder -> methodBuilder.withCode(cob -> {

                        var methodStart = cob.newLabel();
                        cob.labelBinding(methodStart);
                        var methodEnd = cob.newLabel();
                        int nextSlot =  cob.parameterSlot(1) + 1;
                        cob.localVariable(nextSlot, "ret", CD_MemorySegment, methodStart, methodEnd);

                        int arraySlot = cob.parameterSlot(1);
                        int recSlot = arraySlot+1;
                        cob.aload(arraySlot).iconst_0().aaload().astore(recSlot);
                        cob.aload(0).aload(cob.parameterSlot(0)).aload(recSlot);
                        cob.invokevirtual(thisClassDesc, "store" + recordType.getSimpleName(),
                                MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(recordType)));
                        cob.astore(nextSlot);
                        cob.aload(nextSlot);
                        cob.areturn();
                        cob.labelBinding(methodEnd);
                    }));
        }
    }

    /**
     * This method creates all the methods required to reach each struct type back from native memory and into
     * its record class.
     *
     * @param cbl The class we're putting the methods into
     * @param extraImports The list of record types we need to support.
     */
    private void createReadMethods(ClassBuilder cbl, List<Class<?>> extraImports) {

        for (Class<?> recordType : extraImports)
        {
            //Creates "private RecordType readRecordTye(MemorySegment memPtr, RecordType origRec)".
            //The returned value is the new record made from the struct in memPtr.
            cbl.withMethod("read" + recordType.getSimpleName(),
                    MethodTypeDesc.of(toDesc(recordType), CD_MemorySegment, toDesc(recordType)),
                    ClassFile.ACC_PRIVATE, methodBuilder -> methodBuilder.withCode(cob -> {
                        int slots = cob.parameterSlot(1) + 1; //start the local variables after the parameters
                        var methodStart = cob.newLabel();
                        cob.labelBinding(methodStart);
                        var methodEnd = cob.newLabel();

                        int groupLayoutSlot = slots++;
                        cob.localVariable(groupLayoutSlot, "layout", toDesc(GroupLayout.class), methodStart, methodEnd);
                        int sizeSlot = slots;
                        cob.localVariable(groupLayoutSlot, "struct_size", ConstantDescs.CD_long, methodStart, methodEnd);
                        slots += 2;
                        cob.getstatic(groupLayouts.get(recordType).layout());
                        cob.astore(groupLayoutSlot);

                        cob.aload(groupLayoutSlot);
                        cob.invokeinterface(toDesc(GroupLayout.class), "byteSize", MethodTypeDesc.of(ConstantDescs.CD_long));
                        cob.lstore(sizeSlot);

                        int memStructSlot = cob.parameterSlot(0);
                        int recordTypeSlot = cob.parameterSlot(1);
                        cob.aload(memStructSlot).lload(sizeSlot);
                        cob.invokestatic(CD_Utils, "resize", MethodTypeDesc.of(CD_MemorySegment,CD_MemorySegment, ConstantDescs.CD_long));
                        cob.astore(memStructSlot);

                        ParamType[] fields = new ParamType[recordType.getDeclaredFields().length];
                        int[] fieldSlots = new int[fields.length];
                        int ii = 0;
                        //Create a local variable for each field of the record
                        for (Field f : recordType.getDeclaredFields()) {
                            var t = f.getType();
                            fields[ii] = ParamType.toType(t);
                            fieldSlots[ii] = slots;
                            cob.localVariable(fieldSlots[ii], f.getName(), toLocalVariableDesc(t), methodStart, methodEnd);
                            slots += fields[ii].requiredSlots();
                            ii++;
                        }

                        ii=0;
                        for (Field f : recordType.getDeclaredFields()) {
                            var ftype = f.getType();

                            if (ftype.isPrimitive()) {
//                                var s_int = memStruct.get(JAVA_INT, TestStructLayoutOffsets[0]);
                                cob.aload(memStructSlot);
                                cob.getstatic(toDesc(ValueLayout.class), primitiveToConstName.get(f.getType()), primativeToVLDescMap.get(f.getType()));
                                cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                cob.invokeinterface(CD_MemorySegment, "get",
                                        MethodTypeDesc.of(primitiveToDescMap.get(ftype), primativeToVLDescMap.get(f.getType()), ConstantDescs.CD_long));
                                storeParam(cob, fieldSlots[ii], ftype);
                            }
                            else if (ftype.isArray())
                            {
                                Annotation[] arrays = f.getAnnotationsByType(Array.class);
                                boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;

                                if (isArrayOfPrimitives(ftype)) {
                                    var c = ftype.getComponentType();

                                    if (arrays.length > 0)  //only works with 1D array
                                    {
                                        cob.aload(memStructSlot);
                                        cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                        int length = ((Array) arrays[0]).length();
                                        cob.loadConstant((long)length * byteSize(c));
                                        cob.invokeinterface(CD_MemorySegment, "asSlice", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_long, ConstantDescs.CD_long));
                                        cob.getstatic(toDesc(ValueLayout.class), primitiveToConstName.get(c), primativeToVLDescMap.get(c));
                                        cob.invokeinterface(CD_MemorySegment, "toArray",
                                                MethodTypeDesc.of(primitiveToDescMap.get(c).arrayType(), primativeToVLDescMap.get(c)));
                                        storeParam(cob, fieldSlots[ii], ftype);
//                                    var s_double = memStruct.asSlice(PassingArraysLayoutOffsets[0], 5 * Double.BYTES).toArray(JAVA_DOUBLE);
                                    }
                                    else if (isPointer)
                                    {
                                        cob.aload(recordTypeSlot);
                                        cob.invokevirtual(toDesc(recordType), f.getName(), MethodTypeDesc.of(primitiveToDescMap.get(c).arrayType()));
                                        cob.arraylength();
                                        int arrLenSlot = slots;
                                        slots = storeParam(cob, arrLenSlot, int.class);

                                        cob.aload(memStructSlot);
                                        cob.getstatic(toDesc(ValueLayout.class), "ADDRESS", toDesc(AddressLayout.class));
                                        cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                        cob.invokeinterface(CD_MemorySegment, "get",
                                                MethodTypeDesc.of(CD_MemorySegment, toDesc(AddressLayout.class), ConstantDescs.CD_long));
                                        int msegmentSlot = slots++;
                                        slots = storeParam(cob, msegmentSlot, MemorySegment.class);

                                        cob.getstatic(toDesc(ValueLayout.class), primitiveToConstName.get(c), primativeToVLDescMap.get(c));
                                        cob.aload(memStructSlot).aload(msegmentSlot).iload(arrLenSlot);
                                        cob.invokestatic(CD_Utils, "toArr",
                                                MethodTypeDesc.of(primitiveToDescMap.get(c).arrayType(),
                                                        primativeToVLDescMap.get(c), CD_MemorySegment, CD_MemorySegment, ConstantDescs.CD_int));
                                        storeParam(cob, fieldSlots[ii], ftype);

//                                    int s_doublePtrSize = rec.s_doublePtr().length;
//                                    var s_doublePtr = Utils.toArr(JAVA_DOUBLE, memStruct, memStruct.get(ADDRESS, PassingArraysLayoutOffsets[3]), s_doublePtrSize);

                                    }
                                }
                            }
                            else if (ftype.isRecord())
                            {
                                boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;

                                if (isPointer)
                                {
                                    cob.aload(memStructSlot);
                                    cob.getstatic(toDesc(ValueLayout.class), "ADDRESS", toDesc(AddressLayout.class));
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                    cob.invokeinterface(CD_MemorySegment, "get",
                                            MethodTypeDesc.of(CD_MemorySegment, toDesc(AddressLayout.class), ConstantDescs.CD_long));
                                    int msegmentSlot = slots;
                                    slots = storeParam(cob, msegmentSlot, MemorySegment.class);

                                    cob.getstatic(groupLayouts.get(ftype).layout).invokeinterface(CD_MemoryLayout, "byteSize", MethodTypeDesc.of(ConstantDescs.CD_long));
                                    int recSizeSlot = slots;
                                    slots = storeParam(cob, recSizeSlot, long.class);

                                    cob.aload(memStructSlot).aload(msegmentSlot).lload(recSizeSlot);
                                    cob.invokestatic(CD_Utils, "slice", MethodTypeDesc.of(CD_MemorySegment, CD_MemorySegment, CD_MemorySegment, ConstantDescs.CD_long));
                                    int recSliceSlot = slots;
                                    slots = storeParam(cob, recSliceSlot, ftype);

                                    cob.aload(cob.parameterSlot(1));
                                    cob.invokevirtual(toDesc(recordType), f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                    int recTypeSlot = slots;
                                    slots = storeParam(cob, recTypeSlot, ftype);

                                    cob.aload(0).aload(recSliceSlot).aload(recTypeSlot);
                                    cob.invokevirtual(thisClassDesc, "read" + ftype.getSimpleName(), MethodTypeDesc.of(toDesc(ftype), CD_MemorySegment, toDesc(ftype)));

//var tsPtr = readTestStruct(Utils.slice(memStruct, memStruct.get(ADDRESS, ComplexStructLayoutOffsets[2]), TestStructLayout.byteSize()), rec.tsPtr());

                                }
                                else
                                {
                                    cob.aload(memStructSlot);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                    cob.invokeinterface(CD_MemorySegment, "asSlice", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_long));
                                    int msegmentSlot = slots;
                                    slots = storeParam(cob, msegmentSlot, MemorySegment.class);

                                    cob.aload(cob.parameterSlot(1));
                                    cob.invokevirtual(toDesc(recordType), f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                    int recTypeSlot = slots;
                                    slots = storeParam(cob, recTypeSlot, ftype);


                                    cob.aload(0).aload(msegmentSlot).aload(recTypeSlot);
                                    cob.invokevirtual(thisClassDesc, "read" + ftype.getSimpleName(), MethodTypeDesc.of(toDesc(ftype), CD_MemorySegment, toDesc(ftype)));

//                                var ts = readTestStruct(memStruct.asSlice(ComplexStructLayoutOffsets[1]), rec.ts());
                                }
                                storeParam(cob, fieldSlots[ii], ftype);
                            }
                            else if (MemorySegment.class.equals(ftype))
                            {
                                cob.aload(memStructSlot);
                                cob.getstatic(toDesc(ValueLayout.class), "ADDRESS", toDesc(AddressLayout.class));
                                cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                cob.invokeinterface(CD_MemorySegment, "get",
                                        MethodTypeDesc.of(CD_MemorySegment, toDesc(AddressLayout.class), ConstantDescs.CD_long));
                                storeParam(cob, fieldSlots[ii], ftype);
//                                var addr = memStruct.get(ADDRESS, StructWithPrtLayoutOffsets[1]);
                            }
                            else if (isGenericPtr(ftype))
                            {
                                throw new PassportException("Generic pointers are not supported in structs yet");
                                //todo
                            }
                            else if (MemoryBlock.class.equals(ftype))
                            {
                                throw new PassportException("Memory blocks are not supported in structs yet");
                                //todo
                            }
                            else if (String.class.equals(ftype))
                            {
                                cob.aload(memStructSlot);
                                cob.getstatic(toDesc(ValueLayout.class), "ADDRESS", toDesc(AddressLayout.class));
                                cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                cob.invokeinterface(CD_MemorySegment, "get",
                                        MethodTypeDesc.of(CD_MemorySegment, toDesc(AddressLayout.class), ConstantDescs.CD_long));
                                int msegmentSlot = slots++;
                                slots = storeParam(cob, msegmentSlot, MemorySegment.class);
                                cob.aload(msegmentSlot);
                                cob.invokestatic(CD_Utils, "readString", MethodTypeDesc.of(ConstantDescs.CD_String, CD_MemorySegment));
                                storeParam(cob, fieldSlots[ii], ftype);
//                                var string = Utils.readString(memStruct.get(ADDRESS, ComplexStructLayoutOffsets[3]));
                            }
                            ii++;
                        }


                        cob.new_(toDesc(recordType)).dup();
                        ii = 0;
                        List<ClassDesc> paramDesc = new ArrayList<>();
                        for (Field f : recordType.getDeclaredFields()) {
                            loadParam(cob, fieldSlots[ii++], f.getType());
                            paramDesc.add(toLocalVariableDesc(f.getType()));
                        }

                        cob.invokespecial(toDesc(recordType), ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, paramDesc), false);
                        cob.areturn();

                        cob.labelBinding(methodEnd);
                    }));
        }
    }

    private int createGroupLayout(CodeBuilder cob, Class<?> recordType, int firstAvailableSlot)
    {
        //How many items will be passed to Utils.makeStruct()?
        int slotsNeeded = 0;
        for (Field f : recordType.getDeclaredFields()) {
            if (getPaddingBytes(f) != 0)
                slotsNeeded++;
            slotsNeeded++;
        }

        cob.bipush(slotsNeeded);
        cob.anewarray(CD_MemoryLayout).dup();
        int memLayoutArrSlot = firstAvailableSlot++;
        cob.astore(memLayoutArrSlot);

        int idx = 0;
        for (Field f : recordType.getDeclaredFields()) {
            long paddingBits = getPaddingBytes(f);
            var ftype = f.getType();

            if (paddingBits < 0) {
                cob.aload(memLayoutArrSlot).loadConstant(idx++); //for the later array store
                cob.loadConstant(-paddingBits);
                cob.invokestatic(CD_MemoryLayout, "paddingLayout",
                        MethodTypeDesc.of(toDesc(PaddingLayout.class), ConstantDescs.CD_long), true);
                cob.aastore();
            }

            if (ftype.isPrimitive()) {
                ClassDesc prim = primativeToVLDescMap.get(ftype);
                String constName = primitiveToConstName.get(ftype);

                cob.aload(memLayoutArrSlot).loadConstant(idx++); //for the later array store
                cob.getstatic(toDesc(ValueLayout.class), constName, prim);
                cob.loadConstant(f.getName());
                cob.invokeinterface(prim, "withName",
                        MethodTypeDesc.of(CD_MemoryLayout, ConstantDescs.CD_String));
                cob.aastore();
            } else if (ftype.isArray()) {
                int layoutSlot = firstAvailableSlot++;

                if (ftype.getComponentType().isPrimitive()) {
                    var ptype = ftype.getComponentType();
                    Annotation[] arrays = f.getAnnotationsByType(Array.class);
                    boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;

                    if (arrays.length > 0) {
                        int length = ((Array) arrays[0]).length();
                        cob.loadConstant((long)length);
                        cob.getstatic(toDesc(ValueLayout.class), primitiveToConstName.get(ptype), primativeToVLDescMap.get(ptype));
                        cob.invokestatic(CD_MemoryLayout, "sequenceLayout",
                                MethodTypeDesc.of(toDesc(SequenceLayout.class), ConstantDescs.CD_long, CD_MemoryLayout), true);
                        cob.loadConstant(f.getName());
                        cob.invokeinterface(toDesc(SequenceLayout.class), "withName",
                                MethodTypeDesc.of(toDesc(SequenceLayout.class), ConstantDescs.CD_String));
                        cob.astore(layoutSlot);
                    } else if (isPointer) {
                        cob.getstatic(toDesc(ValueLayout.class), "ADDRESS", toDesc(AddressLayout.class));
                        cob.loadConstant(f.getName());
                        cob.invokeinterface(toDesc(AddressLayout.class), "withName",
                                MethodTypeDesc.of(toDesc(AddressLayout.class), ConstantDescs.CD_String));
                        cob.astore(layoutSlot);
                    }
                }

                cob.aload(memLayoutArrSlot).loadConstant(idx++).aload(layoutSlot); //for the later array store
                cob.aastore();
            }
            else if (ftype.isRecord())
            {
                boolean isPointer = f.getAnnotationsByType(Ptr.class).length > 0;
                cob.aload(memLayoutArrSlot).loadConstant(idx++); //for the later array store

                if (isPointer)
                {
                    cob.getstatic(toDesc(ValueLayout.class), "ADDRESS", toDesc(AddressLayout.class));
                    cob.loadConstant(f.getName());
                    cob.invokeinterface(toDesc(AddressLayout.class), "withName",
                            MethodTypeDesc.of(toDesc(AddressLayout.class), ConstantDescs.CD_String));
                }
                else
                {
                    cob.getstatic(groupLayouts.get(ftype).layout).loadConstant(f.getName());
                    cob.invokeinterface(toDesc(GroupLayout.class), "withName", MethodTypeDesc.of(toDesc(GroupLayout.class), ConstantDescs.CD_String));
//                    TestStructLayout.withName("ts")
                }
                cob.aastore();
            }
            else if (ftype.equals(String.class) || MemorySegment.class.equals(ftype))
            {
                cob.aload(memLayoutArrSlot).loadConstant(idx++); //for the later array store
                cob.getstatic(toDesc(ValueLayout.class), "ADDRESS", toDesc(AddressLayout.class));
                cob.loadConstant(f.getName());
                cob.invokeinterface(toDesc(AddressLayout.class), "withName",
                        MethodTypeDesc.of(toDesc(AddressLayout.class), ConstantDescs.CD_String));
                cob.aastore();
            }
            else if (isGenericPtr(ftype) || MemoryBlock.class.equals(ftype))
            {
                throw new PassportException("Memory blocks and pointers in structs are not supported yet");
                //todo
            }


            if (paddingBits > 0) {
                cob.aload(memLayoutArrSlot).loadConstant(idx++); //for the later array store
                cob.loadConstant(paddingBits);
                cob.invokestatic(CD_MemoryLayout, "paddingLayout",
                        MethodTypeDesc.of(toDesc(PaddingLayout.class), ConstantDescs.CD_long), true);
                cob.aastore();
            }
        }

        cob.aload(memLayoutArrSlot);
        cob.invokestatic(CD_Utils, "makeStruct",
                MethodTypeDesc.of(toDesc(GroupLayout.class), CD_MemoryLayout.arrayType(1)));
        cob.putstatic(groupLayouts.get(recordType).layout);

        return firstAvailableSlot;
    }

    private void createRecordOffsets(CodeBuilder cob, Class<?> recordType, int firstAvailableSlot)
    {
        //StructLayoutOffsets = new long[n]
        cob.bipush(recordType.getDeclaredFields().length);
        cob.newarray(TypeKind.LONG).dup();
        cob.putstatic(groupLayouts.get(recordType).offsets);
        int pathElementSlot = firstAvailableSlot++;
        cob.iconst_1().anewarray(toDesc(MemoryLayout.PathElement.class)).astore(pathElementSlot);
        int offsetSlot = firstAvailableSlot;
        firstAvailableSlot += 2;

        int i = 0;
        for (Field f : recordType.getDeclaredFields()) {
            cob.aload(pathElementSlot).iconst_0();
            cob.loadConstant(f.getName());
            cob.invokestatic(toDesc(MemoryLayout.PathElement.class), "groupElement",
                    MethodTypeDesc.of(toDesc(MemoryLayout.PathElement.class), ConstantDescs.CD_String), true);
            cob.aastore();

            //long n = GroupLayout.byteOffset(pe)
            cob.getstatic(groupLayouts.get(recordType).layout);
            cob.aload(pathElementSlot);
            cob.invokeinterface(CD_MemoryLayout, "byteOffset",
                    MethodTypeDesc.of(ConstantDescs.CD_long, toDesc(MemoryLayout.PathElement.class).arrayType()));
            cob.lstore(offsetSlot);

            //StructLayoutOffsets[i++] = n;
            cob.getstatic(groupLayouts.get(recordType).offsets);
            cob.loadConstant(i++);
            cob.lload(offsetSlot);
            cob.lastore();
        }
    }


    public T build(Map<String, MethodHandle> methods) throws Throwable
    {
        Class<? extends T> foreignImpl = (Class<? extends T>)defineClass(fullName, classBytes, 0, classBytes.length);

        try {
            return foreignImpl.getDeclaredConstructor(methods.getClass()).newInstance(methods);
        }
        catch (Throwable ve)
        {
            ve.printStackTrace();
            parseClass();
            throw ve;
        }
    }

    private void addMethod(ClassBuilder clb, Method iMethod)
    {
        var params = Arrays.stream(iMethod.getParameterTypes()).map(ParamKeeper::classify).map(ParamKeeper::typeForInterfaceMethod).toList();
        var returnKeeper = ParamKeeper.classify(iMethod.getReturnType());
        var methodSig = MethodTypeDesc.of(returnKeeper.typeForInterfaceMethod(), params);
        var methodTypeDesc = toDesc(MethodHandle.class);

        var paramsVirt = Arrays.stream(iMethod.getParameterTypes()).map(ParamKeeper::classify).filter(ParamKeeper::requiredForVirtualCall).map(ParamKeeper::typeForVirtualCall).toList();
        var methodSigVirt = MethodTypeDesc.of(returnKeeper.typeForVirtualCall(), paramsVirt);

        clb.withMethod(iMethod.getName(), methodSig, ClassFile.ACC_PUBLIC,
                mb -> mb.withCode(cob -> {
                            List<ParamKeeper> keepers = classifyParams(cob, iMethod);
                            var start = cob.newLabel();
                            cob.labelBinding(start);

                            int used = Arrays.stream(iMethod.getParameterTypes()).mapToInt(this::paramSize).sum();
                            used += 1;
                            var arenaSlot = keepers.stream().filter(k -> k.classtype.equals(Arena.class)).mapToInt(k->k.stored).findFirst();
                            keepers = keepers.stream().filter(k -> !k.classtype.equals(Arena.class)).toList();

                            for (var k : keepers) {
                                if (k.classtype.equals(MemoryBlock.class) && arenaSlot.isPresent()) {
                                    cob.aload(k.stored).aload(arenaSlot.getAsInt());
                                    cob.invokevirtual(toDesc(MemoryBlock.class), "toPtr",
                                            MethodTypeDesc.of(CD_MemorySegment, CD_Arena));
                                    used++;
                                    cob.astore(used);
                                    k.stored = used;
                                    k.type = ParamType.addressType;
                                } else if (isGenericPtr(k.classtype)) {
                                    cob.aload(k.stored);
                                    cob.invokevirtual(toDesc(GenericPointer.class), "getPtr",
                                            MethodTypeDesc.of(CD_MemorySegment));
                                    used++;
                                    cob.astore(used);
                                    k.stored = used;
                                    k.type = ParamType.addressType;
                                }
                            }

                            cob.aload(0).getfield(methodHandles.get("m_" + iMethod.getName()));
                            int idx = 1; // slot 0 is the method handle
                            for (var k : keepers)
                            {
                                k.loadParam(cob);
                                idx++;
                            }

                            cob.invokevirtual(methodTypeDesc, "invokeExact", methodSigVirt );
                            storeParam(cob, idx, iMethod.getReturnType());
                            loadParam(cob, idx, iMethod.getReturnType());

                            for (var k : keepers) {
                                if (k.classtype.equals(MemoryBlock.class)) {
                                    cob.aload(k.storedOrig);
                                    cob.invokevirtual(toDesc(MemoryBlock.class), "readBack", ConstantDescs.MTD_void);
                                }
                            }


                            returnParam(cob, iMethod.getReturnType());
                            var error = toDesc(Error.class);
                            var end = cob.newLabel();
                            cob.labelBinding(end);
                            var handler = cob.newLabel();
                            cob.labelBinding(handler)
                            .astore(5)
                            .new_(error)
                            .dup()
                            .aload(5)
                            .invokespecial(error, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Throwable), false)
                            .athrow();
                            var handlerEnd = cob.newLabel();
                            cob.labelBinding(handlerEnd);
                            cob.exceptionCatchAll(start, end, handler);
                        }
                        ));
    }


    private List<ParamKeeper> classifyParams(CodeBuilder cob, Method iMethod)
    {
        ArrayList<ParamKeeper> keepers = new ArrayList<>();
        Annotation[][] annotations = iMethod.getParameterAnnotations();
        int slot = 0;
        for (Class<?> c : iMethod.getParameterTypes())
        {
            keepers.add(new ParamKeeper(c, ParamType.toType(c), cob.parameterSlot(slot), annotations[slot]));
            slot++;
        }
        return keepers;
    }

    private void addMethodWithArena(ClassBuilder clb, Method iMethod)
    {
        var params = Arrays.stream(iMethod.getParameterTypes()).map(ParamKeeper::classify).map(ParamKeeper::typeForInterfaceMethod).toList();
        var returnKeeper = ParamKeeper.classify(iMethod.getReturnType());
        var methodSig = MethodTypeDesc.of(returnKeeper.typeForInterfaceMethod(), params);

        var paramsVirt = Arrays.stream(iMethod.getParameterTypes()).map(ParamKeeper::classify).filter(ParamKeeper::requiredForVirtualCall).map(ParamKeeper::typeForVirtualCall).toList();
        var methodSigVirt = MethodTypeDesc.of(returnKeeper.typeForVirtualCall(), paramsVirt);

        var methodTypeDesc = toDesc(MethodHandle.class);
        var error = toDesc(Error.class);

        clb.withMethod(iMethod.getName(), methodSig, ClassFile.ACC_PUBLIC,
                mb -> mb.withCode(cob -> {
                            List<ParamKeeper> keepers = classifyParams(cob, iMethod);
                            var passedArena = keepers.stream().filter(ParamKeeper::isArena).findFirst();
                            keepers = keepers.stream().filter(k -> !k.isArena()).toList();

                            int used = Arrays.stream(iMethod.getParameterTypes()).mapToInt(this::paramSize).sum();
                            used += 1;

                            int arenaSlot = passedArena.isPresent() ? passedArena.get().stored : used++;
                            var start = cob.newLabel();
                            cob.labelBinding(start);
                            if (passedArena.isEmpty()) {
                                var iv = getCodeTemplate();
                                //I could not get arena creation to work. So I coded it in another class
                                //read that class, take that byte code and insert it here.
                                iv.ifPresent(cob::accept);
                                cob.astore(arenaSlot);
                            }
                            var startAutoClose = cob.newLabel();
                            cob.labelBinding(startAutoClose);

                            int ii = -1;
                            //loop converts parameters to native memory if required
                            for (Class<?> t : iMethod.getParameterTypes()) {
                                ii++;

                                if (t.isArray()) {
                                    if (t.getComponentType().isRecord())
                                    {
//                                        cob.aload(arenaSlot).aload(keepers.get(ii).stored);
//                                        cob.invokestatic(CD_Utils, "storeStruct",
//                                                MethodTypeDesc.of(CD_MemorySegment,
//                                                        CD_Arena, ConstantDescs.CD_Object.arrayType()));
                                        cob.aload(0).aload(arenaSlot).aload(keepers.get(ii).stored);
                                        cob.invokevirtual(thisClassDesc, "store" + t.getComponentType().getSimpleName(),
                                                MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(t.getComponentType()).arrayType()));
                                    }
                                    else if (isGenericPtr(t.getComponentType()))
                                    {
                                        cob.aload(arenaSlot).aload(keepers.get(ii).stored);
                                        if (isRefArgReadBackOnly(keepers.get(ii).annotations))
                                            cob.iconst_1();
                                        else
                                            cob.iconst_0();
                                        cob.invokestatic(CD_Utils, "toMS",
                                                MethodTypeDesc.of(CD_MemorySegment,
                                                        CD_SegmentAllocator, toDesc(GenericPointer.class).arrayType(1), ConstantDescs.CD_boolean));
                                    }
                                    else if (t.getComponentType().equals(String.class))
                                    {
                                        cob.aload(keepers.get(ii).stored).aload(arenaSlot);
                                        cob.invokestatic(CD_Utils, "toCString",
                                                MethodTypeDesc.of(CD_MemorySegment,
                                                        ConstantDescs.CD_String.arrayType(), CD_Arena));
                                    }
                                    else {
                                        if (isArrayOfPrimitives(t) || is2DArrayOfPrimitives(t))
                                        {
                                            if (isPtrPtrArg(keepers.get(ii).annotations))
                                            {
                                                cob.aload(arenaSlot).aload(keepers.get(ii).stored);
                                                cob.invokestatic(CD_Utils, "toPtrPTrMS",
                                                        MethodTypeDesc.of(CD_MemorySegment,
                                                                CD_SegmentAllocator, ParamKeeper.classify(t).typeForInterfaceMethod()));
                                            }
                                            else {
                                                //assumes an array of primitives
                                                cob.aload(arenaSlot).aload(keepers.get(ii).stored);
                                                if (isRefArgReadBackOnly(keepers.get(ii).annotations))
                                                    cob.iconst_1();
                                                else
                                                    cob.iconst_0();
                                                cob.invokestatic(CD_Utils, "toMS",
                                                        MethodTypeDesc.of(CD_MemorySegment,
                                                                CD_SegmentAllocator, ParamKeeper.classify(t).typeForInterfaceMethod(), ConstantDescs.CD_boolean));
                                            }
                                        }
                                    }
                                }
                                else if (t.isRecord())
                                {
//                                    cob.aload(arenaSlot).aload(keepers.get(ii).stored);
//                                    cob.invokestatic(CD_Utils, "storeStruct",
//                                            MethodTypeDesc.of(MemorySegment,
//                                                    arenaDesc, ConstantDescs.CD_Object));
                                    cob.aload(0).aload(arenaSlot).aload(keepers.get(ii).stored);
                                    cob.invokevirtual(thisClassDesc, "store" + t.getSimpleName(),
                                            MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(t)));

                                }
                                else if (t.equals(String.class))
                                {
                                    cob.aload(keepers.get(ii).stored).aload(arenaSlot);
                                    cob.invokestatic(CD_Utils, "toCString",
                                            MethodTypeDesc.of(CD_MemorySegment,
                                                    ConstantDescs.CD_String, CD_Arena));
                                } else if (t.equals(MemoryBlock.class)) {
                                    cob.aload(keepers.get(ii).stored).aload(arenaSlot);
                                    cob.invokevirtual(toDesc(MemoryBlock.class), "toPtr",
                                            MethodTypeDesc.of(CD_MemorySegment, CD_Arena));
                                } else if (isGenericPtr(t)) {
                                    cob.aload(keepers.get(ii).stored);
                                    cob.invokevirtual(toDesc(GenericPointer.class), "getPtr",
                                            MethodTypeDesc.of(CD_MemorySegment));
                                } else //is primitive
                                    continue;

                                //update all of the stored locations of parameters after they've been converted to MemorySegments
                                used++;
                                cob.astore(used);
                                keepers.get(ii).stored = used;
                                keepers.get(ii).type = ParamType.addressType;
                            }
                            cob.aload(0).getfield(methodHandles.get("m_" + iMethod.getName()));

                            //Move parameters to stack for the native function call
                            for (ParamKeeper k : keepers)
                            {
                                if (k.type == ParamType.addressType)
                                {
                                    var mtd = MethodTypeDesc.of(CD_MemorySegment, CD_MemorySegment);
                                    k.loadParam(cob);
                                    cob.invokestatic(CD_Utils, "toAddr", mtd);
                                    used++;
                                    cob.astore(used).aload(used);
                                    k.stored = used;
                                }
                                else {
                                     k.loadParam(cob);
                                }
                            }

                            //call the native method
                            cob.invokevirtual(methodTypeDesc, "invokeExact", methodSigVirt );
                            //capture the return of the native method
                            used++;
                            var virtMethodRetType = iMethod.getReturnType();
                            int newUsed = storeParam(cob, used, virtMethodRetType);

                            if (iMethod.getReturnType().equals(String.class))
                            {
                                var cdescString = ClassDesc.of(String.class.getName());
                                var mtd = MethodTypeDesc.of(cdescString, CD_MemorySegment);
                                loadParam(cob, used, iMethod.getReturnType());
                                cob.invokestatic(CD_Utils, "readString", mtd);
                                used++;
                                newUsed = storeParam(cob, used, iMethod.getReturnType());
                            } else if (isGenericPtr(iMethod.getReturnType())) {

                                var sig = MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment);
                                cob.new_(toDesc(Pointer.class)).dup();

                                loadParam(cob, used,  virtMethodRetType);
                                cob.invokespecial(toDesc(Pointer.class), ConstantDescs.INIT_NAME, sig);
                                used++;
                                newUsed = storeParam(cob, used, iMethod.getReturnType());
                            }


                            //Read back any parameters that were changed by the native method
                            for (ParamKeeper k : keepers)
                            {
                                //Only things annotated with @RefArg need to be read back
                                if (!isRefArg(k.annotations) || !k.classtype.isArray())
                                    continue;

                                if (k.classtype.getComponentType().isRecord()) {
//                                    cob.aload(k.stored).aload(k.storedOrig);
//                                    cob.invokestatic(CD_Utils, "readBackStruct",
//                                            MethodTypeDesc.of(ConstantDescs.CD_void,
//                                                    CD_MemorySegment, ConstantDescs.CD_Object.arrayType()));
                                    cob.aload(k.storedOrig).loadConstant(0);
                                    cob.aload(0).aload(k.stored).aload(k.storedOrig).loadConstant(0).aaload();
                                    var recType = k.classtype.getComponentType();
                                    cob.invokevirtual(thisClassDesc, "read" + recType.getSimpleName(),
                                            MethodTypeDesc.of(toDesc(recType), CD_MemorySegment, toDesc(recType)));
                                    cob.aastore();
                                }
                                else if (k.classtype.getComponentType().equals(String.class))
                                {
                                    cob.aload(k.stored).aload(k.storedOrig);
                                    cob.invokestatic(CD_Utils, "fromCString",
                                            MethodTypeDesc.of(ConstantDescs.CD_void,
                                                    CD_MemorySegment, ConstantDescs.CD_String.arrayType()));

                                }
                                else if (k.classtype.equals(MemoryBlock.class)) {
                                    cob.aload(k.storedOrig);
                                    cob.invokevirtual(toDesc(MemoryBlock.class), "readBack",
                                            ConstantDescs.MTD_void);
                                }
                                else if (isGenericPtr(k.classtype.getComponentType()))
                                {
                                    cob.aload(k.storedOrig).aload(k.stored);
                                    cob.invokestatic(CD_Utils, "toArr",
                                            MethodTypeDesc.of(ConstantDescs.CD_void,
                                                    Utils.toDesc(GenericPointer.class).arrayType(), CD_MemorySegment));

                                }
                                else {//primitive array
                                    //Loads the arguments for toMS()
                                    cob.aload(k.storedOrig).aload(k.stored);
                                    cob.invokestatic(CD_Utils, "toArr",
                                            MethodTypeDesc.of(ConstantDescs.CD_void,
                                                    k.typeForInterfaceMethod(), CD_MemorySegment));
                                }

                            }

                            if (passedArena.isEmpty()) {
                                cob.aload(arenaSlot);
                                cob.invokeinterface(CD_Arena, "close", ConstantDescs.MTD_void);
                            }

                            loadParam(cob, used, iMethod.getReturnType());
                            used = newUsed;
                            returnParam(cob, iMethod.getReturnType());
                            var end = cob.newLabel();
                            cob.labelBinding(end);
                            var handler = cob.newLabel();
                            cob.labelBinding(handler)
                                    .astore(5)
                                    .new_(error)
                                    .dup()
                                    .aload(5)
                                    .invokespecial(error, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Throwable), false)
                                    .athrow();
                            var handlerEnd = cob.newLabel();
                            cob.labelBinding(handlerEnd);
                            cob.exceptionCatchAll(start, end, handler);
                        }
                ));
    }

    private int paramSize(Class<?> c)
    {
        if (c.equals(double.class) || c.equals(long.class))
            return 2;
        return 1;
    }

    private static void loadParam(CodeBuilder cob, int idx, Class<?> c)
    {
        if (c.equals(void.class))
            return;

        if (c.equals(double.class))
            cob.dload(idx);
        else if (c.equals(long.class))
            cob.lload(idx);
        else if (c.equals(float.class))
            cob.fload(idx);
        else if (c.equals(int.class) || c.equals(short.class) || c.equals(byte.class))
            cob.iload(idx );
        else
            cob.aload(idx);
    }

    private static int storeParam(CodeBuilder cob, int idx, Class<?> c)
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
        else if (c.equals(int.class) || c.equals(short.class) || c.equals(byte.class))
            cob.istore(idx);
        else
            cob.astore(idx);
        return idx+1;
    }

    private void returnParam(CodeBuilder cob, Class<?> c)
    {
        if (c.equals(void.class))
            cob.return_();
        else if (c.equals(double.class))
            cob.dreturn();
        else if (c.equals(long.class))
            cob.lreturn();
        else if (c.equals(float.class))
            cob.freturn();
        else if (c.equals(int.class) || c.equals(short.class) || c.equals(byte.class))
            cob.ireturn();
        else
            cob.areturn();
    }

    private int byteSize(Class<?> c)
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
        return Integer.BYTES;
    }


    public static ClassDesc toDesc(Class<?> c)
    {
        return Utils.toDesc(c);
    }

    public static ClassDesc toLocalVariableDesc(Class<?> c)
    {
        if (primitiveToDescMap.containsKey(c))
            return primitiveToDescMap.get(c);

        if (c.isRecord())
            return toDesc(c);
        if (c.equals(String.class))
            return ConstantDescs.CD_String;
        if (c.equals(MemorySegment.class))
            return CD_MemorySegment;
        if (isArrayOfPrimitives(c))
            return primitiveToDescMap.get(c.getComponentType()).arrayType(1);
        if (is2DArrayOfPrimitives(c))
            return primitiveToDescMap.get(c.getComponentType()).arrayType(2);
        if (c.isArray() && c.getComponentType().isRecord())
            return toDesc(c).arrayType(1);

        throw new PassportException("Record fields can only be primitive, records, or arrays of either. Not: " + c.getName());
    }


    private Optional<CodeElement> getCodeTemplate()
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try {
            InputStream in = PassportBuilder.class.getResourceAsStream("Utils.class");
            byte[] data = new byte[4096];
            int len;

            while ((len = in.read(data)) > 0)
                bout.write(data, 0, len);
            in.close();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        ClassModel cm = ClassFile.of().parse(bout.toByteArray());

        for (var mm : cm.methods())
        {
            if (!mm.methodName().stringValue().equals("templatedMethod"))
                continue;

            for (CodeElement ce : mm.code().get().elementList())
            {
                if (ce.toString().contains("ofConfined"))
                    return Optional.of(ce);
            }
        }

        return Optional.empty();
    }

    private static String recordToLayoutName(Class<?> rec)
    {
        return rec.getSimpleName()+ "Layout";
    }

    private static String recordToOffsetName(Class<?> rec)
    {
        return rec.getSimpleName()+ "Offsets";
    }

    private static boolean needsArena(Method m)
    {
        Class<? extends Arena> arena;
        try (var a = Arena.ofConfined())
        {
            arena = a.getClass();
        }
        return Arrays.stream(m.getParameterTypes()).anyMatch(c -> c.isArray() || c.isRecord() || c.equals(arena) || c.equals(String.class));
    }

    record RecordDepends(Class<?> rec, List<Class<?>> depends)
    {
        boolean isSatisfied(List<Class<?>> orderedList)
        {
            return orderedList.containsAll(depends);
        }
    }

    /**
     * Creates an ordered list of all records that the interface uses. All the records
     * will need readers and writers. The ordering is based on which records depend on others.
     * i.e. If a record has no other records that it uses then it is sorted first. If a record
     * contains other records then it is sorted later. This helps ensure that all the
     * GroupLayouts for each record/struct are created properly since records that make use
     * of other records will need the GroupLayout of the child record, so those need to be made first.
     *
     * @return
     */
    List<Class<?>> getOrderedListOfRecords()
    {
        List<Method> interfaceMethods = PassportFactory.getDeclaredMethods(interfaceClass);
        Set<Class<?>> extraImports = findAllExtraImports(interfaceMethods);
        var recordList = extraImports.stream().filter(Class::isRecord).toList();

        List<RecordDepends> dependencies = new ArrayList<>();
        for (var c : recordList)
        {
            List<Class<?>> depends = new ArrayList<>();
            Arrays.stream(c.getDeclaredFields()).filter(f -> f.getType().isRecord()).forEach(f -> depends.add(f.getType()));
            dependencies.add(new RecordDepends(c, depends));
        }
        List<Class<?>> orderedRecords = new ArrayList<>();
        while (!dependencies.isEmpty()) {
            for (int n = dependencies.size() - 1; n >= 0; --n) {
                if (dependencies.get(n).depends.isEmpty() || dependencies.get(n).isSatisfied(orderedRecords))
                {
                    orderedRecords.add(dependencies.get(n).rec);
                    dependencies.remove(n);
                }
            }
        }
        return orderedRecords;
    }

    private void parseClass()
    {
        var classModel = ClassFile.of().parse(classBytes);

        for (var mm : classModel.methods())
        {
            if (!mm.methodName().equalsString("storeComplexStruct"))
                continue;
            System.out.println("============================================");
            System.out.println(mm.methodName());
            System.out.println("Signature: " + mm.methodType());


            CodeModel code = mm.code().get();
            System.out.println(code.toDebugString());
//            int i = 1;
//            for (CodeElement ce : code.elementList())
//            {
//                System.out.printf("%3d. %s\n", i, ce.toString());
//                i += ce.
//            }
        }

    }

    private static void println(CodeBuilder cob, String s)
    {
        cob.getstatic(toDesc(System.class), "out", toDesc(PrintStream.class)).ldc(s)
                .invokevirtual(toDesc(PrintStream.class), "println", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String));
    }
}
