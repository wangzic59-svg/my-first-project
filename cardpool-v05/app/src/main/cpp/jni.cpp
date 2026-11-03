#include <jni.h>
#include "engine.hpp"
extern "C" JNIEXPORT jint JNICALL Java_com_example_cardpool_NativeEngine_remaining(JNIEnv* env,jclass,jint total,jint seen){
    try{return cardpool::remaining(total,seen);}catch(const std::exception& e){env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),e.what());return 0;}
}
extern "C" JNIEXPORT jdoubleArray JNICALL Java_com_example_cardpool_NativeEngine_predict(JNIEnv* env,jclass,jint target,jint pool,jdouble odds,jint slots,jboolean independent){
    try{auto p=cardpool::predict(target,pool,odds,slots,independent);jdouble values[]={p.slot,p.shop,p.expected};auto a=env->NewDoubleArray(3);if(a)env->SetDoubleArrayRegion(a,0,3,values);return a;}
    catch(const std::exception& e){env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),e.what());return nullptr;}
}
