//
// Created by dmclean on 2025-10-30.
//

#ifndef FOREIGN_LINK_ENUM_EXAMPLES_H
#define FOREIGN_LINK_ENUM_EXAMPLES_H
typedef enum
{
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY
}intEnum;

typedef enum
{
    no_err = 0,
    no_file = 10,
    no_permission = 200,
    no_socket = 2100
}errCodes;


typedef enum
{
    SATURDAY = 0l,
    SUNDAY = 0xFFFFFFFFF
}longEnum;

typedef enum
{
    value0, value1, value2
}retEnum;

typedef struct
{
    int ivalue;
    intEnum eiValue;
    long long lvalue;
    longEnum elValue;
}EnumStruct;

typedef struct
{
    intEnum allDays[3];
    int countDays;
    intEnum *allDaysPtr;
}EnumSimpleArraysStruct;

typedef struct
{
    intEnum allDays[3];
    int countDays;
    intEnum *allDaysPtr;
    errCodes error;
    longEnum weekend;
    longEnum weekends[2];
    int countWeekends;
    longEnum *weekendPtr;
}EnumArraysStruct;

typedef union
{
    int u_i;
    errCodes u_err;
    intEnum u_weekday;
    longEnum u_weekend;
}EnumUnion;

extern unsigned long long passEnum(intEnum ie, longEnum le);
extern intEnum todayInt(intEnum* ie);
extern longEnum todayLong(longEnum* ie);
extern void todayTransfer(intEnum ie, intEnum* ie2, longEnum le, longEnum *le2);
extern retEnum toRetEnum(int v);

extern void readBackErrCode(int setErr, errCodes* code);
extern long long passSimpleEnumStruct(EnumStruct *enums);
extern void passEnumWArrStruct(EnumSimpleArraysStruct *enums);
extern void passComplexStructEnum(EnumArraysStruct *enums);

extern void enumwithUnion(int field, long long  value, EnumUnion* eu);

#endif //FOREIGN_LINK_ENUM_EXAMPLES_H
