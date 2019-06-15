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
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.ImageView;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import com.sony.smarteyeglass.SmartEyeglassControl;
import com.sony.smarteyeglass.extension.cameranavigation.tflite.Classifier;
import com.sony.smarteyeglass.extension.cameranavigation.tflite.MultiBoxTracker;
import com.sony.smarteyeglass.extension.cameranavigation.tflite.TFLiteObjectDetectionAPIModel;
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
public final class ImageManager extends ControlExtension {

    /**
     * Uses SmartEyeglass API version
     */
    private static final int SMARTEYEGLASS_API_VERSION = 3; // Change to 4?
    private static final String MODEL_FILE = "detect.tflite";
    private static final String LABELS_FILE = "file:///android_asset/labelmap.txt";
    public static final int INPUT_SIZE = 300;
    private static final boolean QUANTIZED = true;
    private final Point DISPLAY_SIZE = new Point();

    private final int width;
    private final int height;
    private final Context context;
    private final SmartEyeglassControlUtils utils;
    private boolean cameraStarted = false;
    private int pointX;
    private int pointY;
    private int pointBaseX;

    private Classifier mClassifier;
    private Executor mExecutor;
    private MultiBoxTracker mTracker;
    public static Handler mHandler;
    private ImageView mImageView;
    private boolean imageViewReceived = false;
    private boolean readyForNextImage = true;
    private int imageCounter;

    /**
     * Creates an instance of this control class.
     *
     * @param context            The context.
     * @param hostAppPackageName Package name of host application.
     */
    public ImageManager(final Context context, final String hostAppPackageName) {
        super(context, hostAppPackageName);
        this.context = context;
        // Initialize listener for camera events
        SmartEyeglassEventListener listener = new SmartEyeglassEventListener() {
            // When camera operation has succeeded
            // handle result according to current recording mode
            @Override
            public void onCameraReceived(final CameraEvent event) {
                Log.d(Constants.LOG_TAG, "Stream Event coming: " + event.toString());
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

        utils = new SmartEyeglassControlUtils(hostAppPackageName, listener);
        utils.setRequiredApiVersion(SMARTEYEGLASS_API_VERSION);
        utils.activate(context);
        width = context.getResources().getDimensionPixelSize(R.dimen.smarteyeglass_control_width);
        height = context.getResources().getDimensionPixelSize(R.dimen.smarteyeglass_control_height);

        try {
            mClassifier = TFLiteObjectDetectionAPIModel.create(this.context.getAssets(), MODEL_FILE, LABELS_FILE, INPUT_SIZE, QUANTIZED);
        } catch(IOException e) {
            Log.e(Constants.IMAGE_MANAGER_TAG, "Unable to create Classifier. Error: \n" + e.toString());
        }

        mExecutor = Executors.newSingleThreadExecutor();
        mTracker = new MultiBoxTracker(this.context);

        // Handles messages that contain results from object detection in ProcessImageRunnable
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                // TODO: Update Eyeglass display with recognition results
                switch(msg.what) {
                    case Constants.IMAGE_PROCESSING_FAILED:
                        Log.e(Constants.IMAGE_MANAGER_TAG, "Image processing failed or results not available");
                        break;
                    case Constants.IMAGE_PROCESSING_COMPLETED:
                        Log.d(Constants.IMAGE_MANAGER_TAG, "Message received! => msg.what = " + msg.what + "\nmsg.obj = " + msg.obj.toString());
                        break;
                    case Constants.ACTIVITY_REFERENCE_READY:
                        mImageView = ((ImageResultActivity)msg.obj).findViewById(R.id.overlay);
                        DISPLAY_SIZE.x = mImageView.getWidth();
                        DISPLAY_SIZE.y = mImageView.getHeight();
                        mTracker.setFrameConfiguration(INPUT_SIZE, INPUT_SIZE, 0);
                        imageViewReceived = true;
                        Log.d(Constants.IMAGE_MANAGER_TAG, "Received activity, extracted imageView");
                        break;
                    default:
                        Log.e(Constants.IMAGE_MANAGER_TAG, "Message status not recognized, ignoring message");
                        break;
                }
            }
        };
    }

    /**
     * Respond to tap on touch pad by triggering camera capture
     */
    @Override
    public void onTouch(final ControlTouchEvent event) {
        if (event.getAction() == Control.TapActions.SINGLE_TAP) {
            if (!cameraStarted) {
                initializeCamera();
            } else {
                cleanupCamera();
            }
            updateDisplay();
        }
    }

    /**
     * Call the startCamera, and start video recording or shooting.
     */
    private void initializeCamera() {
        try {
            Log.d(Constants.LOG_TAG, "startCamera ");
            utils.startCamera();
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

        imageCounter = 0;

        // Read the settings for the extension.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // TODO: Look into performance with high stream rate and deciding when to switch (i.e. server available)
        // CAMERA_MODE_JPG_STREAM_LOW_RATE is 7.5fps, CAMERA_MODE_JPG_STREAM_HIGH_RATE is 15fps
        int recordingMode = SmartEyeglassControl.Intents.CAMERA_MODE_JPG_STREAM_LOW_RATE;
        int preferenceId = R.string.preference_key_resolution_movie;

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
        } else {
            Log.e(Constants.IMAGE_MANAGER_TAG, "Data was null or already invalid");
        }
            
        if (bitmap == null) {
            Log.e(Constants.LOG_TAG, "bitmap == null");
            return;
        }

        if (!imageViewReceived) {
            ImageResultActivity.mHandler.obtainMessage(Constants.REQUEST_FOR_ACTIVITY_REFERENCE).sendToTarget();
        }

        imageCounter++;

        if(readyForNextImage) {
            if(!imageViewReceived) {
                // Need to release UI thread so it can receive message from ImageResultActivity and set imageViewReceived flag to true
                return; // Though we really should never enter this if-statement
            }
            // Use Executor to execute task (Runnable) that runs Tensorflow's object detection API on image from SmartEyeGlass camera
            mExecutor.execute(new ProcessImageRunnable(mClassifier, mTracker, mHandler, imageCounter, DISPLAY_SIZE, data));
            readyForNextImage = false;
        }

        ImageResultActivity.mHandler.obtainMessage(Constants.STREAMED_IMAGE_READY, Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(data, 0, data.length), INPUT_SIZE, INPUT_SIZE, true)).sendToTarget();
            
        Log.d(Constants.IMAGE_MANAGER_TAG, "Camera frame was received : #" + imageCounter);
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
        if (cameraStarted) {
            canvas.drawText("JPEG Streaming...", pointBaseX, pointY, paint);
            canvas.drawText("Tap to stop.", pointBaseX, (pointY * 2), paint);
            canvas.drawText("Frame Number: " + Integer.toString(imageCounter), pointBaseX, (pointY * 3), paint);
        } else {
            canvas.drawText("Tap to start JPEG Stream.", pointBaseX, pointY, paint);
        }

        utils.showBitmap(displayBitmap);
    }
}