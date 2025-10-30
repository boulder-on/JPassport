//
// Created by dmclean on 2025-10-30.
//

#include "enum_examples.h"

unsigned long long passEnum(enum intEnum ie, enum longEnum le)
{
    return (long long)ie + le;
}

enum intEnum todayInt(enum intEnum* ie)
{
    *ie = FRIDAY;
    return FRIDAY;
}

enum longEnum todayLong(enum longEnum* le)
{
    *le = SUNDAY;
    return SUNDAY;
}

void todayTransfer(enum intEnum ie, enum intEnum* ie2, enum longEnum le, enum longEnum *le2)
{
    *ie2 = ie;
    *le2 = le;
}

enum retEnum toRetEnum(int v)
{
    return v;
}
