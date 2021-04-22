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

double sumD(double d1, double d2);
double sumArrD(const double *arr, int count);
double sumArrDD(const double *arr, const double *arr2, int count);
void readD(double *v, int set);

#endif //FL_DLL_LIBRARY_H
