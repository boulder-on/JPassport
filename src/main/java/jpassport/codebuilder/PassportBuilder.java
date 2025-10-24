package jpassport.codebuilder;

import jpassport.*;
import jpassport.Utils;
import jpassport.annotations.Critical;
import jpassport.pointers.GenericPointer;
import jpassport.pointers.MemoryBlock;

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

import static jpassport.Utils.toDesc;
import static jpassport.codebuilder.ArgClassification.*;
import static jpassport.codebuilder.CBConstants.*;

public class PassportBuilder<T extends Passport> extends ClassLoader implements CBConstants {

    private final String fullName;
    private final byte[] classBytes;

    private static int Class_ID = 1; //Used to make unique package names

    public static Map<Class<?>, ClassDesc> primativeToVLDescMap;
    public static Map<Class<?>, String> primitiveToConstName = new HashMap<>();
    public static Map<Class<?>, ClassDesc> primitiveToDescMap = new HashMap<>();

    static {
        primativeToVLDescMap = new HashMap<>();
        primativeToVLDescMap.put(int.class, toDesc(ValueLayout.OfInt.class));
        primativeToVLDescMap.put(long.class, toDesc(ValueLayout.OfLong.class));
        primativeToVLDescMap.put(short.class, toDesc(ValueLayout.OfShort.class));
        primativeToVLDescMap.put(byte.class, toDesc(ValueLayout.OfByte.class));
        primativeToVLDescMap.put(double.class, toDesc(ValueLayout.OfDouble.class));
        primativeToVLDescMap.put(float.class, toDesc(ValueLayout.OfFloat.class));
        primativeToVLDescMap.put(boolean.class, toDesc(ValueLayout.OfBoolean.class));
        primativeToVLDescMap.put(char.class, toDesc(ValueLayout.OfChar.class));

        primitiveToConstName.put(int.class, "JAVA_INT");
        primitiveToConstName.put(long.class, "JAVA_LONG");
        primitiveToConstName.put(short.class, "JAVA_SHORT");
        primitiveToConstName.put(byte.class, "JAVA_BYTE");
        primitiveToConstName.put(double.class, "JAVA_DOUBLE");
        primitiveToConstName.put(float.class, "JAVA_FLOAT");
        primitiveToConstName.put(boolean.class, "JAVA_BOOLEAN");
        primitiveToConstName.put(char.class, "JAVA_CHAR");

        primitiveToDescMap.put(int.class, ConstantDescs.CD_int);
        primitiveToDescMap.put(long.class, ConstantDescs.CD_long);
        primitiveToDescMap.put(short.class, ConstantDescs.CD_short);
        primitiveToDescMap.put(byte.class, ConstantDescs.CD_byte);
        primitiveToDescMap.put(double.class, ConstantDescs.CD_double);
        primitiveToDescMap.put(float.class, ConstantDescs.CD_float);
        primitiveToDescMap.put(boolean.class, ConstantDescs.CD_boolean);
        primitiveToDescMap.put(char.class, ConstantDescs.CD_char);
    }

    private final ClassDesc thisClassDesc;
    private final HashMap<String, FieldRefEntry> methodHandles = new HashMap<>();
    private final HashMap<Class<?>, FieldRefEntry> enumMaps = new HashMap<>();
    private final boolean withDebug;

    public PassportBuilder(Class<T> interfaceClass, boolean withDebug)
    {
        this(interfaceClass, "jpassport.called_" + Class_ID++, interfaceClass.getSimpleName() + "_impl", withDebug);
    }

    public PassportBuilder(Class<T> interfaceClass, String packageName, String className, boolean withDebug)
    {
        thisClassDesc = ClassDesc.of(packageName, className);
        this.withDebug = withDebug;
        List<Method> interfaceMethods = PassportFactory.getDeclaredMethods(interfaceClass);

        classBytes = ClassFile.of().build(thisClassDesc, clb ->
        {
            var entry = ConstantPoolBuilder.of().classEntry(toDesc(Passport.class));
            var mainInterface = ConstantPoolBuilder.of().classEntry(toDesc(interfaceClass));
            clb.withInterfaces(entry, mainInterface);
            clb.withFlags(ClassFile.ACC_PUBLIC);

            var structBuilder = new StructRWBuilder<>(interfaceClass, thisClassDesc, withDebug);
            structBuilder.createRecordAccessors(clb);

            var classDescHM = toDesc(HashMap.class);
            var descMap = toDesc(Map.class);
            clb.withField("methods", classDescHM, ClassFile.ACC_PUBLIC);

            var nte = clb.constantPool().nameAndTypeEntry("methods", classDescHM);
            var methodsfield = clb.constantPool().fieldRefEntry(clb.constantPool().classEntry(thisClassDesc), nte);


            var desc = MethodTypeDesc.of(ConstantDescs.CD_void, classDescHM);
            var descputAll = MethodTypeDesc.of(ConstantDescs.CD_void, descMap);

            //Create the constructor for the implementation
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
                                    .invokevirtual(thisClassDesc, INIT_STRUCTS_METHOD_NAME, ConstantDescs.MTD_void)
                                    .return_()));

            buildMethodHandleVariables(clb, interfaceMethods);
            buildEnumMaps(clb, interfaceMethods);
            buildStaticInitialization(interfaceClass, clb);

            //Build all implementations of the interface methods
            for (Method m : interfaceMethods)
            {
                if (needsArena(m))
                    addMethodWithArena(clb, interfaceClass, m);
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
                Files.createDirectories(dest);
                Files.write(destFile, classBytes);
            } catch (IOException e) {
                System.err.println("Error writing class file to " + dest);
            }
        }
        fullName = packageName + "." + className;
    }

    private void buildStaticInitialization(Class<T> interfaceClass, ClassBuilder clb) {
        //Create a method that assigns all the method handles for the native methods
        clb.withMethod(ConstantDescs.CLASS_INIT_NAME,  ConstantDescs.MTD_void,
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, mb -> mb.withCode(
                   cob -> {

                       //loads all method handles as static final variables
                       for (String name : methodHandles.keySet())
                       {
                           cob.ldc(toDesc(interfaceClass)).aconst_null().ldc(name);
                           cob.invokestatic(toDesc(PassportFactory.class), "getHandle",
                                   MethodTypeDesc.of(toDesc(MethodHandle.class), ConstantDescs.CD_Class, ConstantDescs.CD_Class, ConstantDescs.CD_String));
                           cob.putstatic(methodHandles.get(name));
                       }

                       for (Class<?> enumClass : enumMaps.keySet())
                       {
                           cob.ldc(toDesc(enumClass));
                           if (CBConstants.isLongEnum(enumClass))
                               cob.invokestatic(CD_Utils, "buildEnumMapLong", MethodTypeDesc.of(CD_HashMap, ConstantDescs.CD_Class));
                           else
                               cob.invokestatic(CD_Utils, "buildEnumMapInteger", MethodTypeDesc.of(CD_HashMap, ConstantDescs.CD_Class));
                           cob.putstatic(enumMaps.get(enumClass));
                       }
                       cob.return_();
                   }
                ));
    }

    private void buildMethodHandleVariables(ClassBuilder clb, List<Method> interfaceMethods) {
        NameAndTypeEntry nte;
        //Create member variables for each of the method handles to the native methods.
        var methodTypeDesc = toDesc(MethodHandle.class);
        for (Method m : interfaceMethods)
        {
            String fieldName = m.getName();
            clb.withField(fieldName, methodTypeDesc, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL );
            nte = clb.constantPool().nameAndTypeEntry(fieldName, methodTypeDesc);
            var fieldRefEntry = clb.constantPool().fieldRefEntry(clb.constantPool().classEntry(thisClassDesc), nte);
            methodHandles.put(fieldName, fieldRefEntry);
        }
    }

    private void buildEnumMaps(ClassBuilder clb, List<Method> interfaceMethods) {
        NameAndTypeEntry nte;
        Set<Class<?>> extraImports = findAllExtraImports(interfaceMethods);
        var hashMap = toDesc(HashMap.class);

        for (var c : extraImports)
        {
            if (!c.isEnum())
                continue;
            String fieldName = c.getSimpleName() + "Lookup";
            clb.withField(fieldName, hashMap, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC);
            nte = clb.constantPool().nameAndTypeEntry(fieldName, hashMap);
            var fieldRefEntry = clb.constantPool().fieldRefEntry(clb.constantPool().classEntry(thisClassDesc), nte);
            enumMaps.put(c, fieldRefEntry);
        }
    }


    public T build(Map<String, MethodHandle> methods) throws Throwable
    {

        try {
            Class<? extends T> foreignImpl = (Class<? extends T>)defineClass(fullName, classBytes, 0, classBytes.length);
            return foreignImpl.getDeclaredConstructor(methods.getClass()).newInstance(methods);
        }
        catch (IllegalAccessError iaEx)
        {
            throw new PassportException("Does your module export all required interfaces and records?", iaEx);
        }
        catch (VerifyError ve)
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

                            int nextlocalVarSlot = keepers.stream().mapToInt(ParamKeeper::getSlotCount).sum() + 1;

                            var arenaSlot = keepers.stream().filter(k -> k.classification == arena).mapToInt(k->k.stored).findFirst();
                            keepers = keepers.stream().filter(k -> k.classification != arena).toList();

                            for (var k : keepers) {
                                if (k.classification == memory_block && arenaSlot.isPresent()) {
                                    cob.aload(k.stored).aload(arenaSlot.getAsInt());
                                    cob.invokevirtual(toDesc(MemoryBlock.class), "toPtr",
                                            MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator));

                                    k.stored = nextlocalVarSlot;
                                    k.type = ParamType.addressType;
                                    nextlocalVarSlot = storeLocalVar(cob, nextlocalVarSlot, MemorySegment.class);
                                } else if (k.classification == generic_ptr) {
                                    cob.aload(k.stored);
                                    cob.invokevirtual(toDesc(GenericPointer.class), "getPtr",
                                            MethodTypeDesc.of(CD_MemorySegment));
                                    k.stored = nextlocalVarSlot;
                                    k.type = ParamType.addressType;
                                    nextlocalVarSlot = storeLocalVar(cob, nextlocalVarSlot, MemorySegment.class);
                                }
                                else if (k.classification == enum_long)
                                {
                                    cob.aload(k.stored);
                                    cob.invokevirtual(toDesc(k.classtype), "getCValue",
                                            MethodTypeDesc.of(ConstantDescs.CD_long));
                                    k.stored = nextlocalVarSlot;
                                    k.type = ParamType.longType;
                                    nextlocalVarSlot = storeLocalVar(cob, nextlocalVarSlot, long.class);
                                }
                                else if (k.classification == enum_int)
                                {
                                    cob.aload(k.stored);
                                    cob.invokevirtual(toDesc(k.classtype), "getCValue",
                                            MethodTypeDesc.of(ConstantDescs.CD_int));
                                    k.stored = nextlocalVarSlot;
                                    k.type = ParamType.intType;
                                    nextlocalVarSlot = storeLocalVar(cob, nextlocalVarSlot, int.class);
                                }
                                else if (k.classification == enum_ordinal)
                                {
                                    cob.aload(k.stored);
                                    cob.invokevirtual(ConstantDescs.CD_Enum, "ordinal",
                                            MethodTypeDesc.of(ConstantDescs.CD_int));
                                    k.stored = nextlocalVarSlot;
                                    k.type = ParamType.intType;
                                    nextlocalVarSlot = storeLocalVar(cob, nextlocalVarSlot, int.class);
                                }
                            }

                            if (withDebug)
                            {
                                cob.bipush(keepers.size()).anewarray(ConstantDescs.CD_Object);
                                int objArrSlot = nextlocalVarSlot;
                                nextlocalVarSlot = storeLocalVar(cob, objArrSlot, Object.class);
                                int i = 0;
                                for (var k : keepers)
                                {
                                    cob.aload(objArrSlot).loadConstant(i++);
                                    k.loadAutoBoxed(cob);
                                    cob.aastore();
                                }

                                cob.aload(0).loadConstant(iMethod.getName()).aload(objArrSlot);
                                cob.invokestatic(CD_Utils, "preNativeCall", MethodTypeDesc.of(ConstantDescs.CD_void,
                                        toDesc(Passport.class), ConstantDescs.CD_String, ConstantDescs.CD_Object.arrayType()));
                            }

                            cob.aload(0).getstatic(methodHandles.get(iMethod.getName()));
                            for (var k : keepers)
                                k.loadParam(cob);

                            cob.invokevirtual(methodTypeDesc, "invokeExact", methodSigVirt );
                            int returnSlot = nextlocalVarSlot;
                            var virtMethodRetType = iMethod.getReturnType();
                            if (isLongEnum(virtMethodRetType))
                                virtMethodRetType = long.class;
                            else if (isIntEnum(virtMethodRetType) || virtMethodRetType.isEnum())
                                virtMethodRetType = int.class;

                            nextlocalVarSlot = storeLocalVar(cob, returnSlot, virtMethodRetType);

                            if (withDebug)
                            {
                                cob.bipush(keepers.size()).anewarray(ConstantDescs.CD_Object);
                                int objArrSlot = nextlocalVarSlot;
                                nextlocalVarSlot = storeLocalVar(cob, objArrSlot, Object.class);
                                int i = 0;
                                for (var k : keepers)
                                {
                                    cob.aload(objArrSlot).loadConstant(i++);
                                    k.loadAutoBoxed(cob);
                                    cob.aastore();
                                }
                                cob.aload(0).loadConstant(iMethod.getName());
                                ParamKeeper.autoBox(cob, virtMethodRetType, returnSlot);
                                cob.aload(objArrSlot);
                                cob.invokestatic(CD_Utils, "postNativeCall", MethodTypeDesc.of(ConstantDescs.CD_void,
                                        toDesc(Passport.class), ConstantDescs.CD_String, ConstantDescs.CD_Object, ConstantDescs.CD_Object.arrayType()));
                            }

                            var argHandler = ArgClassification.classify(iMethod.getReturnType(), null);
                            switch(argHandler)
                            {
                                case string_ -> {
                                    var cdescString = ClassDesc.of(String.class.getName());
                                    var mtd = MethodTypeDesc.of(cdescString, CD_MemorySegment);
                                    loadParam(cob, returnSlot, iMethod.getReturnType());
                                    cob.invokestatic(CD_Utils, "readString", mtd);
                                    returnSlot = nextlocalVarSlot;
                                    nextlocalVarSlot = storeLocalVar(cob, returnSlot, iMethod.getReturnType());
                                }
                                case generic_ptr -> {
                                    var sig = MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment);
                                    cob.new_(toDesc(iMethod.getReturnType())).dup();

                                    loadParam(cob, returnSlot,  virtMethodRetType);
                                    cob.invokespecial(toDesc(iMethod.getReturnType()), ConstantDescs.INIT_NAME, sig);
                                    returnSlot = nextlocalVarSlot;
                                    nextlocalVarSlot = storeLocalVar(cob, returnSlot, iMethod.getReturnType());
                                }
                                case enum_long,enum_int, enum_ordinal -> {
                                    cob.getstatic(enumMaps.get(iMethod.getReturnType()));
                                    ParamKeeper.autoBox(cob, argHandler == enum_long ? long.class : int.class, returnSlot);
                                    cob.invokevirtual(CD_HashMap, "get", MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object));
                                    cob.checkcast(toDesc(iMethod.getReturnType()));
                                    returnSlot = nextlocalVarSlot;
                                    nextlocalVarSlot = storeLocalVar(cob, returnSlot, iMethod.getReturnType());
                                }
                            }

                            for (var k : keepers) {
                                //Memory blocks should always be read back there's no need to say @RegArg
                                if (k.classification == memory_block) {
                                    cob.aload(k.storedOrig);
                                    cob.invokevirtual(toDesc(MemoryBlock.class), "readBack", ConstantDescs.MTD_void);
                                }
                            }


                            loadParam(cob, returnSlot, iMethod.getReturnType());
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


    private void addMethodWithArena(ClassBuilder clb, Class<?> interfaceClass, Method iMethod)
    {
        var params = Arrays.stream(iMethod.getParameterTypes()).map(ParamKeeper::classify).map(ParamKeeper::typeForInterfaceMethod).toList();
        var returnKeeper = ParamKeeper.classify(iMethod.getReturnType());
        var methodSig = MethodTypeDesc.of(returnKeeper.typeForInterfaceMethod(), params);

        var paramsVirt = Arrays.stream(iMethod.getParameterTypes())
                .map(ParamKeeper::classify)
                .filter(ParamKeeper::requiredForVirtualCall)
                .map(ParamKeeper::typeForVirtualCall).toList();
        var methodSigVirt = MethodTypeDesc.of(returnKeeper.typeForVirtualCall(), paramsVirt);

        var methodTypeDesc = toDesc(MethodHandle.class);
        var error = toDesc(Error.class);
        boolean isCriticalMethod = iMethod.getAnnotation(Critical.class) != null;

        clb.withMethod(iMethod.getName(), methodSig, ClassFile.ACC_PUBLIC,
                mb -> mb.withCode(cob -> {
                            List<ParamKeeper> keepers = classifyParams(cob, iMethod);
                            var passedArena = keepers.stream().filter(ParamKeeper::isArena).findFirst();
                            keepers = keepers.stream().filter(k -> !k.isArena()).toList();

                            int nextlocalVarSlot = keepers.stream().mapToInt(ParamKeeper::getSlotCount).sum() + 1;

                            int arenaSlot = passedArena.map(paramKeeper -> paramKeeper.stored).orElse(nextlocalVarSlot);
                            var start = cob.newLabel();
                            cob.labelBinding(start);
                            if (passedArena.isEmpty()) {
                                var iv = getCodeTemplate();
                                //I could not get arena creation to work. So I coded it in another class
                                //read that class, take that byte code and insert it here.
                                iv.ifPresent(cob);
                                nextlocalVarSlot = storeLocalVar(cob, arenaSlot, Arena.class);
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
                                    case primitive_array -> {
                                        if (isCriticalMethod) {
                                            //critical methods can access heap memory, which saves a data copy.
                                            //load the array as heap memory
                                            cob.aload(keepers.get(ii).stored);
                                            cob.invokestatic(CD_Utils, "toMS",
                                                    MethodTypeDesc.of(CD_MemorySegment, ParamKeeper.classify(t).typeForInterfaceMethod()));
                                        }
                                        else {
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
                                    case primitive_array2D -> {
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
                                        cob.invokevirtual(thisClassDesc, "storeArr" + t.getComponentType().getSimpleName(),
                                                MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, toDesc(t.getComponentType()).arrayType()));
                                    }
                                    case record_array_ptr -> {
                                        cob.aload(0).aload(arenaSlot).aload(keepers.get(ii).stored);
                                        cob.invokevirtual(thisClassDesc, "storePtr" + t.getComponentType().getSimpleName(),
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
                                        cob.aload(arenaSlot).aload(keepers.get(ii).stored);
                                        cob.invokestatic(CD_Utils, "toPtr",
                                                MethodTypeDesc.of(CD_MemorySegment, CD_Arena, toDesc(MemoryBlock.class)));
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
                                    case error_capture -> {
                                        cob.aload(keepers.get(ii).stored).aload(arenaSlot);
                                        cob.invokevirtual(CD_ErrorCapture, "alloc", MethodTypeDesc.of(CD_MemorySegment, CD_Arena));
                                    }
                                    case enum_long -> {
                                        cob.aload(keepers.get(ii).stored);
                                        cob.invokevirtual(toDesc(keepers.get(ii).classtype), "getCValue",
                                                MethodTypeDesc.of(ConstantDescs.CD_long));
//                                        nextlocalVarSlot++;
                                        keepers.get(ii).stored = nextlocalVarSlot;
                                        keepers.get(ii).type = ParamType.longType;
                                        nextlocalVarSlot = storeLocalVar(cob, nextlocalVarSlot, long.class);
                                        continue;
                                    }
                                    case enum_int -> {
                                        cob.aload(keepers.get(ii).stored);
                                        cob.invokevirtual(toDesc(keepers.get(ii).classtype), "getCValue",
                                                MethodTypeDesc.of(ConstantDescs.CD_int));
//                                        nextlocalVarSlot++;
                                        keepers.get(ii).stored = nextlocalVarSlot;
                                        keepers.get(ii).type = ParamType.intType;
                                        nextlocalVarSlot = storeLocalVar(cob, nextlocalVarSlot, int.class);
                                        continue;
                                    }
                                    case enum_ordinal -> {
                                        cob.aload(keepers.get(ii).stored);
                                        cob.invokevirtual(ConstantDescs.CD_Enum, "ordinal",
                                                MethodTypeDesc.of(ConstantDescs.CD_int));
//                                        nextlocalVarSlot++;
//                                        cob.istore(nextlocalVarSlot);
                                        keepers.get(ii).stored = nextlocalVarSlot;
                                        keepers.get(ii).type = ParamType.intType;
                                        nextlocalVarSlot = storeLocalVar(cob, nextlocalVarSlot, int.class);
                                        continue;
                                    }
                                    case enum_array -> {
                                        cob.aload(arenaSlot).aload(keepers.get(ii).stored);
                                        var enumType = ArgClassification.classify(keepers.get(ii).classtype.getComponentType());
                                        var argType = ConstantDescs.CD_int.arrayType(1);
                                        if (enumType == ArgClassification.enum_long)
                                        {
                                            cob.invokestatic(CD_Utils, "enumToPrimitiveLong",
                                                    MethodTypeDesc.of(ConstantDescs.CD_long.arrayType(1), ConstantDescs.CD_Object.arrayType(1)));
                                            argType = ConstantDescs.CD_long.arrayType(1);
                                        }
                                        else
                                            cob.invokestatic(CD_Utils, "enumToPrimitiveInteger",
                                                    MethodTypeDesc.of(ConstantDescs.CD_int.arrayType(1), ConstantDescs.CD_Object.arrayType(1)));
                                        keepers.get(ii).tmpvar = nextlocalVarSlot;
                                        nextlocalVarSlot = storeLocalVar(cob, nextlocalVarSlot, int[].class);

                                        cob.aload(keepers.get(ii).tmpvar);

                                        if (isRefArgReadBackOnly(keepers.get(ii).annotations))
                                            cob.iconst_1();
                                        else
                                            cob.iconst_0();
                                        cob.invokestatic(CD_Utils, "toMS",
                                                MethodTypeDesc.of(CD_MemorySegment,
                                                        CD_SegmentAllocator, argType, ConstantDescs.CD_boolean));

                                    }
                                    default ->
                                        throw new PassportException(varHandling + " not supported as an argument");
                                }

                                //update all the stored locations of parameters after they've been converted to MemorySegments
//                                nextlocalVarSlot++;
//                                cob.astore(nextlocalVarSlot);
                                keepers.get(ii).stored = nextlocalVarSlot;
                                keepers.get(ii).type = ParamType.addressType;
                                nextlocalVarSlot = storeLocalVar(cob, nextlocalVarSlot, MemorySegment.class);
                            }

                            if (withDebug)
                            {
                                cob.bipush(keepers.size()).anewarray(ConstantDescs.CD_Object);
                                int objArrSlot = nextlocalVarSlot;
                                nextlocalVarSlot = storeLocalVar(cob, objArrSlot, Object.class);
                                int i = 0;
                                for (var k : keepers)
                                {
                                    cob.aload(objArrSlot).loadConstant(i++);
                                    k.loadAutoBoxed(cob);
                                    cob.aastore();
                                }

                                cob.aload(0).loadConstant(iMethod.getName()).aload(objArrSlot);
                                cob.invokestatic(CD_Utils, "preNativeCall", MethodTypeDesc.of(ConstantDescs.CD_void,
                                        toDesc(Passport.class), ConstantDescs.CD_String, ConstantDescs.CD_Object.arrayType()));
                            }


                            cob.aload(0).getstatic(methodHandles.get(iMethod.getName()));

                            //Move parameters to stack for the native function call
                            for (ParamKeeper k : keepers)
                                 k.loadParam(cob);

                            //call the native method
                            cob.invokevirtual(methodTypeDesc, "invokeExact", methodSigVirt );
                            //capture the return of the native method
                            int returnSlot = nextlocalVarSlot;
                            var virtMethodRetType = iMethod.getReturnType();
                            if (isLongEnum(virtMethodRetType))
                                virtMethodRetType = long.class;
                            else if (isIntEnum(virtMethodRetType) || virtMethodRetType.isEnum())
                                virtMethodRetType = int.class;
                            nextlocalVarSlot = storeLocalVar(cob, returnSlot, virtMethodRetType);

                            if (withDebug)
                            {
                                cob.bipush(keepers.size()).anewarray(ConstantDescs.CD_Object);
                                int objArrSlot = nextlocalVarSlot;
                                nextlocalVarSlot = storeLocalVar(cob, objArrSlot, Object.class);
                                int i = 0;
                                for (var k : keepers)
                                {
                                    cob.aload(objArrSlot).loadConstant(i++);
                                    k.loadAutoBoxed(cob);
                                    cob.aastore();
                                }
                                cob.aload(0).loadConstant(iMethod.getName());
                                ParamKeeper.autoBox(cob, virtMethodRetType, returnSlot);
                                cob.aload(objArrSlot);
                                cob.invokestatic(CD_Utils, "postNativeCall", MethodTypeDesc.of(ConstantDescs.CD_void,
                                        toDesc(Passport.class), ConstantDescs.CD_String, ConstantDescs.CD_Object, ConstantDescs.CD_Object.arrayType()));
                            }

                            var argHandler = ArgClassification.classify(iMethod.getReturnType(), null);
                            switch(argHandler)
                            {
                                case string_ -> {
                                    var cdescString = ClassDesc.of(String.class.getName());
                                    var mtd = MethodTypeDesc.of(cdescString, CD_MemorySegment);
                                    loadParam(cob, returnSlot, iMethod.getReturnType());
                                    cob.invokestatic(CD_Utils, "readString", mtd);
                                    returnSlot = nextlocalVarSlot;
                                    nextlocalVarSlot = storeLocalVar(cob, returnSlot, iMethod.getReturnType());
                                }
                                case generic_ptr -> {
                                    var sig = MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment);
                                    cob.new_(toDesc(iMethod.getReturnType())).dup();

                                    loadParam(cob, returnSlot,  virtMethodRetType);
                                    cob.invokespecial(toDesc(iMethod.getReturnType()), ConstantDescs.INIT_NAME, sig);
                                    returnSlot = nextlocalVarSlot;
                                    nextlocalVarSlot = storeLocalVar(cob, returnSlot, iMethod.getReturnType());
                                }
                                case enum_long,enum_int, enum_ordinal -> {
                                    cob.getstatic(enumMaps.get(iMethod.getReturnType()));
                                    ParamKeeper.autoBox(cob, argHandler == enum_long ? long.class : int.class, returnSlot);
                                    cob.invokevirtual(CD_HashMap, "get", MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object));
                                    cob.checkcast(toDesc(iMethod.getReturnType()));
                                    returnSlot = nextlocalVarSlot;
                                    nextlocalVarSlot = storeLocalVar(cob, returnSlot, iMethod.getReturnType());
                                }
                            }

                            boolean globalRefArg = isRefArg(interfaceClass.getAnnotations());

                            //Read back any parameters that were changed by the native method
                            for (ParamKeeper k : keepers)
                            {
                                //Only things annotated with @RefArg need to be read back
                                //Memory blocks should always be read back there's no need to say @RegArg
                                if (!(globalRefArg || isRefArg(k.annotations) || k.classification == memory_block || k.classification == error_capture))
                                    continue;

                                switch (k.classification)
                                {
                                    case record_array -> {
                                        cob.aload(k.storedOrig).loadConstant(0);
                                        cob.aload(0).aload(k.stored).aload(k.storedOrig);
                                        var recType = k.classtype.getComponentType();
                                        cob.invokevirtual(thisClassDesc, "readArr" + recType.getSimpleName(),
                                                MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment, toDesc(recType).arrayType(1)));
                                    }
                                    case record_array_ptr -> {
                                        cob.aload(k.storedOrig).loadConstant(0);
                                        cob.aload(0).aload(k.stored).aload(k.storedOrig);
                                        var recType = k.classtype.getComponentType();
                                        cob.invokevirtual(thisClassDesc, "readPtrs" + recType.getSimpleName(),
                                                MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment, toDesc(recType).arrayType(1)));

                                    }
                                    case string_array -> {
                                        cob.aload(k.stored).aload(k.storedOrig);
                                        cob.invokestatic(CD_Utils, "fromCString",
                                                MethodTypeDesc.of(ConstantDescs.CD_void,
                                                        CD_MemorySegment, ConstantDescs.CD_String.arrayType()));
                                    }
                                    case memory_block -> {
                                        cob.aload(k.storedOrig);
                                        cob.invokestatic(CD_Utils, "readBack",
                                                MethodTypeDesc.of(ConstantDescs.CD_void, toDesc(MemoryBlock.class)));
                                    }
                                    case generic_ptr_array -> {
                                        cob.aload(k.storedOrig).aload(k.stored);
                                        cob.invokestatic(CD_Utils, "toArr",
                                                MethodTypeDesc.of(ConstantDescs.CD_void,
                                                        toDesc(GenericPointer.class).arrayType(), CD_MemorySegment));

                                    }
                                    case primitive_array -> {
                                        // critical methods can access heap memory, we have already passed
                                        // the array as heap memory. If it was manipulated in native code
                                        // then that is automatically reflected i java, so there's no
                                        // need to read it back
                                        if (!isCriticalMethod) {
                                            cob.aload(k.storedOrig).aload(k.stored);
                                            cob.invokestatic(CD_Utils, "toArr",
                                                    MethodTypeDesc.of(ConstantDescs.CD_void,
                                                            k.typeForInterfaceMethod(), CD_MemorySegment));
                                        }
                                    }
                                    case error_capture -> {
                                        cob.aload(k.storedOrig).aload(k.stored);
                                        cob.invokevirtual(CD_ErrorCapture, "readAfter",
                                                MethodTypeDesc.of(ConstantDescs.CD_void, CD_MemorySegment));
                                    }
                                    case enum_array -> {
                                        var etype = k.classtype.getComponentType();
                                        var enumType = ArgClassification.classify(etype);
                                        var primitiveType = enumType == enum_long ? ConstantDescs.CD_long.arrayType(1) : ConstantDescs.CD_int.arrayType(1);

                                        cob.aload(k.tmpvar).aload(k.stored);
                                        cob.invokestatic(CD_Utils, "toArr", MethodTypeDesc.of(ConstantDescs.CD_void, primitiveType, CD_MemorySegment));
                                        cob.aload(k.tmpvar).aload(k.storedOrig);
                                        cob.getstatic(enumMaps.get(etype));
                                        if (enumType == enum_long)
                                        {
                                            cob.invokestatic(CD_Utils, "primitiveToEnumLong",
                                                    MethodTypeDesc.of(ConstantDescs.CD_void, primitiveType,
                                                            ConstantDescs.CD_Object.arrayType(1), CD_HashMap));
                                        }
                                        else {
                                            cob.invokestatic(CD_Utils, "primitiveToEnumInteger",
                                                    MethodTypeDesc.of(ConstantDescs.CD_void, primitiveType,
                                                            ConstantDescs.CD_Object.arrayType(1), CD_HashMap));
                                        }
                                    }
                                }
                            }

                            if (passedArena.isEmpty()) {
                                cob.aload(arenaSlot);
                                cob.invokeinterface(CD_Arena, "close", ConstantDescs.MTD_void);
                            }

                            loadParam(cob, returnSlot, iMethod.getReturnType());
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

    private Optional<CodeElement> getCodeTemplate()
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try {
            InputStream in = Utils.class.getResourceAsStream("Utils.class");
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
        return Arrays.stream(m.getParameterTypes()).anyMatch(c -> c.isArray() || c.isRecord()
                || c.equals(arena) || c.equals(String.class) || c.equals(ErrorCapture.class));
    }

    private void parseClass()
    {
        var classModel = ClassFile.of().parse(classBytes);

        for (var mm : classModel.methods())
        {
            if (!mm.methodName().stringValue().equals("toRetEnum"))
                continue;
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

    static void println(CodeBuilder cob, String s)
    {
        cob.getstatic(toDesc(System.class), "out", toDesc(PrintStream.class)).ldc(s)
                .invokevirtual(toDesc(PrintStream.class), "println", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String));
    }
}
