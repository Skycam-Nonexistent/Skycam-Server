package com.arkconcepts.cameraserve;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.arkconcepts.cameraserve.utils.FaceManager;
import com.arkconcepts.cameraserve.utils.ImageListSer;
import com.arkconcepts.cameraserve.views.CameraView;
import com.arkconcepts.cameraserve.views.FaceCollectView;
import com.arkconcepts.cameraserve.views.FaceRecognizeView;
import com.orhanobut.dialogplus.DialogPlus;
import com.orhanobut.dialogplus.OnItemClickListener;

import org.bytedeco.javacpp.opencv_core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import cn.finalteam.rxgalleryfinal.RxGalleryFinal;
import cn.finalteam.rxgalleryfinal.bean.MediaBean;
import cn.finalteam.rxgalleryfinal.imageloader.ImageLoaderType;
import cn.finalteam.rxgalleryfinal.rxbus.RxBusResultSubscriber;
import cn.finalteam.rxgalleryfinal.rxbus.event.ImageMultipleResultEvent;
import cn.finalteam.rxgalleryfinal.rxbus.event.ImageRadioResultEvent;

import static org.bytedeco.javacpp.opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE;
import static org.bytedeco.javacpp.opencv_imgcodecs.imread;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceHolder holder;
    private Camera camera;
    private boolean previewRunning = false;
    private int camId = 0;
    private ByteArrayOutputStream previewStream = new ByteArrayOutputStream();
    private int rotationSteps = 0;
    private boolean aboveLockScreen = true;

    private static SsdpAdvertiser ssdpAdvertiser = new SsdpAdvertiser();
    private static Thread ssdpThread = new Thread(ssdpAdvertiser);
    private static MjpegServer mjpegServer = new MjpegServer();
    private static Thread serverThread = new Thread(mjpegServer);
    private static HashMap<Integer, List<Camera.Size>> cameraSizes = new HashMap<>();
    private static ReentrantReadWriteLock frameLock = new ReentrantReadWriteLock();
    private static byte[] jpegFrame;

    //  ————人脸识别————！！
    private boolean isRecognizePreview = false;

    private Handler handler = new Handler(){   //关于handler：主要接受子线程发送的数据， 并用此数据配合主线程更新UI。
        @Override  //表示重写父类handlemessage->handleMessage
        //当有消息发送出来的时候就执行Handler的这个方法
        public void handleMessage(Message msg){
            super.handleMessage(msg);  //覆盖handleMessage方法
            switch (msg.what) {  //根据收到的消息的what类型处理
                case 0:
                    Toast.makeText(getApplicationContext(), "系统初始化开始", Toast.LENGTH_SHORT).show();
                    break;
                case 1:
                    Toast.makeText(getApplicationContext(), "系统初始化完成", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Log.e("错误","handler无法处理该消息");
            }
        }
    };

    public static byte[] getJpegFrame() {
        try {
            frameLock.readLock().lock();
            return jpegFrame;
        } finally {
            frameLock.readLock().unlock();
        }
    }

    public static HashMap<Integer, List<Camera.Size>> getCameraSizes() {
        return cameraSizes;
    }

    private static void setJpegFrame(ByteArrayOutputStream stream) {
        try {
            frameLock.writeLock().lock();
            jpegFrame = stream.toByteArray();
        } finally {
            frameLock.writeLock().unlock();
        }
    }

    private void cacheResolutions() {
        int cams = Camera.getNumberOfCameras();
        for (int i = 0; i < cams; i++) {
            Camera cam = Camera.open(i);
            Camera.Parameters params = cam.getParameters();
            cameraSizes.put(i, params.getSupportedPreviewSizes());
            cam.release();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cacheResolutions();

        FloatingActionButton flipButton = findViewById(R.id.flipButton);
        flipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int cams = Camera.getNumberOfCameras();
                camId++;
                if (camId > cams - 1) camId = 0;
                if (previewRunning) stopPreview();
                if (camera != null) camera.release();
                camera = null;

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                preferences.edit().putString("cam", String.valueOf(camId)).apply();

                openCamAndPreview();

                Toast.makeText(MainActivity.this, "Cam " + (camId + 1),
                        Toast.LENGTH_SHORT).show();
            }
        });

        FloatingActionButton settingsButton = findViewById(R.id.settingsButton); //设置键
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        SurfaceView cameraView = findViewById(R.id.surfaceView);
        holder = cameraView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        //  ————人脸识别部分————  //
        checkPermissions();  //检查权限 见下面函数
        // bug - 应该在获取读写权限的回调用创建文件夹
        createProjectDirectory(); //创建项目目录 见下面函数

        //初始化
        new Thread(){
            @Override
            public void run(){
                //在新线程里执行长耗时方法
                handler.sendEmptyMessage(0); //handler信息为0 系统初始化开始
                FaceManager.initClassifier(getApplicationContext()); //调用facemanager的初始化函数
                FaceManager.initRecognizer(getApplicationContext()); //同上
                handler.sendEmptyMessage(1); //handler信息为1 系统初始化完成
            }

        }.start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadPreferences();

        openCamAndPreview();

        if (!ssdpThread.isAlive()) ssdpThread.start();
        if (!serverThread.isAlive()) serverThread.start();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        if (aboveLockScreen)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        this.finish();
        System.exit(0);
    }

    private void openCamAndPreview() {
        try {
            if (camera == null) camera = Camera.open(camId);
            startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        camId = Integer.parseInt(preferences.getString("cam", "0"));
        Integer rotDegrees = Integer.parseInt(preferences.getString("rotation", "0"));
        rotationSteps = rotDegrees / 90;
        Integer port = Integer.parseInt(preferences.getString("port", "8080"));
        MjpegServer.setPort(port);
        aboveLockScreen = preferences.getBoolean("above_lock_screen", aboveLockScreen);
        Boolean allIps = preferences.getBoolean("allow_all_ips", false);
        MjpegServer.setAllIpsAllowed(allIps);
    }

    private void startPreview() {
        if (previewRunning) stopPreview();

        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

        if (display.getRotation() == Surface.ROTATION_0) {
            camera.setDisplayOrientation(90);
        } else if (display.getRotation() == Surface.ROTATION_270) {
            camera.setDisplayOrientation(180);
        } else {
            camera.setDisplayOrientation(0);
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String res = preferences.getString("resolution", "640x480");
        String[] resParts = res.split("x");

        Camera.Parameters p = camera.getParameters();
        p.setPreviewSize(Integer.parseInt(resParts[0]), Integer.parseInt(resParts[1]));
        camera.setParameters(p);

        try {
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        camera.startPreview();

        holder.addCallback(this);

        previewRunning = true;
    }

    private void stopPreview() {
        if (!previewRunning) return;

        holder.removeCallback(this);
        camera.stopPreview();
        camera.setPreviewCallback(null);

        previewRunning = false;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        stopPreview();
        if (camera != null) camera.release();
        camera = null;

        openCamAndPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        openCamAndPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        stopPreview();
        if (camera != null) camera.release();
        camera = null;
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        previewStream.reset();
        Camera.Parameters p = camera.getParameters();

        int previewHeight = p.getPreviewSize().height,
            previewWidth = p.getPreviewSize().width;

        switch(rotationSteps) {
            case 1:
                bytes = Rotator.rotateYUV420Degree90(bytes, previewWidth, previewHeight);
                break;
            case 2:
                bytes = Rotator.rotateYUV420Degree180(bytes, previewWidth, previewHeight);
                break;
            case 3:
                bytes = Rotator.rotateYUV420Degree270(bytes, previewWidth, previewHeight);
                break;
        }

        if (rotationSteps == 1 || rotationSteps == 3) {
            int tmp = previewHeight;
            previewHeight = previewWidth;
            previewWidth = tmp;
        }

        int format = p.getPreviewFormat();
        new YuvImage(bytes, format, previewWidth, previewHeight, null)
                .compressToJpeg(new Rect(0, 0, previewWidth, previewHeight),
                        100, previewStream);

        setJpegFrame(previewStream);
    }

    /*  ————添加人脸采集&识别功能————  */

    //页面按钮行为绑定 人脸采集
    public void startFaceCollect(View view) {  //采集
        // 弹出选择框（从图库选择 or 从摄像头录入）
        ArrayAdapter adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        adapter.add("从图库导入");
        adapter.add("实时录入");
        DialogPlus dialog = DialogPlus.newDialog(this)
                .setAdapter(adapter)
                .setCancelable(true)
                .setExpanded(true)
                .setOnItemClickListener(new OnItemClickListener() {
                    @Override
                    public void onItemClick(DialogPlus dialog, Object item, View view, int position) { //点击不同的item 有不同的操作
                        switch (position) {
                            case 0:
                                //启动图库
                                openCustomGallery(); //信息采集模块1  图库
                                break;
                            case 1:
                                //启动实时录入
                                openFaceCollectView(); //实时人脸录入模块
                                break;
                                default:
                                Log.e("错误", "list点击错误");
                        }
                        dialog.dismiss();
                    }
                })
                .create();
        dialog.show();
    }

    // 页面按钮行为绑定 人脸识别
    // 要实现自动识别
    public void startFaceRecognize(View view) {
        // 弹出选择框（从图库选择 or 从摄像头录入）
        ArrayAdapter adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        adapter.add("从图库中识别");
        adapter.add("实时识别");
        DialogPlus dialog = DialogPlus.newDialog(this)
                .setAdapter(adapter)
                .setCancelable(true)
                .setExpanded(true)
                .setOnItemClickListener(new OnItemClickListener() {
                    @Override
                    public void onItemClick(DialogPlus dialog, Object item, View view, int position) {
                        switch (position) {
                            case 0:
                                //启动图库
                                openCustomGalleryForRec(); //信息采集模块2
                                break;
                            case 1:
                                //启动相机
                                openFaceRecognizeView(); //实施预览模块
                                break;
                            default:
                                Log.e("错误", "list点击错误");
                        }
                        dialog.dismiss();
                    }
                })
                .create();
        dialog.show();
    }

    //信息采集模块1 启动图库
    private void openCustomGallery() {  //启动图库
        //rxgalleryfinal 一个强大的图片选择器
        //Log.d("context","打开了吗");
        RxGalleryFinal
                .with(MainActivity.this)
                .image()
                .multiple()
                .maxSize(15)
                .imageLoader(ImageLoaderType.GLIDE)
                .subscribe(new RxBusResultSubscriber<ImageMultipleResultEvent>() {
                    @Override
                    protected void onEvent(ImageMultipleResultEvent imageMultipleResultEvent) throws Exception {
                        //图片选择结果
                        List<MediaBean> pictures = imageMultipleResultEvent.getResult();
                        List<String> faces = new ArrayList();
                        for(MediaBean p:pictures){
                            faces.add(p.getOriginalPath());
                        }
                        //输出识别出的脸
                        boolean hasDetected = false;
                        showDetectResult(faces, hasDetected); //人脸检测结果展示模块
                    }
                })
                .openGallery();
    }

    //信息采集模块2 识别
    private void openCustomGalleryForRec() {  //识别..
        RxGalleryFinal
                .with(MainActivity.this)
                .image()
                .radio()
                .imageLoader(ImageLoaderType.GLIDE)
                .subscribe(new RxBusResultSubscriber<ImageRadioResultEvent>() {
                    @Override
                    protected void onEvent(ImageRadioResultEvent imageRadioResultEvent) throws Exception {
                        //图片选择  结果
                        MediaBean picture = imageRadioResultEvent.getResult();
                        //调用人脸检测模块
                        opencv_core.Mat grayImg = imread(picture.getOriginalPath(), CV_LOAD_IMAGE_GRAYSCALE);
                        Map<String, Object> r = FaceManager.detectFace(grayImg);
                        //调用人脸识别模块
                        if(r != null) {
                            opencv_core.IplImage sourcePicture = (opencv_core.IplImage) r.get("sourcePicture");
                            opencv_core.CvSeq facesSeq = (opencv_core.CvSeq) r.get("faces");
                            opencv_core.CvRect faceRect = FaceManager.getBiggestRect(facesSeq);
                            opencv_core.Mat face = FaceManager.getFaceFromPicture(sourcePicture, faceRect);
                            opencv_core.Mat centerFace = FaceManager.facePreProcess(face);
                            if (centerFace == null) centerFace = face;
                            centerFace = FaceManager.lightNormalize(centerFace);
                            String name = FaceManager.recognizeFace(centerFace);
                            String gender = FaceManager.recognizeGender(centerFace);
                            Toast.makeText(getApplicationContext(), "识别结果：" + name + "_" + gender, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplicationContext(), "没有检测到人脸", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .openGallery();
    }

    //实时人脸录入模块
    private void openFaceCollectView() {
        //使用场景：人脸对着前置摄像头，上下，左右转动
        isRecognizePreview = true;
        RelativeLayout layout = new RelativeLayout(this);
        final FaceCollectView faceCollectView = new FaceCollectView(this);
        CameraView cameraView = new CameraView(this, faceCollectView);
        //拍照按钮 && 保存按钮
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int height = dm.heightPixels;
        Button takePhotoBtn = new Button(this);
        takePhotoBtn.setText("拍照");
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(200,200);
        layoutParams.topMargin = height - 400;
        takePhotoBtn.setLayoutParams(layoutParams);
        takePhotoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean hasRecorded = faceCollectView.recordFace(); // facecollectview类里的函数
                if (hasRecorded) {
                    Toast.makeText(getApplicationContext(), "人脸捕获成功", Toast.LENGTH_SHORT).show();
                }
            }
        });
        Button saveBtn = new Button(this);
        saveBtn.setText("录入");
        RelativeLayout.LayoutParams layoutParams2 = new RelativeLayout.LayoutParams(200,200);
        layoutParams2.leftMargin = 200;
        layoutParams2.topMargin = height - 400;
        saveBtn.setLayoutParams(layoutParams2);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean hasDetected = true;
                setContentView(R.layout.activity_main);
                showDetectResult(faceCollectView.getFaces(), hasDetected);  //facecollectview模块
            }
        });
        layout.addView(cameraView);
        layout.addView(faceCollectView);
        layout.addView(takePhotoBtn);
        layout.addView(saveBtn);
        setContentView(layout);
    }

    //实时预览模块
    //要实现的是  自动实时预览
    private void openFaceRecognizeView() {
        try {
            isRecognizePreview = true;

            FrameLayout layout = new FrameLayout(this);
            final FaceRecognizeView faceRecognizeView = new FaceRecognizeView(this);
            final CameraView cameraView = new CameraView(this, faceRecognizeView);
            //设置的前置，所以切换一下
            cameraView.switchCamera();
            faceRecognizeView.switchCamera();
            //前后摄像头切换按钮           ----应该不需要
            Button switchBtn = new Button(this);
            switchBtn.setText("切换");
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 200);
            switchBtn.setLayoutParams(flp);
            switchBtn.setOnClickListener(new View.OnClickListener() {
               @Override
                public void onClick(View view) {
                    cameraView.switchCamera();
                    faceRecognizeView.switchCamera();
                }
            });

            layout.addView(cameraView);
            layout.addView(faceRecognizeView);
        //    layout.addView(switchBtn);
            setContentView(layout);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //人脸检测结果展示模块
    private void showDetectResult(List<String> faces, boolean hasDetected) {
        //启动另一个activity
        Intent intent = new Intent();
        intent.setAction("showFaces");
        ImageListSer imageListSer = new ImageListSer();
        imageListSer.setImages(faces);
        intent.putExtra("faces", imageListSer);
        intent.putExtra("hasDetected", hasDetected);
        startActivity(intent);
    }

    //创建目录
    private void createProjectDirectory() {  //创建目录
        // 代码冗余，后期合并
        File folder = new File(Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/");
        if (!folder.exists()) {
            folder.mkdir();
        }
        folder = new File(Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/faces");
        if (!folder.exists()) {
            folder.mkdir();
        }
        folder = new File(Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/temp");
        if (!folder.exists()) {
            folder.mkdir();
        }
        folder = new File(Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/test");
        if (!folder.exists()) {
            folder.mkdir();
        }
        folder = new File(Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/result");
        if (!folder.exists()) {
            folder.mkdir();
        }
    }

    private void checkPermissions() { //检查权限s
        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE); //存储
        checkPermission(Manifest.permission.CAMERA);  //camera
    }
    private void checkPermission(String permission) { //检查权限
        if (!(ActivityCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{permission}, 1);
            //没有权限的时候就要求获得权限。
        }
    }
}
