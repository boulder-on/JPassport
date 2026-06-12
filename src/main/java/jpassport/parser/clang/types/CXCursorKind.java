package jpassport.parser.clang.types;

import jpassport.enums.EnumInt;

public enum CXCursorKind implements EnumInt {
    /* Declarations */
    CXCursor_UnexposedDecl(1),
    /** A C or C++ struct. */
    CXCursor_StructDecl(2),
    /** A C or C++ union. */
    CXCursor_UnionDecl(3),
    /** A C++ class. */
    CXCursor_ClassDecl(4),
    /** An enumeration. */
    CXCursor_EnumDecl(5),
    /**
     * A field (in C) or non-static data member (in C++) in a
     * struct), union), or C++ class.
     */
    CXCursor_FieldDecl(6),
    /** An enumerator constant. */
    CXCursor_EnumConstantDecl(7),
    /** A function. */
    CXCursor_FunctionDecl(8),
    /** A variable. */
    CXCursor_VarDecl(9),
    /** A function or method parameter. */
    CXCursor_ParmDecl(10),
    /** An Objective-C \@interface. */
    CXCursor_ObjCInterfaceDecl(11),
    /** An Objective-C \@interface for a category. */
    CXCursor_ObjCCategoryDecl(12),
    /** An Objective-C \@protocol declaration. */
    CXCursor_ObjCProtocolDecl(13),
    /** An Objective-C \@property declaration. */
    CXCursor_ObjCPropertyDecl(14),
    /** An Objective-C instance variable. */
    CXCursor_ObjCIvarDecl(15),
    /** An Objective-C instance method. */
    CXCursor_ObjCInstanceMethodDecl(16),
    /** An Objective-C class method. */
    CXCursor_ObjCClassMethodDecl(17),
    /** An Objective-C \@implementation. */
    CXCursor_ObjCImplementationDecl(18),
    /** An Objective-C \@implementation for a category. */
    CXCursor_ObjCCategoryImplDecl(19),
    /** A typedef. */
    CXCursor_TypedefDecl(20),
    /** A C++ class method. */
    CXCursor_CXXMethod(21),
    /** A C++ namespace. */
    CXCursor_Namespace(22),
    /** A linkage specification), e.g. 'extern "C"'. */
    CXCursor_LinkageSpec(23),
    /** A C++ constructor. */
    CXCursor_Constructor(24),
    /** A C++ destructor. */
    CXCursor_Destructor(25),
    /** A C++ conversion function. */
    CXCursor_ConversionFunction(26),
    /** A C++ template type parameter. */
    CXCursor_TemplateTypeParameter(27),
    /** A C++ non-type template parameter. */
    CXCursor_NonTypeTemplateParameter(28),
    /** A C++ template template parameter. */
    CXCursor_TemplateTemplateParameter(29),
    /** A C++ function template. */
    CXCursor_FunctionTemplate(30),
    /** A C++ class template. */
    CXCursor_ClassTemplate(31),
    /** A C++ class template partial specialization. */
    CXCursor_ClassTemplatePartialSpecialization(32),
    /** A C++ namespace alias declaration. */
    CXCursor_NamespaceAlias(33),
    /** A C++ using directive. */
    CXCursor_UsingDirective(34),
    /** A C++ using declaration. */
    CXCursor_UsingDeclaration(35),
    /** A C++ alias declaration */
    CXCursor_TypeAliasDecl(36),
    /** An Objective-C \@synthesize definition. */
    CXCursor_ObjCSynthesizeDecl(37),
    /** An Objective-C \@dynamic definition. */
    CXCursor_ObjCDynamicDecl(38),
    /** An access specifier. */
    CXCursor_CXXAccessSpecifier(39),

    CXCursor_FirstDecl(CXCursor_UnexposedDecl.value),
    CXCursor_LastDecl(CXCursor_CXXAccessSpecifier.value),

    /* References */
    CXCursor_FirstRef(40), /* Decl references */
    CXCursor_ObjCSuperClassRef(40),
    CXCursor_ObjCProtocolRef(41),
    CXCursor_ObjCClassRef(42),
    CXCursor_TypeRef(43),
    CXCursor_CXXBaseSpecifier(44),
    /**
     * A reference to a class template), function template), template
     * template parameter), or class template partial specialization.
     */
    CXCursor_TemplateRef(45),
    /**
     * A reference to a namespace or namespace alias.
     */
    CXCursor_NamespaceRef(46),
    /**
     * A reference to a member of a struct), union), or class that occurs in
     * some non-expression context), e.g.), a designated initializer.
     */
    CXCursor_MemberRef(47),
    CXCursor_LabelRef(48),
    CXCursor_OverloadedDeclRef(49),
    CXCursor_VariableRef(50),

    CXCursor_LastRef(CXCursor_VariableRef.value),

    /* Error conditions */
    CXCursor_FirstInvalid(70),
    CXCursor_InvalidFile(70),
    CXCursor_NoDeclFound(71),
    CXCursor_NotImplemented(72),
    CXCursor_InvalidCode(73),
    CXCursor_LastInvalid(CXCursor_InvalidCode.value),

    /* Expressions */
    CXCursor_FirstExpr(100),

    /**
     * An expression whose specific kind is not exposed via this
     * interface.
     *
     * Unexposed expressions have the same operations as any other kind
     * of expression; one can extract their location information),
     * spelling), children), etc. However), the specific kind of the
     * expression is not reported.
     */
    CXCursor_UnexposedExpr(100),

    /**
     * An expression that refers to some value declaration), such
     * as a function), variable), or enumerator.
     */
    CXCursor_DeclRefExpr(101),

    /**
     * An expression that refers to a member of a struct), union),
     * class), Objective-C class), etc.
     */
    CXCursor_MemberRefExpr(102),

    /** An expression that calls a function. */
    CXCursor_CallExpr(103),

    /** An expression that sends a message to an Objective-C
     object or class. */
    CXCursor_ObjCMessageExpr(104),

    /** An expression that represents a block literal. */
    CXCursor_BlockExpr(105),

    /** An integer literal.
     */
    CXCursor_IntegerLiteral(106),

    /** A floating point number literal.
     */
    CXCursor_FloatingLiteral(107),

    /** An imaginary number literal.
     */
    CXCursor_ImaginaryLiteral(108),

    /** A string literal.
     */
    CXCursor_StringLiteral(109),

    /** A character literal.
     */
    CXCursor_CharacterLiteral(110),

    /** A parenthesized expression), e.g. "(1)".
     *
     * This AST node is only formed if full location information is requested.
     */
    CXCursor_ParenExpr(111),

    /** This represents the unary-expression's (except sizeof and
     * alignof).
     */
    CXCursor_UnaryOperator(112),

    /** [C99 6.5.2.1] Array Subscripting.
     */
    CXCursor_ArraySubscriptExpr(113),
    CXCursor_BinaryOperator(114),

    /** Compound assignment such as "+=".
     */
    CXCursor_CompoundAssignOperator(115),

    /** The ?: ternary operator.
     */
    CXCursor_ConditionalOperator(116),

    /** An explicit cast in C (C99 6.5.4) or a C-style cast in C++
     * (C++ [expr.cast])), which uses the syntax (Type)expr.
     *
     * For example: (int)f.
     */
    CXCursor_CStyleCastExpr(117),

    /** [C99 6.5.2.5]
     */
    CXCursor_CompoundLiteralExpr(118),

    /** Describes an C or C++ initializer list.
     */
    CXCursor_InitListExpr(119),
    CXCursor_AddrLabelExpr(120),
    CXCursor_StmtExpr(121),

    CXCursor_GenericSelectionExpr(122),
    CXCursor_GNUNullExpr(123),
    CXCursor_CXXStaticCastExpr(124),
    CXCursor_CXXDynamicCastExpr(125),
    CXCursor_CXXReinterpretCastExpr(126),
    CXCursor_CXXConstCastExpr(127),
    CXCursor_CXXFunctionalCastExpr(128),

    /** A C++ typeid expression (C++ [expr.typeid]).
     */
    CXCursor_CXXTypeidExpr(129),

    /** [C++ 2.13.5] C++ Boolean Literal.
     */
    CXCursor_CXXBoolLiteralExpr(130),

    /** [C++0x 2.14.7] C++ Pointer Literal.
     */
    CXCursor_CXXNullPtrLiteralExpr(131),

    /** Represents the "this" expression in C++
     */
    CXCursor_CXXThisExpr(132),

    /** [C++ 15] C++ Throw Expression.
     *
     * This handles 'throw' and 'throw' assignment-expression. When
     * assignment-expression isn't present), Op will be null.
     */
    CXCursor_CXXThrowExpr(133),

    /** A new expression for memory allocation and constructor calls), e.g:
     * "new CXXNewExpr(foo)".
     */
    CXCursor_CXXNewExpr(134),

    /** A delete expression for memory deallocation and destructor calls),
     * e.g. "delete[] pArray".
     */
    CXCursor_CXXDeleteExpr(135),

    /** A unary expression. (noexcept), sizeof), or other traits)
     */
    CXCursor_UnaryExpr(136),

    /** An Objective-C string literal i.e. @"foo".
     */
    CXCursor_ObjCStringLiteral(137),

    /** An Objective-C \@encode expression.
     */
    CXCursor_ObjCEncodeExpr(138),

    /** An Objective-C \@selector expression.
     */
    CXCursor_ObjCSelectorExpr(139),

    /** An Objective-C \@protocol expression.
     */
    CXCursor_ObjCProtocolExpr(140),

    /** An Objective-C "bridged" cast expression), which casts between
     * Objective-C pointers and C pointers), transferring ownership in the process.
     *
     * \code
     *   NSString *str((__bridge_transfer NSString *)CFCreateString();
     * \endcode
     */
    CXCursor_ObjCBridgedCastExpr(141),
    CXCursor_PackExpansionExpr(142),
    CXCursor_SizeOfPackExpr(143),
    CXCursor_LambdaExpr(144),

    /** Objective-c Boolean Literal.
     */
    CXCursor_ObjCBoolLiteralExpr(145),

    /** Represents the "self" expression in an Objective-C method.
     */
    CXCursor_ObjCSelfExpr(146),

    /** OpenMP 5.0 [2.1.5), Array Section].
     */
    CXCursor_OMPArraySectionExpr(147),

    /** Represents an @available(...) check.
     */
    CXCursor_ObjCAvailabilityCheckExpr(148),

    /**
     * Fixed point literal
     */
    CXCursor_FixedPointLiteral(149),

    /** OpenMP 5.0 [2.1.4), Array Shaping].
     */
    CXCursor_OMPArrayShapingExpr(150),

    /**
     * OpenMP 5.0 [2.1.6 Iterators]
     */
    CXCursor_OMPIteratorExpr(151),
    CXCursor_CXXAddrspaceCastExpr(152),

    CXCursor_LastExpr(CXCursor_CXXAddrspaceCastExpr.value),

    /* Statements */
    CXCursor_FirstStmt(200),
    /**
     * A statement whose specific kind is not exposed via this
     * interface.
     *
     * Unexposed statements have the same operations as any other kind of
     * statement; one can extract their location information), spelling),
     * children), etc. However), the specific kind of the statement is not
     * reported.
     */
    CXCursor_UnexposedStmt(200),

    /** A labelled statement in a function.
     *
     * This cursor kind is used to describe the "start_over:" label statement in
     * the following example:
     *
     * \code
     *   start_over:
     *     ++counter;
     * \endcode
     *
     */
    CXCursor_LabelStmt(201),

    /** A group of statements like { stmt stmt }.
     *
     * This cursor kind is used to describe compound statements), e.g. function
     * bodies.
     */
    CXCursor_CompoundStmt(202),

    /** A case statement.
     */
    CXCursor_CaseStmt(203),

    /** A default statement.
     */
    CXCursor_DefaultStmt(204),

    /** An if statement
     */
    CXCursor_IfStmt(205),

    /** A switch statement.
     */
    CXCursor_SwitchStmt(206),

    /** A while statement.
     */
    CXCursor_WhileStmt(207),

    /** A do statement.
     */
    CXCursor_DoStmt(208),

    /** A for statement.
     */
    CXCursor_ForStmt(209),

    /** A goto statement.
     */
    CXCursor_GotoStmt(210),

    /** An indirect goto statement.
     */
    CXCursor_IndirectGotoStmt(211),

    /** A continue statement.
     */
    CXCursor_ContinueStmt(212),

    /** A break statement.
     */
    CXCursor_BreakStmt(213),

    /** A return statement.
     */
    CXCursor_ReturnStmt(214),

    /** A GCC inline assembly statement extension.
     */
    CXCursor_GCCAsmStmt(215),
    CXCursor_AsmStmt(CXCursor_GCCAsmStmt.value),

    /** Objective-C's overall \@try-\@catch-\@finally statement.
     */
    CXCursor_ObjCAtTryStmt(216),

    /** Objective-C's \@catch statement.
     */
    CXCursor_ObjCAtCatchStmt(217),

    /** Objective-C's \@finally statement.
     */
    CXCursor_ObjCAtFinallyStmt(218),

    /** Objective-C's \@throw statement.
     */
    CXCursor_ObjCAtThrowStmt(219),

    /** Objective-C's \@synchronized statement.
     */
    CXCursor_ObjCAtSynchronizedStmt(220),

    /** Objective-C's autorelease pool statement.
     */
    CXCursor_ObjCAutoreleasePoolStmt(221),

    /** Objective-C's collection statement.
     */
    CXCursor_ObjCForCollectionStmt(222),

    /** C++'s catch statement.
     */
    CXCursor_CXXCatchStmt(223),

    /** C++'s try statement.
     */
    CXCursor_CXXTryStmt(224),

    /** C++'s for (* : *) statement.
     */
    CXCursor_CXXForRangeStmt(225),

    /** Windows Structured Exception Handling's try statement.
     */
    CXCursor_SEHTryStmt(226),

    /** Windows Structured Exception Handling's except statement.
     */
    CXCursor_SEHExceptStmt(227),

    /** Windows Structured Exception Handling's finally statement.
     */
    CXCursor_SEHFinallyStmt(228),

    /** A MS inline assembly statement extension.
     */
    CXCursor_MSAsmStmt(229),

    /** The null statement ";": C99 6.8.3p3.
     *
     * This cursor kind is used to describe the null statement.
     */
    CXCursor_NullStmt(230),

    /** Adaptor class for mixing declarations with statements and
     * expressions.
     */
    CXCursor_DeclStmt(231),

    /** OpenMP parallel directive.
     */
    CXCursor_OMPParallelDirective(232),

    /** OpenMP SIMD directive.
     */
    CXCursor_OMPSimdDirective(233),

    /** OpenMP for directive.
     */
    CXCursor_OMPForDirective(234),

    /** OpenMP sections directive.
     */
    CXCursor_OMPSectionsDirective(235),

    /** OpenMP section directive.
     */
    CXCursor_OMPSectionDirective(236),

    /** OpenMP single directive.
     */
    CXCursor_OMPSingleDirective(237),

    /** OpenMP parallel for directive.
     */
    CXCursor_OMPParallelForDirective(238),

    /** OpenMP parallel sections directive.
     */
    CXCursor_OMPParallelSectionsDirective(239),

    /** OpenMP task directive.
     */
    CXCursor_OMPTaskDirective(240),

    /** OpenMP master directive.
     */
    CXCursor_OMPMasterDirective(241),

    /** OpenMP critical directive.
     */
    CXCursor_OMPCriticalDirective(242),

    /** OpenMP taskyield directive.
     */
    CXCursor_OMPTaskyieldDirective(243),

    /** OpenMP barrier directive.
     */
    CXCursor_OMPBarrierDirective(244),

    /** OpenMP taskwait directive.
     */
    CXCursor_OMPTaskwaitDirective(245),

    /** OpenMP flush directive.
     */
    CXCursor_OMPFlushDirective(246),

    /** Windows Structured Exception Handling's leave statement.
     */
    CXCursor_SEHLeaveStmt(247),

    /** OpenMP ordered directive.
     */
    CXCursor_OMPOrderedDirective(248),

    /** OpenMP atomic directive.
     */
    CXCursor_OMPAtomicDirective(249),

    /** OpenMP for SIMD directive.
     */
    CXCursor_OMPForSimdDirective(250),

    /** OpenMP parallel for SIMD directive.
     */
    CXCursor_OMPParallelForSimdDirective(251),

    /** OpenMP target directive.
     */
    CXCursor_OMPTargetDirective(252),

    /** OpenMP teams directive.
     */
    CXCursor_OMPTeamsDirective(253),

    /** OpenMP taskgroup directive.
     */
    CXCursor_OMPTaskgroupDirective(254),

    /** OpenMP cancellation point directive.
     */
    CXCursor_OMPCancellationPointDirective(255),

    /** OpenMP cancel directive.
     */
    CXCursor_OMPCancelDirective(256),

    /** OpenMP target data directive.
     */
    CXCursor_OMPTargetDataDirective(257),

    /** OpenMP taskloop directive.
     */
    CXCursor_OMPTaskLoopDirective(258),

    /** OpenMP taskloop simd directive.
     */
    CXCursor_OMPTaskLoopSimdDirective(259),

    /** OpenMP distribute directive.
     */
    CXCursor_OMPDistributeDirective(260),

    /** OpenMP target enter data directive.
     */
    CXCursor_OMPTargetEnterDataDirective(261),

    /** OpenMP target exit data directive.
     */
    CXCursor_OMPTargetExitDataDirective(262),

    /** OpenMP target parallel directive.
     */
    CXCursor_OMPTargetParallelDirective(263),

    /** OpenMP target parallel for directive.
     */
    CXCursor_OMPTargetParallelForDirective(264),

    /** OpenMP target update directive.
     */
    CXCursor_OMPTargetUpdateDirective(265),

    /** OpenMP distribute parallel for directive.
     */
    CXCursor_OMPDistributeParallelForDirective(266),

    /** OpenMP distribute parallel for simd directive.
     */
    CXCursor_OMPDistributeParallelForSimdDirective(267),

    /** OpenMP distribute simd directive.
     */
    CXCursor_OMPDistributeSimdDirective(268),

    /** OpenMP target parallel for simd directive.
     */
    CXCursor_OMPTargetParallelForSimdDirective(269),

    /** OpenMP target simd directive.
     */
    CXCursor_OMPTargetSimdDirective(270),

    /** OpenMP teams distribute directive.
     */
    CXCursor_OMPTeamsDistributeDirective(271),

    /** OpenMP teams distribute simd directive.
     */
    CXCursor_OMPTeamsDistributeSimdDirective(272),

    /** OpenMP teams distribute parallel for simd directive.
     */
    CXCursor_OMPTeamsDistributeParallelForSimdDirective(273),

    /** OpenMP teams distribute parallel for directive.
     */
    CXCursor_OMPTeamsDistributeParallelForDirective(274),

    /** OpenMP target teams directive.
     */
    CXCursor_OMPTargetTeamsDirective(275),

    /** OpenMP target teams distribute directive.
     */
    CXCursor_OMPTargetTeamsDistributeDirective(276),

    /** OpenMP target teams distribute parallel for directive.
     */
    CXCursor_OMPTargetTeamsDistributeParallelForDirective(277),

    /** OpenMP target teams distribute parallel for simd directive.
     */
    CXCursor_OMPTargetTeamsDistributeParallelForSimdDirective(278),

    /** OpenMP target teams distribute simd directive.
     */
    CXCursor_OMPTargetTeamsDistributeSimdDirective(279),

    /** C++2a std::bit_cast expression.
     */
    CXCursor_BuiltinBitCastExpr(280),

    /** OpenMP master taskloop directive.
     */
    CXCursor_OMPMasterTaskLoopDirective(281),

    /** OpenMP parallel master taskloop directive.
     */
    CXCursor_OMPParallelMasterTaskLoopDirective(282),

    /** OpenMP master taskloop simd directive.
     */
    CXCursor_OMPMasterTaskLoopSimdDirective(283),

    /** OpenMP parallel master taskloop simd directive.
     */
    CXCursor_OMPParallelMasterTaskLoopSimdDirective(284),

    /** OpenMP parallel master directive.
     */
    CXCursor_OMPParallelMasterDirective(285),

    /** OpenMP depobj directive.
     */
    CXCursor_OMPDepobjDirective(286),

    /** OpenMP scan directive.
     */
    CXCursor_OMPScanDirective(287),

    /** OpenMP tile directive.
     */
    CXCursor_OMPTileDirective(288),

    /** OpenMP canonical loop.
     */
    CXCursor_OMPCanonicalLoop(289),

    /** OpenMP interop directive.
     */
    CXCursor_OMPInteropDirective(290),

    /** OpenMP dispatch directive.
     */
    CXCursor_OMPDispatchDirective(291),

    /** OpenMP masked directive.
     */
    CXCursor_OMPMaskedDirective(292),

    /** OpenMP unroll directive.
     */
    CXCursor_OMPUnrollDirective(293),

    CXCursor_LastStmt(CXCursor_OMPUnrollDirective.value),

    /**
     * Cursor that represents the translation unit itself.
     *
     * The translation unit cursor exists primarily to act as the root
     * cursor for traversing the contents of a translation unit.
     */
    CXCursor_TranslationUnit(300),

    /* Attributes */
    CXCursor_FirstAttr(400),
    /**
     * An attribute whose specific kind is not exposed via this
     * interface.
     */
    CXCursor_UnexposedAttr(400),

    CXCursor_IBActionAttr(401),
    CXCursor_IBOutletAttr(402),
    CXCursor_IBOutletCollectionAttr(403),
    CXCursor_CXXFinalAttr(404),
    CXCursor_CXXOverrideAttr(405),
    CXCursor_AnnotateAttr(406),
    CXCursor_AsmLabelAttr(407),
    CXCursor_PackedAttr(408),
    CXCursor_PureAttr(409),
    CXCursor_ConstAttr(410),
    CXCursor_NoDuplicateAttr(411),
    CXCursor_CUDAConstantAttr(412),
    CXCursor_CUDADeviceAttr(413),
    CXCursor_CUDAGlobalAttr(414),
    CXCursor_CUDAHostAttr(415),
    CXCursor_CUDASharedAttr(416),
    CXCursor_VisibilityAttr(417),
    CXCursor_DLLExport(418),
    CXCursor_DLLImport(419),
    CXCursor_NSReturnsRetained(420),
    CXCursor_NSReturnsNotRetained(421),
    CXCursor_NSReturnsAutoreleased(422),
    CXCursor_NSConsumesSelf(423),
    CXCursor_NSConsumed(424),
    CXCursor_ObjCException(425),
    CXCursor_ObjCNSObject(426),
    CXCursor_ObjCIndependentClass(427),
    CXCursor_ObjCPreciseLifetime(428),
    CXCursor_ObjCReturnsInnerPointer(429),
    CXCursor_ObjCRequiresSuper(430),
    CXCursor_ObjCRootClass(431),
    CXCursor_ObjCSubclassingRestricted(432),
    CXCursor_ObjCExplicitProtocolImpl(433),
    CXCursor_ObjCDesignatedInitializer(434),
    CXCursor_ObjCRuntimeVisible(435),
    CXCursor_ObjCBoxable(436),
    CXCursor_FlagEnum(437),
    CXCursor_ConvergentAttr(438),
    CXCursor_WarnUnusedAttr(439),
    CXCursor_WarnUnusedResultAttr(440),
    CXCursor_AlignedAttr(441),
    CXCursor_LastAttr(CXCursor_AlignedAttr.value),

    /* Preprocessing */
    CXCursor_PreprocessingDirective(500),
    CXCursor_MacroDefinition(501),
    CXCursor_MacroExpansion(502),
    CXCursor_MacroInstantiation(CXCursor_MacroExpansion.value),
    CXCursor_InclusionDirective(503),
    CXCursor_FirstPreprocessing(CXCursor_PreprocessingDirective.value),
    CXCursor_LastPreprocessing(CXCursor_InclusionDirective.value),

    /* Extra Declarations */
    /**
     * A module import declaration.
     */
    CXCursor_ModuleImportDecl(600),
    CXCursor_TypeAliasTemplateDecl(601),
    /**
     * A static_assert or _Static_assert node
     */
    CXCursor_StaticAssert(602),
    /**
     * a friend declaration.
     */
    CXCursor_FriendDecl(603),
    CXCursor_FirstExtraDecl(CXCursor_ModuleImportDecl.value),
    CXCursor_LastExtraDecl(CXCursor_FriendDecl.value),

    /**
     * A code completion overload candidate.
     */
    CXCursor_OverloadCandidate(700);

    private final int value;
    CXCursorKind(int v) {
        value = v;
    }

    @Override
    public int getCValue() {
        return value;
    }
}
