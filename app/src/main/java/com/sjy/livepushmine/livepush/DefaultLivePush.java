package com.sjy.livepushmine.livepush;

import android.content.Context;

import com.sjy.livepushmine.record.RecorderRenderer;

import javax.microedition.khronos.egl.EGLContext;

public class DefaultLivePush extends BaseVideoPush {

    public DefaultLivePush(Context context, EGLContext eglContext){
        super(context,eglContext);
    }

    public void setRenderId(int textureId){
        setRender(new RecorderRenderer(super.mContext,textureId));
    }
}
