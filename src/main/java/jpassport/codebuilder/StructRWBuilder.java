package jpassport.codebuilder;

import jpassport.*;
import jpassport.annotations.Array;
import jpassport.pointers.GenericPointer;
import jpassport.pointers.MemoryBlock;

import java.lang.annotation.Annotation;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static jpassport.codebuilder.CBConstants.skipUnionField;
import static jpassport.codebuilder.PassportBuilder.*;
import static jpassport.Utils.toDesc;
import static jpassport.codebuilder.CBConstants.*;

/**
 * This class builds the byte code required to translate records into structs and back.
 *
 * @param <T>
 */
public class StructRWBuilder<T extends Passport> implements CBConstants{
    record RecordVariables(FieldRefEntry layout, FieldRefEntry offsets) {}

    private final ClassDesc thisClassDesc;
    private final Class<T> interfaceClass;
    private final HashMap<Class<?>, RecordVariables> groupLayouts = new HashMap<>();
    private final boolean withDebug;



    public StructRWBuilder(Class<T> iclass, ClassDesc desc, boolean withDebug)
    {
        interfaceClass = iclass;
        thisClassDesc = desc;
        this.withDebug = withDebug;
    }

    public void createRecordAccessors(ClassBuilder cbl)
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
                            int firstAvailableSlot = 1;
                            for (Class<?> c : orderedRecords) {
                                firstAvailableSlot = createGroupLayout(cob, c, firstAvailableSlot);
                                firstAvailableSlot = createRecordOffsets(cob, c, firstAvailableSlot);
                            }
                            cob.return_();
                        }
                ));

        createStoreMethods(cbl, orderedRecords);
        createReadMethods(cbl, orderedRecords);
    }

    private int getFieldCount(Class<?> recordType)
    {
        int ret = 0;
        for (Field f : recordType.getDeclaredFields()) {
            if (skipUnionField(recordType, f))
                continue;
            ret++;
        }
        return ret;
    }

    private int createGroupLayout(CodeBuilder cob, Class<?> recordType, int firstAvailableSlot)
    {
        //How many items will be passed to Utils.makeStruct()?
        int slotsNeeded = 0;
        for (Field f : recordType.getDeclaredFields()) {
            if (skipUnionField(recordType, f))
                continue;

            if (getPaddingBytes(f) != 0)
                slotsNeeded++;
            slotsNeeded++;
        }

        cob.bipush(slotsNeeded);
        cob.anewarray(CD_MemoryLayout).dup();
        int memLayoutArrSlot = firstAvailableSlot++;
        cob.astore(memLayoutArrSlot);

        verifyUnion(recordType);

        int idx = 0;
        for (Field f : recordType.getDeclaredFields()) {
            long paddingBits = getPaddingBytes(f);
            var ftype = f.getType();

            if (skipUnionField(recordType, f))
                continue;

            if (paddingBits < 0) {
                cob.aload(memLayoutArrSlot).loadConstant(idx++); //for the later array store
                cob.loadConstant(-paddingBits);
                cob.invokestatic(CD_MemoryLayout, "paddingLayout",
                        MethodTypeDesc.of(toDesc(PaddingLayout.class), ConstantDescs.CD_long), true);
                cob.aastore();
            }

            var varHandling = ArgClassification.classify(f);

            switch (varHandling)
            {
                case primitive -> {
                    ClassDesc prim = primativeToVLDescMap.get(ftype);
                    String constName = primitiveToConstName.get(ftype);

                    cob.aload(memLayoutArrSlot).loadConstant(idx++); //for the later array store
                    cob.getstatic(CD_ValueLayout, constName, prim);
                    cob.loadConstant(f.getName());
                    cob.invokeinterface(prim, "withName",
                            MethodTypeDesc.of(CD_MemoryLayout, ConstantDescs.CD_String));
                    cob.aastore();
                }
                case primitive_array -> {
                    int layoutSlot = firstAvailableSlot++;
                    var ptype = ftype.getComponentType();
                    Annotation[] arrays = f.getAnnotationsByType(Array.class);
                    if (arrays.length == 0)
                        throw new PassportException("Struct members that are primitive arrays must either be @Ptr or @Array(length=n) - " + recordType.getSimpleName() + "." + f.getName());
                    int length = ((Array) arrays[0]).length();
                    cob.loadConstant((long)length);
                    cob.getstatic(CD_ValueLayout, primitiveToConstName.get(ptype), primativeToVLDescMap.get(ptype));
                    cob.invokestatic(CD_MemoryLayout, "sequenceLayout",
                            MethodTypeDesc.of(toDesc(SequenceLayout.class), ConstantDescs.CD_long, CD_MemoryLayout), true);
                    cob.loadConstant(f.getName());
                    cob.invokeinterface(toDesc(SequenceLayout.class), "withName",
                            MethodTypeDesc.of(toDesc(SequenceLayout.class), ConstantDescs.CD_String));
                    cob.astore(layoutSlot);
                    cob.aload(memLayoutArrSlot).loadConstant(idx++).aload(layoutSlot); //for the later array store
                    cob.aastore();
                }
                case record_ -> {
                    cob.aload(memLayoutArrSlot).loadConstant(idx++); //for the later array store
                    cob.getstatic(groupLayouts.get(ftype).layout).loadConstant(f.getName());
                    cob.invokeinterface(toDesc(GroupLayout.class), "withName", MethodTypeDesc.of(toDesc(GroupLayout.class), ConstantDescs.CD_String));
                    cob.aastore();
                }
                case record_array -> {

                    cob.aload(memLayoutArrSlot).loadConstant(idx++); //for the later array store
                    Annotation[] arrays = f.getAnnotationsByType(Array.class);
                    if (arrays.length == 0)
                        throw new PassportException("Struct members that are primitive arrays must either be @Ptr or @Array(length=n) - " + recordType.getSimpleName() + "." + f.getName());
                    int length = ((Array) arrays[0]).length();
                    cob.loadConstant((long)length);
                    cob.getstatic(groupLayouts.get(ftype.getComponentType()).layout);
                    cob.invokestatic(CD_MemoryLayout, "sequenceLayout",
                            MethodTypeDesc.of(toDesc(SequenceLayout.class), ConstantDescs.CD_long, CD_MemoryLayout), true);
                    cob.loadConstant(f.getName());
                    cob.invokeinterface(toDesc(SequenceLayout.class), "withName",
                            MethodTypeDesc.of(toDesc(SequenceLayout.class), ConstantDescs.CD_String));

                    cob.aastore();
                }
                case primitive_array_ptr, record_ptr, string_, mem_segment, memory_block, generic_ptr, record_array_ptr -> {
                    cob.aload(memLayoutArrSlot).loadConstant(idx++); //for the later array store
                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                    cob.loadConstant(f.getName());
                    cob.invokeinterface(CD_AddressLayout, "withName",
                            MethodTypeDesc.of(CD_AddressLayout, ConstantDescs.CD_String));
                    cob.aastore();
                }
                default ->
                    throw new PassportException(varHandling + " is not supported in structs are not supported yet");

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
        if (isUnion(recordType)) {
            cob.invokestatic(CD_Utils, "makeUnion",
                    MethodTypeDesc.of(toDesc(UnionLayout.class), CD_MemoryLayout.arrayType(1)));
        }
        else {
            cob.invokestatic(CD_Utils, "makeStruct",
                    MethodTypeDesc.of(toDesc(GroupLayout.class), CD_MemoryLayout.arrayType(1)));
        }
        cob.putstatic(groupLayouts.get(recordType).layout);

        return firstAvailableSlot;
    }

    private int createRecordOffsets(CodeBuilder cob, Class<?> recordType, int firstAvailableSlot)
    {
        //StructLayoutOffsets = new long[n]
        int fieldCount = getFieldCount(recordType);
        cob.bipush(fieldCount);
        cob.newarray(TypeKind.LONG).dup();
        cob.putstatic(groupLayouts.get(recordType).offsets);
        int pathElementSlot = firstAvailableSlot++;
        cob.iconst_1().anewarray(toDesc(MemoryLayout.PathElement.class)).astore(pathElementSlot);
        int offsetSlot = firstAvailableSlot;
//        firstAvailableSlot += 2;

        int i = 0;
        for (Field f : recordType.getDeclaredFields()) {
            if (skipUnionField(recordType, f))
                continue;

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

        return firstAvailableSlot;
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
                    MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(recordType), CD_MemorySegment),
                    ClassFile.ACC_PRIVATE, methodBuilder -> methodBuilder.withCode(cob -> {

                        int arenaSlot = cob.parameterSlot(0);
                        int inputRecSlot = cob.parameterSlot(1);
                        var recDesc = toDesc(recordType);

                        //if the input record is null, return MemorySegment.NULL
                        var jumpTo = cob.newLabel();
                        cob.aload(inputRecSlot).ifnonnull(jumpTo);
                        cob.getstatic(CD_MemorySegment, "NULL", CD_MemorySegment);
                        cob.areturn();

                        cob.labelBinding(jumpTo);
                        int slots = cob.parameterSlot(2) + 1; //start the local variables after the parameters
                        var methodStart = cob.newLabel();
                        cob.labelBinding(methodStart);
                        var methodEnd = cob.newLabel();
                        int memSegSlot = cob.parameterSlot(2);

                        //If someone passed in the memory segment we should write into, then we don't need to allocate one
                        var skipAlloc = cob.newLabel();
                        cob.aload(memSegSlot).ifnonnull(skipAlloc);
                        cob.aload(arenaSlot); //load the Arena
                        cob.getstatic(groupLayouts.get(recordType).layout());
                        cob.invokeinterface(toDesc(GroupLayout.class), "byteSize", MethodTypeDesc.of(ConstantDescs.CD_long));
                        cob.invokeinterface(CD_SegmentAllocator, "allocate", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_long));
                        cob.astore(memSegSlot);
                        cob.labelBinding(skipAlloc);

                        boolean isUnion = isUnion(recordType);
                        int recToReadSlot = slots++;
                        if (isUnion) {
                            for (Field f : recordType.getDeclaredFields()) {
                                if (f.getType().equals(UnionFieldIO.class))
                                {
                                    //load the field index of the union that we are going to write out to memory
                                    cob.aload(inputRecSlot);
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(CD_UnionFieldIO));
                                    cob.astore(recToReadSlot);
                                    break;
                                }
                            }
                        }

                        int ii = 0;
                        slots++;
                        for (Field f : recordType.getDeclaredFields()) {
                            if (skipUnionField(recordType, f))
                                continue;

                            Label endLabel = cob.newLabel();
                            if (isUnion)
                            {
                                //if this is not the right field of the union to write to memory, then skip it.
                                cob.aload(recToReadSlot).loadConstant(ii).loadConstant(f.getName());
                                cob.invokevirtual(CD_UnionFieldIO, "toNative", MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_int, ConstantDescs.CD_String));
                                cob.ifeq(endLabel);
                            }

                            Class<?> ftype = f.getType();
                            var varHandling = ArgClassification.classify(f);
                            switch(varHandling)
                            {
                                case primitive -> {
                                    cob.aload(inputRecSlot);

                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(primitiveToDescMap.get(ftype)));
                                    int fieldSlot = slots;
                                    slots = storeParam(cob, fieldSlot, ftype);

                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    int offsetSlot = slots;
                                    slots = storeParam(cob, offsetSlot, long.class);

                                    cob.aload(memSegSlot);
                                    cob.getstatic(CD_ValueLayout, primitiveToConstName.get(ftype), primativeToVLDescMap.get(ftype));
                                    cob.lload(offsetSlot);
                                    loadParam(cob, fieldSlot, ftype);

                                    cob.invokeinterface(CD_MemorySegment, "set",
                                            MethodTypeDesc.of(ConstantDescs.CD_void, primativeToVLDescMap.get(ftype), ConstantDescs.CD_long, primitiveToDescMap.get(ftype)));
                                }
                                case primitive_array -> {
                                    var arrDesc = primitiveToDescMap.get(ftype.getComponentType()).arrayType();
//                                    Annotation[] arrays = f.getAnnotationsByType(Array.class);
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
                                    cob.pop();
//                                        memStruct.asSlice(PassingArraysLayoutOffsets[0] + offset).copyFrom(MemorySegment.ofArray(rec.s_double()));


                                }
                                case primitive_array_ptr -> {
                                    var arrDesc = primitiveToDescMap.get(ftype.getComponentType()).arrayType();
                                    cob.aload(inputRecSlot);
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(arrDesc));
                                    int valueSlot = slots;
                                    slots = storeParam(cob, valueSlot, ftype);

                                    cob.aload(arenaSlot).aload(valueSlot).iconst_0();
                                    cob.invokestatic(CD_Utils, "toMS", MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, arrDesc, ConstantDescs.CD_boolean));
                                    int memorySlot = slots;
                                    slots = storeParam(cob, memorySlot, MemorySegment.class);
                                    cob.aload(memSegSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    cob.aload(memorySlot);
                                    cob.invokeinterface(CD_MemorySegment, "set",
                                            MethodTypeDesc.of(ConstantDescs.CD_void, CD_AddressLayout, ConstantDescs.CD_long, CD_MemorySegment));
                                    //memStruct.set(ADDRESS, PassingArraysLayoutOffsets[3] + offset, Utils.toMS(scope, rec.s_doublePtr(), false));

                                }
                                case record_ -> {
                                    cob.aload(inputRecSlot);

                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                    int fieldSlot = slots;
                                    slots = storeParam(cob, fieldSlot, ftype);

                                    cob.aload(0).aload(arenaSlot).aload(fieldSlot);
                                    cob.invokevirtual(thisClassDesc, "store" + ftype.getSimpleName(), MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(ftype)));
                                    int memorySlot = slots;
                                    slots = storeParam(cob, memorySlot, MemorySegment.class);
                                    cob.aload(memSegSlot);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    cob.invokeinterface(CD_MemorySegment, "asSlice", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_long));
                                    int sliceSlot = slots;
                                    slots = storeParam(cob, sliceSlot, MemorySegment.class);

                                    cob.aload(sliceSlot).aload(memorySlot);
                                    cob.invokeinterface(CD_MemorySegment, "copyFrom", MethodTypeDesc.of(CD_MemorySegment, CD_MemorySegment));
                                    cob.pop();

                                }
                                case record_ptr -> {
                                    cob.aload(inputRecSlot);

                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                    int fieldSlot = slots;
                                    slots = storeParam(cob, fieldSlot, ftype);

                                    cob.aload(0).aload(arenaSlot).aload(fieldSlot);
                                    cob.invokevirtual(thisClassDesc, "store" + ftype.getSimpleName(), MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(ftype)));
                                    int memorySlot = slots;
                                    slots = storeParam(cob, memorySlot, MemorySegment.class);
                                    cob.aload(memSegSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    cob.aload(memorySlot);
                                    cob.invokeinterface(CD_MemorySegment, "set",
                                            MethodTypeDesc.of(ConstantDescs.CD_void, CD_AddressLayout, ConstantDescs.CD_long, CD_MemorySegment));

                                }
                                case record_array -> {
                                    cob.aload(inputRecSlot);
                                    var rec_type = ftype.getComponentType();
                                    var cd_rec_type = toDesc(rec_type);
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(cd_rec_type.arrayType(1)));
                                    int fieldSlot = slots;
                                    slots = storeParam(cob, fieldSlot, ftype);

                                    cob.aload(0).aload(arenaSlot).aload(fieldSlot);
                                    cob.invokevirtual(thisClassDesc, "storeArr" + rec_type.getSimpleName(), MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, cd_rec_type.arrayType(1)));
                                    int memorySlot = slots;
                                    slots = storeParam(cob, memorySlot, MemorySegment.class);
                                    cob.aload(memSegSlot);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    cob.invokeinterface(CD_MemorySegment, "asSlice", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_long));
                                    cob.aload(memorySlot);
                                    cob.invokeinterface(CD_MemorySegment, "copyFrom", MethodTypeDesc.of(CD_MemorySegment, CD_MemorySegment));
                                    cob.pop();
//                                    cob.aload(memorySlot);
                                }
                                case record_array_ptr -> {
                                    cob.aload(inputRecSlot);
                                    var rec_type = ftype.getComponentType();
                                    var cd_rec_type = toDesc(rec_type);

                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(cd_rec_type.arrayType(1)));
                                    int fieldSlot = slots;
                                    slots = storeParam(cob, fieldSlot, ftype);

                                    cob.aload(0).aload(arenaSlot).aload(fieldSlot);
                                    cob.invokevirtual(thisClassDesc, "storePtr" + rec_type.getSimpleName(), MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, cd_rec_type.arrayType(1)));
                                    int memorySlot = slots;
                                    slots = storeParam(cob, memorySlot, MemorySegment.class);
                                    cob.aload(memSegSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    cob.aload(memorySlot);
                                    cob.invokeinterface(CD_MemorySegment, "set",
                                            MethodTypeDesc.of(ConstantDescs.CD_void, CD_AddressLayout, ConstantDescs.CD_long, CD_MemorySegment));

                                }
                                case generic_ptr -> {
                                    cob.aload(inputRecSlot);
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                    int gptr = slots;
                                    slots = storeParam(cob, gptr, ftype);
                                    cob.aload(gptr);
                                    cob.invokevirtual(toDesc(GenericPointer.class), "getPtr", MethodTypeDesc.of(CD_MemorySegment));
                                    int memorySlot = slots;
                                    slots = storeParam(cob, memorySlot, MemorySegment.class);

                                    cob.aload(memSegSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    cob.aload(memorySlot);
                                    cob.invokeinterface(CD_MemorySegment, "set",
                                            MethodTypeDesc.of(ConstantDescs.CD_void, CD_AddressLayout, ConstantDescs.CD_long, CD_MemorySegment));

                                }
                                case mem_segment -> {
                                    cob.aload(inputRecSlot);
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                    int addrSlot = slots;
                                    slots = storeParam(cob, addrSlot, ftype);

                                    cob.aload(memSegSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    cob.aload(addrSlot);
                                    cob.invokeinterface(CD_MemorySegment, "set",
                                            MethodTypeDesc.of(ConstantDescs.CD_void, CD_AddressLayout, ConstantDescs.CD_long, CD_MemorySegment));
//                                memStruct.set(ADDRESS, StructWithPrtLayoutOffsets[1] + offset, rec.addr());

                                }
                                case memory_block -> {
                                    cob.aload(inputRecSlot);
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                    int addrSlot = slots;
                                    slots = storeParam(cob, addrSlot, ftype);

                                    cob.aload(addrSlot).aload(arenaSlot);
                                    cob.invokevirtual(toDesc(MemoryBlock.class), "toPtr", MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator));
                                    addrSlot = slots;
                                    slots = storeParam(cob, addrSlot, MemorySegment.class);

                                    cob.aload(memSegSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    cob.aload(addrSlot);
                                    cob.invokeinterface(CD_MemorySegment, "set",
                                            MethodTypeDesc.of(ConstantDescs.CD_void, CD_AddressLayout, ConstantDescs.CD_long, CD_MemorySegment));
                                }

                                case string_ -> {
                                    cob.aload(inputRecSlot);
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                    int stringSlot = slots;
                                    slots = storeParam(cob, stringSlot, ftype);

                                    cob.aload(stringSlot).aload(arenaSlot);
                                    cob.invokestatic(CD_Utils, "toCString", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_String, CD_SegmentAllocator));
                                    int cstringSlot = slots;
                                    slots = storeParam(cob, cstringSlot, MemorySegment.class);

                                    cob.aload(memSegSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    cob.aload(cstringSlot);
                                    cob.invokeinterface(CD_MemorySegment, "set",
                                            MethodTypeDesc.of(ConstantDescs.CD_void, CD_AddressLayout, ConstantDescs.CD_long, CD_MemorySegment));
//                                memStruct.set(ADDRESS, ComplexStructLayoutOffsets[3] + offset, Utils.toCString(rec.string(), scope));
                                }
                                default ->
                                    throw new PassportException(varHandling + " not implemented");
                            }

                            cob.labelBinding(endLabel);
                        }

                        if (withDebug)
                        {
//                            sb.append(String.format("\t\tUtils.structBuilt(this, memStruct, %1$sLayout, \"%1$s\", recs);", c.getSimpleName()));

                            cob.aload(0).aload(memSegSlot).getstatic(groupLayouts.get(recordType).layout);
                            cob.loadConstant(recordType.getSimpleName()).aload(inputRecSlot);
                            cob.invokestatic(CD_Utils, "structBuilt", MethodTypeDesc.of(ConstantDescs.CD_void,
                                    toDesc(Passport.class), CD_MemorySegment, CD_MemoryLayout, ConstantDescs.CD_String, ConstantDescs.CD_Object));
                        }

                        cob.aload(memSegSlot).areturn();
                        cob.labelBinding(methodEnd);
                    }));

            cbl.withMethod("store" + recordType.getSimpleName(),
                    MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(recordType)),
                    ClassFile.ACC_PRIVATE, methodBuilder -> methodBuilder.withCode(cob -> {

                    cob.aload(0).aload(cob.parameterSlot(0)).aload(cob.parameterSlot(1)).aconst_null();
                    cob.invokevirtual(thisClassDesc, "store" + recordType.getSimpleName(), MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(recordType), CD_MemorySegment));
                    cob.areturn();
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

            cbl.withMethod("storePtr" + recordType.getSimpleName(),
                    MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(recordType).arrayType()),
                    ClassFile.ACC_PRIVATE, methodBuilder -> methodBuilder.withCode(cob -> {

                        var code = SampleCode.getCodeTemplate(SampleCode.TemplateFunction.store_ptr);

                        for (CodeElement ce : code.elementList())
                        {
                            if (ce.toString().contains("storeSimpleRec"))
                                cob.invokevirtual(thisClassDesc, "store" + recordType.getSimpleName(), MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(recordType)));
                            else if (ce.toString().contains("name=this") || ce.toString().contains("name=rec"))
                                continue;
                            else
                                cob.accept(ce);
                        }
                     }));

            cbl.withMethod("storeArr" + recordType.getSimpleName(),
                    MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(recordType).arrayType()),
                    ClassFile.ACC_PRIVATE, methodBuilder -> methodBuilder.withCode(cob -> {

                        var code = SampleCode.getCodeTemplate(SampleCode.TemplateFunction.store_arr);

                        for (CodeElement ce : code.elementList())
                        {
                            if (ce.toString().contains("SimpleRecLayout"))
                                cob.getstatic(groupLayouts.get(recordType).layout());
                            else if (ce.toString().contains("storeSimpleRec"))
                                cob.invokevirtual(thisClassDesc, "store" + recordType.getSimpleName(), MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(recordType), CD_MemorySegment));
                            else if (ce.toString().contains("name=this") || ce.toString().contains("name=rec"))
                                continue;
                            else
                                cob.accept(ce);
                        }
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
            var recDesc = toDesc(recordType);

            //Creates "private RecordType readRecordTye(MemorySegment memPtr, RecordType origRec)".
            //The returned value is the new record made from the struct in memPtr.
            cbl.withMethod("read" + recordType.getSimpleName(),
                    MethodTypeDesc.of(recDesc, CD_MemorySegment, recDesc),
                    ClassFile.ACC_PRIVATE, methodBuilder -> methodBuilder.withCode(cob -> {
                        int slots = cob.parameterSlot(1) + 1; //start the local variables after the parameters

                        //If the memory segment is NULL or the records are null,return null
                        var postIf = cob.newLabel();
                        var earlyReturn = cob.newLabel();
                        cob.getstatic(CD_MemorySegment, "NULL", CD_MemorySegment);
                        cob.aload(cob.parameterSlot(0));
                        cob.invokeinterface(CD_MemorySegment, "equals", MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Object));
                        cob.ifne(earlyReturn);
                        int inputRecordSlot = cob.parameterSlot(1);
                        cob.aload(inputRecordSlot);
                        cob.ifnonnull(postIf);
                        cob.labelBinding(earlyReturn).aconst_null().areturn();

                        cob.labelBinding(postIf);
                        var methodStart = cob.newLabel();
                        cob.labelBinding(methodStart);
                        var methodEnd = cob.newLabel();

                        int sizeSlot = slots;
                        cob.localVariable(sizeSlot, "struct_size", ConstantDescs.CD_long, methodStart, methodEnd);
                        slots += 2;
                        cob.getstatic(groupLayouts.get(recordType).layout());
                        cob.invokeinterface(toDesc(GroupLayout.class), "byteSize", MethodTypeDesc.of(ConstantDescs.CD_long));
                        cob.lstore(sizeSlot);

                        int memStructSlot = cob.parameterSlot(0);
                        int recordSlot = cob.parameterSlot(1);
                        cob.aload(memStructSlot).lload(sizeSlot);
                        cob.invokestatic(CD_Utils, "resize", MethodTypeDesc.of(CD_MemorySegment,CD_MemorySegment, ConstantDescs.CD_long));
                        cob.astore(memStructSlot);

                        int fieldCount = getFieldCount(recordType);

                        ParamType[] fields = new ParamType[fieldCount];
                        int[] fieldSlots = new int[fields.length];
                        int ii = 0;
                        //Create a local variable for each field of the record
                        for (Field f : recordType.getDeclaredFields()) {
                            if (skipUnionField(recordType, f))
                                continue;
                            var t = f.getType();
                            fields[ii] = ParamType.toType(t);
                            fieldSlots[ii] = slots;
                            cob.localVariable(fieldSlots[ii], f.getName(), toLocalVariableDesc(t), methodStart, methodEnd);
                            slots += fields[ii].requiredSlots();
                            ii++;
                        }

                        boolean isUnion = isUnion(recordType);
                        int recToReadSlot = slots++;
                        if (isUnion) {
                            for (Field f : recordType.getDeclaredFields()) {
                                if (f.getType().equals(UnionFieldIO.class))
                                {
                                    //get the index of the field that we are reading back from the union.
                                    cob.aload(inputRecordSlot);
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(CD_UnionFieldIO));
                                    cob.astore(recToReadSlot);
                                    break;
                                }
                            }
                        }



                        ii=0;
                        for (Field f : recordType.getDeclaredFields()) {
                            if (skipUnionField(recordType, f))
                                continue;

                            var ftype = f.getType();
                            var varHandling = ArgClassification.classify(f);
                            Label endIfLabel = cob.newLabel();
                            Label endElseLabel = cob.newLabel();

                            //if this is not the right union field to read back then skip it.
                            if (isUnion)
                            {
                                cob.aload(recToReadSlot).loadConstant(ii).loadConstant(f.getName());
                                cob.invokevirtual(CD_UnionFieldIO, "fromNative", MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_int, ConstantDescs.CD_String));
                                cob.ifne(endIfLabel);

                                cob.aload(inputRecordSlot);
                                ClassDesc cd = switch (varHandling)
                                {
                                    case primitive -> primitiveToDescMap.get(ftype);
                                    case primitive_array, primitive_array_ptr -> primitiveToDescMap.get(ftype.getComponentType()).arrayType(1);
                                    case record_array, record_array_ptr -> toDesc(ftype.getComponentType()).arrayType(1);
                                    default -> toDesc(ftype);
                                };
                                cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(cd));
                                storeParam(cob, fieldSlots[ii], ftype);
                                cob.goto_(endElseLabel).labelBinding(endIfLabel);
                            }


                            switch (varHandling)
                            {
                                case primitive -> {
//                                var s_int = memStruct.get(JAVA_INT, TestStructLayoutOffsets[0]);
                                    cob.aload(memStructSlot);
                                    cob.getstatic(CD_ValueLayout, primitiveToConstName.get(f.getType()), primativeToVLDescMap.get(f.getType()));
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                    cob.invokeinterface(CD_MemorySegment, "get",
                                            MethodTypeDesc.of(primitiveToDescMap.get(ftype), primativeToVLDescMap.get(f.getType()), ConstantDescs.CD_long));
                                    storeParam(cob, fieldSlots[ii], ftype);
                                }
                                case primitive_array ->{
                                    var c = ftype.getComponentType();

                                    Annotation[] arrays = f.getAnnotationsByType(Array.class);
                                    cob.aload(memStructSlot);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                    int length = ((Array) arrays[0]).length();
                                    cob.loadConstant((long)length * byteSize(c));
                                    cob.invokeinterface(CD_MemorySegment, "asSlice", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_long, ConstantDescs.CD_long));
                                    cob.getstatic(CD_ValueLayout, primitiveToConstName.get(c), primativeToVLDescMap.get(c));
                                    cob.invokeinterface(CD_MemorySegment, "toArray",
                                            MethodTypeDesc.of(primitiveToDescMap.get(c).arrayType(), primativeToVLDescMap.get(c)));
                                    storeParam(cob, fieldSlots[ii], ftype);
//                                    var s_double = memStruct.asSlice(PassingArraysLayoutOffsets[0], 5 * Double.BYTES).toArray(JAVA_DOUBLE);

                                }

                                case primitive_array_ptr -> {
                                    var c = ftype.getComponentType();
                                    var arrDesc = primitiveToDescMap.get(c).arrayType();
                                    cob.aload(recordSlot);
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(arrDesc));
                                    int arrSlot = slots;
                                    slots = storeParam(cob, arrSlot, ftype);

                                    cob.aload(memStructSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                    cob.invokeinterface(CD_MemorySegment, "get",
                                            MethodTypeDesc.of(CD_MemorySegment, CD_AddressLayout, ConstantDescs.CD_long));
                                    int msegmentSlot = slots;
                                    slots = storeParam(cob, msegmentSlot, MemorySegment.class);

                                    cob.aload(memStructSlot).aload(msegmentSlot).aload(arrSlot);
                                    cob.invokestatic(CD_Utils, "toArr",
                                            MethodTypeDesc.of(arrDesc, CD_MemorySegment, CD_MemorySegment, arrDesc));
                                    storeParam(cob, fieldSlots[ii], ftype);
                                }

                                case record_ -> {
                                    cob.aload(memStructSlot);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                    cob.invokeinterface(CD_MemorySegment, "asSlice", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_long));
                                    int msegmentSlot = slots;
                                    slots = storeParam(cob, msegmentSlot, MemorySegment.class);

                                    cob.aload(cob.parameterSlot(1));
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                    int recTypeSlot = slots;
                                    slots = storeParam(cob, recTypeSlot, ftype);


                                    cob.aload(0).aload(msegmentSlot).aload(recTypeSlot);
                                    cob.invokevirtual(thisClassDesc, "read" + ftype.getSimpleName(), MethodTypeDesc.of(toDesc(ftype), CD_MemorySegment, toDesc(ftype)));
                                    storeParam(cob, fieldSlots[ii], ftype);

//                                var ts = readTestStruct(memStruct.asSlice(ComplexStructLayoutOffsets[1]), rec.ts());

                                }
                                case record_ptr -> {
                                    cob.aload(memStructSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                    cob.invokeinterface(CD_MemorySegment, "get",
                                            MethodTypeDesc.of(CD_MemorySegment, CD_AddressLayout, ConstantDescs.CD_long));
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
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                    int recTypeSlot = slots;
                                    slots = storeParam(cob, recTypeSlot, ftype);

                                    cob.aload(0).aload(recSliceSlot).aload(recTypeSlot);
                                    cob.invokevirtual(thisClassDesc, "read" + ftype.getSimpleName(), MethodTypeDesc.of(toDesc(ftype), CD_MemorySegment, toDesc(ftype)));
                                    storeParam(cob, fieldSlots[ii], ftype);

//var tsPtr = readTestStruct(Utils.slice(memStruct, memStruct.get(ADDRESS, ComplexStructLayoutOffsets[2]), TestStructLayout.byteSize()), rec.tsPtr());

                                }
                                case record_array -> {
                                    var rec_type = ftype.getComponentType();
                                    var cd_rec_type = toDesc(rec_type);
                                    cob.aload(recordSlot).invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(cd_rec_type.arrayType(1)));
                                    cob.arraylength().anewarray(cd_rec_type);
                                    int newArrSlot = slots;
                                    slots = storeParam(cob, newArrSlot, rec_type);

                                    cob.aload(memStructSlot);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();

                                    cob.aload(recordSlot);
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(cd_rec_type.arrayType(1)));
                                    cob.arraylength().i2l();

                                    cob.getstatic(groupLayouts.get(rec_type).layout).invokeinterface(CD_MemoryLayout, "byteSize", MethodTypeDesc.of(ConstantDescs.CD_long));
                                    cob.lmul();

                                    cob.invokeinterface(CD_MemorySegment, "asSlice", MethodTypeDesc.of(CD_MemorySegment, ConstantDescs.CD_long, ConstantDescs.CD_long));
                                    int msegmentSlot = slots;
                                    slots = storeParam(cob, msegmentSlot, MemorySegment.class);

                                    cob.aload(0).aload(msegmentSlot).aload(newArrSlot);
                                    cob.invokevirtual(thisClassDesc, "readArr" + rec_type.getSimpleName(), MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment, cd_rec_type.arrayType(1)));
                                    cob.aload(newArrSlot);
                                    storeParam(cob, fieldSlots[ii], ftype);
                                }
                                case record_array_ptr -> {
                                    var rec_type = ftype.getComponentType();
                                    var cd_rec_type = toDesc(rec_type);

                                    cob.aload(recordSlot);
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(cd_rec_type.arrayType(1)));
                                    cob.arraylength().anewarray(cd_rec_type);
                                    int newArrSlot = slots;
                                    slots = storeParam(cob, newArrSlot, rec_type);

                                    cob.aload(memStructSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                    cob.invokeinterface(CD_MemorySegment, "get",
                                            MethodTypeDesc.of(CD_MemorySegment, CD_AddressLayout, ConstantDescs.CD_long));
                                    int memseg = slots;
                                    slots = storeParam(cob, memseg, MemorySegment.class);
                                    cob.aload(0).aload(memseg).aload(newArrSlot);
                                    cob.invokevirtual(thisClassDesc, "readPtrs" + rec_type.getSimpleName(), MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment, cd_rec_type.arrayType(1)));
                                    cob.aload(newArrSlot);
                                    storeParam(cob, fieldSlots[ii], ftype);
//                                    var array_of_ptrs = new TestStruct[rec.array_of_ptrs().length];
//                                    readPtrsTestStruct(memStruct.get(ADDRESS, PassingStructsLayoutOffsets[2]), array_of_ptrs);

                                }

                                case generic_ptr -> {
                                    cob.aload(memStructSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                    cob.invokeinterface(CD_MemorySegment, "get",
                                            MethodTypeDesc.of(CD_MemorySegment, CD_AddressLayout, ConstantDescs.CD_long));
                                    int memseg = slots;
                                    slots = storeParam(cob, memseg, MemorySegment.class);

                                    var sig = MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment);
                                    cob.new_(CBConstants.toDesc(ftype)).dup();

                                    cob.aload(memseg);
                                    cob.invokespecial(CBConstants.toDesc(ftype), ConstantDescs.INIT_NAME, sig);
                                    storeParam(cob, fieldSlots[ii], ftype);

                                }

                                case mem_segment -> {
                                    cob.aload(memStructSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                    cob.invokeinterface(CD_MemorySegment, "get",
                                            MethodTypeDesc.of(CD_MemorySegment, CD_AddressLayout, ConstantDescs.CD_long));
                                    storeParam(cob, fieldSlots[ii], ftype);
//                                var addr = memStruct.get(ADDRESS, StructWithPrtLayoutOffsets[1]);

                                }
                                case string_ -> {
                                    cob.aload(memStructSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                    cob.invokeinterface(CD_MemorySegment, "get",
                                            MethodTypeDesc.of(CD_MemorySegment, CD_AddressLayout, ConstantDescs.CD_long));
                                    int msegmentSlot = slots;
                                    slots = storeParam(cob, msegmentSlot, MemorySegment.class);
                                    cob.aload(msegmentSlot);
                                    cob.invokestatic(CD_Utils, "readString", MethodTypeDesc.of(ConstantDescs.CD_String, CD_MemorySegment));
                                    storeParam(cob, fieldSlots[ii], ftype);
//                                var string = Utils.readString(memStruct.get(ADDRESS, ComplexStructLayoutOffsets[3]));
                                }
                                case memory_block -> {
                                    cob.aload(memStructSlot);
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                    cob.invokeinterface(CD_MemorySegment, "get",
                                            MethodTypeDesc.of(CD_MemorySegment, CD_AddressLayout, ConstantDescs.CD_long));
                                    int msegmentSlot = slots;
                                    slots = storeParam(cob, msegmentSlot, MemorySegment.class);

                                    cob.aload(recordSlot);
                                    cob.invokevirtual(recDesc, f.getName(), MethodTypeDesc.of(toDesc(ftype)));
                                    int origBlockSlot = slots;
                                    slots = storeParam(cob, origBlockSlot, MemoryBlock.class);

                                    cob.aload(msegmentSlot).aload(origBlockSlot);
                                    cob.invokestatic(toDesc(MemoryBlock.class), "recreate", MethodTypeDesc.of(toDesc(MemoryBlock.class), CD_MemorySegment, toDesc(MemoryBlock.class)));
                                    storeParam(cob, fieldSlots[ii], ftype);
                                }
                                default ->
                                    throw new PassportException(varHandling + " not supported");
                            }

                            ii++;

                            cob.labelBinding(endElseLabel);
                        }


                        cob.new_(toDesc(recordType)).dup();
                        ii = 0;
                        List<ClassDesc> paramDesc = new ArrayList<>();
                        for (Field f : recordType.getDeclaredFields()) {
                            if (skipUnionField(recordType, f))
                            {
                                //set the same to and from native indexes
                                cob.aload(inputRecordSlot);
                                cob.invokevirtual(toDesc(recordType), f.getName(), MethodTypeDesc.of(CD_UnionFieldIO));
                                paramDesc.add(CD_UnionFieldIO);
                                continue;
                            }

                            loadParam(cob, fieldSlots[ii++], f.getType());
                            paramDesc.add(toLocalVariableDesc(f.getType()));
                        }

                        cob.invokespecial(toDesc(recordType), ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, paramDesc), false);
                        int retSlot = slots++;
                        cob.astore(retSlot);

                        if (withDebug)
                        {
                            cob.aload(0).aload(memStructSlot).getstatic(groupLayouts.get(recordType).layout);
                            cob.loadConstant(recordType.getSimpleName()).aload(retSlot);
                            cob.invokestatic(CD_Utils, "structReadBack", MethodTypeDesc.of(ConstantDescs.CD_void,
                                    toDesc(Passport.class), CD_MemorySegment, CD_MemoryLayout, ConstantDescs.CD_String, ConstantDescs.CD_Object));

                        }

                        cob.aload(retSlot).areturn();
                        cob.labelBinding(methodEnd);
                    }));

            cbl.withMethod("readPtrs" + recordType.getSimpleName(),
                    MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment, toDesc(recordType).arrayType()),
                    ClassFile.ACC_PRIVATE, methodBuilder -> methodBuilder.withCode(cob -> {

                        var code = SampleCode.getCodeTemplate(SampleCode.TemplateFunction.read_ptr);

                        for (CodeElement ce : code.elementList())
                        {
                            if (ce.toString().contains("SimpleRecLayout"))
                                cob.getstatic(groupLayouts.get(recordType).layout());
                            else if (ce.toString().contains("readSimpleRec"))
                                cob.invokevirtual(thisClassDesc, "read" + recordType.getSimpleName(), MethodTypeDesc.of(toDesc(recordType), CD_MemorySegment, toDesc(recordType)));
                            else if (ce.toString().contains("jpassport/codebuilder/SampleCode$SimpleRec"))
                                continue;
                            else
                                cob.accept(ce);
                        }
                    }));

            cbl.withMethod("readArr" + recordType.getSimpleName(),
                    MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment, toDesc(recordType).arrayType()),
                    ClassFile.ACC_PRIVATE, methodBuilder -> methodBuilder.withCode(cob -> {

                        var code = SampleCode.getCodeTemplate(SampleCode.TemplateFunction.read_arr);

                        for (CodeElement ce : code.elementList())
                        {
                            if (ce.toString().contains("SimpleRecLayout"))
                                cob.getstatic(groupLayouts.get(recordType).layout());
                            else if (ce.toString().contains("readSimpleRec"))
                                cob.invokevirtual(thisClassDesc, "read" + recordType.getSimpleName(), MethodTypeDesc.of(toDesc(recordType), CD_MemorySegment, toDesc(recordType)));
                            else if (ce.toString().contains("jpassport/codebuilder/SampleCode$SimpleRec"))
                                continue;
                            else
                                cob.accept(ce);
                        }
                    }));

        }
    }


    private static String recordToLayoutName(Class<?> rec)
    {
        return rec.getSimpleName()+ "Layout";
    }

    private static String recordToOffsetName(Class<?> rec)
    {
        return rec.getSimpleName()+ "Offsets";
    }


    private record RecordDepends(Class<?> rec, Set<Class<?>> depends)
    {
        boolean isSatisfied(List<Class<?>> orderedList)
        {
            //if all the record types that this record needs are already in the list, then this record can be added to the list.
            //this ensures that records that this one needs are defined before this record needs them.
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
     * @return A list of all record types that we need to build structs for. Records that contain other records are sorted later in the list
     */
    private List<Class<?>> getOrderedListOfRecords()
    {
        List<Method> interfaceMethods = PassportFactory.getDeclaredMethods(interfaceClass);
        Set<Class<?>> extraImports = findAllExtraImports(interfaceMethods);
        var recordList = extraImports.stream().filter(Class::isRecord).toList();

        List<RecordDepends> dependencies = new ArrayList<>();
        for (var c : recordList)
        {
            Set<Class<?>> depends = new HashSet<>();
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

    private static ClassDesc toLocalVariableDesc(Class<?> c)
    {
        if (primitiveToDescMap.containsKey(c))
            return primitiveToDescMap.get(c);

        if (c.isRecord() || MemoryBlock.class.equals(c))
            return CBConstants.toDesc(c);
        if (c.equals(String.class))
            return ConstantDescs.CD_String;
        if (c.equals(MemorySegment.class))
            return CD_MemorySegment;
        if (isGenericPtr(c))
            return toDesc(c);
        if (isArrayOfPrimitives(c))
            return primitiveToDescMap.get(c.getComponentType()).arrayType(1);
        if (is2DArrayOfPrimitives(c))
            return primitiveToDescMap.get(c.getComponentType()).arrayType(2);
        if (c.isArray() && c.getComponentType().isRecord())
            return CBConstants.toDesc(c.getComponentType()).arrayType(1);

        throw new PassportException("Record fields can only be primitive, records, or arrays of either. Not: " + c.getName());
    }

}
