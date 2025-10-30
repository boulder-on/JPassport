//
// Created by dmclean on 2025-10-30.
//

#ifndef FOREIGN_LINK_STRUCT_EXAMPLES_H
#define FOREIGN_LINK_STRUCT_EXAMPLES_H

struct PassingData
{
    int s_int;
    long long s_long;
    float s_float;
    double s_double;
};

struct ComplexPassing
{
    int s_ID;
    struct PassingData s_passingData;
    struct PassingData* s_ptrPassingData;
    char* s_string;
};

struct PassingStructs
{
    struct PassingData s_simple;
    struct PassingData array_data[3];
    int countofPtrs;
    struct PassingData** s_ptrtoptr;
};

struct PassingArrays
{
    double s_double[5];
    long long s_long[8];
    long long s_doublePtrCount;
    double* s_doublePtr;
    long long s_longPtrCount;
    long long* s_longPtr;
};

struct PassMemoryBlock
{
    char *data;
    int data_size;
};

extern double passStruct(struct PassingData* data);
extern double passComplex(struct ComplexPassing* complex);
extern double passStructWithArrays(struct PassingArrays* structWithArrays);
extern void passMemoryBlock(struct PassMemoryBlock* memoryBlock);
extern double passStructOfStructs(struct PassingStructs* data);
extern double passStructArrBlock(struct PassingData data[], int count, int multiply);
extern double passStructArrPtr(struct PassingData** data, int count, int multiply);


#endif //FOREIGN_LINK_STRUCT_EXAMPLES_H
