package com.jackleeentertainment.vidrecord.ui.layout;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.jackleeentertainment.vidrecord.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {

    // Base
    private static final String TAG = "ContiCaptureActivity";
    Context mContext;
    private PowerManager.WakeLock mWakeLock;

    // Screen
    int screenWidth, screenHeight = 0;

    // Record
    private File mOutputFile;
    private float floatSecondsOfVideo;
    public static boolean isVideoSaved = false;

    //PERMISSIONS
    final int REQUEST_PERSMISSIONS = 81;



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Turn off the window's title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        // Fullscreen mode
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_jack);

        mContext = this;

        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_IMMERSIVE);
        }

        // WakeLock
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
        mWakeLock.acquire();

        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        //Find screen dimensions
        screenWidth = displaymetrics.widthPixels;
        screenHeight = displaymetrics.heightPixels;

        // init Configs
        mOutputFile = new File(getFilesDir(), "continuous-capture.mp4");
        Log.d(TAG, "getFilesDir() " + getFilesDir().toString());
        floatSecondsOfVideo = 0.0f;

        Log.d(TAG, String.valueOf(Build.VERSION.SDK_INT));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            /**************************
             (0) Check Permission stat
             ***************************/
            ArrayList<String> arlPermissiionsToGet = new ArrayList<>();

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                arlPermissiionsToGet.add(Manifest.permission.CAMERA);
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                arlPermissiionsToGet.add(Manifest.permission.RECORD_AUDIO);
            }

            if (arlPermissiionsToGet.size() > 0) {

                Log.d(TAG, "arlPermissiionsToGet.size() : " + String.valueOf(arlPermissiionsToGet.size()));

                /*********************
                 (1) Need Explanations?
                 **********************/
                ArrayList<String> arlExplanations = new ArrayList<>();

                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.CAMERA)) {
                    arlExplanations.add("explain why need camera");
                }

                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.RECORD_AUDIO)) {
                    arlExplanations.add("explain why need audiorecord");
                }

                if (arlExplanations.size() > 0) {
                    //show dialog for explanation
                }

                /************************
                 (2) Request Permissions
                 *************************/
                ActivityCompat.requestPermissions(this,
                        arlPermissiionsToGet.toArray(new String[arlPermissiionsToGet.size()]),
                        REQUEST_PERSMISSIONS);

            } else {

                // All Persmissions Are OK

                android.support.v4.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                CameraFragment cameraFragment = new CameraFragment();
                transaction.replace(R.id.fragment_container, cameraFragment, "CameraFragment");
                transaction.addToBackStack(null);
                transaction.commit();
            }
        } else {
            android.support.v4.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            CameraFragment cameraFragment = new CameraFragment();
            transaction.replace(R.id.fragment_container, cameraFragment, "CameraFragment");
            transaction.addToBackStack(null);
            transaction.commit();
        }


    }



    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

        Log.d(TAG, "onRequestPermissionsResult()" + Arrays.toString(permissions));

        switch (requestCode) {

            case REQUEST_PERSMISSIONS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!

                    android.support.v4.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    CameraFragment cameraFragment = new CameraFragment();
                    transaction.replace(R.id.fragment_container, cameraFragment, "CameraFragment");
                    transaction.addToBackStack(null);
                    transaction.commitAllowingStateLoss();
                } else {

                    // show dialog to finish

                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
}
