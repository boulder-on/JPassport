//
// Created by dmclean on 2025-10-30.
//

#ifndef FOREIGN_LINK_UNION_EXAMPLES_H
#define FOREIGN_LINK_UNION_EXAMPLES_H

#include "struct_examples.h"

typedef union
{
    int u_i;
    long u_l;
    short u_s;
    float u_f;
    double u_d;
}SimpleUnion;

typedef union
{
    long u_i;
    PassingData u_pd;
    short u_s;
}UnionWithStruct;

typedef union
{
    long long u_i[5];
    long long *u_ip;
}UnionWithArrays;

extern void useSimpleUnion(int idxSrc, int idxDest, SimpleUnion* simple);
extern void useUnionWithStruct(PassingData *srcVals, UnionWithStruct* withStruct);
extern void useUnionWithArray(int direction, UnionWithArrays* withArrays);

extern void useSimpleUnion2(int idxSrc, int idxDest, SimpleUnion* simple);
extern void useUnionWithStruct2(PassingData *srcVals, UnionWithStruct* withStruct);
extern void useUnionWithArray2(int direction, UnionWithArrays* withArrays);


#endif //FOREIGN_LINK_UNION_EXAMPLES_H
