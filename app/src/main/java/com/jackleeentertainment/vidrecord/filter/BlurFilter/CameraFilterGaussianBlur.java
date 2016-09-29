package com.jackleeentertainment.vidrecord.filter.BlurFilter;

import android.content.Context;

import com.jackleeentertainment.vidrecord.filter.CameraFilter;
import com.jackleeentertainment.vidrecord.filter.FilterGroup;


public class CameraFilterGaussianBlur extends FilterGroup<CameraFilter> {

    public CameraFilterGaussianBlur(Context context, float blur) {
        super();
        addFilter(new CameraFilterGaussianSingleBlur(context, blur, false));
        addFilter(new CameraFilterGaussianSingleBlur(context, blur, true));
    }
}
