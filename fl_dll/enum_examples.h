//
// Created by dmclean on 2025-10-30.
//

#ifndef FOREIGN_LINK_ENUM_EXAMPLES_H
#define FOREIGN_LINK_ENUM_EXAMPLES_H
enum intEnum
{
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY
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

extern unsigned long long passEnum(enum intEnum ie, enum longEnum le);
extern enum intEnum todayInt(enum intEnum* ie);
extern enum longEnum todayLong(enum longEnum* ie);
extern void todayTransfer(enum intEnum ie, enum intEnum* ie2, enum longEnum le, enum longEnum *le2);
extern enum retEnum toRetEnum(int v);

#endif //FOREIGN_LINK_ENUM_EXAMPLES_H
