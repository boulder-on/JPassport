//
// Created by dmclean on 2025-10-30.
//
#include <stdlib.h>
#include "union_examples.h"

void useSimpleUnion(int idxSrc, int idxDest, SimpleUnion* simple)
{
    if (idxSrc < 0 || idxSrc > 4 || idxDest < 0 || idxDest > 4)
        return;

    double v = -1;
    switch(idxSrc)
    {
        case 0:
            v = simple->u_s;
            break;
        case 1:
            v = simple->u_i;
            break;
        case 2:
            v = (double)simple->u_l;
            break;
        case 3:
            v = simple->u_f;
            break;
        case 4:
            v = simple->u_d;
            break;
    }

    switch (idxDest) {
        case 0:
            simple->u_s = (short)v;
            break;
        case 1:
            simple->u_i = (int)v;
            break;
        case 2:
            simple->u_l = (long)v;
            break;
        case 3:
            simple->u_f = (float)v;
            break;
        case 4:
            simple->u_d = v;
            break;
    }
}

void useSimpleUnion2(int idxSrc, int idxDest, SimpleUnion* simple)
{
    useSimpleUnion(idxSrc, idxDest, simple);
}

void useUnionWithStruct(PassingData *srcVals, UnionWithStruct* withStruct)
{
    withStruct->u_pd.s_int = srcVals->s_int;
    withStruct->u_pd.s_long = srcVals->s_long;
    withStruct->u_pd.s_float = srcVals->s_float;
    withStruct->u_pd.s_double = srcVals->s_double;
}

void useUnionWithStruct2(PassingData *srcVals, UnionWithStruct* withStruct)
{
    useUnionWithStruct(srcVals, withStruct);
}

void useUnionWithArray(int direction, UnionWithArrays* withArrays) {
    if (direction == 1) {
        long long *data = malloc(sizeof (long long) * 5);
        for (int n = 0; n < 5; ++n)
        {
            data[n] = withArrays->u_i[n];
        }

        withArrays->u_ip = data;
    }
    else
    {
        long long data[5];
        for (int n = 0; n < 5; ++n)
            data[n] = withArrays->u_ip[n];
        for (int n = 0; n < 5; ++n)
            withArrays->u_i[n] = data[n];
    }

}

void useUnionWithArray2(int direction, UnionWithArrays* withArrays) {
    useUnionWithArray(direction, withArrays);
}
