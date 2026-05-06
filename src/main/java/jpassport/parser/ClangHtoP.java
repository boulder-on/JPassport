package jpassport.parser;

import jpassport.PassportFactory;
import jpassport.parser.clang.types.*;
import jpassport.pointers.FunctionPtr;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static jpassport.parser.clang.types.CXCursorKind.*;
import static jpassport.parser.clang.types.CXTypeKind.*;

public class ClangHtoP implements AutoCloseable{
    FunctionPtr typedefVisitor;
    FunctionPtr structUnionVisitor;
    FunctionPtr enumVisitor;
    FunctionPtr functionVisitor;
    FunctionPtr visitFunctionParamPtr;
    FunctionPtr visitStructFieldfunctionPtr;
    FunctionPtr visitEnumfunctionPtr;
    static Arena tmpArena = Arena.global();
    static clangParser clang;

    List<CFunction> allFunctions = new ArrayList<>();
    List<CRecord> allRecords = new ArrayList<>();
    List<CEnum> allEnums = new ArrayList<>();

    public record CFunction(ArgDef returnType,
                     String name,
                     List<ArgDef> parameters,
                     String CCode) {
    }

    record Type(String name, boolean isNative, String ptr, boolean ptrRequired){
        public Type(String name, boolean isNative, String ptr)
        {
            this(name, isNative, ptr, false);
        }
    }
    record CEnum(String name, List<CEnumValue> values, String CCode) {
    }

    record CEnumValue (
            String name,
            Long value,
            boolean forceLong){}

    record CAlias(String alias, String replace) {

        String clean(String orig) {
            String regEx = "\\s*" + alias.replace(" ", "\\s+") + "(\\*+\\s+|\\s+)";
            Pattern p = Pattern.compile(regEx, Pattern.DOTALL);

            Matcher m = p.matcher(orig);
            while (m.find()) {
                String found = m.group(0);
                int ptrCount = found.length() - found.replace("*", "").length();

                if (ptrCount == 0)
                    try {
                        orig = orig.replace(found, " " + replace + " ");
                    }
                    catch(Throwable th) {
                        th.printStackTrace();
                    }
                else {
                    String ptrs = "";
                    for (int i = 0; i < ptrCount; i++)
                        ptrs = ptrs + "*";

                    orig = orig.replace(found, " " + replace + ptrs + " ");

                }

            }

            return orig;
        }
    }

    public record ArgDef(String type, String name, String ptr, boolean error, String origText){}

    record CRecord(String name, String kind, List<ArgDef> fields, String CCode) {

        //in many C APIs you will find code like:
        // typedef struct MyStruct MyStruct;
        // This allows the library maker to hide the fields of the struct
        //These typedefs are always used as pointers only and can therefore
        //be converted to GenericPointer types.
        public boolean isEmptyStruct() {
            return this.fields.isEmpty();
        }
    }

    static String[] ignoreKeywords = new String[] {
            "const",
//            "__stdcall ",
            "typedef ",
            "volatile ",
            "unsigned ",
            "signed ",
            "__unaligned ",
            "enum ",
//            "IN ",
            "OUT ",
            "_In_ ",
            "_Out_ ",
            "_Outptr_ ",
            "_Inout_ ",
            "_In_opt_",
            "_Inout_opt_",
            "static ",
            "_Reserved_",
            "FAR"
    };


    static final HashMap<String, Type> typeDefToNative = new HashMap<>();

    static {
        typeDefToNative.put("char", new Type("char", true, ""));
        typeDefToNative.put("uint8_t", new Type("byte", true, ""));
        typeDefToNative.put("uint_least8_t", new Type("byte", true, ""));
        typeDefToNative.put("uint_fast8_t", new Type("byte", true, ""));
        typeDefToNative.put("short", new Type("short", true, ""));
        typeDefToNative.put("int", new Type("int", true, ""));
        typeDefToNative.put("long", new Type("long", true, ""));
        typeDefToNative.put("time_t", new Type("long", true, ""));
        typeDefToNative.put("float", new Type("float", true, ""));
        typeDefToNative.put("double", new Type("double", true, ""));
        typeDefToNative.put("size_t", new Type("long", true, ""));
        typeDefToNative.put("uint32_t", new Type("int", true, ""));
        typeDefToNative.put("uint64_t", new Type("long", true, ""));

        typeDefToNative.put("HANDLE", new Type("MemorySegment", true, ""));

        typeDefToNative.put("PVOID", new Type("byte", true, "*", true));
        typeDefToNative.put("LPVOID", new Type("byte", true, "*", true));
        typeDefToNative.put("LPCSTR", new Type("String", true, ""));
        typeDefToNative.put("LPCWSTR", new Type("char", true, "*", true));
        typeDefToNative.put("PWSTR", new Type("char", true, "*", true));
        typeDefToNative.put("LPWSTR", new Type("String", true, ""));
        typeDefToNative.put("DWORD", new Type("long", true, "", false));
        typeDefToNative.put("DWORD64", new Type("long", true, "", false));
        typeDefToNative.put("PDWORD", new Type("long", true, "*", true));
        typeDefToNative.put("LPDWORD", new Type("long", true, "*", true));
        typeDefToNative.put("DWORD_PTR", new Type("long", true, "*", true));
        typeDefToNative.put("LARGE_INTEGER", new Type("long", true, "", false));

        typeDefToNative.put("LONG", new Type("long", true, ""));
        typeDefToNative.put("ULONG", new Type("long", true, ""));
        typeDefToNative.put("LONG64", new Type("long", true, ""));
        typeDefToNative.put("ULONG64", new Type("long", true, ""));
        typeDefToNative.put("LONGLONG", new Type("long", true, ""));
        typeDefToNative.put("ULONGLONG", new Type("long", true, ""));
        typeDefToNative.put("BOOLEAN", new Type("boolean", true, ""));
        typeDefToNative.put("BOOL", new Type("boolean", true, ""));
        typeDefToNative.put("bool", new Type("boolean", true, ""));
        typeDefToNative.put("PBOOL", new Type("boolean", true, "*"));
        typeDefToNative.put("BYTE", new Type("byte", true, ""));
        typeDefToNative.put("LPBYTE", new Type("byte", true, "[]"));

        typeDefToNative.put("INT32", new Type("int", true, ""));
        typeDefToNative.put("UINT32", new Type("int", true, ""));
        typeDefToNative.put("UINT8", new Type("byte", true, ""));
        typeDefToNative.put("UINT64", new Type("long", true, ""));
        typeDefToNative.put("SHORT", new Type("short", true, ""));
        typeDefToNative.put("BSTR", new Type("String", true, ""));
        typeDefToNative.put("WCHAR", new Type("String", true, ""));
        typeDefToNative.put("UCHAR", new Type("char", true, ""));
        typeDefToNative.put("LPSTR", new Type("String", true, ""));
        typeDefToNative.put("UINT_PTR", new Type("int", true, "*", true));
    }

    private static final List<CAlias> commonAliases = new ArrayList(List.of(
            new CAlias("__stdcall", "FunctionPtr"),
            new CAlias("__RPC_USER", "FunctionPtr"),

            new CAlias("unsigned char", "byte"),
            new CAlias("unsigned short", "short"),
            new CAlias("unsigned int", "int"),
            new CAlias("unsigned long", "long"),
            new CAlias("unsigned long long", "long"),
            new CAlias("unsigned long long int", "long"),
            new CAlias("long long unsigned int", "long"),
            new CAlias("short int", "short"),
            new CAlias("long long int", "long"),
            new CAlias("long long", "long"),
            new CAlias("long double", "double"),
            new CAlias("size_t", "long"),
            new CAlias("__int32", "int"),
            new CAlias("__uint32", "int"),
            new CAlias("__int16", "short"),
            new CAlias("__uint16", "short"),
            new CAlias("__uint64", "long"),
            new CAlias("__int64", "long")
    ));

    CXIndex Idx;
    CXTranslationUnit tu;

    public ClangHtoP(Path toPCH) throws Throwable
    {
        initVisitors();
        String libName = System.getProperty("os.name").startsWith("Windows") ? "libclang" : "clang";
        System.setProperty("java.library.path", "C:\\Program Files\\LLVM\\bin");
        System.setProperty("java.lib.path", "C:\\Program Files\\LLVM\\bin");
//        System.loadLibrary(libName);

        System.setProperty("jpassport.build.home", "out/testing");
        clang = PassportFactory.link_written(libName, clangParser.class);

        Idx = clang.clang_createIndex(1, 1);
        tu = clang.clang_createTranslationUnit(Idx, toPCH.toAbsolutePath().toString());
    }

    public ClangHtoP(String hName) throws Throwable
    {
        initVisitors();
        String libName = System.getProperty("os.name").startsWith("Windows") ? "libclang" : "clang";
        System.setProperty("java.library.path", "C:\\Program Files\\LLVM\\bin");
        System.setProperty("java.lib.path", "C:\\Program Files\\LLVM\\bin");


        System.setProperty("jpassport.build.home", "out/testing");
        clang = PassportFactory.link_written(libName, clangParser.class);

        String contents = "#include \"" + hName + "\"\n";
        CXUnsavedFile [] unsavedFiles = new CXUnsavedFile[] {new CXUnsavedFile(hName + ".c", contents, contents.length())};

        Idx = clang.clang_createIndex(1, 1);
//        tu = clang.clang_createTranslationUnit(Idx, toPCH.toAbsolutePath().toString());
        String[] args = {
                "-IC:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0",
                "-IC:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\cppwinrt",
                "-IC:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\cppwinrt\\winrt",
                "-IC:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\um",
                "-IC:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\shared",
                "-IC:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.22621.0\\ucrt",
                "-IC:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\VC\\Tools\\MSVC\\14.40.33807\\include"
        };


        tu = clang.clang_createTranslationUnitFromSourceFile(Idx, hName + ".c", args.length, args, 1, unsavedFiles);
    }

    public void close()
    {
        clang.clang_disposeTranslationUnit(tu);
        clang.clang_disposeIndex(Idx);

    }


    void     initVisitors()
    {
        typedefVisitor = PassportFactory.createCallback(this, "typdefVisitor");
        structUnionVisitor = PassportFactory.createCallback(this, "structUnionVisitor");
        enumVisitor = PassportFactory.createCallback(this, "enumVisitor");
        visitEnumfunctionPtr = PassportFactory.createCallback(this, "enum_constant_visitor");
        visitStructFieldfunctionPtr = PassportFactory.createCallback(this, "struct_field_visitor");

        functionVisitor = PassportFactory.createCallback(this, "functionVisitor");
        visitFunctionParamPtr = PassportFactory.createCallback(this, "functionParamVisitor");

    }

    public void parseClang() {
        var cursor = clang.clang_getTranslationUnitCursor(tu);
        clang.clang_visitChildren(cursor.origMem(), typedefVisitor, MemorySegment.NULL);
        clang.clang_visitChildren(cursor.origMem(), structUnionVisitor, MemorySegment.NULL);
        clang.clang_visitChildren(cursor.origMem(), enumVisitor, MemorySegment.NULL);
        clang.clang_visitChildren(cursor.origMem(), functionVisitor, MemorySegment.NULL);
    }

    CFunction currentFunction;

    public MemorySegment functionVisitor(MemorySegment cursor,
                                 MemorySegment parent,
                                 MemorySegment client_data)
    {
        CXCursorKind kind = clang.clang_getCursorKind(cursor);

        if (kind == CXCursor_FunctionDecl)
                if (clang.clang_isCursorDefinition(cursor) == 1) {
                    String fname = getString(cursor);

                    CXType returnType = clang.clang_getCursorResultType(cursor);
                    CXString s = clang.clang_getTypeSpelling(returnType);
                    String rname = clang.clang_getCString(s);
                    var argDef = splitArg(returnType, rname, "", 0);
                    currentFunction = new CFunction(argDef.orElseGet(null), fname, new ArrayList<>(), "");
                    clang.clang_visitChildren(cursor, visitFunctionParamPtr, MemorySegment.NULL);
                    allFunctions.add(currentFunction);
                }

        return tmpArena.allocateFrom(JAVA_INT, CXChildVisitResult.CXChildVisit_Recurse.ordinal() );
    }

    public MemorySegment functionParamVisitor(MemorySegment cursor,
                                         MemorySegment parent,
                                         MemorySegment client_data)
    {
        CXCursorKind kind = clang.clang_getCursorKind(cursor);

        if (kind == CXCursor_ParmDecl)
            if (clang.clang_isCursorDefinition(cursor) == 1) {
                String pname = getString(cursor);
                CXType type = clang.clang_getCursorType(cursor);
                CXString s = clang.clang_getTypeSpelling(type);
                String rname = clang.clang_getCString(s);
                var argDef = splitArg(type, rname, pname, currentFunction.parameters.size());
                if (argDef.isPresent())
                    currentFunction.parameters.add(argDef.get());
                else
                    currentFunction.parameters.add(new ArgDef("int", pname, "", false, ""));
            }

        return tmpArena.allocateFrom(JAVA_INT, CXChildVisitResult.CXChildVisit_Recurse.ordinal() );
    }

    Stack<CRecord> buildingRecords = new Stack<>();
    int AnonStructCount = 1;
    int AnonUnionCount = 1;

    public MemorySegment structUnionVisitor(MemorySegment cursor,
                                 MemorySegment parent,
                                 MemorySegment client_data)
    {
        CXCursorKind ckind = clang.clang_getCursorKind(cursor);

        switch (ckind) {
            case CXCursor_StructDecl: {
                String sname = getString(cursor);
                String kind = "struct";

                if (sname.isEmpty() && client_data.address() != 0)
                    sname = getString(client_data);

                if (sname.isEmpty())
                {
                    sname = "anonStruct" + AnonStructCount;
                    AnonStructCount++;
                }
                var myStruct = new CRecord(sname, kind, new ArrayList<>(), origSource(cursor));
                var parentStruct = buildingRecords.isEmpty() ? null : buildingRecords.peek();
                buildingRecords.push(myStruct);

                clang.clang_visitChildren(cursor, visitStructFieldfunctionPtr, MemorySegment.NULL);
                if (parentStruct != null)
                {
                    String fieldName = myStruct.name.isEmpty() ? "arg" + parentStruct.fields.size() : myStruct.name;
                    parentStruct.fields.add(new ArgDef(myStruct.name, fieldName, "", false, myStruct.CCode));
                }
                allRecords.add(myStruct);
                buildingRecords.pop();
                break;
            }
            case CXCursor_UnionDecl: {
                String uname = getString(cursor);
                String kind = "union";
                if (uname.isEmpty())
                {
                    uname = "anonUnion" + AnonStructCount;
                    AnonStructCount++;
                }
                var myUnion = new CRecord(uname, kind, new ArrayList<>(), origSource(cursor));
                var parentStruct = buildingRecords.isEmpty() ? null : buildingRecords.peek();
                buildingRecords.push(myUnion);

                clang.clang_visitChildren(cursor, visitStructFieldfunctionPtr, MemorySegment.NULL);
                if (parentStruct != null)
                {
                    parentStruct.fields.add(new ArgDef(myUnion.name, "", "", false, myUnion.CCode));
                }
                allRecords.add(myUnion);
                buildingRecords.pop();
                break;
            }

            default:
                break;
        }

        return tmpArena.allocateFrom(JAVA_INT, CXChildVisitResult.CXChildVisit_Recurse.ordinal() );
    }

    public MemorySegment typdefVisitor(MemorySegment cursor,
                                 MemorySegment parent,
                                 MemorySegment client_data)
    {
        CXCursorKind kind = clang.clang_getCursorKind(cursor);

        switch (kind) {
            case CXCursor_TypedefDecl: {
                CXType type = clang.clang_getCursorType(cursor);
                String paramname = getString(cursor);

                parseTypedef(cursor, type);

                var typedefType = clang.clang_getTypedefDeclUnderlyingType(cursor);
                if (typedefType.kind() == CXType_Elaborated)
                {
                    clang.clang_visitChildren(cursor, structUnionVisitor, cursor);
                    clang.clang_visitChildren(cursor, enumVisitor, cursor);
                }
                break;
            }

            default:
                break;
        }

        return tmpArena.allocateFrom(JAVA_INT, CXChildVisitResult.CXChildVisit_Recurse.ordinal() );
    }


    public MemorySegment struct_field_visitor(MemorySegment cursor,
                                              MemorySegment parent,
                                              MemorySegment data) {
        var kind = clang.clang_getCursorKind(cursor);
        switch (kind) {
            case CXCursor_StructDecl, CXCursor_UnionDecl: {
                structUnionVisitor(cursor, parent, data);
            }

            case CXCursor_FieldDecl: {
                CXType type = clang.clang_getCursorType(cursor);
                CXString s = clang.clang_getTypeSpelling(type);
                CXType valueType = clang.clang_getTypedefDeclUnderlyingType(cursor);
                if (valueType.kind() == CXType_Invalid)
                    valueType = clang.clang_getCanonicalType(type);
                CXString s2 = clang.clang_getTypeSpelling(valueType);

                String paramname = getString(cursor);
                String ctype = clang.clang_getCString(s2);
                clang.clang_disposeString(s.origData());
                clang.clang_disposeString(s2.origData());

                int fieldOffset = !buildingRecords.isEmpty() ? buildingRecords.peek().fields.size() : 1;
                var argDef = splitArg(valueType, ctype,paramname, fieldOffset);
                argDef.ifPresent(def -> buildingRecords.peek().fields.add(def));
            }
        }
        return tmpArena.allocateFrom(JAVA_INT, CXChildVisitResult.CXChildVisit_Continue.ordinal() );
    }

    CEnum currentEnum;
    int AnonEnumCount = 1;

    public MemorySegment enumVisitor(MemorySegment cursor,
                                            MemorySegment parent,
                                            MemorySegment client_data)
    {
        CXCursorKind kind = clang.clang_getCursorKind(cursor);

        if (kind == CXCursor_EnumDecl) {
            String ename = getString(cursor);

            if (ename.isEmpty() && client_data.address() != 0)
                ename = getString(client_data);

            if (ename.isEmpty())
            {
                ename = "enumAnon" + AnonEnumCount;
                AnonEnumCount++;
            }
            currentEnum = new CEnum(ename, new ArrayList<>(), origSource(cursor));
                clang.clang_visitChildren(cursor, visitEnumfunctionPtr, MemorySegment.NULL);
                allEnums.add(currentEnum);
            }

        return tmpArena.allocateFrom(JAVA_INT, CXChildVisitResult.CXChildVisit_Recurse.ordinal() );
    }

    public MemorySegment enum_constant_visitor(MemorySegment cursor,
                                               MemorySegment parent,
                                               MemorySegment data) {

        var kind = clang.clang_getCursorKind(cursor);
        if (kind == CXCursor_EnumConstantDecl) {

            long val = clang.clang_getEnumConstantDeclValue(cursor);
            String ename = getString(cursor);
            boolean forceLong = val < Integer.MIN_VALUE || val > Integer.MAX_VALUE;
            CEnumValue value = new CEnumValue(ename, val, forceLong);
            currentEnum.values.add(value);
        }
        return tmpArena.allocateFrom(JAVA_INT, CXChildVisitResult.CXChildVisit_Continue.ordinal() );
    }


    Optional<ArgDef> splitArg(CXType cxt, String typeOrig, String name, int countArg) {

        if (cxt.kind() == CXType_Typedef)
            cxt = clang.clang_getCanonicalType(cxt);

        String ptrDetails = "";
        if (cxt.kind() == CXType_Pointer)
        {
            cxt = clang.clang_getPointeeType(cxt);
            ptrDetails = "*";

            if (cxt.kind() == CXType_Typedef || cxt.kind() == CXType_Record)
            {
                cxt = clang.clang_getCanonicalType(cxt);
            }
        }

        if (cxt.kind() == CXType_ConstantArray)
        {
            ptrDetails = "[" + clang.clang_getArraySize(cxt) + "]";
            cxt = clang.clang_getArrayElementType(cxt);
        }
        else if (cxt.kind() == CXType_IncompleteArray)
        {
            ptrDetails = "[]";
            cxt = clang.clang_getArrayElementType(cxt);
        }

        if (cxt.kind() == CXType_Elaborated || cxt.kind() == CXType_Record)
        {
            cxt = clang.clang_getCanonicalType(cxt);
        }

        String type = switch (cxt.kind()) {
            case CXTypeKind.CXType_Int, CXType_UInt -> "int";
            case CXType_Long, CXType_LongLong, CXType_ULong, CXType_ULongLong -> "long";
            case CXType_Short, CXType_UShort -> "short";
//            case CXType_Void -> "void";
            case CXTypeKind.CXType_Bool  -> "boolean";
            case CXTypeKind.CXType_Float -> "float";
            case CXTypeKind.CXType_Double, CXType_LongDouble -> "double";
            case CXType_Char16, CXType_UChar, CXType_Char_S  -> "byte";
            case CXType_Char32, CXType_SChar -> "char";
            case CXType_FunctionProto -> "FunctionPtr";
            case CXType_Pointer -> "GenericPointer";
            case CXType_Enum -> typeOrig.replace("enum ", "");
            case CXType_FirstBuiltin ->  "MemorySegment";
            case CXType_Record ->  typeOrig.replace("struct ", "").replace("union", "");
            case CXType_ConstantArray, CXType_IncompleteArray -> typeOrig;
            default  -> "";
        };

        if (typeOrig.contains(" * "))
            typeOrig = typeOrig.replace(" * ", "* ");
        typeOrig = typeOrig.replace("struct ", " ");
        if (typeOrig.equals("void"))
            typeOrig = "";
        if (typeOrig.contains("[ "))
            typeOrig = typeOrig.replace("[ ", "[");
        if (typeOrig.contains(" ]"))
            typeOrig = typeOrig.replace(" ]", "]");
        String[] parts = typeOrig.split("(\\s+|\\))");

         if (parts.length > 2) {
             ptrDetails = Arrays.stream(parts).filter(HeaderToPassport::isAllPtr).findFirst().orElse("");
            String cdetails = ptrDetails;
            parts = Arrays.stream(parts).filter(s -> !s.equals(cdetails)).toList().toArray(new String[0]);
        }

        //meant to catch arguments that are just defined by type with no name - common in win32 api
        if (parts.length == 1)
            parts = new String[] {parts[0], "arg" + countArg, };

        if (!Arrays.stream(parts).filter(s -> s.contains("FunctionPtr")).findAny().isEmpty())
        {
            for (int n = 0; n < parts.length; ++n)
            {
                if (parts[n].contains("FunctionPtr"))
                {
                    return Optional.of(new ArgDef("FunctionPtr", parts[n+1], "", false, ""));
                }
            }
        }

        if (parts.length == 1 || parts.length > 3) {
//            warnings.add("ERROR: I don't know how to parse the argument: " + argDef);
            return Optional.empty();//new ArgDef("error", "error", "", true, argDef);
        }

//        String type = parts[0].replace("[]", "*");
//        String name = parts[1].replace("[]", "*");
        type = type.replace("&", "*");
        name = name.replace("&", "*");
        String ptr = ptrDetails;
        while (type.endsWith("*")) {
            ptr = ptr + "*";
            type = type.substring(0, type.length() - 1);
        }

        while (name.endsWith("*")) {
            ptr = ptr + "*";
            name = name.substring(0, name.length() - 1);
        }

        while (name.startsWith("*")) {
            ptr = ptr + "*";
            name = name.substring(1);
        }

        if (type.contains("[")) {
            int start = type.indexOf("[");
            int end = type.lastIndexOf("]");
            ptr = type.substring(start, end + 1);
            type = type.replace(ptr, "");
        }

        if (name.contains("[")) {
            int start = name.indexOf("[");
            int end = name.lastIndexOf("]");
            ptr = name.substring(start);
            name = name.replace(ptr, "");
        }

        for (String remove : ignoreKeywords)
            type = type.replace(remove, "");

        if (name.isEmpty())
            name = "arg" + countArg;

        return Optional.of(new ArgDef(type, name, ptr, false, ""));
    }


    static void parseTypedef(MemorySegment cursor, CXType cxType)
    {
        CXType valueType = clang.clang_getTypedefDeclUnderlyingType(cursor);
        if (valueType.kind() == CXType_Invalid)
            valueType = clang.clang_getCanonicalType(cxType);
        CXString s2 = clang.clang_getTypeSpelling(valueType);

        CXString s = clang.clang_getTypeSpelling(cxType);
        String name = clang.clang_getCString(s);
        String type = clang.clang_getCString(s2);
        var commonAlias = convertToCommonAlias(type);
        if (commonAlias.isPresent())
            type = commonAlias.get().replace;

        String ptr = "";
        if (type.contains(" ")) {
            type = type.replace("struct ", "").replace("union", "").replace("enum", "").trim();

            String[] typeParts = type.split(" ");

            type = typeParts[Math.min(1, typeParts.length - 1)];
            if (type.contains("*"))
            {
                var allptrs = Arrays.stream(typeParts).filter(str -> str.contains("*")).toList();
                for (String str : allptrs)
                    ptr = ptr + str;
            }
            type = Arrays.stream(typeParts).filter(str -> !str.contains("*")).findFirst().orElse(type);
        }

        if (!typeDefToNative.containsKey(name)) {
            Type t;
            if (typeDefToNative.containsKey(type)) {
                t = typeDefToNative.get(type);
                typeDefToNative.put(name, t);
            }
            else
            {
                typeDefToNative.put(name, new Type(type, false, ptr));
            }
        }
    }

    static Optional<CAlias> convertToCommonAlias(String type)
    {
        return commonAliases.stream().filter(a -> a.alias.equals(type)).findFirst();
    }

    String origSource(MemorySegment cursor)
    {
        var range = clang.clang_getCursorExtent(cursor);
        MemorySegment tokens = Arena.ofAuto().allocate(10*24);
        int[] numTokens = new int[]{0};

        clang.clang_tokenize(tu, range.origMem(), tokens, numTokens);

        if (numTokens[0] > 10)
        {
            tokens = Arena.ofAuto().allocate(numTokens[0]*24);
            clang.clang_tokenize(tu, range.origMem(), tokens, numTokens);
        }

        CXString spelling;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < numTokens[0]; i++) {
            MemorySegment mst = tokens.get(ValueLayout.ADDRESS,0).reinterpret(numTokens[0]*24);
            if (i > 0)
                mst = mst.asSlice(i*24,24);

            spelling = clang.clang_getTokenSpelling(tu, mst);
            String CCode = clang.clang_getCString(spelling);
            if (CCode.contains("/*") || CCode.contains("*/"))
                continue;
            sb.append(CCode).append("\n");
            clang.clang_disposeString(spelling.origData());
        }

        clang.clang_disposeTokens(tu, tokens.get(ValueLayout.ADDRESS,0), numTokens[0]);
        return sb.toString();
    }

    String getString(MemorySegment cursor) {
        CXString name = clang.clang_getCursorSpelling(cursor);
        String ename = clang.clang_getCString(name);
        clang.clang_disposeString(name.origData());
        return ename;
    }
}
