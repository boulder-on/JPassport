#include "library.h"

#include <stdio.h>
#include <stdlib.h>
#include <mem.h>


double sumD(const double d1, const double d2)
{
    return (d1 + d2);
}

double sumArrD(const double *arr, const int count)
{
    double r = 0;

    for (int n = 0; n < count; ++n)
        r += arr[n];

    return r;
}

double sumArrDD(const double *arr,const double *arr2, const int count)
{
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
    long long r = 0;

    for (int n = 0; n < count; ++n)
        r += arr[n];

    return r;
}

void readL(long long *val, long long set)
{
    *val = set;
}

int sumArrI(const int *arr, const int count)
{
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
