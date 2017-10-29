package com.arkconcepts.cameraserve.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Environment;
import android.view.View;

import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_core.Mat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.arkconcepts.cameraserve.utils.FaceManager;

import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import static org.bytedeco.javacpp.opencv_core.cvFlip;
import static org.bytedeco.javacpp.opencv_core.cvTranspose;
import static org.bytedeco.javacpp.opencv_imgcodecs.cvSaveImage;


//实时人脸录入视图

public class FaceCollectView extends View implements Camera.PreviewCallback {

    private Map<String, Object> r;
    private List<String> faces = new ArrayList();

    public FaceCollectView(Context context) {
        super(context);
    }

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
        // 水平 + 垂直 翻转
        cvFlip(rotatedImg,rotatedImg, -1);
        //检测
        Mat matImage = new Mat(rotatedImg);
        r =  FaceManager.detectFace(matImage);

        camera.addCallbackBuffer(bytes);
        postInvalidate();
    }

    //绘制
    @Override
    protected void onDraw(Canvas canvas) {
        if (r == null) return;
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
        CvSeq faces = (CvSeq) r.get("faces");

        int total = faces.total();
        // sacle与显示尺寸和检测图片尺寸有关
        IplImage sourcePicture = (IplImage) r.get("sourcePicture");
        int imgHeight = sourcePicture.height();
        int imgWidth = sourcePicture.width();
        float scaleX = (float) getWidth() / imgWidth;
        float scaleY = (float) getHeight() / imgHeight;

        // 挑选尺寸最大的人脸
        CvRect drawRect = FaceManager.getBiggestRect(faces);

        int x = drawRect.x(), y = drawRect.y(), w = drawRect.width(), h = drawRect.height();
        canvas.drawRect(x*scaleX, y*scaleY, (x+w)*scaleX, (y+h)*scaleY, paint);
    }

    //保存图像到指定文件 图像:已识别的人脸经过处理、光照归一化之后  路径:"/FaceRecognizeSystem/temp/" + fileName;
    public boolean recordFace() {
        if (r == null) return false;
        IplImage sourcePicture = (IplImage) r.get("sourcePicture");
        CvSeq facesSeq = (CvSeq) r.get("faces");
        CvRect faceRect = FaceManager.getBiggestRect(facesSeq);
        Mat face = FaceManager.getFaceFromPicture(sourcePicture, faceRect);
        // 存在缓存里
        String fileName =  System.currentTimeMillis() + ".jpg";
        String absPath = Environment.getExternalStorageDirectory() + "/ScServer/temp/" + fileName;  ///////////////////////////////
        Mat centerFace = FaceManager.facePreProcess(face);
        if (centerFace == null) return false;
        centerFace = FaceManager.lightNormalize(centerFace);
        cvSaveImage(absPath, new IplImage(centerFace));
        faces.add(absPath);
        return true;
    }

    public List<String> getFaces() {
        return faces;
    }
}
