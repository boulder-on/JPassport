//
// Created by dmclean on 2025-10-30.
//

#include <errno.h>
#include <stdio.h>

#ifdef _WIN32
#include "errhandlingapi.h"
#endif

#include "error_capture_example.h"

extern void setAnError(int errval)
{
    errno = errval;

#ifdef _WIN32
    SetLastError(errval);
#endif
}
