package jpassport;

import jpassport.codebuilder.*;

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
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.*;

import static jpassport.PassportWriter.*;

import static jpassport.codebuilder.ArgClassification.*;
import static jpassport.codebuilder.CBConstants.storeParam;
import static jpassport.codebuilder.CBConstants.loadParam;
import static jpassport.codebuilder.CBConstants.toDesc;

public class PassportBuilder<T extends Passport> extends ClassLoader implements CBConstants {

    private final String fullName;
    private final byte[] classBytes;

    private static int Class_ID = 1; //Used to make unique package names

    public static Map<Class<?>, ClassDesc> primativeToVLDescMap;
    public static Map<Class<?>, String> primitiveToConstName = new HashMap<>();
    public static Map<Class<?>, ClassDesc> primitiveToDescMap = new HashMap<>();
    static final String INIT_METHODS_METHOD_NAME = "initMethods";

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

    private final ClassDesc thisClassDesc;
    private final HashMap<String, FieldRefEntry> methodHandles = new HashMap<>();

    public PassportBuilder(Class<T> interfaceClass)
    {
        this(interfaceClass, "jpassport.called_" + Class_ID++, interfaceClass.getSimpleName() + "_impl");
    }

    public PassportBuilder(Class<T> interfaceClass, String packageName, String className)
    {
        thisClassDesc = ClassDesc.of(packageName, className);
        List<Method> interfaceMethods = PassportFactory.getDeclaredMethods(interfaceClass);

        classBytes = ClassFile.of().build(thisClassDesc, clb ->
        {
            var entry = ConstantPoolBuilder.of().classEntry(toDesc(Passport.class));
            var mainInterface = ConstantPoolBuilder.of().classEntry(toDesc(interfaceClass));
            clb.withInterfaces(entry, mainInterface);
            clb.withFlags(ClassFile.ACC_PUBLIC);

            var structBuilder = new StructRWBuilder<>(interfaceClass, thisClassDesc);
            structBuilder.createRecordAccessors(clb);

            var classDescHM = toDesc(HashMap.class);
            var descMap = toDesc(Map.class);
            clb.withField("methods", classDescHM, ClassFile.ACC_PUBLIC);

            var nte = clb.constantPool().nameAndTypeEntry("methods", classDescHM);
            var methodsfield = clb.constantPool().fieldRefEntry(clb.constantPool().classEntry(thisClassDesc), nte);


            var desc = MethodTypeDesc.of(ConstantDescs.CD_void, classDescHM);
            var descputAll = MethodTypeDesc.of(ConstantDescs.CD_void, descMap);

            //Create the constructor for the iplemenation
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

            //Build all implementations of the interface methods
            for (Method m : interfaceMethods)
            {
                if (needsArena(m))
                    addMethodWithArena(clb, m);
                else
                    addMethod(clb, m);
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
                System.err.println("Error writing class file to " + dest);
            }
        }
        fullName = packageName + "." + className;
    }


    public T build(Map<String, MethodHandle> methods) throws Throwable
    {
        Class<? extends T> foreignImpl = (Class<? extends T>)defineClass(fullName, classBytes, 0, classBytes.length);

        try {
            return foreignImpl.getDeclaredConstructor(methods.getClass()).newInstance(methods);
        }
        catch (Throwable ve)
        {
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

                            int used = keepers.stream().mapToInt(ParamKeeper::getSlotCount).sum();
                            used += 1;
                            var arenaSlot = keepers.stream().filter(k -> k.classification == arena).mapToInt(k->k.stored).findFirst();
                            keepers = keepers.stream().filter(k -> k.classification != arena).toList();

                            for (var k : keepers) {
                                if (k.classification == memory_block && arenaSlot.isPresent()) {
                                    cob.aload(k.stored).aload(arenaSlot.getAsInt());
                                    cob.invokevirtual(toDesc(MemoryBlock.class), "toPtr",
                                            MethodTypeDesc.of(CD_MemorySegment, CD_Arena));
                                    used++;
                                    cob.astore(used);
                                    k.stored = used;
                                    k.type = ParamType.addressType;
                                } else if (k.classification == generic_ptr) {
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
                                //Memory blocks should always be read back there's no need to say @RegArg
                                if (k.classification == memory_block) {
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

                            int used = keepers.stream().mapToInt(ParamKeeper::getSlotCount).sum();
                            used += 1;

                            int arenaSlot = passedArena.isPresent() ? passedArena.get().stored : used++;
                            var start = cob.newLabel();
                            cob.labelBinding(start);
                            if (passedArena.isEmpty()) {
                                var iv = getCodeTemplate();
                                //I could not get arena creation to work. So I coded it in another class
                                //read that class, take that byte code and insert it here.
                                iv.ifPresent(cob);
                                cob.astore(arenaSlot);
                            }
                            var startAutoClose = cob.newLabel();
                            cob.labelBinding(startAutoClose);

                            int ii = -1;
                            //loop converts parameters to native memory if required
                            for (Class<?> t : iMethod.getParameterTypes()) {
                                ii++;

                                var varHandling = ArgClassification.classify(t, keepers.get(ii).annotations);

                                switch (varHandling)
                                {
                                    case primitive, mem_segment -> {
                                        continue;
                                    }
                                    case primitive_array, primitive_array2D -> {
                                        cob.aload(arenaSlot).aload(keepers.get(ii).stored);
                                        if (isRefArgReadBackOnly(keepers.get(ii).annotations))
                                            cob.iconst_1();
                                        else
                                            cob.iconst_0();
                                        cob.invokestatic(CD_Utils, "toMS",
                                                MethodTypeDesc.of(CD_MemorySegment,
                                                        CD_SegmentAllocator, ParamKeeper.classify(t).typeForInterfaceMethod(), ConstantDescs.CD_boolean));
                                    }
                                    case primitive_array2D_ptr2ptrs -> {
                                        cob.aload(arenaSlot).aload(keepers.get(ii).stored);
                                        cob.invokestatic(CD_Utils, "toPtrPTrMS",
                                                MethodTypeDesc.of(CD_MemorySegment,
                                                        CD_SegmentAllocator, ParamKeeper.classify(t).typeForInterfaceMethod()));
                                    }
                                    case record_ -> {
                                        cob.aload(0).aload(arenaSlot).aload(keepers.get(ii).stored);
                                        cob.invokevirtual(thisClassDesc, "store" + t.getSimpleName(),
                                                MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(t)));
                                    }
                                    case record_array -> {
                                        cob.aload(0).aload(arenaSlot).aload(keepers.get(ii).stored);
                                        cob.invokevirtual(thisClassDesc, "store" + t.getComponentType().getSimpleName(),
                                                MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(t.getComponentType()).arrayType()));
                                    }
                                    case string_ -> {
                                        cob.aload(keepers.get(ii).stored).aload(arenaSlot);
                                        cob.invokestatic(CD_Utils, "toCString",
                                                MethodTypeDesc.of(CD_MemorySegment,
                                                        ConstantDescs.CD_String, CD_Arena));
                                    }
                                    case string_array -> {
                                        cob.aload(keepers.get(ii).stored).aload(arenaSlot);
                                        cob.invokestatic(CD_Utils, "toCString",
                                                MethodTypeDesc.of(CD_MemorySegment,
                                                        ConstantDescs.CD_String.arrayType(), CD_Arena));
                                    }
                                    case memory_block -> {
                                        cob.aload(keepers.get(ii).stored).aload(arenaSlot);
                                        cob.invokevirtual(toDesc(MemoryBlock.class), "toPtr",
                                                MethodTypeDesc.of(CD_MemorySegment, CD_Arena));
                                    }
                                    case generic_ptr -> {
                                        cob.aload(keepers.get(ii).stored);
                                        cob.invokevirtual(toDesc(GenericPointer.class), "getPtr",
                                                MethodTypeDesc.of(CD_MemorySegment));
                                    }
                                    case generic_ptr_array -> {
                                        cob.aload(arenaSlot).aload(keepers.get(ii).stored);
                                        if (isRefArgReadBackOnly(keepers.get(ii).annotations))
                                            cob.iconst_1();
                                        else
                                            cob.iconst_0();
                                        cob.invokestatic(CD_Utils, "toMS",
                                                MethodTypeDesc.of(CD_MemorySegment,
                                                        CD_SegmentAllocator, toDesc(GenericPointer.class).arrayType(1), ConstantDescs.CD_boolean));
                                    }
                                    default ->
                                        throw new PassportException(varHandling + " not supported as an argument");
                                }

                                //update all the stored locations of parameters after they've been converted to MemorySegments
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
                            storeParam(cob, used, virtMethodRetType);

                            var argHandler = ArgClassification.classify(iMethod.getReturnType(), null);
                            switch(argHandler)
                            {
                                case string_ -> {
                                    var cdescString = ClassDesc.of(String.class.getName());
                                    var mtd = MethodTypeDesc.of(cdescString, CD_MemorySegment);
                                    loadParam(cob, used, iMethod.getReturnType());
                                    cob.invokestatic(CD_Utils, "readString", mtd);
                                    used++;
                                    storeParam(cob, used, iMethod.getReturnType());
                                }
                                case generic_ptr -> {
                                    var sig = MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment);
                                    cob.new_(toDesc(Pointer.class)).dup();

                                    loadParam(cob, used,  virtMethodRetType);
                                    cob.invokespecial(toDesc(Pointer.class), ConstantDescs.INIT_NAME, sig);
                                    used++;
                                    storeParam(cob, used, iMethod.getReturnType());
                                }
                            }

                            //Read back any parameters that were changed by the native method
                            for (ParamKeeper k : keepers)
                            {
                                //Only things annotated with @RefArg need to be read back
                                //Memory blocks should always be read back there's no need to say @RegArg
                                if (!(isRefArg(k.annotations) || k.classification == memory_block))
                                    continue;

                                switch (k.classification)
                                {
                                    case record_array -> {
                                        cob.aload(k.storedOrig).loadConstant(0);
                                        cob.aload(0).aload(k.stored).aload(k.storedOrig).loadConstant(0).aaload();
                                        var recType = k.classtype.getComponentType();
                                        cob.invokevirtual(thisClassDesc, "read" + recType.getSimpleName(),
                                                MethodTypeDesc.of(toDesc(recType), CD_MemorySegment, toDesc(recType)));
                                        cob.aastore();
                                    }
                                    case string_array -> {
                                        cob.aload(k.stored).aload(k.storedOrig);
                                        cob.invokestatic(CD_Utils, "fromCString",
                                                MethodTypeDesc.of(ConstantDescs.CD_void,
                                                        CD_MemorySegment, ConstantDescs.CD_String.arrayType()));
                                    }
                                    case memory_block -> {
                                        cob.aload(k.storedOrig);
                                        cob.invokevirtual(toDesc(MemoryBlock.class), "readBack",
                                                ConstantDescs.MTD_void);

                                    }
                                    case generic_ptr_array -> {
                                        cob.aload(k.storedOrig).aload(k.stored);
                                        cob.invokestatic(CD_Utils, "toArr",
                                                MethodTypeDesc.of(ConstantDescs.CD_void,
                                                        toDesc(GenericPointer.class).arrayType(), CD_MemorySegment));

                                    }
                                    case primitive_array -> {
                                        //Loads the arguments for toMS()
                                        cob.aload(k.storedOrig).aload(k.stored);
                                        cob.invokestatic(CD_Utils, "toArr",
                                                MethodTypeDesc.of(ConstantDescs.CD_void,
                                                        k.typeForInterfaceMethod(), CD_MemorySegment));
                                    }
                                }
                            }

                            if (passedArena.isEmpty()) {
                                cob.aload(arenaSlot);
                                cob.invokeinterface(CD_Arena, "close", ConstantDescs.MTD_void);
                            }

                            loadParam(cob, used, iMethod.getReturnType());
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


    public static ClassDesc toLocalVariableDesc(Class<?> c)
    {
        if (primitiveToDescMap.containsKey(c))
            return primitiveToDescMap.get(c);

        if (c.isRecord() || MemoryBlock.class.equals(c))
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
            if (in == null)
                throw new PassportException("Util class is missing from JPassport jar.");

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
            if (!mm.methodName().stringValue().equals("templatedMethod"))
                continue;

            var code = mm.code();
            if (code.isEmpty())
                throw new PassportException("Could not find arena code declaration.");

            for (CodeElement ce : code.get().elementList())
            {
                if (ce.toString().contains("ofConfined"))
                    return Optional.of(ce);
            }
        }

        return Optional.empty();
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

    private void parseClass()
    {
        var classModel = ClassFile.of().parse(classBytes);

        for (var mm : classModel.methods())
        {
            System.out.println("============================================");
            System.out.println(mm.methodName());
            System.out.println("Signature: " + mm.methodType());

            var code = mm.code();
            if (code.isEmpty())
                System.out.println("NO CODE FOUND.");
            else {
                System.out.println(code.get().toDebugString());
            }
        }
    }

    private static void println(CodeBuilder cob, String s)
    {
        cob.getstatic(toDesc(System.class), "out", toDesc(PrintStream.class)).ldc(s)
                .invokevirtual(toDesc(PrintStream.class), "println", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String));
    }
}
