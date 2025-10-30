//
// Created by dmclean on 2025-10-30.
//

#include <stdio.h>
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

void readBackErrCode(int setErr, enum errCodes* code)
{
    code[0] = setErr;
}

extern void passEnumWArrStruct(struct EnumSimpleArraysStruct *enums)
{
    for (int n = 0; n < 3; ++n) {
        enum intEnum tmp = enums->allDays[n];
        enums->allDays[n] = enums->allDaysPtr[n];
        enums->allDaysPtr[n] = tmp;
    }
}

long long passSimpleEnumStruct(struct EnumStruct *enums)
{
    enums->eiValue = enums->ivalue;
    enums->elValue = enums->lvalue;
    return enums->ivalue + enums->lvalue;
}

extern void passComplexStructEnum(struct EnumArraysStruct *enums)
{
    for (int n = 0; n < 3; ++n) {
        enum intEnum tmp = enums->allDays[n];
        enums->allDays[n] = enums->allDaysPtr[n];
        enums->allDaysPtr[n] = tmp;
    }

    enums->error = no_file;
    enums->weekend = SUNDAY;
    enums->weekends[0] = SATURDAY;
    enums->weekends[1] = SUNDAY;

}