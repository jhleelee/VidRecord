package com.jackleeentertainment.vidrecord.ui.layout;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;

import android.opengl.GLES20;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;


import com.jackleeentertainment.vidrecord.App;
import com.jackleeentertainment.vidrecord.core.GlobalSetting;
import com.jackleeentertainment.vidrecord.core.camera.CameraUtils;
import com.jackleeentertainment.vidrecord.core.data.EncoderBufferHolder;
import com.jackleeentertainment.vidrecord.core.data.JTimeStamp;
import com.jackleeentertainment.vidrecord.core.data.JaehakEncoder;
import com.jackleeentertainment.vidrecord.R;
import com.jackleeentertainment.vidrecord.grafika.AspectFrameLayout;
import com.jackleeentertainment.vidrecord.ui.widget.Bar;
import com.jackleeentertainment.vidrecord.ui.widget.FilterBaseAdapter;
import com.jackleeentertainment.vidrecord.core.data.Grab;
import com.jackleeentertainment.vidrecord.ui.widget.LVItemFilter;
import com.jackleeentertainment.vidrecord.ui.widget.ProgressView;
import com.jackleeentertainment.vidrecord.filter.FilterManager;
import com.jackleeentertainment.vidrecord.grafika.gles.EglCore;
import com.jackleeentertainment.vidrecord.grafika.gles.FullFrameRect;
import com.jackleeentertainment.vidrecord.grafika.gles.WindowSurface;
import com.sprylab.android.widget.TextureVideoView;

import org.lucasr.twowayview.TwoWayView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Created by Jacklee on 16. 4. 16..
 */
public class CameraFragment extends Fragment implements
        AdapterView.OnItemClickListener,
        SurfaceHolder.Callback,
        SurfaceTexture.OnFrameAvailableListener {

    // Base
    private static String TAG = "CameraFragment";
    private View view;
    private PowerManager.WakeLock mWakeLock;

    // UI
    TextureVideoView ttv;
    ImageButton ibRecord, ibFilter, ibDelete, ibNext, ibFlash, ibChangeCamera, ibPlay;
    LinearLayout loRecordBtnEtc;
    ProgressView progressView;
    TwoWayView lvFilter;
    FrameLayout fr;
    FilterBaseAdapter filterBaseAdapter;
    ArrayList<LVItemFilter> rvItemFilterList = new ArrayList<>();
    public ImageView ivCover;

    //MainHandler
    MainHandler mainHandler;

    // Camera
    Camera mCamera;
    Camera.Parameters cameraParameters = null;
    int numCameras = 0;
    int mCameraPreviewThousandFps;
    boolean isFlashOn = false;
    static int selectedIntFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;

    //GL
    EglCore mEglCore;
    WindowSurface surfaceSurfaceView_BpToSurfaceFlingerConsumer;
    SurfaceTexture surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener;  // receives the output from the camera preview
    WindowSurface windowSurface_BpToGraphicBufferSource;
    FullFrameRect fullFrameRect;

    int intOpenGLTextureName;
    boolean isSurfaceCreated = false;
    boolean isResumed = false;
    private int mIncomingWidth, mIncomingHeight;
    private final float[] mTmpMatrix = new float[16];

    //VideoFramesAndSettings
    public static int intTotalVideoLengthSeconds = 30;
    private int mFrameNum;
    int intMaxRecordSec = 15;
    private boolean isVideoViewPrepared = false;

    //Encoder
    JaehakEncoder jaehakEncoder;
    boolean isPressingRecordBtn = false;
    int intRecordedFrameNum = 0;
    int byteArrayTotalDx = 0;

    //Filter
    FilterManager.FilterType mCurrentFilterType = FilterManager.FilterType.Normal;
    FilterManager.FilterType mNewFilterType = null;

    //SaveVideo
    private File outputFile;

    private static class MainHandler extends Handler implements JaehakEncoder.Callback {
        public static final int MSG_BLINK_TEXT = 0;
        public static final int MSG_FRAME_AVAILABLE = 1;
        public static final int MSG_FILE_SAVE_COMPLETE = 2;
        public static final int MSG_BUFFER_STATUS = 3;

        private WeakReference<CameraFragment> mWeakFragment;

        public MainHandler(CameraFragment cameraFragment) {
            mWeakFragment = new WeakReference<CameraFragment>(cameraFragment);
        }

        // JaehakEncoder.Callback, called on encoder thread
        @Override
        public void fileSaveComplete(int status) {
            sendMessage(obtainMessage(MSG_FILE_SAVE_COMPLETE, status, 0, null));
        }

        // JaehakEncoder.Callback, called on encoder thread
        @Override
        public void bufferStatus(long totalTimeMsec) {
            sendMessage(obtainMessage(MSG_BUFFER_STATUS,
                    (int) (totalTimeMsec >> 32), (int) totalTimeMsec));
        }


        @Override
        public void handleMessage(Message msg) {
            CameraFragment cameraFragment = mWeakFragment.get();
            if (cameraFragment == null) {
                Log.d(TAG, "Got message for dead activity");
                return;
            }

            switch (msg.what) {

                case MSG_FRAME_AVAILABLE: {
                    /************************************************************************

                     [ContinueosCaptureActivity : SurfaceTexture.OnFrameAvailableListener]

                     @Override public void onFrameAvailable(SurfaceTexture surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener) {
                     //Log.d(TAG, "frame available");
                     mainHandler.sendEmptyMessage(MainHandler.MSG_FRAME_AVAILABLE);
                     }

                     ***********************************************************************/
                    cameraFragment.drawFrameOntoSurfaceViewAndEncoderSurface();
//                    activity.updateProgressBarJack(activity.isPressingRecordBtn);
                    break;
                }
                case MSG_FILE_SAVE_COMPLETE: {
                    FullscreenActivity.isVideoSaved = true;
                    cameraFragment.fileSaveComplete(msg.arg1);
                    break;
                }
                case MSG_BUFFER_STATUS: {
                    long duration = (((long) msg.arg1) << 32) |
                            (((long) msg.arg2) & 0xffffffffL);
                    Log.d(TAG, "handleMessage() MSG_BUFFER_STATUS " + String.valueOf(duration));
                    cameraFragment.updateBufferStatus(duration);
                    break;
                }
                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
        }
    }


    /**
     * Fragment LifeCycle
     */

    public static CameraFragment newInstance(int page, String title) {
        Log.d(TAG, "newInstance()");
        CameraFragment cameraFragment = new CameraFragment();
        Bundle args = new Bundle();
        cameraFragment.setArguments(args);
        return cameraFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate ...");
        Grab.arlGrabbedBbEncodedData = new ArrayList<>();
        Grab.arlTimesOfBlocks = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView ...");
        view = inflater.inflate(R.layout.fragment_camera, container, false);
        initUI();
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // configure machine
        checkHasFlashIfNotFlashButtonInvisible();
        getActualScreenDimension();
        getVideoDimensionMinimumResolution();
        // output file
        setOutputFile(new File(getActivity().getFilesDir(),
                "jack_" + String.valueOf(System.currentTimeMillis()) + ".mp4"));
        //filter
        initFilterAndAdapter();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart()");
        super.onStart();
        mainHandler = new MainHandler(this);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        isResumed = true;
        super.onResume();
        isFlashOn = false;
        openCamera(
                selectedIntFacing,
                GlobalSetting.VIDEO_WIDTH,
                GlobalSetting.VIDEO_HEIGHT,
                GlobalSetting.DESIRED_PREVIEW_FPS); //15
        initListenersForCamera();
        trySetCameraTextureAndEncoder();
        ibNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveVideo();

            }
        });
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause()");
        isResumed = false;
        super.onPause();
        releaseCamera();

        if (jaehakEncoder != null) {
            jaehakEncoder.shutdown();
            jaehakEncoder = null;
        }
        if (surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener != null) {
            surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener.release();
            surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener = null;
        }
        if (surfaceSurfaceView_BpToSurfaceFlingerConsumer != null) {
            surfaceSurfaceView_BpToSurfaceFlingerConsumer.release();
            surfaceSurfaceView_BpToSurfaceFlingerConsumer = null;
        }
        if (fullFrameRect != null) {
            fullFrameRect.release(false);
            fullFrameRect = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
        Log.d(TAG, "onPause() done");
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
        mainHandler.removeCallbacksAndMessages(null);
    }


    /******************************
     * Camera
     ******************************/

    private void openCamera(int selectedIntFacing, int desiredWidth, int desiredHeight, int desiredFps) {
        String TAG_openCamera = "openCamera()";

        // (1)
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized");
        }
        Log.d(TAG_openCamera, "(1) Check If Camera Already Initialized - done");

        // (2)
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

        // Try to find a front-facing camera (e.g. for videoconferencing).
        numCameras = Camera.getNumberOfCameras();
        Log.d(TAG_openCamera, "numCameras :" + String.valueOf(numCameras));

        for (int i = 0; i < numCameras; i++) {
            Log.d(TAG_openCamera, "for (int i = 0; i < numCameras; i++) : " + String.valueOf(i));
            /**
             * The direction that the camera faces. It should be
             * CAMERA_FACING_BACK or CAMERA_FACING_FRONT.
             */
            Log.d(TAG_openCamera, "selectedIntFacing : " + String.valueOf(selectedIntFacing));

            if (i == selectedIntFacing) {
                Log.d(TAG_openCamera, "Camera.open(i) : " + String.valueOf(i));
                mCamera = Camera.open(i);
            }

            if (i != Camera.CameraInfo.CAMERA_FACING_FRONT) {
                ibFlash.setVisibility(View.GONE);
            } else {
                ibFlash.setVisibility(View.VISIBLE);
            }
        }
        Log.d(TAG_openCamera, "(2) Get Camera Info - a. numCamera , b. FacingAt - done");

        // (3)
        if (mCamera == null) {
            Log.d(TAG_openCamera, "No front-facing camera found; opening default");
            mCamera = Camera.open();    // opens first back-facing camera
        }

        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }
        Log.d(TAG_openCamera, "(3) openCamera() - done");


        /**
         * set Camera Parameters
         */

        mCamera.setDisplayOrientation(90); // *** 90 Degree
        cameraParameters = mCamera.getParameters();
        CameraUtils.choosePreviewSize(cameraParameters, desiredWidth, desiredHeight); //cameraParameters.setPreviewSize(GlobalSetting.SCREEN_WIDTH, GlobalSetting.SCREEN_HEIGHT);
        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        cameraParameters.setRecordingHint(true);
        mCamera.setParameters(cameraParameters);

        /**
         * set Fps
         */

        mCameraPreviewThousandFps = CameraUtils.chooseFixedPreviewFps(cameraParameters, desiredFps * 1000);
        Log.d(TAG_openCamera, "mCameraPreviewThousandFps " + String.valueOf(mCameraPreviewThousandFps));

        /**
         * set the preview aspect ratio.
         */
        Camera.Size cameraPreviewSize = cameraParameters.getPreviewSize();
        AspectFrameLayout layout = (AspectFrameLayout) view.findViewById(R.id.continuousCapture_afl);
        layout.setAspectRatio((double) cameraPreviewSize.height / cameraPreviewSize.width); // ** 90 degree

        //log
        String previewFacts = cameraPreviewSize.width + "x" + cameraPreviewSize.height +
                " @" + (mCameraPreviewThousandFps / 1000.0f) + "fps";
        Log.d(TAG_openCamera, "Camera config: " + previewFacts);

    }


    private void initListenersForCamera() {
        /**
         * Flash
         */
        ibFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                    Log.d(TAG, "No Flash Supported");
                    return;
                }
                if (isFlashOn) {
                    isFlashOn = false;
                    Log.d(TAG, "isFlashOn = false");

                    ibFlash.setSelected(false);
                    cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                } else {
                    isFlashOn = true;
                    Log.d(TAG, "isFlashOn = true");

                    ibFlash.setSelected(true);
                    cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                }
                mCamera.setParameters(cameraParameters);
            }
        });

        /**
         * ChangeCamera
         */
        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            ibChangeCamera.setVisibility(View.VISIBLE);
        }

        ibChangeCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "btChangeCamera onClick()");
                coverView(true);

                //(1) Stop Preview &Release
                Log.d(TAG, "Pause camera...");
                isResumed = false;
                releaseCamera();

                if (jaehakEncoder != null) {
                    jaehakEncoder.shutdown();
                    jaehakEncoder = null;
                }
                if (surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener != null) {
                    surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener.release();
                    surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener = null;
                }
                if (surfaceSurfaceView_BpToSurfaceFlingerConsumer != null) {
                    surfaceSurfaceView_BpToSurfaceFlingerConsumer.release();
                    surfaceSurfaceView_BpToSurfaceFlingerConsumer = null;
                }
                if (fullFrameRect != null) {
                    fullFrameRect.release(false);
                    fullFrameRect = null;
                }
                if (mEglCore != null) {
                    mEglCore.release();
                    mEglCore = null;
                }
                if (mWakeLock != null) {
                    mWakeLock.release();
                    mWakeLock = null;
                }
                Log.d(TAG, "Pause camera... done");

                //(2) Change CameraFacing
                if (selectedIntFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    selectedIntFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
                } else {
                    selectedIntFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
                }

                //(3) Open & Start Preview
                Log.d(TAG, "Open camera... ");
                isResumed = true;
                openCamera(
                        selectedIntFacing,
                        GlobalSetting.VIDEO_WIDTH,  //720 ** 90 Degree
                        GlobalSetting.VIDEO_HEIGHT, // 1280 ** 90 Degree
                        GlobalSetting.DESIRED_PREVIEW_FPS); //15
                initListenersForCamera();
                trySetCameraTextureAndEncoder();
                Log.d(TAG, "Open camera... done");
                coverView(false);
            }
        });

        /**
         * Record
         */
        //initListener
        ibRecord.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        Log.d(TAG, "ACTION_DOWN");
                        v.setBackgroundDrawable(getActivity().getResources().getDrawable(R.drawable.ib_record_ontouch_down));

                        isPressingRecordBtn = true;
                        JTimeStamp.onTouch();

                        //Create Bar
                        Bar bar = new Bar();
                        bar.setaTimeMill(System.currentTimeMillis());
                        bar.setaAudioShortDx(lklShorts.size());
                        bar.setaVideoDxOfByteArrayTotal(byteArrayTotalDx);
                        arlBar.add(bar);

                        //View
                        progressView.setCurrentState(ProgressView.State.START);

//                        Log.d(TAG, "Grab.arlGrabbedBbEncodedData.size() " + String.valueOf(Grab.arlGrabbedBbEncodedData.size()));
//                        if (Grab.arlGrabbedBbEncodedData.size() <= (GlobalSetting.DESIRED_PREVIEW_FPS * intMaxRecordSec)) {
//                            isPressingRecordBtn = true;
//                            Grab.longGrabBeginTimeOfBlock = System.currentTimeMillis();
//                            Grab.arlTimesOfBlocks.add(Grab.longGrabBeginTimeOfBlock);
//                        } else {
//
//                        }

                        return true;

                    case MotionEvent.ACTION_UP:
                        Log.d(TAG, "ACTION_UP");
                        v.setBackgroundDrawable(getActivity().getResources().getDrawable(R.drawable.ib_record_ontouch_up));

                        progressView.setCurrentState(ProgressView.State.PAUSE);

                        Log.d(TAG, "intRecordedFrameNum : " + String.valueOf(intRecordedFrameNum));
                        /**
                         * Decode an immutable bitmap from the specified byte array.
                         *
                         * @param data byte array of compressed image data
                         * @param offset offset into imageData for where the decoder should begin
                         *               parsing.
                         * @param length the number of bytes, beginning at offset, to parse
                         * @return The decoded bitmap, or null if the image could not be decoded.
                         */
                        isPressingRecordBtn = false;

//                        getLastFrameBitmap();
                        JTimeStamp.offTouch();


                        int lastDxRecordedInByteArrayTotal = EncoderBufferHolder.encBuffer
                                .getHeadStart() - 1;
                        Log.d(TAG, "lastDxRecordedInByteArrayTotal : " + String.valueOf(lastDxRecordedInByteArrayTotal));

                        //Finish Bar
                        arlBar.getLast().setbTimeMill(System.currentTimeMillis());
                        arlBar.getLast().setbAudioShortDx(lklShorts.size() - 1);
                        arlBar.getLast().setbVideoDxOfFrame(intRecordedFrameNum - 1);
                        arlBar.getLast().setbVideoDxOfByteArrayTotal(lastDxRecordedInByteArrayTotal);


                        //progressView
                        long totalRecordedTime = 0;
                        for (int i = 0; i < arlBar.size(); i++) {
                            totalRecordedTime += (arlBar.get(i).getbTimeMill() - arlBar.get(i).getaTimeMill());
                        }

                        progressView.putProgressList(totalRecordedTime);

//                        if (totalTime >= recordingMinimumTime) {
//                            currentRecorderState = RecorderState.SUCCESS;
//                            mHandler.sendEmptyMessage(2);
//                        } else if (totalTime >= recordingChangeTime) {
//                            currentRecorderState = RecorderState.CHANGE;
//                            mHandler.sendEmptyMessage(2);
//                        }

                        return true;
                }
                return false;
            }
        });

        /**
         * Filter
         */

        fr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loRecordBtnEtc.setVisibility(View.VISIBLE);
                lvFilter.setVisibility(View.GONE);
                fr.setVisibility(View.INVISIBLE);
            }
        });

        ibFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loRecordBtnEtc.setVisibility(View.GONE);
                lvFilter.setVisibility(View.VISIBLE);
                fr.setVisibility(View.VISIBLE);

            }
        });
        ibNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveVideo();

            }
        });


        /**
         * Play
         */
        ibPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveVideo();

            }
        });
        /**
         * Delete
         */
        ibDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (arlBar != null && arlBar.size() > 1) {
                    Bar lastBar = arlBar.get(arlBar.size() - 1);
                    Bar lastBar2 = arlBar.get(arlBar.size() - 2);

                    //Delete Video ByteArray
                    byte[] deductedByteArray = new byte[EncoderBufferHolder.encBuffer.getBaTotalSize()]; //22,500,000

                    Log.d(TAG, "lastBar2.getbVideoDxOfByteArrayTotal() + 1 : " + String.valueOf(lastBar2.getbVideoDxOfByteArrayTotal() + 1));
                    ByteBuffer bbDeducted = ByteBuffer.wrap(
                            EncoderBufferHolder.encBuffer.getBaTotal(),
                            0,
                            lastBar2.getbVideoDxOfByteArrayTotal() + 1); // add one because it is length

//                    bbDeducted.flip();
                    Log.d(TAG,
                            "bbDeducted.limit() : " + String.valueOf(bbDeducted.limit()) +
                                    ", bbDeducted.array().length : " + String.valueOf(bbDeducted.array().length)

                    );
                    bbDeducted.get(deductedByteArray, 0, bbDeducted.limit()); //BufferUnderflowException - Unchecked exception thrown when a relative get operation reaches the source buffer's limit.
                    EncoderBufferHolder.encBuffer.setBaTotal(deductedByteArray);
                    EncoderBufferHolder.encBuffer.setBbTotal(bbDeducted);
                    EncoderBufferHolder.encBuffer.setIntMetaHead(lastBar2.getbVideoDxOfFrame() + 1);

                    //Delete arlShort Part
                    while (lklShorts.size() > lastBar.getaAudioShortDx()) {
                        lklShorts.remove(lklShorts.size() - 1);
                    }

                    //Delete ProgressView Part
                    progressView.removeLastItemOfLinkedList();
                    arlBar.removeLast();
                } else if (arlBar != null && arlBar.size() == 1) {

                    Bar lastBar = arlBar.get(arlBar.size() - 1);

                    //Delete Video ByteArray
                    byte[] deductedByteArray = new byte[EncoderBufferHolder.encBuffer.getBaTotalSize()]; //22,500,000
                    EncoderBufferHolder.encBuffer.setBaTotal(deductedByteArray);
                    EncoderBufferHolder.encBuffer.setBbTotal(ByteBuffer.allocate(deductedByteArray.length));
                    EncoderBufferHolder.encBuffer.setIntMetaHead(0);

                    //Delete arlShort Part
                    while (lklShorts.size() > lastBar.getaAudioShortDx()) {
                        lklShorts.remove(lklShorts.size() - 1);
                    }

                    //Delete ProgressView Part
                    progressView.removeLastItemOfLinkedList();

                    arlBar = new LinkedList<Bar>();
                }
            }
        });


    }

    private void releaseCamera() {
        Log.d(TAG, "releaseCamera()");
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.lock();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }


    /******************************
     * encoder
     ******************************/

    private void trySetCameraTextureAndEncoder() {
        String TAG_openCamera = "setCamTtrEncoder()";

        if (isSurfaceCreated && isResumed) {
            Log.d(TAG_openCamera, "isSurfaceCreated && isResumed");
            mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
            surfaceSurfaceView_BpToSurfaceFlingerConsumer = new WindowSurface(
                    mEglCore,
                    surfaceHolder.getSurface(),
                    false);
            surfaceSurfaceView_BpToSurfaceFlingerConsumer.makeCurrent();

            initFilter(mCurrentFilterType); // YET
            fullFrameRect = new FullFrameRect(
                    FilterManager.getCameraFilter(
                            mCurrentFilterType,
                            getActivity().getApplicationContext()));
            intOpenGLTextureName = fullFrameRect.createTexture();

            surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener = new SurfaceTexture(intOpenGLTextureName);
            surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener.setOnFrameAvailableListener(this);

            try {
                mCamera.setPreviewTexture(surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            mCamera.startPreview();


            // TODO: adjust bit rate based on frame rate?
            // TODO: adjust video width/height based on what we're getting from the camera preview?
            //       (can we guarantee that camera preview size is compatible with AVC video encoder?)
            try {
                jaehakEncoder = new JaehakEncoder(
                        GlobalSetting.VIDEO_HEIGHT, //width Width of encoded video, in pixels.  Should be a multiple of 16.
                        GlobalSetting.VIDEO_WIDTH,//height Height of encoded video, in pixels.  Usually a multiple of 16 (1080 is ok).
                        6000000, // Target bit rate, in bits.
                        mCameraPreviewThousandFps / 1000,//Expected frame rate.
                        intTotalVideoLengthSeconds,//desiredSpanSec How many seconds of video we want to have in our buffer at any time.
                        mainHandler);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }

            windowSurface_BpToGraphicBufferSource = new WindowSurface(
                    mEglCore,
                    jaehakEncoder.getInputSurface(),
                    true);
        }
    }


    private void drawFrameOntoSurfaceViewAndEncoderSurface() {

        if (mEglCore == null) {
            Log.d(TAG, "Skipping drawFrameOntoSurfaceViewAndEncoderSurface after shutdown");
            return;
        }

        // Latch the next frame from the camera.
        /**
         * Makes our EGL context current, using the supplied surface for both "draw" and "read".
         */
        surfaceSurfaceView_BpToSurfaceFlingerConsumer.makeCurrent(); // (1) Producer Set
        surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener.updateTexImage(); // JHE - To BBB?
        surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener.getTransformMatrix(mTmpMatrix);

        // Fill the SurfaceView with it.
        SurfaceView sv = (SurfaceView) view.findViewById(R.id.continuousCapture_CameraSurfaceView);
        int intWidthOfSurfaceView = sv.getWidth();
        int intHeightOfSurfaceView = sv.getHeight();

        /**************************************
         *
         * CameraFilter - CameraRecordRenderer #onDrawFrame
         *
         *************************************/
        GLES20.glViewport(0, 0, intWidthOfSurfaceView, intHeightOfSurfaceView);  // (2) Window Size Set
        if (mNewFilterType != null && mNewFilterType != mCurrentFilterType) {
            fullFrameRect.changeProgram(
                    FilterManager.getCameraFilter(
                            mNewFilterType,
                            getActivity().getApplicationContext()
                    )
            );
            mCurrentFilterType = mNewFilterType;
        }
        fullFrameRect.getFilter().setTextureSize(mIncomingWidth, mIncomingHeight);
        fullFrameRect.drawFrameWithFilter(intOpenGLTextureName, mTmpMatrix);              // (3) Filter Set

        // drawExtra(mFrameNum, intWidthOfSurfaceView, intHeightOfSurfaceView);
        surfaceSurfaceView_BpToSurfaceFlingerConsumer.swapBuffers();            // (4) Producer Execute's Swap


        if (isPressingRecordBtn) {

            // Send it to the video encoder.
            if (!GlobalSetting.mFileSaveInProgress) {
                windowSurface_BpToGraphicBufferSource.makeCurrent(); // (1) Producer Set
                GLES20.glViewport(0, 0, GlobalSetting.VIDEO_WIDTH, GlobalSetting.VIDEO_HEIGHT);        // (2) Window Size Set
                // JHE - Filter Again?
                fullFrameRect.drawFrameWithFilter(intOpenGLTextureName, mTmpMatrix); // (3) Filter Set
                // drawExtra(mFrameNum, VIDEO_WIDTH, VIDEO_HEIGHT);
                jaehakEncoder.frameAvailableSoon();
                windowSurface_BpToGraphicBufferSource.setPresentationTime(surfaceTexture_BcFrCameraSurf_ByCamaraSetPreviewTextureAndSetOnFrameAvailableListener.getTimestamp());
                windowSurface_BpToGraphicBufferSource.swapBuffers(); // (4) Producer Execute's Swap
            }

            intRecordedFrameNum++;
            Log.d(TAG, "JHE Recorded Frames : " + String.valueOf(intRecordedFrameNum));

            byteArrayTotalDx = EncoderBufferHolder.encBuffer
                    .getDxLaskPacketStart();
            Log.d(TAG, "JHE byteArrayTotalDx : " + String.valueOf(byteArrayTotalDx));

            /***********************
             * ProgressView
             **********************/
        }
        mFrameNum++;
    }



    /************************
     * SurfaceHolder.Callback
     ************************/

    SurfaceHolder surfaceHolder;

    @Override   // SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated()");
        surfaceHolder = holder;
        isSurfaceCreated = true;
//        mCamera.setDisplayOrientation(90);

        trySetCameraTextureAndEncoder();
//        updateControls();
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged()");
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);

    }

    @Override   // SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed()");
        Log.d(TAG, "surfaceDestroyed holder=" + holder);
        isSurfaceCreated = false;
        surfaceHolder = null;
    }

    @Override   // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
    public void onFrameAvailable(SurfaceTexture surfaceTexture_BufferConsumer_FromCameraSurface) {
//        Log.d(TAG, "frame available");
        mainHandler.sendEmptyMessage(MainHandler.MSG_FRAME_AVAILABLE);
    }


    /************************
     * Click and Capture
     ************************/

    public void saveVideo() {
        Log.d(TAG, "capture");
        if (GlobalSetting.mFileSaveInProgress) {
            Log.w(TAG, "HEY: file save is already in progress");
            return;
        }
        // The button is disabled in onCreate(), and not enabled until the encoder and output
        // surface is ready, so it shouldn't be possible to get here with a null jaehakEncoder.
        GlobalSetting.mFileSaveInProgress = true;
//        updateControls();
        /**
         * NotifySaving
         */
        jaehakEncoder.saveVideo(outputFile);
    }




    /**
     *
     * Filter
     *
     */


    public void initFilter(FilterManager.FilterType filterType) {
        mCurrentFilterType = filterType;
    }

    private void prefareFilterList() {
        LVItemFilter rvItemFilter = new LVItemFilter();
        rvItemFilterList.add(rvItemFilter);

        rvItemFilter = new LVItemFilter(FilterManager.FilterType.Normal, "Normal");
        rvItemFilterList.add(rvItemFilter);

        rvItemFilter = new LVItemFilter(FilterManager.FilterType.Blend, "Blend");
        rvItemFilterList.add(rvItemFilter);

        rvItemFilter = new LVItemFilter(FilterManager.FilterType.SoftLight, "SoftLight");
        rvItemFilterList.add(rvItemFilter);

        rvItemFilter = new LVItemFilter(FilterManager.FilterType.ToneCurve0, "ToneCurve0");
        rvItemFilterList.add(rvItemFilter);


        rvItemFilter = new LVItemFilter(FilterManager.FilterType.ToneCurve1, "ToneCurve1");
        rvItemFilterList.add(rvItemFilter);


        rvItemFilter = new LVItemFilter(FilterManager.FilterType.ToneCurve2, "ToneCurve2");
        rvItemFilterList.add(rvItemFilter);


        rvItemFilter = new LVItemFilter(FilterManager.FilterType.ToneCurve3, "ToneCurve3");
        rvItemFilterList.add(rvItemFilter);


        rvItemFilter = new LVItemFilter(FilterManager.FilterType.ToneCurve4, "ToneCurve4");
        rvItemFilterList.add(rvItemFilter);


        rvItemFilter = new LVItemFilter(FilterManager.FilterType.ToneCurve5, "ToneCurve5");
        rvItemFilterList.add(rvItemFilter);


        rvItemFilter = new LVItemFilter(FilterManager.FilterType.ToneCurve6, "ToneCurve6");
        rvItemFilterList.add(rvItemFilter);


        rvItemFilter = new LVItemFilter(FilterManager.FilterType.ToneCurve7, "ToneCurve7");
        rvItemFilterList.add(rvItemFilter);


        rvItemFilter = new LVItemFilter(FilterManager.FilterType.ToneCurve8, "ToneCurve8");
        rvItemFilterList.add(rvItemFilter);


        rvItemFilter = new LVItemFilter(FilterManager.FilterType.ToneCurve9, "ToneCurve9");
        rvItemFilterList.add(rvItemFilter);


        rvItemFilter = new LVItemFilter(FilterManager.FilterType.ToneCurve10, "ToneCurve10");
        rvItemFilterList.add(rvItemFilter);

        rvItemFilter = new LVItemFilter(FilterManager.FilterType.Jack, "Jack");
        rvItemFilterList.add(rvItemFilter);

        filterBaseAdapter.notifyDataSetChanged();
    }

    private void initOnClickListeners() {

        lvFilter.setOnItemClickListener(this);


    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        LVItemFilter itemFilter = filterBaseAdapter.getItem(position);
        mNewFilterType = itemFilter.getFilterType();
    }


    LinkedList<Bar> arlBar = new LinkedList<>();
    LinkedList<short[]> lklShorts = new LinkedList<>();

    /**
     * The file save has completed.  We can resume recording.
     */
    private void fileSaveComplete(int status) {
        Log.d(TAG, "fileSaveComplete " + status);
        GlobalSetting.mFileSaveInProgress = false;
        ibDelete.setClickable(true);
        ibRecord.setClickable(true);
        ibPlay.setClickable(true);
        initVideoView();

        //START PLAYBACK
//        ((JackActivity) getActivity()).goToFragment(new PreviewFragment());

    }


//    private void fileSaveComplete(int status) {
//        Log.d(TAG, "fileSaveComplete " + status);
//        if (!mFileSaveInProgress) {
//            throw new RuntimeException("WEIRD: got fileSaveCmplete when not in progress");
//        }
//        mFileSaveInProgress = false;
//        updateControls();
//        String str = getString(R.string.nowRecording);
//
//        if (status == 0) {
//            str = getString(R.string.recordingSucceeded);
//        } else {
//            str = getString(R.string.recordingFailed, status);
//        }
//        Toast toast = Toast.makeText(this, str, Toast.LENGTH_SHORT);
//        toast.show();
//    }




    /**
     *
     * Video
     *
     */

    public void coverView(boolean b) {
        if (b) {
            ivCover.setVisibility(View.VISIBLE);
        } else {
            ivCover.setVisibility(View.GONE);
        }
    }

    public void initVideoView() {
        Log.d(TAG, "initVideoView()");
        ttv.setVisibility(View.VISIBLE);
        ttv.setVideoPath(getVideoPath());
        ttv.setMediaController(null);
        ttv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(final MediaPlayer mp) {
                isVideoViewPrepared = true;
                mp.setLooping(false);
                if (FullscreenActivity.isVideoSaved) {
                    Log.d(TAG, "JackActivity.isVideoSaved");
                    startVideoPlayback();
                }
//                startVideoAnimation();
            }
        });
    }

    public void startVideoPlayback() {
        // "forces" anti-aliasing - but increases time for taking frames - so keep it disabled
        // ttv.setScaleX(1.00001f);
        Log.d(TAG, "startVideoPlayback()");
        ttv.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d(TAG, "onCompletion");
                ttv.setVisibility(View.INVISIBLE);
            }
        });

        final int duration = ttv.getDuration();
        new Thread(new Runnable() {
            public void run() {
                do {
                    int curPosition = ttv.getCurrentPosition();
//                    Log.d(TAG, "curPosition " + String.valueOf(curPosition));
                }
                while (ttv.getCurrentPosition() < duration);
            }
        }).start();
        ttv.start();
    }

    private String getVideoPath() {
        return (getOutputFile()).getAbsolutePath();
    }

    private void getLastFrameBitmap() {
        Log.d(TAG, "getLastFrameBitmap");
        Bitmap bmpLastFrame = BitmapFactory.decodeByteArray(
                jaehakEncoder.getEncoderThreadCircularEncoderByteBufferWithIndex().array(),
                0,
                jaehakEncoder.getEncoderThreadCircularEncoderByteBufferWithIndex().array().length);
        ivCover.setImageBitmap(bmpLastFrame);
    }


    /**
     *
     * Flash
     *
     */

    boolean checkHasFlashIfNotFlashButtonInvisible() {
        boolean hasFlash = false;
//        hasFlash = getActivity().getApplicationContext().getPackageManager()
//                .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
//        if (!hasFlash) {
//            btFlash.setVisibility(View.GONE);
//        }
        return hasFlash;
    }

    private void turnOnFlash(Camera camera, Camera.Parameters params) {
        if (!isFlashOn) {
            if (camera == null || params == null) {
                return;
            }
            params = camera.getParameters();
            params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            camera.setParameters(params);
            camera.startPreview();
            isFlashOn = true;
            toggleFlashBtnUI(isFlashOn);
        }
    }

    private void toggleFlashBtnUI(boolean isFlashOn) {

        if (isFlashOn) {
            ibFlash.setBackgroundColor(
                    App.getContext().getResources().getColor(R.color.colorAccent)
            );
        } else {
            ibFlash.setBackgroundColor(
                    App.getContext().getResources().getColor(R.color.colorPrimary)
            );
        }
    }

    /**
     * conf Dim and Resolution
     */

    private void getActualScreenDimension() {
        DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenWidthPixels = metrics.widthPixels;
        int screenHeightPixels = metrics.heightPixels;
        Log.d(TAG, "getActualScreenDimension() screenWidthPixels, screenHeightPixels :"
                + String.valueOf(screenWidthPixels) + ", " //1440
                + String.valueOf(screenHeightPixels));     //2392
        GlobalSetting.SCREEN_WIDTH = screenHeightPixels;
        GlobalSetting.SCREEN_HEIGHT = screenHeightPixels;
    }

    private void getVideoDimensionMinimumResolution() {
        if (GlobalSetting.SCREEN_WIDTH % 720 == 0) {
            int widthDividedby720 = GlobalSetting.SCREEN_WIDTH / 720; //2
            GlobalSetting.VIDEO_WIDTH = GlobalSetting.SCREEN_WIDTH / widthDividedby720; // 1440 / 2 = 720
            GlobalSetting.VIDEO_HEIGHT = GlobalSetting.SCREEN_HEIGHT / widthDividedby720; // 2392 / 2 = 1196
        }
    }


    /**
     * UI
     */

    void initFilterAndAdapter() {
        filterBaseAdapter = new FilterBaseAdapter(rvItemFilterList, getActivity());
        lvFilter.setAdapter(filterBaseAdapter);
        prefareFilterList();
        initOnClickListeners();
    }

    private void updateBufferStatus(long durationUsec) {
//        floatSecondsOfVideo = durationUsec / 1000000.0f;
//        updateControls();
    }

    private void initUI() {
        ivCover = (ImageView) view.findViewById(R.id.ivCover);
        progressView = (ProgressView) view.findViewById(R.id.progressView);
        RelativeLayout.LayoutParams paramsPb = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        paramsPb.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        paramsPb.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
        ibNext = (ImageButton) view.findViewById(R.id.ibNext);
        ibDelete = (ImageButton) view.findViewById(R.id.ibDelete);
        ibRecord = (ImageButton) view.findViewById(R.id.ibRecord);
        ibFilter = (ImageButton) view.findViewById(R.id.ibFilter);
        loRecordBtnEtc = (LinearLayout) view.findViewById(R.id.loRecordBtnEtc);
        lvFilter = (TwoWayView) view.findViewById(R.id.lvFilter);
        lvFilter.setLongClickable(false);
        fr = (FrameLayout) view.findViewById(R.id.fr);
        ibFlash = (ImageButton) view.findViewById(R.id.ibFlash);
        ibChangeCamera = (ImageButton) view.findViewById(R.id.ibChangeCamera);
        ibPlay = (ImageButton) view.findViewById(R.id.ibPlay);
        ttv = (TextureVideoView) view.findViewById(R.id.ttv);
        SurfaceView surfaceView = (SurfaceView) view.findViewById(R.id.continuousCapture_CameraSurfaceView);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
    }

    /**
     * Getter & Setter
     */


    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }


}
