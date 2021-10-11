//
// Created by hsh on 2021/8/7.
//

#ifndef LIVEPUSHMINE_DZJNICALL_H
#define LIVEPUSHMINE_DZJNICALL_H


#include <jni.h>
#include "android/log.h"
#include "DZConstDefine.h"

enum ThreadMode{
    THREAD_CHILD,THREAD_MAIN
};

class DZJniCall{

public:
    JNIEnv *env;
    JavaVM *javaVm;
    jobject jLivePush;
    jmethodID jErrorMethodId;
    jmethodID jConnectMthId;
public:
    DZJniCall(JavaVM *javaVm, JNIEnv *env,jobject pJobject);
    ~DZJniCall();

public:
    void callConnectError(ThreadMode threadMode,int errorCode,const char* msg);
    void callConnectSuccess(ThreadMode threadMode);
};



#endif //LIVEPUSHMINE_DZJNICALL_H
