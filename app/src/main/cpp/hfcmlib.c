//
// Created by herbert on 10/10/17.
//

#include "hfcmlib.h"
#include <jni.h>

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

JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_vE(JNIEnv *env, jobject instance) {
    return (*env)->NewStringUTF(env, dHM);
}

JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_vKE(JNIEnv *env, jobject instance) {
    return (*env)->NewStringUTF(env, dKE);
}

JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_vHH(JNIEnv *env, jobject instance) {
    return (*env)->NewStringUTF(env, dHH);
}

JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_dPJ(JNIEnv *env, jobject instance) {
    return (*env)->NewStringUTF(env, dPJ);
}

JNIEXPORT jstring JNICALL
Java_de_herb64_funinspace_MainActivity_dAS(JNIEnv *env, jobject instance) {
    return (*env)->NewStringUTF(env, dAS);
}