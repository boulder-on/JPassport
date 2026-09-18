package jpassport.apibuilder;

import jpassport.DebugPassport;
import jpassport.PassportFactory;
import jpassport.parser.*;
import jpassport.parser.clang.types.*;
import jpassport.pointers.FunctionPtr;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static jpassport.parser.clang.types.CXCursorKind.*;
import static jpassport.parser.clang.types.CXTypeKind.*;

public class HeaderDefs implements AutoCloseable {

    public List<CFunction> allFunctions = new ArrayList<>();
    List<CRecord> allRecords = new ArrayList<>();
    List<CEnum> allEnums = new ArrayList<>();
    CFunction currentFunction;
    FunctionPtr structUnionVisitor;
    FunctionPtr enumVisitor;
    FunctionPtr functionVisitor;
    FunctionPtr visitFunctionParamPtr;
    FunctionPtr visitStructFieldfunctionPtr;
    FunctionPtr typedefVisitor;
    FunctionPtr visitEnumfunctionPtr;


    enum OUTPUT_OBJECTS {methods, records, enums}

    int[] counts = new int[OUTPUT_OBJECTS.values().length];

    public record CFunction(ArgDef returnType,
                            String name,
                            List<ArgDef> parameters,
                            String CCode) {
    }

    record CType(String name, boolean isNative, String ptr, boolean ptrRequired) {
        public CType(String name, boolean isNative, String ptr) {
            this(name, isNative, ptr, false);
        }
    }

    record CEnum(String name, List<CEnumValue> values, String CCode, BooleanHolder isUsed) {
    }

    record CEnumValue(
            String name,
            Long value,
            boolean forceLong) {
    }

    public record CAlias(String alias, String replace) {

//        String clean(String orig) {
//            String regEx = "\\s*" + alias.replace(" ", "\\s+") + "(\\*+\\s+|\\s+)";
//            Pattern p = Pattern.compile(regEx, Pattern.DOTALL);
//
//            Matcher m = p.matcher(orig);
//            while (m.find()) {
//                String found = m.group(0);
//                int ptrCount = found.length() - found.replace("*", "").length();
//
//                if (ptrCount == 0)
//                    try {
//                        orig = orig.replace(found, " " + replace + " ");
//                    } catch (Throwable th) {
//                        th.printStackTrace();
//                    }
//                else {
//                    String ptrs = "";
//                    for (int i = 0; i < ptrCount; i++)
//                        ptrs = ptrs + "*";
//
//                    orig = orig.replace(found, " " + replace + ptrs + " ");
//
//                }
//
//            }
//
//            return orig;
//        }
    }

    public record ArgDef(String type, String name, String ptr, boolean error, String origText) {
    }

    record CRecord(String name, String kind, List<ArgDef> fields, String CCode, BooleanHolder isUsed) {
        //in many C APIs you will find code like:
        // typedef struct MyStruct MyStruct;
        // This allows the library maker to hide the fields of the struct
        //These typedefs are always used as pointers only and can therefore
        //be converted to GenericPointer types.
        public boolean isEmptyStruct() {
            return this.fields.isEmpty();
        }
    }

    static String[] ignoreKeywords = new String[]{
            "const",
            "typedef ",
            "volatile ",
            "unsigned ",
            "signed ",
            "__unaligned ",
            "enum ",
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


    static final HashMap<String, CType> typeDefToNative = new HashMap<>();

    static {
        typeDefToNative.put("char", new CType("char", true, ""));
        typeDefToNative.put("unsigned char", new CType("byte", true, ""));
        typeDefToNative.put("uint8_t", new CType("byte", true, ""));
        typeDefToNative.put("uint_least8_t", new CType("byte", true, ""));
        typeDefToNative.put("uint_fast8_t", new CType("byte", true, ""));
        typeDefToNative.put("short", new CType("short", true, ""));
        typeDefToNative.put("int", new CType("int", true, ""));
        typeDefToNative.put("long", new CType("long", true, ""));
        typeDefToNative.put("time_t", new CType("long", true, ""));
        typeDefToNative.put("float", new CType("float", true, ""));
        typeDefToNative.put("double", new CType("double", true, ""));
        typeDefToNative.put("size_t", new CType("long", true, ""));
        typeDefToNative.put("uint32_t", new CType("int", true, ""));
        typeDefToNative.put("uint64_t", new CType("long", true, ""));

        typeDefToNative.put("HANDLE", new CType("MemorySegment", true, ""));

        typeDefToNative.put("PVOID", new CType("byte", true, "", false));
        typeDefToNative.put("LPVOID", new CType("byte", true, "", false));
        typeDefToNative.put("LPCSTR", new CType("String", true, ""));
        typeDefToNative.put("LPCWSTR", new CType("char", true, "*", true));
        typeDefToNative.put("PWSTR", new CType("char", true, "*", true));
        typeDefToNative.put("LPWSTR", new CType("String", true, ""));
        typeDefToNative.put("DWORD", new CType("long", true, "", false));
        typeDefToNative.put("DWORD64", new CType("long", true, "", false));
        typeDefToNative.put("PDWORD", new CType("long", true, "*", true));
        typeDefToNative.put("LPDWORD", new CType("long", true, "*", true));
        typeDefToNative.put("DWORD_PTR", new CType("long", true, "*", true));
        typeDefToNative.put("LARGE_INTEGER", new CType("long", true, "", false));

        typeDefToNative.put("LONG", new CType("long", true, ""));
        typeDefToNative.put("ULONG", new CType("long", true, ""));
        typeDefToNative.put("LONG64", new CType("long", true, ""));
        typeDefToNative.put("ULONG64", new CType("long", true, ""));
        typeDefToNative.put("LONGLONG", new CType("long", true, ""));
        typeDefToNative.put("ULONGLONG", new CType("long", true, ""));
        typeDefToNative.put("BOOLEAN", new CType("boolean", true, ""));
        typeDefToNative.put("BOOL", new CType("boolean", true, ""));
        typeDefToNative.put("bool", new CType("boolean", true, ""));
        typeDefToNative.put("PBOOL", new CType("boolean", true, "*"));
        typeDefToNative.put("BYTE", new CType("byte", true, ""));
        typeDefToNative.put("LPBYTE", new CType("byte", true, "[]"));

        typeDefToNative.put("INT32", new CType("int", true, ""));
        typeDefToNative.put("UINT32", new CType("int", true, ""));
        typeDefToNative.put("UINT8", new CType("byte", true, ""));
        typeDefToNative.put("UINT64", new CType("long", true, ""));
        typeDefToNative.put("SHORT", new CType("short", true, ""));
        typeDefToNative.put("BSTR", new CType("String", true, ""));
        typeDefToNative.put("WCHAR", new CType("String", true, ""));
        typeDefToNative.put("UCHAR", new CType("char", true, ""));
        typeDefToNative.put("LPSTR", new CType("String", true, ""));
        typeDefToNative.put("UINT_PTR", new CType("int", true, "*", true));
    }

    private static final List<CAlias> commonAliases = List.of(
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
    );

    private static clangParser clang = null;
    private final CXTranslationUnit tu;
    CXIndex index;
    private final String interfaceName;
    Path headerPath;
    String[] clangArgs;


    public HeaderDefs(Path header_path, String[] clang_args) throws Throwable {
        if (clang == null) {
            String libName = System.getProperty("os.name").startsWith("Windows") ? "libclang" : "clang";
            System.setProperty("java.library.path", "C:\\Program Files\\LLVM\\bin");
            System.setProperty("java.lib.path", "C:\\Program Files\\LLVM\\bin");
            System.setProperty("jpassport.build.home", "out/testing");
            clang = PassportFactory.link_written(libName, clangParser.class, true);
        }

        interfaceName = header_path.getFileName().toString().replace(".", "_");

        typedefVisitor = PassportFactory.createCallback(this, "typedefVisitor");
        structUnionVisitor = PassportFactory.createCallback(this, "structUnionVisitor");
        visitFunctionParamPtr = PassportFactory.createCallback(this, "functionParamVisitor");
        visitStructFieldfunctionPtr = PassportFactory.createCallback(this, "struct_field_visitor");
        enumVisitor = PassportFactory.createCallback(this, "enumVisitor");
        visitEnumfunctionPtr = PassportFactory.createCallback(this, "enum_constant_visitor");
        functionVisitor = PassportFactory.createCallback(this, "functionVisitor");

        index = clang.clang_createIndex(0, 0);

        headerPath = header_path;
        clangArgs = clang_args;
        tu = clang.clang_parseTranslationUnit(
                index,
                headerPath.toAbsolutePath().toString(),
                clang_args, clang_args.length,
                null, 0,
                0x0
        );

    }

    public void close() {
        clang.clang_disposeTranslationUnit(tu);
        clang.clang_disposeIndex(index);
    }

    public void parseClang() {
        var cursor = clang.clang_getTranslationUnitCursor(tu);

        System.out.println("Typedefs");
        clang.clang_visitChildren(cursor.origMem(), typedefVisitor, MemorySegment.NULL);
        System.out.println("Structs");
        clang.clang_visitChildren(cursor.origMem(), structUnionVisitor, MemorySegment.NULL);
        System.out.println("Enums");
        clang.clang_visitChildren(cursor.origMem(), enumVisitor, MemorySegment.NULL);
        System.out.println("Functions");
        clang.clang_visitChildren(cursor.origMem(), functionVisitor, MemorySegment.NULL);
        System.out.println("Done");

        System.out.printf("functions, structs, enums=(%d, %d, %d)\n", allFunctions.size(), allRecords.size(), allEnums.size());
    }

    static Optional<CAlias> convertToCommonAlias(String type) {
        var ret =  commonAliases.stream().filter(a -> a.alias.equals(type)).findFirst();

        if (ret.isEmpty()) {
            for (var calias : commonAliases) {
                if (type.startsWith(calias.alias))
                    return Optional.of(calias);
            }
        }

        return ret;
    }

    Optional<CRecord> typeNameToRecord(String typeName) {
        return allRecords.stream().filter(rec -> rec.name.equals(typeName)).findFirst();
    }

    void parseTypedef(MemorySegment cursor, CXType cxType) {
        CXType valueType = clang.clang_getTypedefDeclUnderlyingType(cursor);
        if (valueType.kind() == CXType_Invalid)
            valueType = clang.clang_getCanonicalType(cxType);
        CXString s2 = clang.clang_getTypeSpelling(valueType);

        CXString s = clang.clang_getTypeSpelling(cxType);
        String name = clang.clang_getCString(s);
        String type = clang.clang_getCString(s2);

        if (type.startsWith("const"))
            type = type.replace("const", "").trim();
        var commonAlias = convertToCommonAlias(type);
        if (commonAlias.isPresent())
            type = commonAlias.get().replace;

        String ptr = "";
        if (type.contains(" ")) {
            type = type.replace("struct ", "").replace("union", "").replace("enum", "").trim();

            String[] typeParts = type.split(" ");

            type = typeParts[Math.min(1, typeParts.length - 1)];
            if (type.contains("*")) {
                var allptrs = Arrays.stream(typeParts).filter(str -> str.contains("*")).toList();
                for (String str : allptrs)
                    ptr = ptr + str;
            }
            type = Arrays.stream(typeParts).filter(str -> !str.contains("*")).findFirst().orElse(type);
        }

        Optional<CRecord> rec_struct = typeNameToRecord(name);
        if (rec_struct.isPresent()) {
            typeDefToNative.put(name, new CType(type, false, ptr));
        }
        else if (!typeDefToNative.containsKey(name)) {
            CType t;
            if (typeDefToNative.containsKey(type)) {
                t = typeDefToNative.get(type);
                typeDefToNative.put(name, t);
            } else {
                typeDefToNative.put(name, new CType(type, false, ptr));
            }
        }
    }

    public int typedefVisitor(MemorySegment cursor,
                             MemorySegment parent,
                             MemorySegment client_data) {
        CXCursorKind kind = clang.clang_getCursorKind(cursor);
        switch (kind) {
            case CXCursor_FunctionDecl: {
                //I don't know why, but walking the AST a second time does not see these
                //DM - TEST removal
//                functionVisitor(cursor, parent, client_data);
            }
            case CXCursor_TypedefDecl: {
                CXType type = clang.clang_getCursorType(cursor);
                String paramname = getString(cursor);

                parseTypedef(cursor, type);

                var typedefType = clang.clang_getTypedefDeclUnderlyingType(cursor);
                if (typedefType.kind() == CXType_Elaborated) {
                    clang.clang_visitChildren(cursor, structUnionVisitor, client_data);
                    clang.clang_visitChildren(cursor, enumVisitor, client_data);
                }
                break;
            }
            case CXCursor_EnumDecl: {
                enumVisitor(cursor, parent, client_data);
            }

            default:
                break;
        }
        return CXChildVisitResult.CXChildVisit_Recurse.ordinal();
    }

    public int functionVisitor(MemorySegment cursor,
                               MemorySegment parent,
                               MemorySegment client_data) {
        CXCursorKind kind = clang.clang_getCursorKind(cursor);

        if (kind == CXCursor_FunctionDecl) {
            String fname = getString(cursor);

            CXType returnType = clang.clang_getCursorResultType(cursor);
            CXString s = clang.clang_getTypeSpelling(returnType);
            String rname = clang.clang_getCString(s);
            var argDef = splitArg(returnType, rname, "", 0, false);

            String src = origSource(cursor);

            currentFunction = new CFunction(argDef.orElseGet(null), fname, new ArrayList<>(), src);
            clang.clang_visitChildren(cursor, visitFunctionParamPtr, client_data);
            allFunctions.add(currentFunction);
        }
        return CXChildVisitResult.CXChildVisit_Recurse.ordinal();
    }

    String origSource(MemorySegment cursor) {
        var range = clang.clang_getCursorExtent(cursor);
        MemorySegment tokens = Arena.ofAuto().allocate(10 * 24);
        int[] numTokens = new int[]{0};

        clang.clang_tokenize(tu, range.origMem(), tokens, numTokens);

        if (numTokens[0] > 10) {
            tokens = Arena.ofAuto().allocate((long)numTokens[0] * 24);
            clang.clang_tokenize(tu, range.origMem(), tokens, numTokens);
        }

        CXString spelling;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < numTokens[0]; i++) {
            MemorySegment mst = tokens.get(ValueLayout.ADDRESS, 0).reinterpret((long)numTokens[0] * 24);
            if (i > 0)
                mst = mst.asSlice((long)i * 24, 24);

            spelling = clang.clang_getTokenSpelling(tu, mst);
            String CCode = clang.clang_getCString(spelling);
            if (CCode.contains("/*") || CCode.contains("*/"))
                continue;
            sb.append(CCode).append("\n");
            clang.clang_disposeString(spelling.origData());
        }

        clang.clang_disposeTokens(tu, tokens.get(ValueLayout.ADDRESS, 0), numTokens[0]);
        return sb.toString();
    }

    String getString(MemorySegment cursor) {
        CXString name = clang.clang_getCursorSpelling(cursor);
        String ename = clang.clang_getCString(name);
        clang.clang_disposeString(name.origData());
        return ename;
    }

    String removeIgnorableKeywords(String cCode)
    {
        for (String remove : ignoreKeywords)
            cCode = cCode.replace(remove, "");
        return cCode;
    }

    Optional<ArgDef> splitArg(CXType cxt, String typeOrig, String name, int countArg, boolean forRecord) {

        if (typeDefToNative.containsKey(typeOrig))
        {
            CType ctype = typeDefToNative.get(typeOrig);
            return Optional.of(new ArgDef(ctype.name, name, ctype.ptr, false, ""));

        }

        if (cxt.kind() == CXType_Typedef)
            cxt = clang.clang_getCanonicalType(cxt);

        String ptrDetails = "";
        if (cxt.kind() == CXType_Pointer) {
            cxt = clang.clang_getPointeeType(cxt);
            ptrDetails = "*";

            if (cxt.kind() == CXType_Typedef || cxt.kind() == CXType_Record) {
                cxt = clang.clang_getCanonicalType(cxt);
            }
        }

        if (cxt.kind() == CXType_ConstantArray) {
            if (forRecord){
                ptrDetails = "[]";
            }
            else {
                ptrDetails = "[" + clang.clang_getArraySize(cxt) + "]";
            }
            cxt = clang.clang_getArrayElementType(cxt);
        } else if (cxt.kind() == CXType_IncompleteArray) {
            ptrDetails = "[]";
            cxt = clang.clang_getArrayElementType(cxt);
        }

        if (cxt.kind() == CXType_Elaborated || cxt.kind() == CXType_Record || cxt.kind() == CXType_Typedef) {
            cxt = clang.clang_getCanonicalType(cxt);
        }

        String type = switch (cxt.kind()) {
            case CXTypeKind.CXType_Int, CXType_UInt -> "int";
            case CXType_Long, CXType_LongLong, CXType_ULong, CXType_ULongLong -> "long";
            case CXType_Short, CXType_UShort -> "short";
//            case CXType_Void -> "void";
            case CXTypeKind.CXType_Bool -> "boolean";
            case CXTypeKind.CXType_Float -> "float";
            case CXTypeKind.CXType_Double, CXType_LongDouble -> "double";
            case CXType_Char16, CXType_UChar, CXType_Char_S -> "byte";
            case CXType_Char32, CXType_SChar -> "char";
            case CXType_FunctionProto, CXType_FunctionNoProto -> "FunctionPtr";
            case CXType_Pointer -> "GenericPointer";
            case CXType_Enum -> typeOrig.replace("enum ", "");
            case CXType_FirstBuiltin -> "MemorySegment";
            case CXType_Record -> typeOrig.replace("struct ", "").replace("union", "");
            case CXType_ConstantArray, CXType_IncompleteArray -> typeOrig;
            default -> "";
        };

        type = removeIgnorableKeywords(type);

        if (cxt.kind() == CXType_FirstBuiltin)
            ptrDetails = "";

        if (cxt.kind() == CXType_Record) {
            //structs can have type[2], but records cannot. So we clean the array portion off here.
            if (type.contains("[") && forRecord)
            {
                int start = type.indexOf('[');
                int end = type.indexOf(']', start + 1);
                String arrDef = type.substring(start, end + 1);
                type = type.replace(arrDef, "");
                //remove brackets.
            }
            return Optional.of(new ArgDef(type.trim(), name, ptrDetails, false, typeOrig));
        }

        if (typeOrig.contains(" * "))
            typeOrig = typeOrig.replace(" * ", "* ");
        else if ( typeOrig.contains(" *")) {
            typeOrig = typeOrig.replace(" *", "* ");

        }
        typeOrig = typeOrig.replace("struct ", " ");
        if (typeOrig.equals("void"))
            typeOrig = "";
        if (typeOrig.contains("[ "))
            typeOrig = typeOrig.replace("[ ", "[");
        if (typeOrig.contains(" ]"))
            typeOrig = typeOrig.replace(" ]", "]");
        String[] parts = typeOrig.split("(\\s+|\\))");

        if (parts.length > 2) {
            ptrDetails = Arrays.stream(parts).filter(HeaderDefs::isAllPtr).findFirst().orElse("");
            String cdetails = ptrDetails;
            parts = Arrays.stream(parts).filter(s -> !s.equals(cdetails)).toList().toArray(new String[0]);
        }

        //meant to catch arguments that are just defined by type with no name - common in win32 api
        if (parts.length == 1)
            parts = new String[]{parts[0], "arg" + countArg,};

        if (!Arrays.stream(parts).filter(s -> s.contains("FunctionPtr")).findAny().isEmpty()) {
            for (int n = 0; n < parts.length; ++n) {
                if (parts[n].contains("FunctionPtr")) {
                    return Optional.of(new ArgDef("FunctionPtr", parts[n + 1], "", false, ""));
                }
            }
        }

        if (parts.length == 1 || parts.length > 3) {
            return Optional.empty();//new ArgDef("error", "error", "", true, argDef);
        }

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

        type = removeIgnorableKeywords(type);

        if (name.isEmpty())
            name = "arg" + countArg;

        return Optional.of(new ArgDef(type.trim(), name, ptr, false, ""));
    }

    static boolean isAllPtr(String def) {
        if (def.chars().filter(ch -> ch == '*').count() == def.length())
            return true;

        if (def.startsWith("[") && def.endsWith("]"))
            return true;
        return false;
    }

    Stack<CRecord> buildingRecords = new Stack<>();
    int AnonStructCount = 1;
    int AnonUnionCount = 1;

    public int structUnionVisitor(MemorySegment cursor,
                                  MemorySegment parent,
                                  MemorySegment client_data) {
        CXCursorKind ckind = clang.clang_getCursorKind(cursor);

        switch (ckind) {
            case CXCursor_StructDecl: {
                String sname = getString(cursor);
                String kind = "struct";

                if (sname.isEmpty() && client_data.address() != 0)
                    sname = getString(client_data);

                if (sname.isEmpty()) {
                    sname = "anonStruct" + AnonStructCount;
                    AnonStructCount++;
                }
                var myStruct = new CRecord(sname, kind, new ArrayList<>(), origSource(cursor), new BooleanHolder());
                var parentStruct = buildingRecords.isEmpty() ? null : buildingRecords.peek();
                buildingRecords.push(myStruct);

                clang.clang_visitChildren(cursor, visitStructFieldfunctionPtr, client_data);
                if (parentStruct != null) {
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
                if (uname.isEmpty()) {
                    uname = "anonUnion" + AnonStructCount;
                    AnonStructCount++;
                }
                var myUnion = new CRecord(uname, kind, new ArrayList<>(), origSource(cursor), new BooleanHolder());
                var parentStruct = buildingRecords.isEmpty() ? null : buildingRecords.peek();
                buildingRecords.push(myUnion);

                clang.clang_visitChildren(cursor, visitStructFieldfunctionPtr, client_data);
                if (parentStruct != null) {
                    parentStruct.fields.add(new ArgDef(myUnion.name, "", "", false, myUnion.CCode));
                }
                allRecords.add(myUnion);
                buildingRecords.pop();
                break;
            }

            default:
                break;
        }

        return CXChildVisitResult.CXChildVisit_Recurse.ordinal();
    }

    public int functionParamVisitor(MemorySegment cursor,
                                    MemorySegment parent,
                                    MemorySegment client_data) {
        CXCursorKind kind = clang.clang_getCursorKind(cursor);

        if (kind == CXCursor_ParmDecl)
            if (clang.clang_isCursorDefinition(cursor) == 1) {
                String pname = getString(cursor);
                CXType type = clang.clang_getCursorType(cursor);
                CXString s = clang.clang_getTypeSpelling(type);
                String rname = clang.clang_getCString(s);

                var argDef = splitArg(type, rname, pname, currentFunction.parameters.size(), false);

                if (argDef.isPresent())
                    currentFunction.parameters.add(argDef.get());
                else
                    currentFunction.parameters.add(new ArgDef("int", pname, "", false, ""));
            }

        return CXChildVisitResult.CXChildVisit_Recurse.ordinal();
    }

    CEnum currentEnum;
    int AnonEnumCount = 1;

    public int enumVisitor(MemorySegment cursor,
                           MemorySegment parent,
                           MemorySegment client_data) {
        CXCursorKind kind = clang.clang_getCursorKind(cursor);

        if (kind == CXCursor_EnumDecl) {
            String ename = getString(cursor);

            if (ename.isEmpty() && client_data.address() != 0)
                ename = getString(client_data);

            if (ename.isEmpty()) {
                ename = "enumAnon" + AnonEnumCount;
                AnonEnumCount++;
            }
            currentEnum = new CEnum(ename, new ArrayList<>(), origSource(cursor), new BooleanHolder());
            clang.clang_visitChildren(cursor, visitEnumfunctionPtr, client_data);
            allEnums.add(currentEnum);
        }
        return CXChildVisitResult.CXChildVisit_Recurse.ordinal();
    }

    public int enum_constant_visitor(MemorySegment cursor,
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
        return CXChildVisitResult.CXChildVisit_Continue.ordinal();
    }

    public int struct_field_visitor(MemorySegment cursor,
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
                var argDef = splitArg(valueType, ctype, paramname, fieldOffset, true);
                argDef.ifPresent(def -> buildingRecords.peek().fields.add(def));
            }
        }
        return CXChildVisitResult.CXChildVisit_Continue.ordinal();
    }

    static String packageName;
    static Path destinationPath;
    List<String> ignoreFunctionNames = new ArrayList<>();
    private static final Set<String> emptyStructs = new HashSet<>();

    public void generateJava(String packageName, Path destination, Set<String> ignoreFunctions) throws IOException {
        destinationPath = destination;
        this.packageName = packageName;
        // ---- Structs / Unions → Records ----
        generateJavaRecords(allRecords);

        // ---- Enums ----
        HashSet<String> typedefnames = new HashSet<>();
        allRecords.forEach(record -> {typedefnames.add(record.name);});
        allEnums.forEach(en -> typedefnames.add(en.name));
        generateInterface(allFunctions, ignoreFunctions, allEnums, allRecords, typedefnames);
//        HashSet<String> allEnumNames = new HashSet<>();
//        HashMap<String, CEnum> allEnumsByName = new HashMap<>();
//        for(CEnum e : allEnums)
//        {
//            if (allEnumNames.contains(e.name))
//            {
//                System.out.println("Clash:" + e.name);
//                var clash = allEnumsByName.get(e.name);
////                System.out.println(clash);
//            }
//            else
//            {
//                allEnumNames.add(e.name);
//                allEnumsByName.put(e.name, e);
//            }
//        }
        generateJavaEnums(allEnums);

        System.out.println("Generation complete: " + destinationPath.toAbsolutePath());
        System.out.println("            Enums created: " + counts[OUTPUT_OBJECTS.enums.ordinal()]);
        System.out.println("  Records/Structs created: " + counts[OUTPUT_OBJECTS.records.ordinal()]);
        System.out.println("Interface methods created: " + counts[OUTPUT_OBJECTS.methods.ordinal()]);
    }

    private void generateInterface(List<CFunction> funcs, Set<String> ignoreFunctions, List<CEnum> enums, List<CRecord> records, Set<String> typedefNames) throws FileNotFoundException {
        if (funcs.isEmpty())
            return;

        Path outFile = destinationPath.resolve(interfaceName + ".java");
        System.out.println("Generating interface " + interfaceName);

        try {
            if (!Files.exists(outFile.getParent()))
                Files.createDirectories(outFile.getParent());
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        HashSet<String> ignorable = new HashSet<>();
        ignoreFunctionNames.forEach(n -> ignorable.add(n));
        HashMap<String, CEnum> enumMap = new HashMap<>();
        enums.forEach(n -> enumMap.put(n.name(), n));
        HashMap<String, CRecord> recordMap = new HashMap<>();
        records.forEach(r -> recordMap.put(r.name(), r));

        try (PrintWriter out = new PrintWriter(outFile.toFile())) {
            out.println("package " + packageName + ";\n\n");
            out.println("import jpassport.Passport;");
            out.println("import java.lang.foreign.MemorySegment;");
            out.println("import jpassport.pointers.*;");
            out.println("import jpassport.annotations.*;\n\n");

            out.println("public interface " + interfaceName + " extends Passport{");
            Set<String> functionsMade = new HashSet<>();

            for (CFunction f : funcs) {
                if (ignoreFunctions.contains(f.name())) {
//                    System.out.println("Skipping function: " + f.name());
                    continue;
                }

//                out.println("/*");
//                out.println(f.CCode());
//                out.println("*/");
                if (functionsMade.contains(f.name()))
                    continue;
                functionsMade.add(f.name());

                out.print("    " + mapType(f.returnType(), true) + " " + f.name() + "(");
                StringBuilder params = new StringBuilder();
                int i = 1;
                for (int n = 0; n < f.parameters().size(); ++n) {
                    var p = f.parameters().get(n);
                    if (p.name().isEmpty())
                        p = new ArgDef(p.type(), "arg" + i, p.ptr(), p.error(), p.origText());
                    if (p.type().equals("...")) {
                        var pp = f.parameters().get(n - 1);
                        p = new ArgDef(pp.type(), "arg" + i, pp.ptr(), p.error(), p.origText());
                    }
                    params.append(formatParam(p)).append(",");
                    if (enumMap.containsKey(p.type()))
                        enumMap.get(p.type()).isUsed().set(true);
                    if (recordMap.containsKey(p.type())) {
                        recordMap.get(p.type()).isUsed().set(true);
                        //todo: recursively set all fields to used
                    }
                    i++;
                }
                if (!f.parameters().isEmpty())
                    params.setLength(params.length() - 1);
                out.print(params);
                out.println(");");
                out.println();
                counts[OUTPUT_OBJECTS.methods.ordinal()]++;
            }
            out.println("}");
        }
    }

    void generateJavaEnums(List<CEnum> enums) throws FileNotFoundException {
        HashMap<String, CEnum> enumMap = new HashMap<>();
        enums.forEach(n -> enumMap.put(n.name(), n));
        enums = new ArrayList<>(enumMap.values());

        for (CEnum e : enums) {
            //            if (!e.isUsed().value())
            //            {
            //                System.out.println("Skipping unused enum: " + e.name());
            //                continue;
            //            }

            boolean useLongs = e.values().stream().anyMatch(CEnumValue::forceLong);
            boolean isSequential = allSequential(e.values());

            Path outFile = destinationPath.resolve(e.name().replace("*", "").trim() + ".java");
            try {
                if (!Files.exists(outFile.getParent()))
                    Files.createDirectories(outFile.getParent());
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            try (PrintWriter out = new PrintWriter(outFile.toFile())) {
                out.println("package " + packageName + ";\n\n");
                out.println("import jpassport.enums.EnumInt;\n");
                out.println("import jpassport.enums.EnumLong;\n");

                boolean useValues = !isSequential || useLongs;

                out.println("/*");
                out.println(e.CCode());
                out.println("*/");

                if (isSequential && !useLongs)
                    out.println("public enum " + e.name() + " {");
                else if (useLongs)
                    out.println("public enum " + e.name() + " implements EnumLong {");
                else
                    out.println("public enum " + e.name() + " implements EnumInt {");

                for (int i = 0; i < e.values().size(); i++) {
                    CEnumValue v = e.values().get(i);
                    if (useValues) {
                        if (useLongs)
                            out.printf("    %s(%dL), //0x%x\n", v.name(), v.value(), v.value());
                        else
                            out.printf("    %s(%d), //0x%x\n", v.name(), v.value(), v.value());
                    } else
                        out.print("    " + v.name() + ",\n");

                }
                out.println(";");

                if (useValues) {

                    if (useLongs) {
                        out.println("\n    public final long value;");
                        out.println("\n    " + e.name() + "(long v) { this.value = v; }");
                        out.println("\n    @Override");
                        out.println("    public long getCValue() {");
                        out.println("        return value;");
                        out.println("    }");
                    } else {
                        out.println("\n    public final int value;");
                        out.println("\n    " + e.name() + "(int v) { this.value = v; }");
                        out.println("\n    @Override");
                        out.println("    public int getCValue() {");
                        out.println("        return value;");
                        out.println("    }");
                    }
                }

                out.println("}\n");
                counts[OUTPUT_OBJECTS.enums.ordinal()]++;
            }
        }
    }

    String mapType(ArgDef def, boolean isReturn) {
        if (def.error())
            return def.origText();

        String t = def.type().trim();
        String ptr = def.ptr();
        if (typeDefToNative.containsKey(t)) {
            //Empty structs are just GenericPointers. So if we are not an empty struct then do not change the pointer
            //count. This preserves things like "uint16 *myarg" which should be treated as a java array
            if (emptyStructs.contains(t) || typeDefToNative.get(t).ptrRequired())
                ptr = typeDefToNative.get(t).ptr();
            var tt = typeDefToNative.get(t);
            if (!tt.name().isEmpty())
                t = tt.name();
        }

        long ptrCount = ptr.chars().filter(ch -> ch == '*').count();

        if (isReturn && ptrCount > 0)
            return "MemorySegment";
        if (isReturn && def.type().isEmpty())
            return "void";

//        if (ptrCount > 2)
//            warnings.add("Pointer count of " + ptrCount + " is not supported directly. You may need to manually edit generated code.");

        String base = t.replaceAll("\\s+", " ");

        if (typeDefToNative.containsKey(base))
            base = typeDefToNative.get(base).name();

        base = base.toLowerCase(Locale.ROOT);

        if (base.startsWith("long long") ||
                base.startsWith("unsigned long long")) {
            base = "long long";
        }

        String javaType = switch (base) {
            case "int" -> "int";
            case "short" -> "short";
            case "long", "long long" -> "long";
            case "float" -> "float";
            case "double" -> "double";
            case "bool" -> "boolean";
            case "char" -> "byte";
            case "void" -> "void";
            case "byte" -> "byte";
            default -> t;
        };

        if (ptrCount > 0) {
            if (base.equals("char")) {
                return "String";
            } else if (base.equals("void")) {
                return "byte[]";
            } else if (emptyStructs.contains(javaType)) {
                //this is the case where an empty struct has been defined. Empty structs get treated
                //as new classes that extend GenericPointer
                return javaType;
            } else {

                for (int n = 0; n < ptrCount; n++)
                    javaType = javaType + "[]";
                return javaType;
            }
        }

        return javaType;
    }

    boolean allSequential(List<HeaderDefs.CEnumValue> enums) {
        long curVal = 0;
        for (HeaderDefs.CEnumValue e : enums) {
            if (e.value() != curVal)
                return false;
            curVal++;
        }
        return true;

    }

    String formatParam(ArgDef p) {
        if (p.error())
            return p.origText();

        long ptrCount = p.ptr().chars().filter(ch -> ch == '*' || ch == '[').count();
        String javaType = mapType(p, false);

        long ptrCount2 = javaType.chars().filter(ch -> ch == '*' || ch == '[').count();
        if (ptrCount2 > ptrCount)
            ptrCount = ptrCount2;

//        if (ptrCount > 2)
//            warnings.add("Pointer count of " + ptrCount + " is not supported directly. You may need to manually edit generated code.");

        if (ptrCount == 2)
            return "@PtrPtrArg " + javaType + " " + p.name();
        else {
            return javaType + " " + p.name();
        }
    }

    private void generateJavaRecords(List<CRecord> records) throws IOException {
        for (CRecord r : records) {

//            if (!r.isUsed().value())
//            {
//                System.out.println("Skipping record: " + r.name());
//                continue;
//            }

            Path outFile = destinationPath.resolve(r.name().trim().replace("*", "") + ".java");

            if (Files.exists(outFile))
                Files.delete(outFile);

            try {
                if (!Files.exists(outFile.getParent()))
                    Files.createDirectories(outFile.getParent());
            }
            catch(IOException ex)
            {
                ex.printStackTrace();
            }
            try (PrintWriter out = new PrintWriter(outFile.toFile())) {
                out.println("package " + packageName + ";\n\n");
                out.println("import jpassport.*;");
                out.println("import jpassport.pointers.*;");
                out.println("import jpassport.annotations.*;\n\n");
                out.println("import java.lang.foreign.*;\n\n");


                out.println("/*");
                out.println(r.CCode());
                out.println("*/");
                if (r.isEmptyStruct()) {
                    out.println("import java.lang.foreign.MemorySegment;\n\n");
                    out.println("public class " + r.name() + " extends GenericPointer {");
                    out.println("public " + r.name() + "(MemorySegment addr) {\n" +
                            "        super(addr);\n" +
                            "    }");
                    out.println("}");
                } else {

                    out.println("public record " + r.name() + " (");
                    Set<String> usedNames = new HashSet<>();

                    for (int i = 0; i < r.fields().size(); i++) {
                        var f = r.fields().get(i);
                        String type = f.type.trim();
                        String name = f.name();
                        if (name.isEmpty() || name.equals(f.type()))
                            name = "arg" + i;
                        if (usedNames.contains(f.name()))
                            continue;
                        if (i > 0)
                            out.println(",");
                        String rec = mapRecordType(f, false);
                        if (rec.contains("unnamed") || rec.contains("anonymous"))
                            rec = f.type(); //unnamed structs are a giant pain in my ass.

                        /** This is some BS code. For reasons I don't understand, when asking for
                         * the type name you get Typename::(unnamed [full path to the header])
                         * This code is trying to get rid of the path.
                         */
                        if (type.contains("::(unnamed") )
                        {
                            if (type.endsWith(")"))
                                rec = type.substring(0, type.indexOf("::(unnamed"));
                        }
                        if (type.contains("::(anonymous") && type.endsWith(")"))
                        {
                            rec = type.substring(0, type.indexOf("::(anonymous"));
                        }

                        out.print("    " + rec + " " + name);
                        usedNames.add(f.name());
                    }

                    if (r.kind().equalsIgnoreCase("union"))
                        out.println("    ,UnionFieldIO unionIO\n");
                    out.println("\n) {}\n");
                }

                counts[OUTPUT_OBJECTS.records.ordinal()]++;
            }
        }
    }

        //find arguments defined in C as arg[row][col]
    static Pattern pattArray = Pattern.compile(
            "\\[.*?\\]",
            Pattern.DOTALL
    );

    static String mapRecordType(ArgDef cType, boolean allowAnnotations) {
        if (cType.error())
            return cType.origText();

        String t = cType.type().trim();
        long ptrCount = cType.ptr().chars().filter(ch -> ch == '*').count();

//        if (ptrCount > 2)
//            warnings.add("Pointer count of " + ptrCount + " is not supported directly. You may need to manually edit generated code.");

//        String base = t.replaceAll("\\s+", " ");
        String base = t;

        if (typeDefToNative.containsKey(base))
        {
            var tt = typeDefToNative.get(base);
            if (!tt.name().isEmpty())
                base = tt.name();
            ptrCount = tt.ptr().chars().filter(ch -> ch == '*').count();
        }

        String annotation = "";
        if (ptrCount == 2)
            annotation = "@PtrPtrArg ";
        else if (ptrCount == 1)
            annotation = "@Ptr ";
        else if (cType.ptr().contains("[")) {
            var matcher = pattArray.matcher(cType.ptr());
            String number = "";
//            if (matcher.find()) {
//                String b = matcher.group(0).trim();
//                number = b.substring(1, b.length() - 1);
////                base = base.replace(b, "[]");
//                base = base + "[]";
//            }
            if (!number.isEmpty()) //should not happen for structs and unions
                annotation = "@Array(length = " + number + ") ";
        }

        if (ptrCount > 0) {
            if (base.equals("char")) {
                return "String";
            } else if (base.equals("void")) {
                return "byte[]";
            } else {
                return removeCPtr(annotation + base + "[]");
            }
        }

        if (!allowAnnotations) {annotation = "";}

        return removeCPtr(annotation + base);
    }

    static String removeCPtr(String type)
    {
        if (type.contains("*"))
            type = type.replace("*", "");
        return type;
    }
}

