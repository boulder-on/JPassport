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
#ifndef FL_DLL_LIBRARY_H
#define FL_DLL_LIBRARY_H

extern double sumD(double d1, double d2);
extern double sumArrD(const double *arr, int count);
extern double sumArrDD(const double *arr, const double *arr2, int count);
extern void readD(double *v, int set);
extern double sumMatD(int rows, int cols, double mat[rows][cols]);
extern double sumMatDPtrPtr(const int rows, const int cols, const double** mat);

extern char* mallocString(const char* origString);

#endif //FL_DLL_LIBRARY_H
