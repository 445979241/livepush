//
// Created by hsh on 2021/8/7.
//

#include "DZLivePush.h"

DZLivePush::DZLivePush(DZJniCall *dzJniCall,const char *url) {
    this->dzJniCall = dzJniCall;
    this->liveUrl = static_cast<char *>(malloc(strlen(url) + 1));
    strcpy(this->liveUrl,url);
    dzPacketQueue = new DZPacketQueue();
}



DZLivePush::~DZLivePush() {
    if(rtmp != NULL){
        RTMP_Close(rtmp);
        free(rtmp);
        rtmp = NULL;
    }
    if(liveUrl != NULL){
        free(liveUrl);
        liveUrl = NULL;
    }
    if(dzPacketQueue != NULL){
        delete dzPacketQueue;
        dzPacketQueue = NULL;
    }
}

void DZLivePush::stop() {
    isPushing = false;

    pthread_join(initConnectTid,NULL);

    LOGE("等待停止");
}

void *initConnectRun(void * context){
    DZLivePush *pLivePush = (DZLivePush *) context;

    //1.创建rtmp
    pLivePush->rtmp =RTMP_Alloc();
    //2.初始化
    RTMP_Init(pLivePush->rtmp);
    //3.设置连接参数，连接的超时时间等
    pLivePush->rtmp->Link.timeout = 10;
    pLivePush->rtmp->Link.lFlags =RTMP_LF_LIVE;
    RTMP_SetupURL(pLivePush->rtmp,pLivePush->liveUrl);
    RTMP_EnableWrite(pLivePush->rtmp);

    //4.开始连接
    if(!RTMP_Connect(pLivePush->rtmp,NULL)){
        LOGE("rtmp connect error");
//        THREAD_CHILD,INIT_RTMP_CONNECT_ERROR,"rtmp connect error"
        pLivePush->dzJniCall->callConnectError(THREAD_CHILD,1,"rtmp connect error");
        return (void *) INIT_RTMP_CONNECT_ERROR;
    }

    if(!RTMP_ConnectStream(pLivePush->rtmp,0)){
        LOGE("rtmp connect stream error");
        pLivePush->dzJniCall->callConnectError(THREAD_CHILD,-11,"rtmp connect rtmp error");
        return (void *) INIT_RTMP_CONNECT_STEAM_ERROR;
    }

    pLivePush->dzJniCall->callConnectSuccess(THREAD_CHILD);

    pLivePush->startTime = RTMP_GetTime();
    //不断循环取数据上传到服务器
    while (pLivePush->isPushing){

        RTMPPacket* packet = pLivePush->dzPacketQueue->pop();
        if(packet != NULL){
            int send_result = RTMP_SendPacket(pLivePush->rtmp,packet,1);
            LOGE("send_result: %d",send_result);

            RTMPPacket_Free(packet);
            free(packet);
            packet = NULL;
        }
    }
    LOGE("停止了");

    return 0;
}

void DZLivePush::initConnect() {
    pthread_create(&initConnectTid,NULL, initConnectRun,this);
}

void DZLivePush::pushSpsPps(jbyte* sps_data, jint sps_length, jbyte* pps_data, jint pps_length) {

    // frame type : 1关键帧，2 非关键帧 (4bit)
    // CodecID : 7表示 AVC (4bit)  , 与 frame type 组合起来刚好是 1 个字节  0x17
    // fixed : 0x00 0x00 0x00 0x00 (4byte)
    // configurationVersion  (1byte)  0x01版本
    // AVCProfileIndication  (1byte)  sps[1] profile
    // profile_compatibility (1byte)  sps[2] compatibility
    // AVCLevelIndication    (1byte)  sps[3] Profile level
    // lengthSizeMinusOne    (1byte)  0xff   包长数据所使用的字节数

    // sps + pps 的数据
    // sps number            (1byte)  0xe1   sps 个数
    // sps data length       (2byte)  sps 长度
    // sps data                       sps 的内容
    // pps number            (1byte)  0x01   pps 个数
    // pps data length       (2byte)  pps 长度
    // pps data                       pps 的内容

    int bodySize = sps_length + pps_length + 16;
    RTMPPacket* rtmpPacket =  (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(rtmpPacket,bodySize);
    RTMPPacket_Reset(rtmpPacket);

    int index = 0;
    char* body = rtmpPacket->m_body;
    //标识位 sps pps,AVC sequence header 与IDR一样
    body[index++] = 0x17;

    //跟着的补齐
    body[index++] = 0x00;
    body[index++] = 0x00;
    body[index++] = 0x00;
    body[index++] = 0x00;

    //版本
    body[index++] = 0x01;

    //编码规格
    body[index++] = sps_data[1];
    body[index++] = sps_data[2];
    body[index++] = sps_data[3];
    // reserved（111111） + lengthSizeMinusOne（2位 nal 长度） 总是0xff
    body[index++] = 0xff;
    // reserved（111） + lengthSizeMinusOne（5位 sps 个数） 总是0xe1
    body[index++] = 0xe1;

    //sps length 2字节
    body[index++] = (sps_length >> 8) & 0xff; //第0个字节
    body[index++] = sps_length & 0xff; //第1个字节
    // sps data
    memcpy(&body[index], sps_data, sps_length);
    index += sps_length;

    //pps
    body[index++] = 0x01;
    body[index++] = (pps_length >> 8) & 0XFF;
    body[index++] = pps_length & 0xFF;

    memcpy(&body[index], pps_data, pps_length);

    rtmpPacket->m_hasAbsTimestamp = 0;
    rtmpPacket->m_nTimeStamp = 0;
    rtmpPacket->m_nBodySize = bodySize;
    rtmpPacket->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    rtmpPacket->m_headerType = RTMP_PACKET_SIZE_LARGE;
    rtmpPacket->m_nChannel = 0x04;
    rtmpPacket->m_nInfoField2 = rtmp->m_stream_id;

//    LOGE("sps pps 发送到dzPacketQueue");
    dzPacketQueue->push(rtmpPacket);
}

void DZLivePush::pushVideo(jbyte *videoByte, jint length,jboolean isKeyFrame) {

    // frame type : 1关键帧，2 非关键帧 (4bit)
    // CodecID : 7表示 AVC (4bit)  , 与 frame type 组合起来刚好是 1 个字节  0x17
    // fixed : 0x01 0x00 0x00 0x00 (4byte)  0x01  表示 NALU 单元

    // video data length       (4byte)  video 长度
    // video data
    // 数据的长度（大小） =  dataLen + 9
    int bodySize = 9+length;
    RTMPPacket* packet = (RTMPPacket *)(malloc(sizeof(RTMPPacket)));
    RTMPPacket_Alloc(packet,bodySize);
    RTMPPacket_Reset(packet);

    int index = 0;
    char* body = packet->m_body;
    // frame type : 1关键帧，2 非关键帧 (4bit)
    // CodecID : 7表示 AVC (4bit)  , 与 frame type 组合起来刚好是 1 个字节  0x17

    if(isKeyFrame)
        body[index++] =0x17;
    else
        body[index++] =0x27;

    body[index++] =0x01;
    body[index++] =0x00;
    body[index++] =0x00;
    body[index++] =0x00;

    body[index++] =(length >> 24) & 0xFF;
    body[index++] =(length >> 16) & 0xFF;
    body[index++] =(length >> 8) & 0xFF;
    body[index++] =length & 0xFF;

    memcpy(&body[index],videoByte,length);

    packet->m_nBodySize = bodySize;
    packet->m_nChannel = 0x04;

    packet->m_nTimeStamp = RTMP_GetTime() - startTime;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nInfoField2 = rtmp->m_stream_id;

//    LOGE("I P 发送到dzPacketQueue");
    dzPacketQueue->push(packet);
}

void DZLivePush::pushAudio(jbyte *audioData, jint audioLen) {


    // 2 字节头信息
    // 前四位表示音频数据格式 AAC  10(A)
    // 五六位表示采样率 0 = 5.5k  1 = 11k  2 = 22k  3(11) = 44k
    // 七位表示采样采样的精度 0 = 8bits  1 = 16bits
    // 八位表示音频类型  0 = mono  1 = stereo
    // 组合起来：1010 1111 -，算出来第一个字节是 0xAF
    // 0x01 代表 aac 原始数据
    int bodySize = audioLen+2;
    RTMPPacket* packet = (RTMPPacket *)(malloc(sizeof(RTMPPacket)));
    RTMPPacket_Alloc(packet,bodySize);
    RTMPPacket_Reset(packet);

    char * body = packet->m_body;
    //上面推算出
    body[0] = 0xaf;

    body[1] = 0x01;

    memcpy(&body[2],audioData,audioLen);

    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = rtmp->m_stream_id;
    packet->m_hasAbsTimestamp = 0;
    packet->m_nTimeStamp = RTMP_GetTime()-startTime;
    packet->m_nChannel = 0x04;
    packet->m_nBodySize = bodySize;

    LOGE("AAC 发送到dzPacketQueue");

    dzPacketQueue->push(packet);
}
