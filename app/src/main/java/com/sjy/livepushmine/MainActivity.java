package com.sjy.livepushmine;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.sjy.livepushmine.camera.widget.CameraFocusView;
import com.sjy.livepushmine.camera.widget.MyCameraView;
import com.sjy.livepushmine.databinding.ActivityMainBinding;
import com.sjy.livepushmine.livepush.DefaultLivePush;

public class MainActivity extends AppCompatActivity {


    private ActivityMainBinding binding;
    private MyCameraView mCameraView;
    private CameraFocusView mFocusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mCameraView = findViewById(R.id.camera_view);


        mFocusView = findViewById(R.id.camera_focus_view);
        mCameraView.setOnFocusListener(new MyCameraView.FocusListener() {
            @Override
            public void beginFocus(int x, int y) {
                mFocusView.beginFocus(x, y);
            }

            @Override
            public void endFocus() {
                mFocusView.endFocus(true);
            }
        });

        findViewById(R.id.live_bt).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startLivePush();
            }
        });

        findViewById(R.id.stop_bt).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                stopLivePush();
            }
        });
    }

    private void stopLivePush() {
        if(mLivePush != null){
            mLivePush.stopPush();
        }
    }

    DefaultLivePush mLivePush;
    private void startLivePush() {

        if (mLivePush ==null){

            mLivePush = new DefaultLivePush(MainActivity.this,mCameraView.getEglContext());
            mLivePush.setRenderId(mCameraView.getTextureId());
            mLivePush.initLiveParams("rtmp://121.5.108.219:1935/cctvf/mystream",
                    720 / 2, 1280 / 2);

            mLivePush.setConnectListner(new LivePush.ConnectListner() {
                @Override
                public void onConnectSuccess() {
                        Log.e("TAG", "connectSuccess");
                }

                @Override
                public void connectError(int errorCode, String errorMsg) {
                    Log.e("TAG", "errorCode:" + errorCode);
                    Log.e("TAG", "errorMsg:" + errorMsg);
                }
            });
            mLivePush.startPush();
        }
    }

}