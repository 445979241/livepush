package com.sjy.livepushmine;

import android.util.Log;

public class LivePush {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("live-push");
    }

    private String mLiveUrl;

    public LivePush(String liveUrl) {
        this.mLiveUrl = liveUrl;
    }

    public interface ConnectListner{
        public void onConnectSuccess();
        public void connectError(int errorCode, String errorMsg);
    }

    ConnectListner connectListner;

    public void onConnectSuccess(){
        Log.e("TAG", "connectSuccess java");
        if(connectListner != null)
            connectListner.onConnectSuccess();
    }

    public void onConnectError(int code ,String msg){
        Log.e("TAG", "connectError java");
        if(connectListner != null)
            connectListner.connectError(code,msg);
    }

    public void setConnectListner(ConnectListner connectListner) {
        this.connectListner = connectListner;
    }

    public void initConnect(){
        nInitConnect(mLiveUrl);
    }

    public void stop(){
        nStop();
    }

    public native void nStop();

    public native void nInitConnect(String mLiveUrl);

    public native void pushSpsPps(byte[] mVideoSps, int length, byte[] mVideoPps, int length1);

    public native void pushVideo(byte[] mVideoBytes, int length,Boolean isKeyFrame);

    public native void pushAudio(byte[] mAudioData, int length);
}
