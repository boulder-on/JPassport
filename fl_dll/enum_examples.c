//
// Created by dmclean on 2025-10-30.
//

#include <stdio.h>
#include "enum_examples.h"

unsigned long long passEnum(intEnum ie, longEnum le)
{
    return (long long)ie + le;
}

intEnum todayInt(intEnum* ie)
{
    *ie = FRIDAY;
    return FRIDAY;
}

longEnum todayLong(longEnum* le)
{
    *le = SUNDAY;
    return SUNDAY;
}

void enumwithUnion(int field, long long value, EnumUnion* eu)
{
    if (field == 1)
        eu->u_err = (int)value;
    else if (field == 2)
        eu->u_weekday = (int)value;
    else if (field == 3)
        eu->u_weekend = value;
}

void todayTransfer(intEnum ie, intEnum* ie2, longEnum le, longEnum *le2)
{
    *ie2 = ie;
    *le2 = le;
}

retEnum toRetEnum(int v)
{
    return v;
}

void readBackErrCode(int setErr, errCodes* code)
{
    code[0] = setErr;
}

extern void passEnumWArrStruct(EnumSimpleArraysStruct *enums)
{
    for (int n = 0; n < 3; ++n) {
        intEnum tmp = enums->allDays[n];
        enums->allDays[n] = enums->allDaysPtr[n];
        enums->allDaysPtr[n] = tmp;
    }
}

long long passSimpleEnumStruct(EnumStruct *enums)
{
    enums->eiValue = enums->ivalue;
    enums->elValue = enums->lvalue;
    return enums->ivalue + enums->lvalue;
}

extern void passComplexStructEnum(EnumArraysStruct *enums)
{
    for (int n = 0; n < 3; ++n) {
        intEnum tmp = enums->allDays[n];
        enums->allDays[n] = enums->allDaysPtr[n];
        enums->allDaysPtr[n] = tmp;
    }

    enums->error = no_file;
    enums->weekend = SUNDAY;
    enums->weekends[0] = SATURDAY;
    enums->weekends[1] = SUNDAY;
    enums->weekendPtr = NULL;
}