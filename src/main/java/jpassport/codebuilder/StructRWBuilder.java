package jpassport.codebuilder;

import jpassport.*;
import jpassport.annotations.Array;
import jpassport.annotations.Ptr;

import java.lang.annotation.Annotation;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static jpassport.PassportBuilder.*;
import static jpassport.PassportWriter.*;
import static jpassport.Utils.toDesc;
import static jpassport.codebuilder.CBConstants.storeParam;
import static jpassport.codebuilder.CBConstants.loadParam;
import static jpassport.codebuilder.CBConstants.byteSize;

public class StructRWBuilder<T extends Passport> implements CBConstants{
    record RecordVariables(FieldRefEntry layout, FieldRefEntry offsets) {}

    private final ClassDesc thisClassDesc;
    private final Class<T> interfaceClass;
    private final HashMap<Class<?>, RecordVariables> groupLayouts = new HashMap<>();



    public StructRWBuilder(Class<T> iclass, ClassDesc desc)
    {
        interfaceClass = iclass;
        thisClassDesc = desc;
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
                cob.getstatic(CD_ValueLayout, constName, prim);
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
                        cob.getstatic(CD_ValueLayout, primitiveToConstName.get(ptype), primativeToVLDescMap.get(ptype));
                        cob.invokestatic(CD_MemoryLayout, "sequenceLayout",
                                MethodTypeDesc.of(toDesc(SequenceLayout.class), ConstantDescs.CD_long, CD_MemoryLayout), true);
                        cob.loadConstant(f.getName());
                        cob.invokeinterface(toDesc(SequenceLayout.class), "withName",
                                MethodTypeDesc.of(toDesc(SequenceLayout.class), ConstantDescs.CD_String));
                        cob.astore(layoutSlot);
                    } else if (isPointer) {
                        cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                        cob.loadConstant(f.getName());
                        cob.invokeinterface(CD_AddressLayout, "withName",
                                MethodTypeDesc.of(CD_AddressLayout, ConstantDescs.CD_String));
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
                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                    cob.loadConstant(f.getName());
                    cob.invokeinterface(CD_AddressLayout, "withName",
                            MethodTypeDesc.of(CD_AddressLayout, ConstantDescs.CD_String));
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
                cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                cob.loadConstant(f.getName());
                cob.invokeinterface(CD_AddressLayout, "withName",
                        MethodTypeDesc.of(CD_AddressLayout, ConstantDescs.CD_String));
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

    private int createRecordOffsets(CodeBuilder cob, Class<?> recordType, int firstAvailableSlot)
    {
        //StructLayoutOffsets = new long[n]
        cob.bipush(recordType.getDeclaredFields().length);
        cob.newarray(TypeKind.LONG).dup();
        cob.putstatic(groupLayouts.get(recordType).offsets);
        int pathElementSlot = firstAvailableSlot++;
        cob.iconst_1().anewarray(toDesc(MemoryLayout.PathElement.class)).astore(pathElementSlot);
        int offsetSlot = firstAvailableSlot;
//        firstAvailableSlot += 2;

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
                                cob.getstatic(CD_ValueLayout, primitiveToConstName.get(ftype), primativeToVLDescMap.get(ftype));
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
                                        cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                        cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                        cob.aload(memorySlot);
                                        cob.invokeinterface(CD_MemorySegment, "set",
                                                MethodTypeDesc.of(ConstantDescs.CD_void, CD_AddressLayout, ConstantDescs.CD_long, CD_MemorySegment));
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
                                    cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                    cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                    cob.aload(memorySlot);
                                    cob.invokeinterface(CD_MemorySegment, "set",
                                            MethodTypeDesc.of(ConstantDescs.CD_void, CD_AddressLayout, ConstantDescs.CD_long, CD_MemorySegment));
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
                                cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                cob.aload(addrSlot);
                                cob.invokeinterface(CD_MemorySegment, "set",
                                        MethodTypeDesc.of(ConstantDescs.CD_void, CD_AddressLayout, ConstantDescs.CD_long, CD_MemorySegment));
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
                                cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii++).laload();
                                cob.aload(cstringSlot);
                                cob.invokeinterface(CD_MemorySegment, "set",
                                        MethodTypeDesc.of(ConstantDescs.CD_void, CD_AddressLayout, ConstantDescs.CD_long, CD_MemorySegment));
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
                                cob.getstatic(CD_ValueLayout, primitiveToConstName.get(f.getType()), primativeToVLDescMap.get(f.getType()));
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
                                        cob.getstatic(CD_ValueLayout, primitiveToConstName.get(c), primativeToVLDescMap.get(c));
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
                                        cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                        cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                        cob.invokeinterface(CD_MemorySegment, "get",
                                                MethodTypeDesc.of(CD_MemorySegment, CD_AddressLayout, ConstantDescs.CD_long));
                                        int msegmentSlot = slots;
                                        slots = storeParam(cob, msegmentSlot, MemorySegment.class);

                                        cob.getstatic(CD_ValueLayout, primitiveToConstName.get(c), primativeToVLDescMap.get(c));
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
                                cob.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
                                cob.getstatic(groupLayouts.get(recordType).offsets).loadConstant(ii).laload();
                                cob.invokeinterface(CD_MemorySegment, "get",
                                        MethodTypeDesc.of(CD_MemorySegment, CD_AddressLayout, ConstantDescs.CD_long));
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


    private static String recordToLayoutName(Class<?> rec)
    {
        return rec.getSimpleName()+ "Layout";
    }

    private static String recordToOffsetName(Class<?> rec)
    {
        return rec.getSimpleName()+ "Offsets";
    }


    record RecordDepends(Class<?> rec, List<Class<?>> depends)
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

}
