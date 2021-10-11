#include <jni.h>
#include <string>
#include "DZJNICall.h"
#include "DZLivePush.h"

// 重写 so 被加载时会调用的一个方法
// 小作业，去了解动态注册
JavaVM *pJavaVM = NULL;
DZJniCall* dzJniCall = NULL;
DZLivePush* dzLivePush = NULL;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *javaVM, void *reserved) {
    pJavaVM = javaVM;
    JNIEnv *env;
    if (javaVM->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    LOGE("JNI_OnLoad");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sjy_livepushmine_LivePush_nInitConnect(
        JNIEnv* env,jobject livepush,jstring liveUrl_) {

    const char* url = env->GetStringUTFChars(liveUrl_,0);

    if(dzLivePush == NULL){
        dzJniCall = new DZJniCall(pJavaVM,env,livepush);
        dzLivePush = new DZLivePush(dzJniCall,url);
    }
    dzLivePush->initConnect();

    LOGE("ReleaseStringUTFChars");
    env->ReleaseStringUTFChars(liveUrl_,url);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_sjy_livepushmine_LivePush_pushVideo(JNIEnv *env, jobject thiz, jbyteArray m_video_bytes,
                                             jint length,jboolean isKeyFrame) {
    jbyte * videoByte = env->GetByteArrayElements(m_video_bytes,0);
    if(dzLivePush !=NULL){
        dzLivePush->pushVideo(videoByte,length,isKeyFrame);
    }
    env->ReleaseByteArrayElements(m_video_bytes,videoByte,0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sjy_livepushmine_LivePush_nStop(JNIEnv *env, jobject thiz) {

    if(dzLivePush != NULL){
        dzLivePush->stop();
        delete dzLivePush;
        dzLivePush = NULL;
    }
    if(dzJniCall != NULL){
        delete dzJniCall;
        dzJniCall = NULL;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sjy_livepushmine_LivePush_pushSpsPps(JNIEnv *env, jobject thiz, jbyteArray m_video_sps,
                                              jint sps_length, jbyteArray m_video_pps, jint pps_length) {
    jbyte * spsByte = env->GetByteArrayElements(m_video_sps,0);
    jbyte * ppsByte = env->GetByteArrayElements(m_video_pps,0);

    if(dzLivePush !=NULL){
        dzLivePush->pushSpsPps(spsByte,sps_length,ppsByte,pps_length);
    }

    env->ReleaseByteArrayElements(m_video_sps,spsByte,0);
    env->ReleaseByteArrayElements(m_video_pps,ppsByte,0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sjy_livepushmine_LivePush_pushAudio(JNIEnv *env, jobject thiz, jbyteArray m_audio_data,
                                             jint length) {
    // TODO: implement pushAudio()
    jbyte * audioData = env->GetByteArrayElements(m_audio_data,0);
    if(dzLivePush !=NULL){
        dzLivePush->pushAudio(audioData,length);
    }
    env->ReleaseByteArrayElements(m_audio_data,audioData,0);
}