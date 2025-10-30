//
// Created by dmclean on 2025-10-30.
//

#ifndef FOREIGN_LINK_CALLBACK_EXAMPLES_H
#define FOREIGN_LINK_CALLBACK_EXAMPLES_H

typedef int (*callbackFN) (int, double);
extern int call_CB(callbackFN fn, int, double);

typedef int (*callbackFNArr) (int*, int);
extern void call_CBArr(callbackFNArr fn, int*, int);

#endif //FOREIGN_LINK_CALLBACK_EXAMPLES_H
