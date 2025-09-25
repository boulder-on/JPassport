/* Copyright (c) 2021 Duncan McLean, All Rights Reserved
 *
 * The contents of this file is dual-licensed under the
 * Apache License 2.0.
 *
 * You may obtain a copy of the Apache License at:
 *
 * http://www.apache.org/licenses/
 *
 * A copy is also included in the downloadable source code.
 */
#include "library.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <stdbool.h>

double sumD(const double d1, const double d2)
{
    return (d1 + d2);
}

double sumArrD(double *arr, const int count)
{
    if (arr == NULL)
        return 0;

    double r = 0;

    for (int n = 0; n < count; ++n)
        r += arr[n];

    arr[0] = arr[count-1];
    return r;
}

double sumDCritical(const double d1, const double d2)
{
    return (d1 + d2);
}

double sumArrDCritical(double *arr, const int count)
{
    if (arr == NULL)
        return 0;

    double r = 0;

    for (int n = 0; n < count; ++n)
        r += arr[n];

    arr[0] = arr[count-1];
    return r;
}

double sumArrDD(const double *arr,const double *arr2, const int count)
{
    if (arr == NULL || arr2 == NULL)
        return 0;

    double r = 0;

    for (int n = 0; n < count; ++n)
        r += arr[n] + arr2[n];

    return r;
}

void readD(double *val, int set)
{
    *val = (double)set;
}

float sumArrF(const float *arr, const int count)
{
    if (arr == NULL)
        return 0;

    float r = 0;

    for (int n = 0; n < count; ++n)
        r += arr[n];

    return r;
}

void readF(float *val, float set)
{
    *val = set;
}

long long sumArrL(const long long *arr, const long long count)
{
    if (arr == NULL)
        return 0;

    long long r = 0;

    for (int n = 0; n < count; ++n)
        r += arr[n];

    return r;
}

void readL(long long *val, long long set)
{
    *val = set;
}

void readPointer(long long *val, long long set)
{
    val[0] = set;
}

long long getPointer(long long *val, long long set)
{
    val[0] = set;
    return val[0];
}

int swapStrings(char** strings, int i, int j)
{
    char* tmp = strings[i];
    strings[i] = strings[j];
    strings[j] = tmp;
    return strlen(strings[i]) + strlen(strings[j]);
}

int sumArrI(const int *arr, const int count)
{
    if (arr == NULL)
        return 0;

    int r = 0;

    for (int n = 0; n < count; ++n)
        r += arr[n];

    return r;
}

void readI(int *val, int set)
{
    *val = set;
}

short sumArrS(const short *arr, const short count)
{
    short r = 0;

    for (int n = 0; n < count; ++n)
        r += arr[n];

    return r;
}

void readS(short *val, short set)
{
    *val = set;
}

char sumArrB(const char *arr, const char count)
{
    char r = 0;

    for (int n = 0; n < count; ++n)
        r += arr[n];

    return r;
}

void readB(char *val, char set)
{
    *val = set;
}


double sumMatD(int rows, int cols, double mat[rows][cols])
{
    int total = 0;

    for (int yy = 0; yy < rows; ++yy)
    {
        for (int xx = 0; xx < cols; ++xx)
            total += mat[yy][xx];
    }

    return total;
}

double sumMatDPtrPtr(const int rows, const int cols, const double** mat)
{
    double total = 0;

    for (int yy = 0; yy < rows; ++yy)
    {
        for (int xx = 0; xx < cols; ++xx)
            total += mat[yy][xx];
    }
    return total;
}


float sumMatF(int rows, int cols, float mat[rows][cols])
{
    float total = 0;

    for (int yy = 0; yy < rows; ++yy)
    {
        for (int xx = 0; xx < cols; ++xx)
            total += mat[yy][xx];
    }

    return total;
}

float sumMatFPtrPtr(const int rows, const int cols, const float** mat)
{
    float total = 0;

    for (int yy = 0; yy < rows; ++yy)
    {
        for (int xx = 0; xx < cols; ++xx)
            total += mat[yy][xx];
    }
    return total;
}

long long sumMatL(int rows, int cols, long long mat[rows][cols])
{
    long long total = 0;

    for (int yy = 0; yy < rows; ++yy)
    {
        for (int xx = 0; xx < cols; ++xx)
            total += mat[yy][xx];
    }

    return total;
}

long long sumMatLPtrPtr(const int rows, const int cols, const long long** mat)
{
    long long total = 0;

    for (int yy = 0; yy < rows; ++yy)
    {
        for (int xx = 0; xx < cols; ++xx)
            total += mat[yy][xx];
    }
    return total;
}

int sumMatI(int rows, int cols, int mat[rows][cols])
{
    int total = 0;

    for (int yy = 0; yy < rows; ++yy)
    {
        for (int xx = 0; xx < cols; ++xx)
            total += mat[yy][xx];
    }

    return total;
}

int sumMatIPtrPtr(const int rows, const int cols, const int** mat)
{
    int total = 0;

    for (int yy = 0; yy < rows; ++yy)
    {
        for (int xx = 0; xx < cols; ++xx)
            total += mat[yy][xx];
    }
    return total;
}

int sumMatS(int rows, int cols, short mat[rows][cols])
{
    int total = 0;

    for (int yy = 0; yy < rows; ++yy)
    {
        for (int xx = 0; xx < cols; ++xx)
            total += mat[yy][xx];
    }

    return total;
}

int sumMatSPtrPtr(const int rows, const int cols, const short ** mat)
{
    int total = 0;

    for (int yy = 0; yy < rows; ++yy)
    {
        for (int xx = 0; xx < cols; ++xx)
            total += mat[yy][xx];
    }
    return total;
}


int sumMatB(int rows, int cols, char mat[rows][cols])
{
    int total = 0;

    for (int yy = 0; yy < rows; ++yy)
    {
        for (int xx = 0; xx < cols; ++xx)
            total += mat[yy][xx];
    }

    return total;
}

int sumMatBPtrPtr(const int rows, const int cols, const char ** mat)
{
    int total = 0;

    for (int yy = 0; yy < rows; ++yy)
    {
        for (int xx = 0; xx < cols; ++xx)
            total += mat[yy][xx];
    }
    return total;
}

int cstringLength(const char* string)
{
    return strlen(string);
}

char* mallocString(const char* origString)
{
    if (origString == NULL)
        return NULL;

    char* ret = malloc(strlen(origString) * sizeof(char));
    strcpy(ret, origString);
    return ret;
}

double* mallocDoubles(const int count)
{
    if (count <= 0)
        return NULL;

    double* ret = malloc(count *sizeof(double ));

    for (int n = 0; n < count; ++n)
        ret[n] = (double)n;

    return ret;
}

void freeMemory(void *memory)
{
    free(memory);
}

double passStruct(struct PassingData* data)
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

double passComplex(struct ComplexPassing* complex)
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

double passStructWithArrays(struct PassingArrays* structWithArrays)
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

void passMemoryBlock(struct PassMemoryBlock* memoryBlock)
{
    for (int n =0; n < memoryBlock->data_size; ++n)
    {
        memoryBlock->data[n] = n % 10;
    }
}

int call_CB(callbackFN fn, int v, double v2)
{
    int sum = 0;
    for (int n = 0; n < v; n++)
        sum += fn(v, v2);
    return sum;
}


void call_CBArr(callbackFNArr fn,  int* vals, int count)
{
    fn(vals, count);
}

extern int fillChars(char* fillThis, int sizemax)
{
    strncpy(fillThis, "hello world", sizemax);
    return strlen(fillThis);
}

extern int passChars(char* fillThis, int sizemax)
{
    int s = 0;
    for (int n = 0; n < sizemax; ++n)
        s += fillThis[n];

    return s;
}

void* PassPointers(void* hMem)
{
    return hMem;
}

extern void setAnError(int errval)
{
//    printf("Setting error to: %d, %d\n", errval, errno);
    errno = errval;
//    printf("2. Setting error to: %d, %d\n", errval, errno);
}

extern double passStructArrBlock(struct PassingData data[], int count, int multiply)
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

extern double passStructArrPtr(struct PassingData** data, int count, int multiply)
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

double printStruct(struct PassingData* data, char* extra)
{
    double ret = (double)data->s_long + data->s_float + data->s_int + data->s_double;
//    printf("%s - %f = %d + %lld + %f + %f\n",extra, ret, data->s_int, data->s_long, data->s_float, data->s_double);
    return ret;
}

extern double passStructOfStructs(struct PassingStructs* data)
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
