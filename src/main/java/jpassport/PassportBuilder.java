package jpassport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

public class PassportBuilder<T extends Passport> extends ClassLoader{

    private String fullName;
    private byte[] classBytes;

    private static int Class_ID = 1; //Used to make unique package names

    public PassportBuilder(Class<T> interfaceClass)
    {
        this(interfaceClass, "jpassport.called_" + Class_ID++, interfaceClass.getSimpleName() + "_impl");
    }

    ClassDesc thisClassDesc;
    private HashMap<String, FieldRefEntry> methodHandles = new HashMap<>();

    public PassportBuilder(Class<T> interfaceClass, String packageName, String className)
    {
        thisClassDesc = ClassDesc.of(packageName, className);
        List<Method> interfaceMethods = PassportFactory.getDeclaredMethods(interfaceClass);

        classBytes = ClassFile.of().build(thisClassDesc, clb ->
        {
            var entry = ConstantPoolBuilder.of().classEntry(ClassDesc.of("jpassport", "Passport"));
            var mainInterface = ConstantPoolBuilder.of().classEntry(ClassDesc.of(interfaceClass.getName()));
            clb.withInterfaces(entry, mainInterface);
            clb.withFlags(ClassFile.ACC_PUBLIC);

            var classDescHM = ClassDesc.ofInternalName("java/util/HashMap");
            var descMap = ClassDesc.ofInternalName("java/util/Map");
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

            var methodTypeDesc = ClassDesc.ofInternalName("java/lang/invoke/MethodHandle");
            for (Method m : interfaceMethods)
            {
                String fieldName = "m_" + m.getName();
                clb.withField(fieldName, methodTypeDesc, ClassFile.ACC_PRIVATE);
                nte = clb.constantPool().nameAndTypeEntry(fieldName, methodTypeDesc);
                var fieldRefEntry = clb.constantPool().fieldRefEntry(clb.constantPool().classEntry(thisClassDesc), nte);
                methodHandles.put(fieldName, fieldRefEntry);
            }

            var cdObject = ClassDesc.ofInternalName("java/lang/Object");
            clb.withMethod("init",  MethodTypeDesc.of(ConstantDescs.CD_void),
                    ClassFile.ACC_PUBLIC, mb -> mb.withCode(
                       cob -> {
                           var start = cob.newBoundLabel();

//                           cob.labelBinding(start);
                           for (String name : methodHandles.keySet())
                           {
                               String fieldName = "m_" + name;
                               cob.ldc(name.substring(2)) // clip off "m_"
                                       .astore(1)
                                       .aload(0)
                                       .aload(0)
                                       .getfield(methodsfield)
                                       .aload(1)
                                       .invokevirtual(classDescHM, "get", MethodTypeDesc.of(cdObject, cdObject))
                                       .typeCheckInstruction(Opcode.CHECKCAST, ClassDesc.ofInternalName("java/lang/invoke/MethodHandle"))
                                        .putfield(methodHandles.get(name));
                           }
                           var end = cob.endLabel();
                           cob.localVariable(1, "key", ClassDesc.ofInternalName("java/lang/String"), start, end);
                           cob.return_();
                       }
                    ));

            for (Method m : interfaceMethods)
            {
                if (needsArena(m))
                    addMethodWithArena(clb, m);
                else
                    addMethod(clb, m);
            }
        }
        );

        fullName = packageName + "." + className;
    }

    private boolean needsArena(Method m)
    {
        var arena = Arena.ofConfined().getClass();
        return Arrays.stream(m.getParameterTypes()).anyMatch(c -> c.isArray() || c.isRecord() || c.equals(arena));
    }

    public T build(Map<String, MethodHandle> methods) throws Throwable
    {
        Class<? extends T> foreignImpl = (Class<? extends T>)defineClass(fullName, classBytes, 0, classBytes.length);

        return foreignImpl.getDeclaredConstructor(methods.getClass()).newInstance(methods);
    }

    private ClassBuilder addMethod(ClassBuilder clb, Method iMethod)
    {
        var params = Arrays.stream(iMethod.getParameterTypes()).map(PassportBuilder::toDesc).toList();
        var methodSig = MethodTypeDesc.of(toDesc(iMethod.getReturnType()), params);
        var methodTypeDesc = ClassDesc.ofInternalName("java/lang/invoke/MethodHandle");
        clb.withMethod(iMethod.getName(), methodSig, ClassFile.ACC_PUBLIC,
                mb -> mb.withCode(cob -> {
                            List<ParamKeeper> keepers = classifyParams(cob, iMethod);
                            var start = cob.newLabel();
                            cob.labelBinding(start);

                            cob.aload(0)
                            .getfield(methodHandles.get("m_" + iMethod.getName()));
                            int idx = 0;
                            for (var k : keepers)
                                k.loadParam(cob);

                            cob.invokevirtual(methodTypeDesc, "invokeExact", methodSig );
                            storeParam(cob, idx, iMethod.getReturnType());
                            loadParam(cob, idx, iMethod.getReturnType());
                            returnParam(cob, iMethod.getReturnType());
                            var error = ClassDesc.ofInternalName("java/lang/Error");
                            var end = cob.newLabel();
                            cob.labelBinding(end);
                            var handler = cob.newLabel();
                            cob.labelBinding(handler)
                            .astore(5)
                            .new_(error)
                            .dup()
                            .aload(5)
                            .invokespecial(error, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ClassDesc.ofInternalName("java/lang/Throwable")), false)
                            .throwInstruction();
                            var handlerEnd = cob.newLabel();
                            cob.labelBinding(handlerEnd);
                            cob.exceptionCatchAll(start, end, handler);
                        }
                        ));
        return clb;
    }

    enum ParamType
    {
        doubleType(2), longType(2),
        floatType(1),
        intType(1), shortType(1), byteType(1), charType(1),
        addressType(1);

        int slot_count = 1;

        ParamType(int slots)
        {
            slot_count = slots;
        }
    }

    static class ParamKeeper
    {
        ParamType type;
        int stored;

        ParamKeeper(ParamType t, int slot)
        {
            type = t;
            stored = slot;
        }

        public String toString()
        {
            return type + "(" + stored + ")";
        }

        void loadParam(CodeBuilder cob)
        {
            switch(type)
            {
                case doubleType:
                    cob.dload(stored);
                    break;
                case longType:
                    cob.lload(stored);
                    break;
                case floatType:
                    cob.fload(stored);
                    break;
                case intType:
                    cob.iload(stored);
                    break;
                case shortType:
                    cob.iload(stored);
                    break;
                case addressType:
                    cob.aload(stored);
                    break;
            }
        }
    }

    private List<ParamKeeper> classifyParams(CodeBuilder cob, Method iMethod)
    {
        ArrayList<ParamKeeper> keepers = new ArrayList<>();
        int slot = 0;
        for (Class c : iMethod.getParameterTypes())
        {
            if (c.equals(double.class))
                keepers.add(new ParamKeeper(ParamType.doubleType, cob.parameterSlot(slot)));
            else if (c.equals(long.class))
                keepers.add(new ParamKeeper(ParamType.longType, cob.parameterSlot(slot)));
            else if (c.equals(float.class))
                keepers.add(new ParamKeeper(ParamType.floatType, cob.parameterSlot(slot)));
            else if (c.equals(int.class))
                keepers.add(new ParamKeeper(ParamType.intType, cob.parameterSlot(slot)));
            else if (c.equals(short.class))
                keepers.add(new ParamKeeper(ParamType.shortType, cob.parameterSlot(slot)));
            else
                keepers.add(new ParamKeeper(ParamType.addressType, cob.parameterSlot(slot)));
            slot++;
        }
        return keepers;
    }

    private ClassBuilder addMethodWithArena(ClassBuilder clb, Method iMethod)
    {

        var params = Arrays.stream(iMethod.getParameterTypes()).map(PassportBuilder::toDesc).toList();
        var paramsVirt = Arrays.stream(iMethod.getParameterTypes()).map(PassportBuilder::toDescVirt).toList();
        var methodSig = MethodTypeDesc.of(toDesc(iMethod.getReturnType()), params);
        var methodSigVirt = MethodTypeDesc.of(toDesc(iMethod.getReturnType()), paramsVirt);
        var methodTypeDesc = ClassDesc.ofInternalName("java/lang/invoke/MethodHandle");
        var arenaDesc = ClassDesc.ofInternalName("java/lang/foreign/Arena");
        var MemorySegment = ClassDesc.ofInternalName("java/lang/foreign/MemorySegment");
        var error = ClassDesc.ofInternalName("java/lang/Error");

        clb.withMethod(iMethod.getName(), methodSig, ClassFile.ACC_PUBLIC,
                mb -> mb.withCode(cob -> {
                            List<ParamKeeper> keepers = classifyParams(cob, iMethod);
                            int used = Arrays.stream(iMethod.getParameterTypes()).mapToInt(this::paramSize).sum();
                            used += 1;
                            int arenaSlot = used++;
                            var start = cob.newLabel();
                            cob.labelBinding(start);
                            var iv = getCodeTemplate();
                            if (iv.isPresent())
                                cob.accept(iv.get());
                            cob.astore(arenaSlot);
                            var startAutoClose = cob.newLabel();
                            cob.labelBinding(startAutoClose);

                            int ii = -1;
                            for (Class t : iMethod.getParameterTypes()) {
                                ii++;
                                if (!t.isArray())
                                    continue;

                                cob.aload(arenaSlot).aload(keepers.get(ii).stored).iconst_0();
                                cob.invokestatic(ClassDesc.ofInternalName("jpassport/Utils"), "toMS",
                                        MethodTypeDesc.of(MemorySegment,
                                                arenaDesc, toDesc(t), ConstantDescs.CD_boolean));
                                used++;
                                cob.astore(used);
                                keepers.get(ii).stored = used;
                                keepers.get(ii).type = ParamType.addressType;
                            }
                            cob.aload(0).getfield(methodHandles.get("m_" + iMethod.getName()));

                            //after here is not updated
                            for (ParamKeeper k : keepers)
                            {
                                if (k.type == ParamType.addressType)
                                {
                                    var mtd = MethodTypeDesc.of(MemorySegment, MemorySegment);
                                    k.loadParam(cob);
                                    cob.invokestatic(ClassDesc.ofInternalName("jpassport/Utils"), "toAddr", mtd);
                                    used++;
                                    cob.astore(used).aload(used);
                                    k.stored = used;
                                }
                                else
                                    k.loadParam(cob);
                            }

                            cob.invokevirtual(methodTypeDesc, "invokeExact", methodSigVirt );
                            int newUsed = storeParam(cob, used, iMethod.getReturnType());
                            cob.aload(arenaSlot);
                            cob.invokeinterface(ClassDesc.ofInternalName("java/lang/foreign/Arena"), "close", MethodTypeDesc.of(ConstantDescs.CD_void));
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
                                    .invokespecial(error, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ClassDesc.ofInternalName("java/lang/Throwable")), false)
                                    .throwInstruction();
                            var handlerEnd = cob.newLabel();
                            cob.labelBinding(handlerEnd);
                            cob.exceptionCatchAll(start, end, handler);
                        }
                ));
        return clb;
    }



    private int paramSize(Class c)
    {
        if (c.equals(double.class) || c.equals(long.class))
            return 2;
        return 1;
    }

    private CodeBuilder loadParam(CodeBuilder cob, int idx, Class c)
    {
        if (c.equals(double.class))
            cob.dload(idx);
        else if (c.equals(long.class))
            cob.lload(idx);
        else if (c.equals(float.class))
            cob.fload(idx);
        else if (c.equals(int.class) || c.equals(short.class))
            cob.iload(idx );
        else
            cob.aload(idx);
        return cob;
    }

    private int storeParam(CodeBuilder cob, int idx, Class c)
    {
        if (c.equals(double.class))
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
        else if (c.equals(int.class) || c.equals(short.class))
            cob.istore(idx);
        else
            cob.astore(idx);
        return idx+1;
    }

    private CodeBuilder returnParam(CodeBuilder cob, Class c)
    {
        if (c.equals(double.class))
            cob.dreturn();
        else if (c.equals(long.class))
            cob.lreturn();
        else if (c.equals(float.class))
            cob.freturn();
        else if (c.equals(int.class) || c.equals(short.class))
            cob.ireturn();
        else
            cob.areturn();
        return cob;
    }

    public static ClassDesc toDesc(Class c)
    {
        if (c.isArray())
        {
            var atype = c.getComponentType();
            if (atype.equals(double.class))
                return ConstantDescs.CD_double.arrayType();

        }
        if (c.equals(double.class))
            return ConstantDescs.CD_double;
        else if (c.equals(long.class))
            return ConstantDescs.CD_long;
        else if (c.equals(int.class))
            return ConstantDescs.CD_int;
        else if (c.equals(short.class))
            return ConstantDescs.CD_short;
        else if (c.equals(byte.class))
            return ConstantDescs.CD_byte;
        else if (c.equals(char.class))
            return ConstantDescs.CD_char;
        return ClassDesc.of(c.getName());
    }

    public static ClassDesc toDescVirt(Class c)
    {
        if (c.isArray())
        {
            return ClassDesc.ofInternalName("java/lang/foreign/MemorySegment");
        }
        if (c.equals(double.class))
            return ConstantDescs.CD_double;
        else if (c.equals(long.class))
            return ConstantDescs.CD_long;
        else if (c.equals(int.class))
            return ConstantDescs.CD_int;
        else if (c.equals(short.class))
            return ConstantDescs.CD_short;
        else if (c.equals(byte.class))
            return ConstantDescs.CD_byte;
        else if (c.equals(char.class))
            return ConstantDescs.CD_char;
        return ClassDesc.of(c.getName());
    }

    private Optional<CodeElement> getCodeTemplate()
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try {
            InputStream in = PassportBuilder.class.getResourceAsStream("Utils.class");
            byte data[] = new byte[4096];
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

            for (CodeElement ce : mm.code().get().elements())
            {
                if (ce.toString().contains("ofConfined"))
                    return Optional.of(ce);
            }
        }

        return Optional.empty();
    }

}
