//
// Created by hsh on 2021/8/7.
//

#include "DZJNICall.h"

DZJniCall::DZJniCall(JavaVM *javaVm, JNIEnv *jniEnv, jobject pJobject) {
    this->javaVm = javaVm;
    this->env = jniEnv;
    this->jLivePush = env->NewGlobalRef(pJobject);

    jclass jclassPlay = jniEnv->GetObjectClass(jLivePush);
    jErrorMethodId = jniEnv->GetMethodID(jclassPlay, "onConnectError", "(ILjava/lang/String;)V");

    jConnectMthId = jniEnv->GetMethodID(jclassPlay, "onConnectSuccess", "()V");
}

DZJniCall::~DZJniCall() {
    env->DeleteGlobalRef(jLivePush);
}

void DZJniCall::callConnectError(ThreadMode threadMode,int errorCode, const char *msg) {

    if (threadMode==THREAD_MAIN){
        jstring jStrMsg = env->NewStringUTF(msg);
        env->CallVoidMethod(jLivePush,jErrorMethodId,errorCode,jStrMsg);
        env->DeleteLocalRef(jStrMsg);

    } else  if (threadMode==THREAD_CHILD){
        JNIEnv *jniEnv;
        if(javaVm->AttachCurrentThread(&jniEnv,0) != JNI_OK){
            LOGE("get child thread jniEnv error!");
            return;
        }

        jstring jStrMsg = jniEnv->NewStringUTF(msg);
        jniEnv->CallVoidMethod(jLivePush,jErrorMethodId,errorCode,jStrMsg);
        jniEnv->DeleteLocalRef(jStrMsg);

        javaVm->DetachCurrentThread();
    }

}

/**
 * 回调到 java 层告诉准备好了
 * @param threadMode
 */
void DZJniCall::callConnectSuccess(ThreadMode threadMode) {
    // 子线程用不了主线程 jniEnv （native 线程）
    // 子线程是不共享 jniEnv ，他们有自己所独有的
    if (threadMode == THREAD_MAIN) {
        env->CallVoidMethod(jLivePush, jConnectMthId);
    } else if (threadMode == THREAD_CHILD) {
        // 获取当前线程的 JNIEnv， 通过 JavaVM
        JNIEnv *env;
        if (javaVm->AttachCurrentThread(&env, 0) != JNI_OK) {
            LOGE("get child thread jniEnv error!");
            return;
        }
        env->CallVoidMethod(jLivePush, jConnectMthId);
        javaVm->DetachCurrentThread();
    }
}
