package com.arkconcepts.cameraserve.views;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;


public class CameraView extends SurfaceView implements SurfaceHolder.Callback {
    /*
    surfaceview可以直接从内存或者DMA等硬件接口取得图像数据,是个非常重要的绘图容器。
     */
    private SurfaceHolder mHolder;  //通过SurfaceHolder这个接口去访问Surface
    private Camera mCamera;
    private Camera.PreviewCallback previewCallback;  //预览帧视频..
    private boolean isBackCamera = false;
    private int width;
    private int height;

    public CameraView(Context context, Camera.PreviewCallback previewCallback) {
        super(context);
        this.previewCallback = previewCallback;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
    }

    public void switchCamera() {   //不需要切换
        isBackCamera = !isBackCamera;
        releaseCamera();
        if (isBackCamera) {
            mCamera = Camera.open(0);
        } else {
            mCamera = Camera.open(1);
        }
        try {
            mCamera.setPreviewDisplay(mHolder);
            setupCamera(width, height);
        } catch (IOException exception) {
            releaseCamera();
        }
    }

    public void surfaceCreated(SurfaceHolder holder) { // 在创建时激发，一般在这里调用画图的线程。
        //0 - 后置    1 - 前置
        mCamera = Camera.open(1);
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException exception) {
            releaseCamera();
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {  //改变屏幕大小 在surface的大小发生改变时激发
        width = w;
        height = h;
        setupCamera(w, h);
    }

    public void surfaceDestroyed(SurfaceHolder holder) { //销毁时激发，一般在这里将画图的线程停止、释放。
        releaseCamera();
    }


    // Bug: 纵向的时候有拉伸
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {    //调到合适的size
        final double ASPECT_TOLERANCE = 0.05;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Camera.Size optimalSize = null; //初始化是null的
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // 尝试找到一个适合宽高比的size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {  //初始化是null的..上面遍历过后没合适的宽高比
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) { //高度合适..
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private void setupCamera(int width, int height) {  //建立
        Camera.Parameters parameters = mCamera.getParameters();

        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        Camera.Size optimalSize = getOptimalPreviewSize(sizes, width, height);
        parameters.setPreviewSize(optimalSize.width, optimalSize.height);

        //自动连续对焦
        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
        {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        mCamera.cancelAutoFocus(); //取消自动对焦
        mCamera.setParameters(parameters);
        //设定竖屏显示
     //   mCamera.setDisplayOrientation(90);
        if (previewCallback != null) {
            mCamera.setPreviewCallbackWithBuffer(previewCallback);
            Camera.Size size = parameters.getPreviewSize();
            byte[] data = new byte[size.width*size.height*
                    ImageFormat.getBitsPerPixel(parameters.getPreviewFormat())/8];
            mCamera.addCallbackBuffer(data);
        }
        mCamera.startPreview();
    }
}
