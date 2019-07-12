/*
Copyright (c) 2011, Sony Mobile Communications Inc.
Copyright (c) 2014, Sony Corporation

 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution.

 * Neither the name of the Sony Mobile Communications Inc.
 nor the names of its contributors may be used to endorse or promote
 products derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.sony.smarteyeglass.extension.cameranavigation;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.util.Log;
import java.io.File;
import com.sony.smarteyeglass.SmartEyeglassControl;
import com.sony.smarteyeglass.extension.util.CameraEvent;
import com.sony.smarteyeglass.extension.util.ControlCameraException;
import com.sony.smarteyeglass.extension.util.SmartEyeglassControlUtils;
import com.sony.smarteyeglass.extension.util.SmartEyeglassEventListener;
import com.sonyericsson.extras.liveware.aef.control.Control;
import com.sonyericsson.extras.liveware.extension.util.control.ControlExtension;
import com.sonyericsson.extras.liveware.extension.util.control.ControlTouchEvent;

/**
 * Shows how to access the SmartEyeglass camera to capture pictures.
 * Demonstrates how to listen to camera events, process
 * camera data, display pictures, and store image data to external storage.
 */
public final class SampleCameraControl extends ControlExtension {

    /**
     * Uses SmartEyeglass API version
     */
    private static final int SMARTEYEGLASS_API_VERSION = 3; // Change to 4?
    public final int width;
    public final int height;
    /**
     * The application context.
     */
    private final Context context;
    /**
     * Instance of the Control Utility class.
     */
    private final SmartEyeglassControlUtils utils;
    private boolean saveToSdcard = false;
    private boolean cameraStarted = false;
    private int saveFileIndex;
    private int recordingMode = SmartEyeglassControl.Intents.CAMERA_MODE_STILL;
    private String saveFilePrefix;
    private File saveFolder;
    private int pointX;
    private int pointY;
    private int pointBaseX;

    /**
     * Creates an instance of this control class.
     *
     * @param context            The context.
     * @param hostAppPackageName Package name of host application.
     */
    public SampleCameraControl(final Context context, final String hostAppPackageName) {
        super(context, hostAppPackageName);
        this.context = context;
        // Initialize listener for camera events
        SmartEyeglassEventListener listener = new SmartEyeglassEventListener() {
            // When camera operation has succeeded
            // handle result according to current recording mode
            @Override
            public void onCameraReceived(final CameraEvent event) {
                switch (recordingMode) {
                    case SmartEyeglassControl.Intents.CAMERA_MODE_STILL:
                        Log.d(Constants.LOG_TAG, "Camera Event coming: " + event.toString());
                        break;
                    case SmartEyeglassControl.Intents.CAMERA_MODE_JPG_STREAM_HIGH_RATE:
                        Log.d(Constants.LOG_TAG, "Stream Event coming: " + event.toString());
                    case SmartEyeglassControl.Intents.CAMERA_MODE_JPG_STREAM_LOW_RATE:
                        Log.d(Constants.LOG_TAG, "Stream Event coming: " + event.toString());
                        break;
                    default:
                        break;
                }
                cameraEventOperation(event);
            }

            // Called when camera operation has failed
            // We just log the error
            @Override
            public void onCameraErrorReceived(final int error) {
                Log.d(Constants.LOG_TAG, "onCameraErrorReceived: " + error);
            }

            // When camera is set to record image to a file,
            // log the operation and clean up
            @Override
            public void onCameraReceivedFile(final String filePath) {
                Log.d(Constants.LOG_TAG, "onCameraReceivedFile: " + filePath);
                updateDisplay();
            }
        };

        /*

        git tested

        */
        utils = new SmartEyeglassControlUtils(hostAppPackageName, listener);
        utils.setRequiredApiVersion(SMARTEYEGLASS_API_VERSION);
        utils.activate(context);
        // saves to /storage/emulated/0/CameraNavigationExtension
        //saveFolder = new File(Environment.getExternalStorageDirectory(), "CameraNavigationExtension");
        // saves to /data/data/com.sony.smarteyeglass.extension.cameranavigation (can't access manually from device, however)
        //saveFolder = new File(context.getFilesDir(), "CameraNavigationExtension");
        // TODO: Check what should be desired save location for camera images
        // saves to /storage/emulated/0/Pictures/CameraNavigationExtension
        saveFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CameraNavigationExtension");
        saveFolder.mkdir();
        Log.d(Constants.LOG_TAG, Environment.getExternalStorageState());
        Log.d(Constants.LOG_TAG, Environment.getExternalStorageDirectory().getAbsolutePath());
        Log.d(Constants.LOG_TAG, context.getFilesDir().getAbsolutePath());
        Log.d(Constants.LOG_TAG, saveFolder.getAbsolutePath());
        width = context.getResources().getDimensionPixelSize(R.dimen.smarteyeglass_control_width);
        height = context.getResources().getDimensionPixelSize(R.dimen.smarteyeglass_control_height);
    }
    
    /**
     * Respond to tap on touch pad by triggering camera capture
     */
    @Override
    public void onTouch(final ControlTouchEvent event) {
        if (event.getAction() == Control.TapActions.SINGLE_TAP) {
            if (recordingMode == SmartEyeglassControl.Intents.CAMERA_MODE_STILL ||
                    recordingMode == SmartEyeglassControl.Intents.CAMERA_MODE_STILL_TO_FILE) {
                if (!cameraStarted) {
                    initializeCamera();
                }
                Log.d(Constants.LOG_TAG, "Select button pressed -> cameraCapture()");
                // Call for camera capture for Still recording modes.
                utils.requestCameraCapture();
            } else {
                if (!cameraStarted) {
                    initializeCamera();
                } else {
                    cleanupCamera();
                }
                updateDisplay();
            }
        }
    }

    /**
     * Call the startCamera, and start video recording or shooting.
     */
    private void initializeCamera() {
        try {
            Time now = new Time();
            now.setToNow();
            // Start camera with filepath if recording mode is Still to file
            if (recordingMode == SmartEyeglassControl.Intents.CAMERA_MODE_STILL_TO_FILE) {
                String filePath = saveFolder + "/" + saveFilePrefix + String.format("%04d", saveFileIndex) + ".jpg";
                saveFileIndex++;
                utils.startCamera(filePath);
            } else {
            // Start camera without filepath for other recording modes
                Log.d(Constants.LOG_TAG, "startCamera ");
                utils.startCamera();
            }
        } catch (ControlCameraException e) {
            Log.d(Constants.LOG_TAG, "Failed to register listener", e);
        }
        Log.d(Constants.LOG_TAG, "onResume: Registered listener");

        cameraStarted = true;
    }

    /**
     * Call the stopCamera, and stop video recording or shooting.
     */
    private void cleanupCamera() {
        utils.stopCamera();
        cameraStarted = false;
    }

    // When app becomes visible, set up camera mode choices
    // and instruct user to begin camera operation
    @Override
    public void onResume() {
        // TODO: Look into limiting on time for screen
        // Note: Setting the screen to be always on will drain the accessory
        // battery. It is done here solely for demonstration purposes.
        setScreenState(Control.Intents.SCREEN_STATE_ON);
        pointX = context.getResources().getInteger(R.integer.POINT_X);
        pointY = context.getResources().getInteger(R.integer.POINT_Y);

        Time now = new Time();
        now.setToNow();
        saveFilePrefix = "cameranavigation_" + now.format2445() + "_";
        saveFileIndex = 0;

        // Read the settings for the extension.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        saveToSdcard = prefs.getBoolean(context.getString(R.string.preference_key_save_to_sdcard), true);
        int recMode = Integer.parseInt(prefs.getString(context.getString(R.string.preference_key_recordmode), "2"));
        int preferenceId = R.string.preference_key_resolution_still;

        switch (recMode) {
            case 0: // recording mode is still
                recordingMode = SmartEyeglassControl.Intents.CAMERA_MODE_STILL;
                break;
            case 1: // recording mode is still to file
                recordingMode = SmartEyeglassControl.Intents.CAMERA_MODE_STILL_TO_FILE;
                break;
            case 2: // recording mode is JPGStream Low
                recordingMode = SmartEyeglassControl.Intents.CAMERA_MODE_JPG_STREAM_LOW_RATE;
                preferenceId = R.string.preference_key_resolution_movie;
                break;
            case 3: // recording mode is JPGStream High
                recordingMode = SmartEyeglassControl.Intents.CAMERA_MODE_JPG_STREAM_HIGH_RATE;
                preferenceId = R.string.preference_key_resolution_movie;
                break;
        }

        // Get and show quality parameters
        int jpegQuality = Integer.parseInt(prefs.getString(
                context.getString(R.string.preference_key_jpeg_quality), "1"));
        int resolution = Integer.parseInt(prefs.getString(
                context.getString(preferenceId), "6"));

        // Set the camera mode to match the setup
        utils.setCameraMode(jpegQuality, resolution, recordingMode);

        cameraStarted = false;
        updateDisplay();
    }

    // Clean up any open files and reset mode when app is paused.
    @Override
    public void onPause() {
        // Stop camera.
        if (cameraStarted) {
            Log.d(Constants.LOG_TAG, "onPause() : stopCamera");
            cleanupCamera();
        }
    }

    // Clean up data structures on termination.
    @Override
    public void onDestroy() {
        utils.deactivate();
    }

    /**
     * Received camera event and operation each event.
     *
     * @param event
     */
    private void cameraEventOperation(CameraEvent event) {
        if (event.getErrorStatus() != 0) {
            Log.d(Constants.LOG_TAG, "error code = " + event.getErrorStatus());
            return;
        }
        
        if(event.getIndex() != 0){
            Log.d(Constants.LOG_TAG, "not operate this event");
            return;
        }
        
        Bitmap bitmap = null;
        byte[] data = null;
   
        if ((event.getData() != null) && ((event.getData().length) > 0)) {
            data = event.getData();
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        }
            
        if (bitmap == null) {
            Log.d(Constants.LOG_TAG, "bitmap == null");
            return;
        }
        
        //if (saveToSdcard == true) { // TODO: Figure out what do with saveToSdcard variable
            String fileName = saveFilePrefix + String.format("%04d", saveFileIndex) + ".jpg";
            new SavePhotoTask(saveFolder,fileName,context).execute(data);
            saveFileIndex++;
        //}
            
        if (recordingMode == SmartEyeglassControl.Intents.CAMERA_MODE_STILL) {
            Bitmap basebitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            basebitmap.setDensity(DisplayMetrics.DENSITY_DEFAULT);
            Canvas canvas = new Canvas(basebitmap);
            Rect rect = new Rect(0, 0, width, height);
            Paint paint = new Paint();
            paint.setStyle(Paint.Style.FILL);
            canvas.drawBitmap(bitmap, rect, rect, paint);
            
            utils.showBitmap(basebitmap);
            return;
        }
            
        Log.d(Constants.LOG_TAG, "Camera frame was received : #" + saveFileIndex);
        updateDisplay();
    }
    
    private void updateDisplay()
    {
        Bitmap displayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        displayBitmap.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        Canvas canvas = new Canvas(displayBitmap);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(16);
        paint.setColor(Color.WHITE);
        // Update layout according to the camera mode
        switch (recordingMode) {
            case SmartEyeglassControl.Intents.CAMERA_MODE_STILL:
                canvas.drawText("Tap to capture : STILL", pointX, pointY, paint);
                break;
            case SmartEyeglassControl.Intents.CAMERA_MODE_STILL_TO_FILE:
                canvas.drawText("Tap to capture : STILL TO FILE", pointX, pointY, paint);
                break;
            case SmartEyeglassControl.Intents.CAMERA_MODE_JPG_STREAM_HIGH_RATE:
            case SmartEyeglassControl.Intents.CAMERA_MODE_JPG_STREAM_LOW_RATE:
                if (cameraStarted) {
                    canvas.drawText("JPEG Streaming...", pointBaseX, pointY, paint);
                    canvas.drawText("Tap to stop.", pointBaseX, (pointY * 2), paint);
                    canvas.drawText("Frame Number: " + Integer.toString(saveFileIndex), pointBaseX, (pointY * 3), paint);
                } else {
                    canvas.drawText("Tap to start JPEG Stream.", pointBaseX, pointY, paint);
                }
                break;
            default:
                canvas.drawText("wrong recording type.", pointBaseX, pointY, paint);
        }

        utils.showBitmap(displayBitmap);
    }
}