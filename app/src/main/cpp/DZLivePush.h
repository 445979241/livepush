//
// Created by hsh on 2021/8/7.
//

#ifndef LIVEPUSHMINE_DZLIVEPUSH_H
#define LIVEPUSHMINE_DZLIVEPUSH_H

#include "DZJNICall.h"
#include "DZPacketQueue.h"
#include <malloc.h>
#include <string.h>
#include "DZConstDefine.h"
extern "C"{
#include "librtmp/rtmp.h"
};

#define ERROR_ 3
class DZLivePush {

public:
    DZJniCall* dzJniCall = NULL;
    char * liveUrl = NULL;
    DZPacketQueue* dzPacketQueue = NULL;
    RTMP *rtmp = NULL;
    bool isPushing = true;
    pthread_t initConnectTid;
    uint32_t startTime;
public:
    DZLivePush(DZJniCall* dzJniCall,const char * url);
    ~DZLivePush();
    void stop();

    void initConnect();

    void pushSpsPps(jbyte* spsByte, jint sps_length, jbyte* ppsByte, jint pps_length);

    void pushVideo(jbyte *videoByte, jint length,jboolean isKeyFrame);

    void pushAudio(jbyte *audioData, jint audioLen);
};


#endif //LIVEPUSHMINE_DZLIVEPUSH_H
