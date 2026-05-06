//
// Created by dmclean on 2025-10-30.
//

#ifndef FOREIGN_LINK_STRUCT_EXAMPLES_H
#define FOREIGN_LINK_STRUCT_EXAMPLES_H

typedef struct
{
    int s_int;
    long long s_long;
    float s_float;
    double s_double;
}PassingData;

typedef struct
{
    int s_ID;
    PassingData s_passingData;
    PassingData* s_ptrPassingData;
    char* s_string;
}ComplexPassing;

typedef struct
{
    PassingData s_simple;
    PassingData array_data[3];
    int countofPtrs;
    PassingData** s_ptrtoptr;
}PassingStructs;

typedef struct
{
    double s_double[5];
    long long s_long[8];
    long long s_doublePtrCount;
    double* s_doublePtr;
    long long s_longPtrCount;
    long long* s_longPtr;
}PassingArrays;

typedef struct
{
    char *data;
    int data_size;
}PassMemoryBlock;

extern double passStruct(PassingData* data);
extern double passComplex(ComplexPassing* complex);
extern double passStructWithArrays(PassingArrays* structWithArrays);
extern void passMemoryBlock(PassMemoryBlock* memoryBlock);
extern double passStructOfStructs(PassingStructs* data);
extern double passStructArrBlock(PassingData data[], int count, int multiply);
extern double passStructArrPtr(PassingData** data, int count, int multiply);

extern PassingData returnStruct();

#endif //FOREIGN_LINK_STRUCT_EXAMPLES_H
