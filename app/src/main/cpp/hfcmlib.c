//
// Created by herbert on 10/10/17.
//

#include "hfcmlib.h"
#include <jni.h>
//#include <stdio.h>
//#include <string.h>

//TODO: this is not clean, need to use modified UTF8 conversion, unless it is 7-bit ascii for sure!!

JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_yT(JNIEnv *env, jobject instance) {
    return (*env)->NewStringUTF(env, dYT);
}

JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_nS(JNIEnv *env, jobject instance) {
    return (*env)->NewStringUTF(env, dNS);
}

JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_vA(JNIEnv *env, jobject instance) {
    return (*env)->NewStringUTF(env, dVA);
}