//
// Created by herbert on 10/9/17.
//

#include "native-lib.h"
#include <jni.h>


JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_getNativeKey1(JNIEnv *env, jobject instance) {
    //return (*env).NewStringUTF("hehe1");
    //return env->NewStringUTF("hehe1");
    //return env->NewStringUTF(env, "hehe12");
    //TODO: this is not clean, need to use modified UTF8 conversion, unless it is 7-bit ascii for sure!!
    return (*env).NewStringUTF("hehe1");

}

JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_getNativeKey2(JNIEnv *env, jobject instance) {

    return (*env).NewStringUTF("hehe2");
}
