package com.jackleeentertainment.vidrecord.filter;

import android.content.Context;
import android.support.annotation.DrawableRes;

import com.jackleeentertainment.vidrecord.R;
import com.jackleeentertainment.vidrecord.grafika.gles.GlUtil;


public class CameraFilterBlendSoftLight extends CameraFilterBlend {

    public CameraFilterBlendSoftLight(Context context, @DrawableRes int drawableId) {
        super(context, drawableId);
    }

    @Override protected int createProgram(Context applicationContext) {

        return GlUtil.createProgram(applicationContext, R.raw.vertex_shader_two_input,
                R.raw.fragment_shader_ext_blend_soft_light);
    }
}