package com.arkconcepts.cameraserve.utils;

import java.io.Serializable;
import java.util.List;


public class ImageListSer implements Serializable {

    private List<String> images;

    public void setImages(List<String> images) {
        this.images = images;
    }

    public List<String> getImages() {
        return images;
    }
}
