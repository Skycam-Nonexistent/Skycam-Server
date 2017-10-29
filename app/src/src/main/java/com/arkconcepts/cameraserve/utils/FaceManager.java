package com.arkconcepts.cameraserve.utils;

import android.content.Context;
import android.os.Environment;
import android.text.format.Time;
import android.util.Log;
import android.widget.Toast;

import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.CvPoint2D32f;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_objdetect.CvHaarClassifierCascade;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.bytedeco.javacpp.opencv_core.BORDER_DEFAULT;
import static org.bytedeco.javacpp.opencv_core.CV_32FC1;
import static org.bytedeco.javacpp.opencv_core.CV_32SC1;
import static org.bytedeco.javacpp.opencv_core.CV_MINMAX;
import static org.bytedeco.javacpp.opencv_core.CvMemStorage;
import static org.bytedeco.javacpp.opencv_core.CvRect;
import static org.bytedeco.javacpp.opencv_core.CvSeq;
import static org.bytedeco.javacpp.opencv_core.CvSize;
import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import static org.bytedeco.javacpp.opencv_core.Mat;
import static org.bytedeco.javacpp.opencv_core.MatVector;
import static org.bytedeco.javacpp.opencv_core.abs;
import static org.bytedeco.javacpp.opencv_core.add;
import static org.bytedeco.javacpp.opencv_core.convertScaleAbs;
import static org.bytedeco.javacpp.opencv_core.cvCopy;
import static org.bytedeco.javacpp.opencv_core.cvCreateImage;
import static org.bytedeco.javacpp.opencv_core.cvCreateMat;
import static org.bytedeco.javacpp.opencv_core.cvGetSeqElem;
import static org.bytedeco.javacpp.opencv_core.cvLoad;
import static org.bytedeco.javacpp.opencv_core.cvReleaseMat;
import static org.bytedeco.javacpp.opencv_core.cvResetImageROI;
import static org.bytedeco.javacpp.opencv_core.cvScalarAll;
import static org.bytedeco.javacpp.opencv_core.cvSetImageROI;
import static org.bytedeco.javacpp.opencv_core.divide;
import static org.bytedeco.javacpp.opencv_core.exp;
import static org.bytedeco.javacpp.opencv_core.mean;
import static org.bytedeco.javacpp.opencv_core.min;
import static org.bytedeco.javacpp.opencv_core.normalize;
import static org.bytedeco.javacpp.opencv_core.pow;
import static org.bytedeco.javacpp.opencv_core.subtract;
import static org.bytedeco.javacpp.opencv_face.FaceRecognizer;
import static org.bytedeco.javacpp.opencv_face.createEigenFaceRecognizer;
import static org.bytedeco.javacpp.opencv_face.createFisherFaceRecognizer;
import static org.bytedeco.javacpp.opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE;
import static org.bytedeco.javacpp.opencv_imgcodecs.cvSaveImage;
import static org.bytedeco.javacpp.opencv_imgcodecs.imread;
import static org.bytedeco.javacpp.opencv_imgcodecs.imwrite;
import static org.bytedeco.javacpp.opencv_imgproc.CV_GAUSSIAN;
import static org.bytedeco.javacpp.opencv_imgproc.CV_INTER_LINEAR;
import static org.bytedeco.javacpp.opencv_imgproc.CV_WARP_FILL_OUTLIERS;
import static org.bytedeco.javacpp.opencv_imgproc.GaussianBlur;
import static org.bytedeco.javacpp.opencv_imgproc.cv2DRotationMatrix;
import static org.bytedeco.javacpp.opencv_imgproc.cvResize;
import static org.bytedeco.javacpp.opencv_imgproc.cvSmooth;
import static org.bytedeco.javacpp.opencv_imgproc.cvWarpAffine;
import static org.bytedeco.javacpp.opencv_imgproc.resize;
import static org.bytedeco.javacpp.opencv_objdetect.CV_HAAR_DO_CANNY_PRUNING;
import static org.bytedeco.javacpp.opencv_objdetect.cvHaarDetectObjects;


public class FaceManager {

    private static String TAG = "FaceManager";

//    开源人脸数据库的尺寸
//    private static int faceWidth = 92;
//    private static int faceHeight = 112;

    // 本地人脸数据库尺寸
    private static int faceWidth = 100;
    private static int faceHeight = 100;

    //单例
    private static FaceRecognizer faceRecognizer = null;  //脸
    private static FaceRecognizer genderRecognizer = null;  //性别
    private static CvHaarClassifierCascade classifier = null;  //
    public static CvHaarClassifierCascade eyesClassifier = null; //眼
    public static CvHaarClassifierCascade noseClassifier = null;  //鼻

    //数据
    public static HashMap<Integer, String> names = null;
    private static HashMap<Integer, String> genders = null;

    //模型参数
    private static int num_components = 80;
    private static double threshold = 9000; //阈值
    private static String genderModalPath = Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/temp/gender-model"; //获取路径


    //系统初始化
    public static void initRecognizer(Context context) {
        faceRecognizer = createEigenFaceRecognizer(num_components, threshold);
        //faceRecognizer = createLBPHFaceRecognizer();
        List<Mat> imgList = new ArrayList();  //图像列表
        List<Integer> labelList = new ArrayList();

        //加载人脸数据库
        names = parseFaceDB(context, imgList, labelList, "face");
        if (names == null) return;   //处理人脸数据库为空的情况
        MatVector images = new MatVector(imgList.size());
        Mat labels = new Mat(labelList.size(), 1, CV_32SC1);
        IntBuffer labelsBuf = labels.createBuffer();
        for (int i = 0; i< imgList.size(); i++) {
            images.put(i,imgList.get(i));
            labelsBuf.put(i, labelList.get(i));
        }
        //训练, 耗时过程，可以保存之前的训练结果
        faceRecognizer.train(images, labels);

        //性别识别器初始化
        genderRecognizer = createFisherFaceRecognizer();
////        genderRecognizer.load(genderModalPath);
//        genders = new HashMap<>();
////        genders.put(0, "男");
////        genders.put(1, "女");
//
//        //加载人脸数据库，模型训练时使用
//        imgList = new ArrayList();
//        labelList = new ArrayList();
//
        genders = parseFaceDB(context, imgList, labelList, "gender");
        images = new MatVector(imgList.size());
        labels = new Mat(labelList.size(), 1, CV_32SC1);
        labelsBuf = labels.createBuffer();
        if (genders == null) return;   //处理人脸数据库为空的情况
        for (int i = 0; i< imgList.size(); i++) {
            images.put(i,imgList.get(i));
            labelsBuf.put(i, labelList.get(i));
        }
        //训练, 耗时过程，可以保存之前的训练结果
        genderRecognizer.train(images, labels);
        //genderRecognizer.save(genderModalPath + "-byhand");
//
//        //测试使用
//        int male = 0, female = 0, testMale = 0, testFemale = 0;
//        List<Mat> imgListGender = new ArrayList();
//        List<Integer> labelListGender = new ArrayList();
//        List<Mat> imgListGenderTest = new ArrayList();
//        List<Integer> labelListGenderTest = new ArrayList();
//        for (int i = 0; i< imgList.size(); i++) {
//            int label = labelList.get(i);
//            if (genders.get(label).equals("男")) {
//                if (male < 0.5*0.7*imgList.size()) {
//                    imgListGender.add(imgList.get(i));
//                    labelListGender.add(label);
//                    male += 1;
//                } else {
//                    imgListGenderTest.add(imgList.get(i));
//                    labelListGenderTest.add(label);
//                    testMale += 1;
//                }
//            } else {
//                if (female < 0.5*0.7*imgList.size()) {
//                    imgListGender.add(imgList.get(i));
//                    labelListGender.add(label);
//                    female += 1;
//                } else {
//                    imgListGenderTest.add(imgList.get(i));
//                    labelListGenderTest.add(label);
//                    testFemale += 1;
//                }
//            }
//        }
//        images = new MatVector(imgListGender.size());
//        labels = new Mat(labelListGender.size(), 1, CV_32SC1);
//        labelsBuf = labels.createBuffer();
//        //取160*2(男女)当训练
//        Log.i(TAG, "train size:" + imgListGender.size() + "male: " + male + " female:" + female);
//        for (int i = 0; i< imgListGender.size(); i++) {
//            images.put(i,imgListGender.get(i));
//            labelsBuf.put(i, labelListGender.get(i));
//        }
//        //训练, 耗时过程，可以保存之前的训练结果
//        genderRecognizer.train(images, labels);
//        //genderRecognizer.save(Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/temp/gender-model-light");
//        //取40*2（男女）当实验
//        int correct = 0;
//        Log.i(TAG, "test size:" + imgListGenderTest.size() + "male: " + testMale + " female:" + testFemale);
//        for (int i = 0; i< imgListGenderTest.size(); i++) {
//            IntPointer label = new IntPointer(1);
//            DoublePointer confidence = new DoublePointer(1);
//            genderRecognizer.predict(imgListGenderTest.get(i), label, confidence);
//            int genderLabel = label.get(0);
//            double c = confidence.get(0);
//            // 距离过大，识别错误，纠正识别结果
////            if (c > 500) {
////                genderLabel = genderLabel == 0 ? 1 : 0;
////                // 上面理解更直观
////                // genderLabel = 1 - genderLabel;
////            }
//            //输出，肉眼看看结果
//            if (genderLabel == labelListGenderTest.get(i)) {
//                Log.i(TAG, "right:" + c);
//                correct ++;
//            } else {
//                Log.i(TAG, "gender distance:" + c);
//                saveImgForTest(new IplImage(imgListGenderTest.get(i)), "非" +  genders.get(genderLabel) + "/" + i + genders.get(genderLabel));
//            }
//            //只有83%左右，还是太差了
//            Log.i(TAG, "correct:" + (float)correct/(i+1));
//        }
    }

    public static void initClassifier(Context context) {
        loadClassifier(context, "haarcascade_frontalface_alt2.xml", "face");
        loadClassifier(context, "haarcascade_eye.xml", "eyes");
        loadClassifier(context, "haarcascade_mcs_nose.xml", "nose");
    }

    // 预处理模块
    // input : 640*360
    // output : 265.5 * 150
    public static Mat processImage(Mat img) { //预处理
        //saveImgForTest(new IplImage(img), "放缩前");
        int height = img.size().height();
        int width = img.size().width();
        int targetSize = 200;
        int resizeHeight = targetSize;
        int resizeWidth = height * targetSize / width;
        resize(img, img, new Size(resizeHeight, resizeWidth));
        // 高斯平滑处理，在不同光照情况下要进行光照补偿
        IplImage iplFace = new IplImage(img);
        //saveImgForTest(iplFace, "平滑前");
        cvSmooth(iplFace, iplFace, CV_GAUSSIAN, 3, 3, 0.0, 0.0);
        //saveImgForTest(iplFace, "平滑后");
        //saveImgForTest(iplFace, "放缩后");
        return new Mat(iplFace);
    }

    //人脸检测模块
    public static Map<String, Object> detectFace(Mat img) {
            img = processImage(img);
        Map<String, Object> r = new HashMap();
        if (classifier == null) {
            Log.e("错误", "请先初始化classifier");
            return null;
        }
        //CvMemStorage内存存储器是一个可用来存储诸如序列，轮廓，图形,子划分等动态增长数据结构的底层结构
        CvMemStorage storage = CvMemStorage.create();
        // 互相转换
        IplImage picture = new IplImage(img);
        CvSeq detectFaces = cvHaarDetectObjects(picture, classifier, storage, 1.1, 3, CV_HAAR_DO_CANNY_PRUNING);
        //cvHaarDetectObjects函数： picture：图像 classifier：特征分类器 storage：存储检测到的候选目标的内存缓冲区
        // 1.1每次搜索窗口依次扩大10%  3：构成检测目标的相邻矩阵的最小个数
        // Log.i(TAG, "人脸个数:" + detectFaces.total());
        if(detectFaces.total() > 0) {
            r.put("sourcePicture", picture);
            r.put("faces", detectFaces);
            return r;
        }
        return null;
    }

    //人脸预处理模块
    //创新点 - 根据已有的眼睛位置和鼻子位置确定人脸核心区域位置
    //步骤：1，检测眼睛（过滤错误眼睛）, 2, 鼻子确定核心脸
    public static Mat facePreProcess(Mat face) {
        CvMemStorage storage = CvMemStorage.create();
        IplImage iplFace = new IplImage(face);
        CvSeq detectEye = cvHaarDetectObjects(iplFace, eyesClassifier, storage, 1.1, 3, CV_HAAR_DO_CANNY_PRUNING);

        //从结果中筛选出左右眼
        List<CvRect> eyes = new ArrayList<>();
        for (int i = 0; i<detectEye.total(); i++ ) {
            CvRect eye = new CvRect(cvGetSeqElem(detectEye, i));
            int eyeX = eye.x() + eye.width()/2, eyeY = eye.y() + eye.height()/2;
            if (eyeY > face.rows()/2) continue;
            Log.i(TAG, "眼睛位置:" + eyeX + "-" + eyeY);
            eyes.add(eye);
        }
        List<CvRect> leftEyes = new ArrayList<>();
        List<CvRect> rightEyes = new ArrayList<>();
        for (CvRect r:eyes) {
            int eyeX = r.x() + r.width()/2;
            if (eyeX < face.cols()/2) {
                leftEyes.add(r);
            } else {
                rightEyes.add(r);
            }
        }

        if (leftEyes.size() > 0 && rightEyes.size() > 0) {
            //找到左右眼(均值处理，求多个点的近似位置)，旋转
            int leftEyeCenterX = 0, leftEyeCenterY = 0, rightEyeCenterX = 0,rightEyeCenterY = 0;
            for (CvRect r:leftEyes) {
                leftEyeCenterX += r.x() + r.width()/2;
                leftEyeCenterY += r.y() + r.height()/2;
            }
            leftEyeCenterX /= leftEyes.size();
            leftEyeCenterY /= leftEyes.size();
            for (CvRect r:rightEyes) {
                rightEyeCenterX += r.x() + r.width()/2;
                rightEyeCenterY += r.y() + r.height()/2;
            }
            rightEyeCenterX /= rightEyes.size();
            rightEyeCenterY /= rightEyes.size();

            //旋转
            double angle = Math.toDegrees(Math.atan((float)(rightEyeCenterY - leftEyeCenterY)/(rightEyeCenterX - leftEyeCenterX)));
            Log.i(TAG, "旋转角度：" + angle);
            //saveImgForTest(iplFace, "旋转前");
            iplFace = rotateImage(iplFace, angle);
            //saveImgForTest(iplFace, "旋转后");
        } else {
            //没找到左右眼，暂不处理
            Log.i(TAG, "没找到左右眼位置");
        }

        //检测鼻子位置：确定核心脸
        CvSeq detectNose = cvHaarDetectObjects(iplFace, noseClassifier, storage, 1.1, 3, CV_HAAR_DO_CANNY_PRUNING);
        //Log.i(TAG, "鼻子个数" + detectNose.total());
        CvRect noseRect = new CvRect(cvGetSeqElem(detectNose, 0));
        if (detectNose.total() > 1 || noseRect.isNull()) return new Mat(iplFace);
        int noseCenterX = noseRect.x() + noseRect.width()/2, noseCenterY = noseRect.y() + noseRect.height()/2;
        Log.i(TAG, noseRect.x() + "-" + noseRect.y() + "-" + noseRect.width() + "-" + noseRect.height() + "-" + noseCenterX + "-" + noseCenterY);

        int offset = 35, marginY = 10;
        int faceX = noseCenterX - offset;
        int faceY = noseCenterY - offset - marginY;
        int faceW = offset * 2;
        int faceH = offset * 2;
        //边缘控制
        if (faceX < 0) {
            faceX = 0;
        }
        if (faceY < 0) {
            faceY = 0;
        }
        if (faceW + faceX > iplFace.width()) {
            faceW = iplFace.width() - faceX;
        }
        if (faceH + faceY > iplFace.height()) {
            faceH = iplFace.height() - faceY;
        }
        CvRect faceRect = new CvRect(faceX, faceY, faceW, faceH);
        Mat center = getFaceFromPicture(iplFace, faceRect);
        //saveMatImgForTest(center, "核心脸");

        return center;
    }

    //人脸识别模块
    public static String recognizeFace(Mat img) {
        //saveMatImgForTest(img, "输出");
        if (faceRecognizer == null) {
            Log.e("错误", "请先初始化faceRecognizer");
            return null;
        }

        // 可以返回更多信息
        IntPointer label = new IntPointer(1);
        DoublePointer confidence = new DoublePointer(1);
        faceRecognizer.predict(img, label, confidence);
        int predictedLabel = label.get(0);
        double c = confidence.get(0);
        // 相识度计算
        double formatC = 1.0f - Math.min(c, threshold)/threshold;
        Log.i("--------", names.get(predictedLabel) + "-" + c + "-" + formatC);
        // 可信度比较低就标识为未知人脸
        if (predictedLabel == -1 || c > threshold) {
            return "未知人脸";
        }
        return names.get(predictedLabel);
    }

    // 性别识别模块
    public static String recognizeGender(Mat img) {
        if (genderRecognizer == null) {
            Log.e("错误", "请先初始化genderRecognizer");
            return null;
        }

        int result = genderRecognizer.predict(img);
        // 错误case
//        if ( genders.get(result) == "女" ) {
//            saveImgForTest(new IplImage(img), "性别识别错误");
//        }
        return genders.get(result);
    }

    // 获取最大的人脸区域
    public static CvRect getBiggestRect(CvSeq facesSeq) {
        CvRect drawRect = null; //矩形区域

        // cvrect也是一种基本数据类型 包含x,y,width,height,通过定义矩形左上角坐标和矩形的宽高确定矩形
        for (int i = 0; i < facesSeq.total(); i++) {
            CvRect r = new CvRect(cvGetSeqElem(facesSeq, i));
            if (drawRect == null) {
                drawRect = r;
            } else {
                if (drawRect.width() < r.width()) {
                    drawRect = r;
                }
            }
        }
        return drawRect;
    }

    // 截取指定区域图片
    public static Mat cropPicture(Mat img, CvRect rect) {
        IplImage sourceImg = new IplImage(img);
        CvSize size = new CvSize();
        cvResetImageROI(sourceImg);
        cvSetImageROI(sourceImg, rect);
        size.width(rect.width());
        size.height(rect.height());
        IplImage imageCropped = cvCreateImage(size, IPL_DEPTH_8U, 1);
        // 如果截取范围超出图片范围会崩溃
        try {
            cvCopy(sourceImg, imageCropped);
        } catch (Exception e) {
            Log.i(TAG, "捕获异常，截取范围超出图片范围:" + e.getMessage());
        }
        return new Mat(imageCropped);

    }

    // 从图片中裁剪出人脸，并缩放
    public static Mat getFaceFromPicture(IplImage sourceImg, CvRect faceRect) {
        CvSize size = new CvSize();
        cvResetImageROI(sourceImg);
        cvSetImageROI(sourceImg, faceRect);
        size.width(faceRect.width());
        size.height(faceRect.height());
        IplImage imageCropped = cvCreateImage(size, IPL_DEPTH_8U, 1);
        // 如果截取范围超出图片范围会崩溃
        try {
            cvCopy(sourceImg, imageCropped);
        } catch (Exception e) {
            Log.i(TAG, "捕获异常，截取范围超出图片范围:" + e.getMessage());
        }
        cvResetImageROI(sourceImg);
        //resize
        IplImage imageResized;
        size.width(faceWidth);
        size.height(faceHeight);
        imageResized = cvCreateImage(size, IPL_DEPTH_8U, 1);
        cvResize(imageCropped, imageResized);
        return new Mat(imageResized);
    }

    //私有方法：读取本地/开源人脸数据
    private static HashMap<Integer, String> parseFaceDB(Context context, List<Mat> imgList, List<Integer> labelList, String type) {
        HashMap<Integer, String> names = new HashMap<Integer, String>();

//        // 加载人脸数据库 label = 0~39
//        AssetManager am = context.getAssets();
//        try {
//            //读取资源文件
//            InputStream inputStream = am.open("att_faces/face_db.csv");
//            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
//            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
//            String info = "";
//            while ((info = bufferedReader.readLine()) != null) {
//                String[] data = info.split(";");
//                //同一个label代表同一个人
//                int label = Integer.valueOf(data[1]);
//                labelList.add(label);
//
//                //获取图片信息
//                String imagePath = data[0].replace("./","att_faces/");
//                String absPath = getAssetsAbsPath(context, imagePath);
//
//                imgList.add(getGrayFace(absPath));
//
//                names.put(label, "测试人脸");
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        //读取faces下面的所有目录 label = 40~无穷
        int label = 0;
        File file = new File(Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/faces");
        File[] subFile = file.listFiles();
        for (int i=0; i<subFile.length; i++) {
            File faceFolder = subFile[i];
            String folderName = faceFolder.getName();
            if (type.equals("face")) {
                if (folderName.
                        equals("男") || folderName.equals("女") || folderName.equals("非男") || folderName.equals("非女")) {
                    continue;
                }
            } else if (type.equals("gender")) {
                if (!folderName.equals("男") && !folderName.equals("女") && !folderName.equals("非男") && !folderName.equals("非女")) {
                    continue;
                }
            }
            Log.i("sheldon", type + " " +folderName);
            if (faceFolder.isDirectory()) {
                File[] faces = faceFolder.listFiles();
                String name = faceFolder.getName();
                names.put(label, name);

                //加载当前人物的人脸
                for (int j=0; j<faces.length; j++) {
                    Mat grayFace = getGrayFace(faces[j].getAbsolutePath());
//                    处理数据集使用
//                    if (type.equals("gender")) {
//                        Map<String, Object> r = FaceManager.detectFace(grayFace);
//                        if (r == null) continue;
//                        IplImage sourcePicture = (IplImage) r.get("sourcePicture");
//                        CvSeq facesSeq = (CvSeq) r.get("faces");
//                        CvRect faceRect = getBiggestRect(facesSeq);
//                        Mat face = getFaceFromPicture(sourcePicture, faceRect);
//                        grayFace = facePreProcess(face);
//                        if (grayFace == null) {
//                            continue;
//                        } else {
//                            grayFace = lightNormalize(grayFace);
//                            saveImgForTest(new IplImage(grayFace), name + "/" + j);
//                        }
//                    }
                    imgList.add(grayFace);
                    labelList.add(label);
                }
                label ++;
            }
        }
        if (label < 2) return null; // LDP need more than two class
        return names;
    }
    private static HashMap<Integer, String> parseTestFaceDB(List<Mat> imgList, List<Integer> labelList, List<Mat> imgTestList, List<Integer> labelTestList, String type) {
        HashMap<Integer, String> names = new HashMap<>();
        File file = new File(Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/test/" + type);
        if (file.exists()) {
            switch (type) {
                case "distance":

                    break;
                case "light":
                    File[] faces = file.listFiles();
                    for (int i=0; i<faces.length; i++) {
                        File face = faces[i];
                        Mat grayFace = getGrayFace(face.getAbsolutePath());
                        Map<String, Object> r = FaceManager.detectFace(grayFace);
                        //调用人脸识别模块
                        if(r != null) {
                            IplImage sourcePicture = (IplImage) r.get("sourcePicture");
                            CvSeq facesSeq = (CvSeq) r.get("faces");
                            CvRect faceRect = FaceManager.getBiggestRect(facesSeq);
                            Mat f = FaceManager.getFaceFromPicture(sourcePicture, faceRect);
                            String name = face.getName();
                            // 文件名示例：subject01.happy.gif
                            String[] data = name.split("\\.");
                            String subjectName = data[0];
                            // 取得01
                            int label = Integer.parseInt(subjectName.substring(subjectName.length() - 2));
                            if (!names.containsKey(label)) {
                                names.put(label, subjectName);
                            }
                            // 划分训练数据和测试数据
                            if (name.contains("normal") || name.contains("centerlight")) {
                                imgTestList.add(f);
                                labelTestList.add(label);
                            } else {
                                imgList.add(f);
                                labelList.add(label);
                            }
                        }
                        Log.i(TAG, "人脸库加载进度：" + (i+1.0)/faces.length);
                    }
                    break;
                default:
                    Log.e("错误", "");
            }
        } else {
            // 处理测试数据不存在的情况
        }

        return names;
    }

    // 评估系统
    public static void evaluateSystem(Context context, String type) {
        faceRecognizer = createFisherFaceRecognizer();

        List<Mat> imgList = new ArrayList();
        List<Integer> labelList = new ArrayList();
        List<Mat> imgTestList = new ArrayList();
        List<Integer> labelTestList = new ArrayList();

        //加载人脸数据库
        names = parseTestFaceDB(imgList, labelList, imgTestList, labelTestList, type);

        if (labelList.size() < 1) return;   //处理人脸数据库为空的情况
        MatVector images = new MatVector(imgList.size());
        Mat labels = new Mat(labelList.size(), 1, CV_32SC1);
        IntBuffer labelsBuf = labels.createBuffer();
        for (int i = 0; i< imgList.size(); i++) {
            images.put(i,imgList.get(i));
            labelsBuf.put(i, labelList.get(i));
        }
        //训练
        Log.i(TAG, "评估：训练开始");
        faceRecognizer.train(images, labels);
        //预测
        int correct = 0;
        int total = 0;
        for (int i = 0; i< imgTestList.size(); i++) {
            Mat testFace = imgTestList.get(i);
            int result = faceRecognizer.predict(testFace);
            if (result == labelTestList.get(i)) {
                correct ++;
            }
            total ++;
        }
        //显示精准度
        String accurate = type + ": 精准度为 " + (correct+0.0)/total + " 细节：correct = " + correct + " total = " + total;
        Toast.makeText(context, accurate, Toast.LENGTH_LONG).show();
        Log.i("系统识别准确率", accurate);
    }

    private static String getAssetsAbsPath(Context context, String relativePath) {
        String filename = relativePath.replace("/", "_");
        File cacheFile = new File(context.getCacheDir() + "/" + filename);
        InputStream is = null;
        try {
            is = context.getAssets().open(relativePath);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            FileOutputStream fos = new FileOutputStream(cacheFile);
            fos.write(buffer);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return cacheFile.getAbsolutePath();
    }

    private static Mat getGrayFace(String absPath) {
        return imread(absPath, CV_LOAD_IMAGE_GRAYSCALE);
    }

    public static void saveImgForTest(IplImage img, String name) {
        String absPath = Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/test/" +  name + ".jpg";
        cvSaveImage(absPath, img);
    }

    public static void saveMatImgForTest(Mat img, String name) {
        String absPath = Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/temp/" +  name + ".jpg";
        imwrite(absPath, img);
    }

    private static IplImage rotateImage(IplImage image, double angle) {
//        saveImgForTest(image, "旋转前");
        IplImage rotatedImage = cvCreateImage(image.cvSize(), image.depth(), image.nChannels());

        CvPoint2D32f center = new CvPoint2D32f();
        center.x(image.width()/2);
        center.y(image.height()/2);
        opencv_core.CvMat mapMatrix = cvCreateMat(2, 3, CV_32FC1);

        cv2DRotationMatrix(center, angle, 1.0, mapMatrix);
        cvWarpAffine(image, rotatedImage, mapMatrix, CV_INTER_LINEAR + CV_WARP_FILL_OUTLIERS, cvScalarAll(0));

        cvReleaseMat(mapMatrix);
//        saveImgForTest(rotatedImage, "旋转后");
        return rotatedImage;
    }

    private static void loadClassifier(Context context, String classifierFilePath, String type) {
        //加载分类器文件
        File classifierFile = new File(getAssetsAbsPath(context, classifierFilePath));
        if (classifierFile.length() <= 0) {
            try {
                throw new IOException("Could not extract the classifier file from Java resource.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        switch (type) {
            case "eyes":
                eyesClassifier = new CvHaarClassifierCascade(cvLoad(classifierFile.getAbsolutePath()));
                break;
            case "face":
                classifier = new CvHaarClassifierCascade(cvLoad(classifierFile.getAbsolutePath()));
                break;
            case "nose":
                noseClassifier = new CvHaarClassifierCascade(cvLoad(classifierFile.getAbsolutePath()));
                break;
        }
        classifierFile.delete();
    }

    //光照归一化处理
    public static Mat lightNormalize(Mat img) {
        Mat m_FaceImg = img;
        //Mat m_FaceImg = imread(Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/temp/face_with_different_light.png", 0);

        Mat m_fFaceImg = new Mat();
        Mat m_fFaceImg1 = new Mat();
        Mat m_fFaceImg2 = new Mat();
        //参数
        double gamma = 0.2;
        double sigma0 = 0.5;
        double sigma1 = 2;
        double alpha = 0.1;
        double tau = 10;

        //格式转换
        m_FaceImg.convertTo(m_fFaceImg, CV_32FC1, 1.0/255, 0);
        //伽马纠正：压缩高光区
        pow(m_fFaceImg, gamma, m_fFaceImg);
        //DoG(Difference of Gaussian)滤波算子，主要用于边缘特征提取，使用高通滤波，增强细节
        Mat dst1 = new Mat(), dst2  = new Mat();
        int dia = 9;
        GaussianBlur(m_fFaceImg, dst1, new Size(dia, dia), sigma0, sigma0, BORDER_DEFAULT);
        GaussianBlur(m_fFaceImg, dst2, new Size(dia, dia), sigma1, sigma1, BORDER_DEFAULT);
        m_fFaceImg = subtract(dst1, dst2).asMat();
        //对比均衡，提高亮度
        // img = img/(mean2(abs(img).^alpha)^(1/alpha));
        m_fFaceImg1 = abs(m_fFaceImg).asMat();
        pow(m_fFaceImg1, alpha, m_fFaceImg1);
        double a = mean(m_fFaceImg1).get(0);
        m_fFaceImg = divide(m_fFaceImg, Math.pow(mean(m_fFaceImg1).get(), 1/alpha)).asMat();
        // img = img/(mean2(min(tau,abs(img)).^alpha)^(1/alpha));
        m_fFaceImg1 = abs(m_fFaceImg).asMat();
        m_fFaceImg1 = min(tau, m_fFaceImg1).asMat();
        pow(m_fFaceImg1, alpha, m_fFaceImg1);
        m_fFaceImg1 = divide(m_fFaceImg1, Math.pow(mean(m_fFaceImg1).get(), 1/alpha)).asMat();
        // tanh :tanh x = (e^(x)-e^(-x)) /(e^x+e^(-x))
        exp(m_fFaceImg, m_fFaceImg1);
        exp(subtract(Mat.zeros(m_fFaceImg.rows(), m_fFaceImg.cols(), m_fFaceImg.type()), m_fFaceImg).asMat(), m_fFaceImg2);
        m_fFaceImg = subtract(m_fFaceImg1, m_fFaceImg2).asMat();
        m_fFaceImg1 = add(m_fFaceImg1, m_fFaceImg2).asMat();
        m_fFaceImg = divide(m_fFaceImg, m_fFaceImg1).asMat();

        normalize(m_fFaceImg, m_fFaceImg, 0, 1, CV_MINMAX, -1, null);
        convertScaleAbs(m_fFaceImg, m_FaceImg, 255, 0);
        //saveMatImgForTest(m_FaceImg, "light-out");
        return m_FaceImg;
    }

    //统计模块
    //保存逻辑：1s内保存两次 && 3s内忽略相同的识别结果
    private static long lastRecordTime = 0;
    private static String recognizeResult = "";
    public static void record(String name, Mat face) {
        long now = System.currentTimeMillis();
        if (lastRecordTime <= 0) {
            lastRecordTime = now;
        }
        long timePass = now - lastRecordTime;
        if (timePass < 500 || (timePass < 3000 && name.equals(recognizeResult))) {
            //不保存
            return;
        } else {
            lastRecordTime = now;
            //存储到 年/月/日文件夹下
            //新建 年/月/日 路径
            recognizeResult = name;
            Time t = new Time();
            t.setToNow();
            int year = t.year, month = t.month + 1, day = t.monthDay;
            String folderPath = Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/result/";
            folderPath += year;
            File folder = new File(folderPath);
            if (!folder.exists()) {
                folder.mkdir();
            }
            folderPath += "/" + month;
            folder = new File(folderPath);
            if (!folder.exists()) {
                folder.mkdir();
            }
            folderPath += "/" + day;
            folder = new File(folderPath);
            if (!folder.exists()) {
                folder.mkdir();
            }
            String absPath = folderPath + "/" + name + "_" + now + ".jpg";
            //TODO:应该加上存储空间的判断，达到一定限制后淘汰旧的内容
            imwrite(absPath, face);
        }
    }

    //统计分析
    public static HashMap<String, Object> getStaticResult(int dayOffset) {
        HashMap<String, Object> result = new HashMap<>();
        List<String> faces = new ArrayList<>();
        long now = System.currentTimeMillis();
        String folderPath = Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/result/";
        //对原始数据进行提炼 => 识别到的人名 及 时间
        HashMap<String, Object> analyze = new HashMap<>();

        //获取需要遍历的路径
        for (int i = 0; i < dayOffset; i++) {
            Date date = new Date(now);
            now -= 1000 * 3600 * 24;
            int year = date.getYear() + 1900, month = date.getMonth() + 1, day = date.getDate();
            String absPath = folderPath + year + "/" + month + "/" + day;
            //遍历所有文件
            File folder = new File(absPath);
            if (!folder.exists()) continue;
            File[] files = folder.listFiles();
            for (File file:files) {
                faces.add(file.getAbsolutePath());
                String name = file.getName().split("\\.")[0];
                //统计结果
                String[] info = name.split("_");
                String recognizeName = info[0], gender = info[1];
                long time = Long.parseLong(info[2]);
                Date dateTime = new Date(time);
                if (!analyze.containsKey(recognizeName)) {
                    analyze.put(recognizeName, new HashSet<>());
                }
                Set<String> times = (Set<String>) analyze.get(recognizeName);
                //合并相同时间
                times.add(year + "/" +month + "/" + day + " " + dateTime.getHours() + ":" + dateTime.getMinutes());
                analyze.put(recognizeName, times);
            }
        }

        //记录原始数据
        result.put("raw", faces);
        //记录分析结果
        result.put("analyze", analyze);

        return result;
    }

    //反馈学习
    public static void correctResult(String name, String gender, String face) {
        //判断是否有更改
        String[] info = face.split("/");
        String fileName = info[info.length - 1];
        info = fileName.split("_");
        String recognizeName = info[0];
        String recognizeGender = info[1];
        if (gender.equals(recognizeGender) && name.equals(recognizeName)) {
            return;
        }
        String facePath = face.substring(6);
        Mat rawFace = imread(facePath, CV_LOAD_IMAGE_GRAYSCALE);
        //saveMatImgForTest(rawFace, "rawFace");
        Mat outFace = facePreProcess(rawFace);
        //saveMatImgForTest(outFace, "outFace");
        Mat out = lightNormalize(outFace);
        //saveMatImgForTest(out, "out");
        if (!gender.equals(recognizeGender)) {
            //update性别库, 0男，1女
            String absPath = Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/faces/" + gender + "/" + fileName;
            imwrite(absPath, out);

        }
        if (!name.equals(recognizeName)) {
            //update人脸库
            String absPath = Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/faces/" + name + "/" + fileName;
            imwrite(absPath, out);
        }
        //标记该图片已被纠正，方案：1，修改文件名 2，在文件名后面添加已纠正标志
        File raw = new File(facePath);
        facePath = facePath.replace(recognizeName, name);
        facePath = facePath.replace(recognizeGender, gender);
        raw.renameTo(new File(facePath));
    }
}
