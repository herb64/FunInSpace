#include "native-lib.h"
#include <jni.h>
#include <stdio.h>
#include <string.h>


JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_getNativeKey1(JNIEnv *env, jobject instance) {
    //TODO: this is not clean, need to use modified UTF8 conversion, unless it is 7-bit ascii for sure!!
    return (*env)->NewStringUTF(env, "hehe1");

}

JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_getNativeKey2(JNIEnv *env, jobject instance) {
    //static const char test[] = "herb";
    //strcpy(teststring, "hugo");
    char* test2;
    size_t ln;
    ln = strlen(teststring);
    return (*env)->NewStringUTF(env, teststring);
}
