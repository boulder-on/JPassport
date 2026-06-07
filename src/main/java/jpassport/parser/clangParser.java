package jpassport.parser;

import jpassport.Passport;
import jpassport.annotations.RefArg;
import jpassport.parser.clang.types.*;
import jpassport.pointers.FunctionPtr;

import java.lang.foreign.MemorySegment;

public interface clangParser extends Passport {
    CXIndex clang_createIndex(int excludeDeclarationsFromPCH,
                              int displayDiagnostics);

    CXTranslationUnit clang_createTranslationUnit(CXIndex CIdx, String ast_filename);
    CXTranslationUnit clang_createTranslationUnitFromSourceFile(
            CXIndex CIdx, String source_filename, int num_clang_command_line_args,
            String[] clang_command_line_args, int num_unsaved_files,
            CXUnsavedFile []unsaved_files);

    CXCursor clang_getTranslationUnitCursor(CXTranslationUnit tu);
    void clang_disposeTranslationUnit(CXTranslationUnit tu);
    void clang_disposeIndex(CXIndex index);

    int clang_visitChildren(MemorySegment parent,
                             FunctionPtr visitor,
                             MemorySegment client_data);

    CXCursorKind clang_getCursorKind(MemorySegment cursor);
    CXString clang_getCursorSpelling(MemorySegment cursor);
    String clang_getCString(CXString string);
    void clang_disposeString(MemorySegment string);
    long clang_isCursorDefinition(MemorySegment cursor);
    CXType clang_getCursorType(MemorySegment C);
    CXString clang_getTypeSpelling(CXType CT);
    long clang_getEnumConstantDeclValue(MemorySegment C);
//    long clang_Type_getSizeOf(CXType T);
//    CXType clang_Type_getValueType(CXType CT);
    CXType clang_getTypedefDeclUnderlyingType(MemorySegment cursor);
    CXType clang_getCursorResultType(MemorySegment cursor);

//    CXType clang_getArgType(CXType T, int i);
    CXSourceRange clang_getCursorExtent(MemorySegment cursor);
    void clang_tokenize(CXTranslationUnit TU, MemorySegment Range,
                        MemorySegment Tokens, @RefArg int[] NumTokens);
    CXString clang_getTokenSpelling(CXTranslationUnit tu, MemorySegment token);
    void clang_disposeTokens(CXTranslationUnit TU, MemorySegment Tokens,
                             int NumTokens);
    CXType clang_getCanonicalType(CXType T);
    CXType clang_getPointeeType(CXType T);
    CXType clang_getArrayElementType(CXType T);
    long clang_getArraySize(CXType T);
    MemorySegment clang_getCursorSemanticParent(MemorySegment cursor);
//    MemorySegment clang_getTypeDeclaration(CXType cxtype);
}
