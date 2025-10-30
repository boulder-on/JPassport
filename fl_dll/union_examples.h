//
// Created by dmclean on 2025-10-30.
//

#ifndef FOREIGN_LINK_UNION_EXAMPLES_H
#define FOREIGN_LINK_UNION_EXAMPLES_H

#include "struct_examples.h"

union SimpleUnion
{
    int u_i;
    long u_l;
    short u_s;
    float u_f;
    double u_d;
};

union UnionWithStruct
{
    long u_i;
    struct PassingData u_pd;
    short u_s;
};

union UnionWithArrays
{
    long long u_i[5];
    long long *u_ip;
};

extern void useSimpleUnion(int idxSrc, int idxDest, union SimpleUnion* simple);
extern void useUnionWithStruct(struct PassingData *srcVals, union UnionWithStruct* withStruct);
extern void useUnionWithArray(int direction, union UnionWithArrays* withArrays);

extern void useSimpleUnion2(int idxSrc, int idxDest, union SimpleUnion* simple);
extern void useUnionWithStruct2(struct PassingData *srcVals, union UnionWithStruct* withStruct);
extern void useUnionWithArray2(int direction, union UnionWithArrays* withArrays);


#endif //FOREIGN_LINK_UNION_EXAMPLES_H
