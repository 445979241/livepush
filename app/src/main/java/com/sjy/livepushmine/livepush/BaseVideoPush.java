package com.sjy.livepushmine.livepush;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Surface;

import com.sjy.livepushmine.LivePush;
import com.sjy.livepushmine.opengl.EglHelper;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.CyclicBarrier;

import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.opengles.GL10;

public abstract class BaseVideoPush {
    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static final int AUDIO_CHANNELS = 2;

    public Context mContext;
    private EGLContext mEglContext;

    private WeakReference<BaseVideoPush> recorderReference = new WeakReference<>(this);

    private Surface mSurface;
    private GLSurfaceView.Renderer mRender;
    // 视频渲染
    VideoRenderThread videoRenderThread;
    // 视频编码
    VideoEncoderThread videoEncoderThread;
    // 音频编码
    AudioEncoderThread audioEncoderThread;
    // 音频录制
    AudioRecordThread audioRecordThread;

    //视频编码器
    MediaCodec mVideoCodec;
    //音频编码器
    MediaCodec mAudioCodec;

    CyclicBarrier startCb = new CyclicBarrier(2);
    CyclicBarrier stopCb = new CyclicBarrier(2);

    private String TAG = "BaseVideoRecorder";
//    private int videoWidth;
//    private int videoHeight;

    LivePush livePush;

    public BaseVideoPush(Context context, EGLContext eglContext) {
        this.mContext = context;
        this.mEglContext = eglContext;

    }

    public void setRender(GLSurfaceView.Renderer mRender) {
        this.mRender = mRender;
    }

    public void initLiveParams(String liveUrl, int videoWidth, int videoHeight) {

        try {
            initVideoCodec(videoWidth,videoHeight);
            initAudioCodec(AUDIO_SAMPLE_RATE,AUDIO_CHANNELS);

            videoRenderThread = new VideoRenderThread(recorderReference);
            videoRenderThread.setSize(videoWidth,videoHeight);

            livePush = new LivePush(liveUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initVideoCodec(int width, int height) {
        try {
            // https://developer.android.google.cn/reference/android/media/MediaCodec mediacodec官方介绍
            // 比方MediaCodec的几种状态
            // avc即h264编码
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,width,height);
            // 设置颜色格式
            // 本地原始视频格式（native raw video format）：这种格式通过COLOR_FormatSurface标记，并可以与输入或输出Surface一起使用
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            // 设置码率，通常码率越高，视频越清晰，但是对应的视频也越大
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,width * height * 4);

            // 设置帧率 三星s21手机camera预览时，支持的帧率为10-30
            // 通常这个值越高，视频会显得越流畅，一般默认设置成30，你最低可以设置成24，不要低于这个值，低于24会明显卡顿
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE,30);
            // 设置 I 帧间隔的时间
            // 通常的方案是设置为 1s，对于图片电影等等特殊情况，这里可以设置为 0，表示希望每一帧都是 KeyFrame
            // IFRAME_INTERVAL是指的帧间隔，这是个很有意思的值，它指的是，关键帧的间隔时间。通常情况下，你设置成多少问题都不大。
            // 比如你设置成10，那就是10秒一个关键帧。但是，如果你有需求要做视频的预览，那你最好设置成1
            // 因为如果你设置成10，那你会发现，10秒内的预览都是一个截图
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);

            // 创建编码器
            // https://www.codercto.com/a/41316.html MediaCodec 退坑指南
            mVideoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mVideoCodec.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);

            // 相机的像素数据绘制到该 surface 上面
            mSurface = mVideoCodec.createInputSurface();

            videoEncoderThread = new VideoEncoderThread(recorderReference);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initAudioCodec(int sampleRate, int channel) {
        try {
            // 采样率，44.1khz，双声道，每个声道16位，2字节
            MediaFormat mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,sampleRate,channel);
            // 设置比特率96k hz
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,96000);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            // 设置输入数据缓冲区的最大大小
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,sampleRate * channel * 2);

            mAudioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mAudioCodec.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);

            audioEncoderThread = new AudioEncoderThread(recorderReference);
            audioRecordThread = new AudioRecordThread(recorderReference);

            Log.e(TAG,"AudioRecordThread "+audioRecordThread.hashCode());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    LivePush.ConnectListner connectListner;

    public void setConnectListner(LivePush.ConnectListner connectListner) {
        this.connectListner = connectListner;
    }

    public void startPush(){
        livePush.setConnectListner(new LivePush.ConnectListner() {
            @Override
            public void onConnectSuccess() {
                if(connectListner != null)
                    connectListner.onConnectSuccess();

                start();
            }

            @Override
            public void connectError(int errorCode, String errorMsg) {
                if(connectListner != null)
                    connectListner.connectError(errorCode,errorMsg);
            }
        });
        livePush.initConnect();
    }

    private void start(){
        // 视频渲染开始
        videoRenderThread.start();
        // 视频编码开始
        videoEncoderThread.start();
        // 音频编码开始
        audioEncoderThread.start();
        // 音频录制
        audioRecordThread.start();
    }



    public void stopPush(){

        livePush.stop();

        audioRecordThread.requestExit();

        videoRenderThread.requestExit();
        videoEncoderThread.requestExit();
        audioEncoderThread.requestExit();

    }


    RecordInfoListener recordInfoListener;
    public void setRecordInfoListener(RecordInfoListener recordInfoListener) {
        this.recordInfoListener = recordInfoListener;
    }
    public interface RecordInfoListener {
        void onTime(long times);
    }


    private class AudioEncoderThread extends Thread{

        WeakReference<BaseVideoPush> videoRecorderWf;
        private boolean shouldExit =false;

        private MediaCodec mAudioCodec;
        MediaCodec.BufferInfo bufferInfo;

        long audioPts = 0;
        final CyclicBarrier stopCb;
        /**
         * 音频轨道
         */
        private int mAudioTrackIndex = -1;

        public AudioEncoderThread(WeakReference<BaseVideoPush> videoRecorderWf){
            this.videoRecorderWf = videoRecorderWf;
            this.mAudioCodec = videoRecorderWf.get().mAudioCodec;
            this.stopCb = videoRecorderWf.get().stopCb;
            bufferInfo = new MediaCodec.BufferInfo();
        }

        @Override
        public void run() {
            mAudioCodec.start();

            while (true){
                try {
                    if(shouldExit){
                        onDestroy();
                        break;
                    }
                    // 返回有效数据填充的输出缓冲区的索引
                    int outputBufferIndex = mAudioCodec.dequeueOutputBuffer(bufferInfo,0);

                    while (outputBufferIndex >= 0){

//                            Log.e(TAG,"outputBufferIndex:"+outputBufferIndex+" count:"+index);
                        // 获取数据
                        ByteBuffer outBuffer = mAudioCodec.getOutputBuffers()[outputBufferIndex];

                        outBuffer.position(bufferInfo.offset);
                        outBuffer.limit(bufferInfo.offset+bufferInfo.size);

                        // 修改视频的 pts,基准时间戳
                        if(audioPts ==0)
                            audioPts = bufferInfo.presentationTimeUs;
                        bufferInfo.presentationTimeUs -= audioPts;

                        byte[] audioData = new byte[outBuffer.remaining()];
                        outBuffer.get(audioData,0,audioData.length);

                        recorderReference.get().livePush.pushAudio(audioData,audioData.length);

                        // 释放 outBuffer
                        mAudioCodec.releaseOutputBuffer(outputBufferIndex,false);
                        outputBufferIndex = mAudioCodec.dequeueOutputBuffer(bufferInfo,0);
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }

        private void onDestroy() {
            try {
                stopCb.await();

                if (mAudioCodec != null){
                    mAudioCodec.stop();
                    mAudioCodec.release();
                    mAudioCodec = null;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void requestExit() {
            shouldExit = true;
        }
    }

    private class AudioRecordThread extends Thread{

        WeakReference<BaseVideoPush> videoRecorderWf;
        private boolean shouldExit =false;

        private MediaCodec mAudioCodec;
        MediaCodec.BufferInfo bufferInfo;

        long audioPts = 0;
        /**
         * 音频轨道
         */
        private int mAudioTrackIndex = -1;

        AudioRecord audioRecord;
        byte[] mAdudioData;
        int bufferSizeInBytes;
        CyclicBarrier stopCb;

        public AudioRecordThread(WeakReference<BaseVideoPush> videoRecorderWf){
            this.videoRecorderWf = videoRecorderWf;
            this.mAudioCodec = videoRecorderWf.get().mAudioCodec;
            stopCb = videoRecorderWf.get().stopCb;
            bufferInfo = new MediaCodec.BufferInfo();

            bufferSizeInBytes = AudioRecord.getMinBufferSize(
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeInBytes);
            mAdudioData = new byte[bufferSizeInBytes];
        }

        @Override
        public void run() {
            audioRecord.startRecording();

            while (true){
                try {
                    if(shouldExit){
                        onDestroy();
                        break;
                    }

                    audioRecord.read(mAdudioData,0,bufferSizeInBytes);

                    int inputBufferTrack = mAudioCodec.dequeueInputBuffer(0);
                    if(inputBufferTrack >= 0){
                        ByteBuffer inputBuffer = mAudioCodec.getInputBuffers()[inputBufferTrack];
                        inputBuffer.clear();

                        inputBuffer.put(mAdudioData);

                        //0.41795918 *1000 000
                        audioPts += 1000000 * bufferSizeInBytes * 1.0f / AUDIO_SAMPLE_RATE * AUDIO_CHANNELS * 2;
                    //                        Log.e(TAG, "callBackPcm: "+audioPts);
                        //数据放入mAudioCodec的队列中
                        mAudioCodec.queueInputBuffer(inputBufferTrack,0,bufferSizeInBytes,audioPts,0);
                    }

                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }

        private void onDestroy() {
            try {
                stopCb.await();

                if (audioRecord != null){
                    audioRecord.stop();
                    audioRecord.release();
                    audioRecord = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        public void requestExit() {
            shouldExit = true;
        }
    }

    /**
     * 将二进制转换成16进制
     *
     * @param buf
     * @return
     */
    public static String parseByte2HexStr(byte buf[]) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < buf.length; i++) {
            String hex = Integer.toHexString(buf[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            sb.append(hex.toUpperCase());
        }
        return sb.toString();
    }

    private class VideoEncoderThread extends Thread{

        WeakReference<BaseVideoPush> videoRecorderWf;
        private boolean shouldExit =false;

        private MediaCodec mVideoCodec;
        MediaCodec.BufferInfo bufferInfo;
        CyclicBarrier stopCb;
        long videoPts = 0;

        /**
         * 视频轨道
         */
        private int mVideoTrackIndex = -1;

        byte[] mVideoPps;
        byte[] mVideoSps;

        public VideoEncoderThread(WeakReference<BaseVideoPush> videoRecorderWf){
            this.videoRecorderWf = videoRecorderWf;
            this.mVideoCodec = videoRecorderWf.get().mVideoCodec;
            this.stopCb = videoRecorderWf.get().stopCb;
            bufferInfo = new MediaCodec.BufferInfo();

        }

        @Override
        public void run() {
            mVideoCodec.start();

            while (true){
                try {
                    if(shouldExit){
                        onDestroy();
                        break;
                    }

                        // 返回有效数据填充的输出缓冲区的索引
                        int outputBufferIndex = mVideoCodec.dequeueOutputBuffer(bufferInfo,0);
                        if(outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                            //TODO

                            ByteBuffer byteBuffer = mVideoCodec.getOutputFormat().getByteBuffer("csd-0");
                            mVideoSps = new byte[byteBuffer.remaining()];
                            byteBuffer.get(mVideoSps,0,mVideoSps.length);

                            String videoData = parseByte2HexStr(mVideoPps);
                            Log.e(TAG+" pps",videoData);

                            byteBuffer = mVideoCodec.getOutputFormat().getByteBuffer("csd-1");
                            mVideoPps = new byte[byteBuffer.remaining()];
                            byteBuffer.get(mVideoPps,0,mVideoPps.length);

                            videoData = parseByte2HexStr(mVideoSps);
                            Log.e(TAG+" sps",videoData);
                        }else {
                            while (outputBufferIndex >= 0){

                                if(bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME){
                                    videoRecorderWf.get().livePush.pushSpsPps(mVideoSps,mVideoSps.length,mVideoPps,mVideoPps.length);
                                    String v1 = parseByte2HexStr(mVideoSps);
                                    Log.e(TAG+"sps data:",v1);
                                    String v2 = parseByte2HexStr(mVideoPps);
                                    Log.e(TAG+"pps data:",v2);
                                }

                                // 获取数据
                                ByteBuffer outBuffer = mVideoCodec.getOutputBuffers()[outputBufferIndex];

                                outBuffer.position(bufferInfo.offset);
                                outBuffer.limit(bufferInfo.offset+bufferInfo.size);

                                // 修改视频的 pts,基准时间戳
                                if(videoPts ==0)
                                    videoPts = bufferInfo.presentationTimeUs;
                                bufferInfo.presentationTimeUs -= videoPts;


                                byte[] mVideoBytes = new byte[outBuffer.remaining()];
                                outBuffer.get(mVideoBytes,0,mVideoBytes.length);

                                videoRecorderWf.get().livePush.pushVideo(mVideoBytes,mVideoBytes.length,
                                        bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME);


                                if(videoRecorderWf.get().recordInfoListener != null){
                                    // us，需要除以1000转为 ms
                                    videoRecorderWf.get().recordInfoListener.onTime( bufferInfo.presentationTimeUs / 1000);
                                }

                                // 释放 outBuffer
                                mVideoCodec.releaseOutputBuffer(outputBufferIndex,false);
                                outputBufferIndex = mVideoCodec.dequeueOutputBuffer(bufferInfo,0);
                            }
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }

        private void onDestroy() {
            try {
                if (mVideoCodec != null){
                    mVideoCodec.stop();
                    mVideoCodec.release();
                    mVideoCodec = null;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void requestExit() {
            shouldExit = true;
        }
    }

    private long drawTime;

    private class VideoRenderThread extends Thread{

        private WeakReference<BaseVideoPush> mVideoRecorderWf;

        boolean mShouldExit;
        EglHelper mEglHelper;
        boolean hasCreateEglContext =false;
        boolean hasSurfaceCreated =false;
        boolean hasSurfaceChanged =false;
        boolean hasDrawFrame =false;
        private int mWidth;
        private int mHeight;
        GL10 egl;


        public VideoRenderThread(WeakReference<BaseVideoPush> mVideoRecorderWf){
            this.mVideoRecorderWf = mVideoRecorderWf;
            mEglHelper = new EglHelper();
        }

        private void requestExit(){
            mShouldExit = true;
        }

        public void run(){

                while (true){
                    // 按下结束时能退出
                    if (mShouldExit){
                        onDestroy();
                        break;
                    }

                    BaseVideoPush baseVideoRecorder = mVideoRecorderWf.get();

                    // 根据GLSurfaceView源码中的循环绘制流程
                    // GLSurfaceView绘制源码解析：https://www.jianshu.com/p/369d5694c8ca
                    if(!hasCreateEglContext){
                        mEglHelper.eglSetup(baseVideoRecorder.mSurface,baseVideoRecorder.mEglContext);
                        hasCreateEglContext = true;
                    }

                    egl = (GL10)  mEglHelper.getEGLContext().getGL();

                    if(!hasSurfaceCreated){
                        // 调用mRender的onSurfaceCreated，做参数和纹理等的初始化
                        baseVideoRecorder.mRender.onSurfaceCreated(egl,mEglHelper.getEglConfig());
                        hasSurfaceCreated = true;
                    }

                    if(!hasSurfaceChanged){
                        // 调用mRender的onSurfaceChanged，做窗口的初始化，和变换
                        baseVideoRecorder.mRender.onSurfaceChanged(egl,mWidth,mHeight);
                        hasSurfaceChanged = true;
                    }

                    drawTime = System.currentTimeMillis();
//                    System.out.println("onDrawFrame:"+drawTime);
                    baseVideoRecorder.mRender.onDrawFrame(egl);

                    // 绘制到 MediaCodec 的 Surface 上面去
                    mEglHelper.swapBuffers();

                    try {
                        //休眠33毫秒，30fps，一秒需要30帧
                        Thread.sleep(16);
                    }catch (Exception r){
                        r.printStackTrace();
                    }
                }
        }

        private void onDestroy() {
            mEglHelper.destroySurface();
        }

        public void setSize(int mWidth, int mHeight) {
            this.mWidth = mWidth;
            this.mHeight = mHeight;
        }
    }

}
