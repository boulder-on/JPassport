/* Copyright (c) 2021 Duncan McLean, All Rights Reserved
 *
 * The contents of this file is dual-licensed under the
 * Apache License 2.0.
 *
 * You may obtain a copy of the Apache License at:
 *
 * http://www.apache.org/licenses/
 *
 * A copy is also included in the downloadable source code.
 */
package jpassport.test;

import com.sun.jna.Library;
import jdk.incubator.foreign.MemoryAddress;
import jpassport.Passport;
import jpassport.annotations.PtrPtrArg;
import jpassport.annotations.RefArg;

public interface TestLink extends Passport, Library {

    double sumD(double d, double d2);
    double sumArrD(double[] d, int len);
    double sumArrDD(double[] d, double[] d2, int len);
    void readD(@RefArg double[] d, int set);

    float sumArrF(float[] i, int len);
    void readF(@RefArg float[] d, float set);

    long sumArrL(long[] i, long len);
    void readL(@RefArg long[] d, long set);

    int sumArrI(int[] i, int len);
    void readI(@RefArg int[] d, int set);

    short sumArrS(short[] i, short len);
    void readS(@RefArg short[] d, short set);

    byte sumArrB(byte[] i, byte len);
    void readB(@RefArg byte[] d, byte set);

    double sumMatD(int rows, int cols, double[][] mat);
    double sumMatDPtrPtr(int rows, int cols, @PtrPtrArg double[][] mat);
    float sumMatF(int rows, int cols, float[][] mat);
    float sumMatFPtrPtr(int rows, int cols, @PtrPtrArg float[][] mat);

    long sumMatL(int rows, int cols, long[][] mat);
    long sumMatLPtrPtr(int rows, int cols, @PtrPtrArg long[][] mat);
    int sumMatI(int rows, int cols, int[][] mat);
    int sumMatIPtrPtr(int rows, int cols, @PtrPtrArg int[][] mat);
    int sumMatS(int rows, int cols, short[][] mat);
    int sumMatSPtrPtr(int rows, int cols, @PtrPtrArg short[][] mat);
    int sumMatB(int rows, int cols, byte[][] mat);
    int sumMatBPtrPtr(int rows, int cols, @PtrPtrArg byte[][] mat);

    int cstringLength(String s);

    String mallocString(String origString);
    MemoryAddress mallocDoubles(int count);
    void freeMemory(MemoryAddress address);
}
