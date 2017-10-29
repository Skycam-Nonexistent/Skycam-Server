package com.arkconcepts.cameraserve.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.view.View;

import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_core.Mat;

import java.nio.ByteBuffer;
import java.util.Map;

import com.arkconcepts.cameraserve.utils.FaceManager;

import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import static org.bytedeco.javacpp.opencv_core.cvFlip;
import static org.bytedeco.javacpp.opencv_core.cvGetSeqElem;
import static org.bytedeco.javacpp.opencv_core.cvTranspose;


//实时人脸识别视图  属性是screenHeight r isBackCamera

public class FaceRecognizeView extends View implements Camera.PreviewCallback {

    private int screenHeight = 0;
    private Map<String, Object> r;
    private boolean isBackCamera = false;

    //构造函数
    public FaceRecognizeView(Context context) {
        super(context);
        screenHeight = getResources().getDisplayMetrics().heightPixels; //获取屏幕的高 （参数）
    }

    //每一帧回调
    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        //灰度处理
        Camera.Size size = camera.getParameters().getPreviewSize();
        int width = size.width;
        int height = size.height;
        int f = 3;
        IplImage grayImage = IplImage.create(width / f, height / f, IPL_DEPTH_8U, 1);
        int imageWidth  = grayImage.width();
        int imageHeight = grayImage.height();
        int dataStride = f*width;
        int imageStride = grayImage.widthStep();
        ByteBuffer imageBuffer = grayImage.getByteBuffer();
        for (int y = 0; y < imageHeight; y++) {
            int dataLine = y*dataStride;
            int imageLine = y*imageStride;
            for (int x = 0; x < imageWidth; x++) {
                imageBuffer.put(imageLine + x, bytes[dataLine + f*x]);
            }
        }

        //顺时针旋转90度，目前只适合竖屏识别
        IplImage rotatedImg = IplImage.create(grayImage.height(), grayImage.width(), grayImage.depth(), grayImage.nChannels());
        cvTranspose(grayImage, rotatedImg);
        if (isBackCamera) {
            // 垂直翻转
            cvFlip(rotatedImg,rotatedImg, 1);
        } else {
            // 水平 + 垂直 翻转
            cvFlip(rotatedImg,rotatedImg, -1);
        }
        //检测
        Mat matImage = new Mat(rotatedImg);
        // 耗时500ms => 放缩处理后 150ms (与图片大小相关，目前保证2米距离能识别)
        // 若想提速，可以进一步缩放，缺点是可识别距离会变短
        // 这是手机平台的性能局限，在距离上折中，满足应用场景需求
        r =  FaceManager.detectFace(matImage);
        //重要
        //more detail: http://stackoverflow.com/questions/14520803/onpreviewframe-only-called-once
        camera.addCallbackBuffer(bytes);
        postInvalidate();
    }

    //绘制
    @Override
    // 整体耗时50ms
    protected void onDraw(Canvas canvas) {
//        long beginTime = System.currentTimeMillis();
        Paint paint = new Paint();
        paint.setColor(Color.GREEN); //设置画笔颜色
        if (r != null) {
            IplImage sourcePicture = (IplImage) r.get("sourcePicture");
            CvSeq facesSeq = (CvSeq) r.get("faces");
            for (int i = 0; i < facesSeq.total(); i++) {
                CvRect faceRect = new CvRect(cvGetSeqElem(facesSeq, i));
                //绘制区域
                paint.setStrokeWidth(2);  //画笔样式为空心时，设置空心画笔的宽度
                paint.setStyle(Paint.Style.STROKE); //描边
                // sacle与显示尺寸和检测图片尺寸有关
                // 1680 * 1080
                // 177 * 100
                int imgHeight = sourcePicture.height();
                int imgWidth = sourcePicture.width();
                float scaleX = (float) getWidth() / imgWidth;
                float scaleY = (float) getHeight() / imgHeight;
                int x = faceRect.x(), y = faceRect.y(), w = faceRect.width(), h = faceRect.height();
                float xScaled = x*scaleX, yScaled = y*scaleY, wScaled = (x+w)*scaleX, hScaled = (y+h)*scaleY;
                canvas.drawRect(xScaled, yScaled, wScaled, hScaled, paint);

                // 识别
                Mat face = FaceManager.getFaceFromPicture(sourcePicture, faceRect);
                Mat centerFace = FaceManager.facePreProcess(face);
                if (centerFace == null) return;
                centerFace = FaceManager.lightNormalize(centerFace);

                // 收集性别数据
                // FaceManager.saveMatImgForTest(centerFace, "女/" + System.currentTimeMillis() + "_女");

                String name = FaceManager.recognizeFace(centerFace);
                String gender = FaceManager.recognizeGender(centerFace);

                if (!name.equals("")) {
                    //绘制名字
                    paint.setTextSize(40); //字体大小
                    canvas.drawText(name + " " + gender, xScaled, yScaled, paint);
                    //记录识别结果
                    FaceManager.record(name + "_" + gender, face);
                }
            }
        }
//        Log.i("time cost:", System.currentTimeMillis() - beginTime + "");
    }

    public void switchCamera() {
        isBackCamera = !isBackCamera;
    }
}
