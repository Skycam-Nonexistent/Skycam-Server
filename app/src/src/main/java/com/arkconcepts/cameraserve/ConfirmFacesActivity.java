package com.arkconcepts.cameraserve;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_core.Mat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.arkconcepts.cameraserve.utils.FaceListAdapter;
import com.arkconcepts.cameraserve.utils.FaceManager;
import com.arkconcepts.cameraserve.utils.ImageListSer;

import static org.bytedeco.javacpp.opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE;
import static org.bytedeco.javacpp.opencv_imgcodecs.cvSaveImage;
import static org.bytedeco.javacpp.opencv_imgcodecs.imread;

public class ConfirmFacesActivity extends AppCompatActivity {

    private EditText input_face_name = null;
    private List<String> faces = null;
    private boolean hasDetected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 关于Bundle类型：
        // 类似于map 内有putxxx()和getxxx()函数用于放入数据或获取数据。
        // 使用Bundle在Activity间传递数据

        super.onCreate(savedInstanceState); //Activity在onCreate()设置所有的“全局”状态
        setContentView(R.layout.activity_confirm_faces); //显示指定控件
        input_face_name = (EditText) findViewById(R.id.input_face_name); //findViewById(R.id.xml文件中对应的id) 获取按钮

        ImageListSer imageListSer = (ImageListSer) getIntent().getSerializableExtra("faces"); //传递对象 将对象序列化
        hasDetected = (boolean) getIntent().getSerializableExtra("hasDetected");
        List<String> facesPaths = imageListSer.getImages();
        faces = new ArrayList();


        for(String p:facesPaths){
            if (hasDetected) {
                faces.add(p);
            } else {
                Mat raw = imread(p, CV_LOAD_IMAGE_GRAYSCALE); //读入图片数据  p->raw->r->faceSeq
                Map<String, Object> r = FaceManager.detectFace(raw); //facemanager里的人脸检测函数
                if(r != null) {
                    //存下
                    IplImage sourcePicture = (IplImage) r.get("sourcePicture");

                    CvSeq facesSeq = (CvSeq) r.get("faces");
                    CvRect faceRect = FaceManager.getBiggestRect(facesSeq); //获取最大的人脸区域
                    Mat face = FaceManager.getFaceFromPicture(sourcePicture, faceRect); //从图片中裁剪出人脸
                    Mat centerFace = FaceManager.facePreProcess(face); //人脸预处理
                    if (centerFace == null) centerFace = face;
                    centerFace = FaceManager.lightNormalize(centerFace); //光照归一化
                    String fileName =  System.currentTimeMillis() + "." + getFileType(p);
                    String absPath = Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/temp/" + fileName; ////////////////////
                    cvSaveImage(absPath, new IplImage(centerFace)); //路径
                    faces.add(absPath);
                }
            }
        }

        //显示多选列表
        ListView listView = (ListView) findViewById(R.id.listView);
        FaceListAdapter adapter = new FaceListAdapter(faces, this);
        listView.setAdapter(adapter);
    }

    //页面按钮行为绑定
    public void cancel(View view) {
        //后续要做很多事情
        this.finish();
    }
    public void confirm(View view) {
        //判断是否输入名字
        String name = input_face_name.getText().toString();
        if (name.equals("")) {
            Toast.makeText(getApplicationContext(), "请输入名字", Toast.LENGTH_SHORT).show();
            input_face_name.requestFocus();
        } else {
            //将faces持久化
            // environment类：该类是用于程序访问SDCard的一个设备访问类

            String faceFolder = Environment.getExternalStorageDirectory() + "/FaceRecognizeSystem/faces/" + name;  //获得外部存储媒体目录（根目录）。 ///////////////////////
            File folder = new File(faceFolder);
            if (!folder.exists()) {
                folder.mkdir();
            }
            for(String path:faces) {
                //移动识别好的人脸到另一个文件夹（吧）
                File tempFile = new File(path);
                tempFile.renameTo(new File(faceFolder + "/" + getFileName(path)));
            }
            Toast.makeText(getApplicationContext(), "人脸数据保存成功，重新建模", Toast.LENGTH_SHORT).show();
            FaceManager.initRecognizer(getApplicationContext()); //初始化
            Toast.makeText(getApplicationContext(), "系统建模完成", Toast.LENGTH_SHORT).show(); //快速的为用户显示少量的信息
            //toast.makText(getApplicationContext()当前的上下文环境 ， 要显示的字符串 ， 显示的时间长短 分length_long长 length_short短  )
            this.finish();
        }
    }

    private String getFileName(String path) {
        String[] arr = path.split("/");
        return arr[arr.length - 1];
    }

    private String getFileType(String path) {
        String[] arr = path.split("[.]");
        return arr[arr.length - 1];
    }
}
