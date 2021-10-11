//
// Created by hsh on 2021/8/7.
//

#include "DZPacketQueue.h"

DZPacketQueue::DZPacketQueue() {
    pPacketQueue = new std::queue<RTMPPacket *>();
    pthread_mutex_init(&packetMutex, NULL);
    pthread_cond_init(&packetCond, NULL);
}

void DZPacketQueue::clear() {
    // 需要清除队列，还需要清除每个 AVPacket* 的内存数据
    pthread_mutex_lock(&packetMutex);

    while (!pPacketQueue->empty()){
        RTMPPacket * packet = pPacketQueue->front();
        pPacketQueue->pop();
        RTMPPacket_Free(packet);
        free(packet);
    }

    pthread_mutex_unlock(&packetMutex);
}

DZPacketQueue::~DZPacketQueue() {
    if (pPacketQueue != NULL) {
        clear();
        delete (pPacketQueue);
        pPacketQueue = NULL;
    }
    pthread_mutex_destroy(&packetMutex);
    pthread_cond_destroy(&packetCond);
}

void DZPacketQueue::push(RTMPPacket *pPacket) {
    pthread_mutex_lock(&packetMutex);

    pPacketQueue->push(pPacket);

    pthread_cond_signal(&packetCond);

    pthread_mutex_unlock(&packetMutex);
}

RTMPPacket *DZPacketQueue::pop() {

    RTMPPacket * packet =NULL;
    pthread_mutex_lock(&packetMutex);
    while (pPacketQueue->empty()){
        pthread_cond_wait(&packetCond,&packetMutex);
    }
    packet = pPacketQueue->front();
    pPacketQueue->pop();
    pthread_mutex_unlock(&packetMutex);
    return packet;
}