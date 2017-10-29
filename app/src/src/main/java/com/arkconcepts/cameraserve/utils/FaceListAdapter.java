package com.arkconcepts.cameraserve.utils;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import com.arkconcepts.cameraserve.R;

//http://www.2cto.com/kf/201303/196574.html

public class FaceListAdapter extends BaseAdapter {

    private List<String> faceList;
    private static HashMap<Integer, Boolean> isSelected;
    private Context context;
    private LayoutInflater inflater = null;

    public FaceListAdapter(List<String> faceList, Context context) {
        this.context = context;
        this.faceList = faceList;
        inflater = LayoutInflater.from(context);
        isSelected = new HashMap<Integer, Boolean>();

        // 初始化数据
        initDate();
    }

    private void initDate() {
        for (int i = 0; i < faceList.size(); i++) {
            getIsSelected().put(i, true);
        }
    }

    public static HashMap<Integer, Boolean> getIsSelected() {
        return isSelected;
    }

    @Override
    public int getCount() {
        return faceList.size();
    }

    @Override
    public String getItem(int i) {
        return faceList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {
        ViewHolder holder = null;
        if (convertView == null) {
            // 获得ViewHolder对象
            holder = new ViewHolder();
            // 导入布局并赋值给convertview
            convertView = inflater.inflate(R.layout.face_list_item, null);
            holder.imageView = (ImageView) convertView.findViewById(R.id.face_img);
            holder.checkBox = (CheckBox) convertView.findViewById(R.id.face_checkbox);
            // 为view设置标签
            convertView.setTag(holder);
        } else {
            // 取出holder
            holder = (ViewHolder) convertView.getTag();
        }
        // 设置listitem内容
        String face = faceList.get(i);
        Uri uri = Uri.fromFile(new File(face));
        holder.imageView.setImageURI(uri);
        // 根据isSelected来设置checkbox的选中状况
        holder.checkBox.setChecked(getIsSelected().get(i));
        return convertView;
    }

    public static class ViewHolder {
        ImageView imageView;
        CheckBox checkBox;
    }
}
