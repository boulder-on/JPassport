//
// Created by dmclean on 2025-10-30.
//

#include "callback_examples.h"

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