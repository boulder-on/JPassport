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
#ifndef FOREIGN_LINK_PRIMITIVE_EXAMPLES_H
#define FOREIGN_LINK_PRIMITIVE_EXAMPLES_H

#include <stdbool.h>

extern double sumD(double d1, double d2);
extern double sumArrD(double *arr, int count);
extern double sumDCritical(double d1, double d2);
extern double sumArrDCritical(double *arr, int count);

extern double sumArrDD(const double *arr, const double *arr2, int count);
extern void readD(double *v, int set);
extern double sumMatD(int rows, int cols, double mat[rows][cols]);
extern double sumMatDPtrPtr(int rows, int cols, const double** mat);

extern float sumArrF(const float *arr, int count);
extern void readF(float *val, float set);
extern float sumMatF(int rows, int cols, float mat[rows][cols]);
extern float sumMatFPtrPtr(int rows, int cols, const float** mat);

extern long long sumArrL(const long long *arr, long long count);
extern void readL(long long *val, long long set);
extern long long sumMatL(int rows, int cols, long long mat[rows][cols]);
extern long long sumMatLPtrPtr(int rows, int cols, const long long** mat);

extern int sumArrI(const int *arr, int count);
extern void readI(int *val, int set);
extern int sumMatI(int rows, int cols, int mat[rows][cols]);
extern int sumMatIPtrPtr(int rows, int cols, const int** mat);

extern short sumArrS(const short *arr, short count);
extern void readS(short *val, short set);
extern int sumMatS(int rows, int cols, short mat[rows][cols]);
extern int sumMatSPtrPtr(int rows, int cols, const short ** mat);

extern char sumArrB(const char *arr, char count);
extern void readB(char *val, char set);
extern int sumMatB(int rows, int cols, char mat[rows][cols]);
extern int sumMatBPtrPtr(int rows, int cols, const char ** mat);

extern int cstringLength(const char* string);
extern char* mallocString(const char* origString);
extern double* mallocDoubles(int count);
extern void freeMemory(void *memory);

extern void readPointer(long long *val, long long set);
extern long long getPointer(long long *val, long long set);

extern int fillChars(char* fillThis, int sizemax);
extern int passChars(char* fillThis, int sizemax);



extern void* PassPointers(void* hMem);





extern bool isTrue(bool b);
extern void stripe(int count, bool* vals);
extern bool compareBool(int count, const bool* b1, const bool* b2, bool* ret);
#endif //FOREIGN_LINK_PRIMITIVE_EXAMPLES_H
