//
// Created by herbert on 10/10/17.
//

#include "hfcmlib.h"
#include <jni.h>
//#include <stdio.h>
//#include <string.h>


JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_yT(JNIEnv *env, jobject instance) {
    //TODO: this is not clean, need to use modified UTF8 conversion, unless it is 7-bit ascii for sure!!
    return (*env)->NewStringUTF(env, dYT);

}

JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_nS(JNIEnv *env, jobject instance) {
    //static const char test[] = "herb";
    //strcpy(teststring, "hugo");
    //char* test2;
    //size_t ln;
    //ln = strlen(teststring);
    return (*env)->NewStringUTF(env, dNS);
}