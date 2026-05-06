//
// Created by dmclean on 2025-10-30.
//

#include <stdlib.h>
#include <strings.h>
#include "struct_examples.h"

double passStruct(PassingData* data)
{
    if (data == NULL)
        return -1;

    double ret = 0;
    ret += (double)data->s_long;
    ret += data->s_float;
    ret += data->s_int;
    ret += data->s_double;

    return ret;
}

double passComplex(ComplexPassing* complex)
{
    double ret = passStruct(&complex->s_passingData);
    ret += passStruct(complex->s_ptrPassingData);

    if (complex->s_string == NULL)
        ret -= 1;
    else {
        int len = strlen(complex->s_string);
        for (int n = 0; n < len; ++n)
            complex->s_string[n] -= 32;
    }

    complex->s_ID += 10;
    complex->s_passingData.s_int += 10;
    if (complex->s_ptrPassingData != NULL)
        complex->s_ptrPassingData->s_int +=20;
    return ret;
}

double passStructWithArrays(PassingArrays* structWithArrays)
{
    if (structWithArrays == NULL)
        return -1;

    double ret = 0;
    int countd = sizeof(structWithArrays->s_double)/sizeof(double);
    for (int n = 0; n < countd; ++n)
    {
        ret += structWithArrays->s_double[n];
    }

    int countl = sizeof(structWithArrays->s_long)/sizeof(long long);
    for (int n = 0; n < countl; ++n)
    {
        ret += (double)structWithArrays->s_long[n];
    }

    if (structWithArrays->s_doublePtr != NULL) {
        for (int n = 0; n < structWithArrays->s_doublePtrCount; ++n) {
            ret += structWithArrays->s_doublePtr[n];

            if (n < countd)
                structWithArrays->s_double[n] = structWithArrays->s_doublePtr[n];
        }
    }

    if (structWithArrays->s_longPtr != NULL) {
        for (int n = 0; n < structWithArrays->s_longPtrCount; ++n) {
            ret += (double)structWithArrays->s_longPtr[n];
            if (n < countl)
                structWithArrays->s_longPtr[n] = structWithArrays->s_long[n];
        }
    }

    return ret;
}

void passMemoryBlock(PassMemoryBlock* memoryBlock)
{
    for (int n =0; n < memoryBlock->data_size; ++n)
    {
        memoryBlock->data[n] = n % 10;
    }
}

extern double passStructArrBlock(PassingData data[], int count, int multiply)
{
    double sum = 0;

    for (int n = 0; n < count; ++n)
    {
        sum += passStruct(&data[n]);
        data[n].s_int = multiply * data[n].s_int;
        data[n].s_long = multiply * data[n].s_long;
        data[n].s_float = (float)multiply * data[n].s_float;
        data[n].s_double = multiply * data[n].s_double;
    }
    return sum;
}

extern double passStructArrPtr(PassingData** data, int count, int multiply)
{
    double sum = 0;

    for (int n = 0; n < count; ++n)
    {
        sum += passStruct(data[n]);
        data[n]->s_int = multiply * data[n]->s_int;
        data[n]->s_long = multiply * data[n]->s_long;
        data[n]->s_float = (float)multiply * data[n]->s_float;
        data[n]->s_double = multiply * data[n]->s_double;
    }
    return sum;
}

double printStruct(PassingData* data, char* extra)
{
    double ret = (double)data->s_long + data->s_float + data->s_int + data->s_double;
//    printf("%s - %f = %d + %lld + %f + %f\n",extra, ret, data->s_int, data->s_long, data->s_float, data->s_double);
    return ret;
}

double passStructOfStructs(PassingStructs* data)
{
    double sum = printStruct(&data->s_simple, "no loop");

    for (int n = 0; n < 3; ++n)
    {
        sum += printStruct(&data->array_data[n], "loop 1");
    }

//    printf("Count of ptrs: %d\n", data->countofPtrs);
    for (int n = 0; n < data->countofPtrs; ++n)
    {
        sum += printStruct(data->s_ptrtoptr[n], "loop 2");
    }

    return sum;
}

PassingData retPD;

PassingData returnStruct()
{
//    PassingData* pd = (PassingData *)malloc(sizeof (PassingData));
    
    retPD.s_int = 1;
    retPD.s_long = 2;
    return retPD;
}