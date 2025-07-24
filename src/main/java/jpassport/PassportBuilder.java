package jpassport;

import jpassport.codebuilder.ParamKeeper;
import jpassport.codebuilder.ParamType;

import java.awt.geom.Area;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.*;

import static jpassport.PassportWriter.*;
import static jpassport.Utils.toDesc;

public class PassportBuilder<T extends Passport> extends ClassLoader{

    private final String fullName;
    private final byte[] classBytes;

    private static int Class_ID = 1; //Used to make unique package names

    public PassportBuilder(Class<T> interfaceClass)
    {
        this(interfaceClass, "jpassport.called_" + Class_ID++, interfaceClass.getSimpleName() + "_impl");
    }

    ClassDesc thisClassDesc;
    private final HashMap<String, FieldRefEntry> methodHandles = new HashMap<>();

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
                                    .invokevirtual(thisClassDesc, "init", ConstantDescs.MTD_void)
                                    .return_()));

            var methodTypeDesc = toDesc(MethodHandle.class);
            for (Method m : interfaceMethods)
            {
                String fieldName = "m_" + m.getName();
                clb.withField(fieldName, methodTypeDesc, ClassFile.ACC_PRIVATE);
                nte = clb.constantPool().nameAndTypeEntry(fieldName, methodTypeDesc);
                var fieldRefEntry = clb.constantPool().fieldRefEntry(clb.constantPool().classEntry(thisClassDesc), nte);
                methodHandles.put(fieldName, fieldRefEntry);
            }

            var cdObject = toDesc(Object.class);
            clb.withMethod("init",  MethodTypeDesc.of(ConstantDescs.CD_void),
                    ClassFile.ACC_PUBLIC, mb -> mb.withCode(
                       cob -> {
                           var start = cob.newBoundLabel();

//                           cob.labelBinding(start);
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
                           cob.localVariable(1, "key", toDesc(String.class), start, end);
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

            System.out.println();
        }
        );

        fullName = packageName + "." + className;
    }

    private boolean needsArena(Method m)
    {
        Class<? extends Arena> arena;
        try (var a = Arena.ofConfined())
        {
            arena = a.getClass();
        }
        return Arrays.stream(m.getParameterTypes()).anyMatch(c -> c.isArray() || c.isRecord() || c.equals(arena) || c.equals(String.class));
    }

    public T build(Map<String, MethodHandle> methods) throws Throwable
    {
        Class<? extends T> foreignImpl = (Class<? extends T>)defineClass(fullName, classBytes, 0, classBytes.length);

        try {
            return foreignImpl.getDeclaredConstructor(methods.getClass()).newInstance(methods);
        }
        catch (VerifyError ve)
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
                                            MethodTypeDesc.of(toDesc(MemorySegment.class), toDesc(Arena.class)));
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
                                    cob.invokevirtual(toDesc(MemoryBlock.class), "readBack",
                                            MethodTypeDesc.of(ConstantDescs.CD_void));
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
                            .invokespecial(error, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, toDesc(Throwable.class)), false)
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
        var arenaDesc = toDesc(Arena.class);
        var MemorySegment = toDesc(MemorySegment.class);
        var error = toDesc(Error.class);

        clb.withMethod(iMethod.getName(), methodSig, ClassFile.ACC_PUBLIC,
                mb -> mb.withCode(cob -> {
                            List<ParamKeeper> keepers = classifyParams(cob, iMethod);
                            int used = Arrays.stream(iMethod.getParameterTypes()).mapToInt(this::paramSize).sum();
                            used += 1;
                            int arenaSlot = used++;
                            var start = cob.newLabel();
                            cob.labelBinding(start);
                            var iv = getCodeTemplate();
                            //I could not get arena creation to work. So I coded it in another class
                            //read that class, take that byte code and insert it here.
                            iv.ifPresent(cob::accept);
                            cob.astore(arenaSlot);
                            var startAutoClose = cob.newLabel();
                            cob.labelBinding(startAutoClose);

                            int ii = -1;
                            //loop converts parameters to native memory if required
                            for (Class<?> t : iMethod.getParameterTypes()) {
                                ii++;

                                if (t.isArray()) {
                                    if (t.getComponentType().isRecord())
                                    {
                                        cob.aload(arenaSlot).aload(keepers.get(ii).stored);
                                        cob.invokestatic(toDesc(Utils.class), "storeStruct",
                                                MethodTypeDesc.of(MemorySegment,
                                                        arenaDesc, ConstantDescs.CD_Object.arrayType()));
                                    }
                                    else if (isGenericPtr(t.getComponentType()))
                                    {
                                        cob.aload(arenaSlot).aload(keepers.get(ii).stored).iconst_0();
                                        cob.invokestatic(toDesc(Utils.class), "toMS",
                                                MethodTypeDesc.of(MemorySegment,
                                                        toDesc(SegmentAllocator.class), toDesc(GenericPointer.class).arrayType(1), ConstantDescs.CD_boolean));
                                    }
                                    else if (t.getComponentType().equals(String.class))
                                    {
                                        cob.aload(keepers.get(ii).stored).aload(arenaSlot);
                                        cob.invokestatic(toDesc(Utils.class), "toCString",
                                                MethodTypeDesc.of(MemorySegment,
                                                        ClassDesc.of(String.class.getName()).arrayType(), arenaDesc));
                                    }
                                    else {
                                        if (isArrayOfPrimitives(t) || is2DArrayOfPrimitives(t))
                                        {
                                            if (isPtrPtrArg(keepers.get(ii).annotations))
                                            {
                                                cob.aload(arenaSlot).aload(keepers.get(ii).stored);
                                                cob.invokestatic(toDesc(Utils.class), "toPtrPTrMS",
                                                        MethodTypeDesc.of(MemorySegment,
                                                                toDesc(SegmentAllocator.class), ParamKeeper.classify(t).typeForInterfaceMethod()));
                                            }
                                            else {
                                                //assumes an array of primitives
                                                cob.aload(arenaSlot).aload(keepers.get(ii).stored).iconst_0();
                                                cob.invokestatic(toDesc(Utils.class), "toMS",
                                                        MethodTypeDesc.of(MemorySegment,
                                                                toDesc(SegmentAllocator.class), ParamKeeper.classify(t).typeForInterfaceMethod(), ConstantDescs.CD_boolean));
                                            }
                                        }
                                    }
                                }
                                else if (t.isRecord())
                                {
                                    cob.aload(arenaSlot).aload(keepers.get(ii).stored);
                                    cob.invokestatic(toDesc(Utils.class), "storeStruct",
                                            MethodTypeDesc.of(MemorySegment,
                                                    arenaDesc, ConstantDescs.CD_Object));
                                }
                                else if (t.equals(String.class))
                                {
                                    cob.aload(keepers.get(ii).stored).aload(arenaSlot);
                                    cob.invokestatic(toDesc(Utils.class), "toCString",
                                            MethodTypeDesc.of(MemorySegment,
                                                    ClassDesc.of(String.class.getName()), arenaDesc));
                                } else if (t.equals(MemoryBlock.class)) {
                                    cob.aload(keepers.get(ii).stored).aload(arenaSlot);
                                    cob.invokevirtual(toDesc(MemoryBlock.class), "toPtr",
                                            MethodTypeDesc.of(MemorySegment, arenaDesc));
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
                                    var mtd = MethodTypeDesc.of(MemorySegment, MemorySegment);
                                    k.loadParam(cob);
                                    cob.invokestatic(toDesc(Utils.class), "toAddr", mtd);
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
                                var mtd = MethodTypeDesc.of(cdescString, MemorySegment);
                                loadParam(cob, used, iMethod.getReturnType());
                                cob.invokestatic(toDesc(Utils.class), "readString", mtd);
                                used++;
                                newUsed = storeParam(cob, used, iMethod.getReturnType());
                            } else if (isGenericPtr(iMethod.getReturnType())) {

                                var sig = MethodTypeDesc.of(ConstantDescs.CD_void, toDesc(MemorySegment.class));
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
                                    cob.aload(k.stored).aload(k.storedOrig);
                                    cob.invokestatic(toDesc(Utils.class), "readBackStruct",
                                            MethodTypeDesc.of(ConstantDescs.CD_void,
                                                    MemorySegment, ConstantDescs.CD_Object.arrayType()));
                                }
                                else if (k.classtype.getComponentType().equals(String.class))
                                {
                                    cob.aload(k.stored).aload(k.storedOrig);
                                    cob.invokestatic(toDesc(Utils.class), "fromCString",
                                            MethodTypeDesc.of(ConstantDescs.CD_void,
                                                    MemorySegment, toDesc(String.class).arrayType()));

                                }
                                else if (k.classtype.equals(MemoryBlock.class)) {
                                    cob.aload(k.storedOrig);
                                    cob.invokevirtual(toDesc(MemoryBlock.class), "readBack",
                                            MethodTypeDesc.of(ConstantDescs.CD_void));
                                }
                                else if (isGenericPtr(k.classtype.getComponentType()))
                                {
                                    cob.aload(k.storedOrig).aload(k.stored);
                                    cob.invokestatic(toDesc(Utils.class), "toArr",
                                            MethodTypeDesc.of(ConstantDescs.CD_void,
                                                    Utils.toDesc(GenericPointer.class).arrayType(), MemorySegment));

                                }
                                else {//primitive array
                                    //Loads the arguments for toMS()
                                    cob.aload(k.storedOrig).aload(k.stored);
                                    cob.invokestatic(toDesc(Utils.class), "toArr",
                                            MethodTypeDesc.of(ConstantDescs.CD_void,
                                                    k.typeForInterfaceMethod(), MemorySegment));
                                }

                            }

                            cob.aload(arenaSlot);
                            cob.invokeinterface(toDesc(Arena.class), "close", MethodTypeDesc.of(ConstantDescs.CD_void));
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
                                    .invokespecial(error, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, toDesc(Throwable.class)), false)
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

    private void loadParam(CodeBuilder cob, int idx, Class<?> c)
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

    private int storeParam(CodeBuilder cob, int idx, Class<?> c)
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

    public static ClassDesc toDesc(Class<?> c)
    {
        return Utils.toDesc(c);
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

    private void parseClass()
    {
        var classModel = ClassFile.of().parse(classBytes);

        for (var mm : classModel.methods())
        {
//            if (mm.methodName().equals("templatedMethod"))
            System.out.println("============================================");
            System.out.println(mm.methodName());
            System.out.println("Signature: " + mm.methodType());

            if (!mm.methodName().equalsString("mallocDoubles"))
                continue;

            CodeModel code = mm.code().get();

            for (CodeElement ce : code.elementList())
            {
                System.out.println(ce);
            }
        }

    }
}
