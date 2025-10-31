//
// Created by dmclean on 2025-10-30.
//

#ifndef FOREIGN_LINK_ENUM_EXAMPLES_H
#define FOREIGN_LINK_ENUM_EXAMPLES_H
enum intEnum
{
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY
};

enum errCodes
{
    no_err = 0,
    no_file = 10,
    no_permission = 200,
    no_socket = 2100
};


enum longEnum
{
    SATURDAY = 0l,
    SUNDAY = 0xFFFFFFFFF
};

enum retEnum
{
    value0, value1, value2
};

struct EnumStruct
{
    int ivalue;
    enum intEnum eiValue;
    long long lvalue;
    enum longEnum elValue;
};

struct EnumSimpleArraysStruct
{
    enum intEnum allDays[3];
    int countDays;
    enum intEnum *allDaysPtr;
};

struct EnumArraysStruct
{
    enum intEnum allDays[3];
    int countDays;
    enum intEnum *allDaysPtr;
    enum errCodes error;
    enum longEnum weekend;
    enum longEnum weekends[2];
    int countWeekends;
    enum longEnum *weekendPtr;
};

extern unsigned long long passEnum(enum intEnum ie, enum longEnum le);
extern enum intEnum todayInt(enum intEnum* ie);
extern enum longEnum todayLong(enum longEnum* ie);
extern void todayTransfer(enum intEnum ie, enum intEnum* ie2, enum longEnum le, enum longEnum *le2);
extern enum retEnum toRetEnum(int v);

extern void readBackErrCode(int setErr, enum errCodes* code);
extern long long passSimpleEnumStruct(struct EnumStruct *enums);
extern void passEnumWArrStruct(struct EnumSimpleArraysStruct *enums);
extern void passComplexStructEnum(struct EnumArraysStruct *enums);

#endif //FOREIGN_LINK_ENUM_EXAMPLES_H
