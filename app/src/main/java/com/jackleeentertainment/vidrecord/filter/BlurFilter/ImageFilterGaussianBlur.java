package com.jackleeentertainment.vidrecord.filter.BlurFilter;

import android.content.Context;

import com.jackleeentertainment.vidrecord.filter.CameraFilter;
import com.jackleeentertainment.vidrecord.filter.FilterGroup;


public class ImageFilterGaussianBlur extends FilterGroup<CameraFilter> {

    public ImageFilterGaussianBlur(Context context, float blur) {
        super();
        addFilter(new ImageFilterGaussianSingleBlur(context, blur, false));
        addFilter(new ImageFilterGaussianSingleBlur(context, blur, true));
    }
}
