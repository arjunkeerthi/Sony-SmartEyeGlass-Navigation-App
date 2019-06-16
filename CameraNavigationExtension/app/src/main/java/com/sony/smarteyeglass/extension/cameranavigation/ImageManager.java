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

    // Uses SmartEyeglass API version
    private static final int SMARTEYEGLASS_API_VERSION = 3; // Change to 4?

    // Application context
    private final Context context;

    // Parameters to create the classifier (as well as assets accessed via context)
    private final String MODEL_FILE = "detect.tflite";
    private final String LABELS_FILE = "file:///android_asset/labelmap.txt";
    public static final int INPUT_SIZE = 300;
    private final boolean QUANTIZED = true;

    // Stores dimensions of view displaying streamed images
    private final Point DISPLAY_SIZE = new Point();

    // Dimensions of display on SmartEyeGlass
    private final int width;
    private final int height;

    // Reference to utility provided by Sony used to interface with SmartEyeGlass
    private final SmartEyeglassControlUtils utils;

    // Keeps track of whether camera is started or not
    private boolean cameraStarted = false;

    // Origin for text drawn on SmartEyeGlass (although I'm not 100% sure what pointBaseX does vs pointX)
    private int pointX;
    private int pointY;
    private int pointBaseX;

    // Responsible for object detection
    private Classifier mClassifier;

    // Responsible for drawing bounding boxes
    private MultiBoxTracker mTracker;

    // Manages single thread on which object detection is executed
    private Executor mExecutor;

    // Handles messages from object detection thread as well as from ImageResultActivity
    private Handler mHandler;

    // Keeps track of whether ImageResultActivity has sent back a reference to the ImageView that displays streamed images
    private boolean imageViewReceived = false;

    // Keeps track of whether object detection thread has finished processing image and is ready for next streamed image
    private boolean readyForNextImage = true;

    // Number of images streamed from SmartEyeGlass camera
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
            // TODO: Might be able to delete this method
            @Override
            public void onCameraReceivedFile(final String filePath) {
                Log.d(Constants.LOG_TAG, "onCameraReceivedFile: " + filePath);
                updateDisplay();
            }
        };

        // Initialize and configure variables for interfacing with SmartEyeGlass
        utils = new SmartEyeglassControlUtils(hostAppPackageName, listener);
        utils.setRequiredApiVersion(SMARTEYEGLASS_API_VERSION);
        utils.activate(context);
        width = context.getResources().getDimensionPixelSize(R.dimen.smarteyeglass_control_width);
        height = context.getResources().getDimensionPixelSize(R.dimen.smarteyeglass_control_height);

        // Initialize the classifier and set number of threads equal to number of cores available on device
        try {
            mClassifier = TFLiteObjectDetectionAPIModel.create(this.context.getAssets(), MODEL_FILE, LABELS_FILE, INPUT_SIZE, QUANTIZED);
            mClassifier.setNumThreads(Runtime.getRuntime().availableProcessors());
        } catch(IOException e) {
            Log.e(Constants.IMAGE_MANAGER_TAG, "Unable to create Classifier. Error: \n" + e.toString());
        }

        // Initialize MultiBoxTracker and Executor
        mTracker = new MultiBoxTracker(this.context);
        mExecutor = Executors.newSingleThreadExecutor();

        // Initializes a Handler for UI thread
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                // TODO: Update Eyeglass display with information retrieved recognition results
                // Uses message status to decide what to do with message
                switch(msg.what) {
                    case Constants.IMAGE_PROCESSING_FAILED:
                        Log.e(Constants.IMAGE_MANAGER_TAG, "Image processing failed or results not available");
                        break;
                    case Constants.IMAGE_PROCESSING_COMPLETED:
                        // This is where analysis of results need to be done
                        readyForNextImage = true;
                        Log.d(Constants.IMAGE_MANAGER_TAG, "Message received! => msg.what = " + msg.what + "\nmsg.obj = " + msg.obj.toString());
                        break;
                    case Constants.IMAGE_VIEW_REFERENCE_READY:
                        // Use the ImageResultActivity reference given to get dimensions of ImageView containing streamed
                        // images and configure tracker
                        ImageView imageView = (ImageView) msg.obj;
                        DISPLAY_SIZE.x = imageView.getWidth();
                        DISPLAY_SIZE.y = imageView.getHeight();
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
        int jpegQuality = Integer.parseInt(prefs.getString(context.getString(R.string.preference_key_jpeg_quality), "1"));
        int resolution = Integer.parseInt(prefs.getString(context.getString(preferenceId), "6"));

        // Set the camera mode to match the setup
        utils.setCameraMode(jpegQuality, resolution, recordingMode);

        cameraStarted = false;

        // This moves from title screen when you scroll to app on SmartEyeGlass display to the next layer
        // asking user to tap to start jpeg stream
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

        // If the ImageResultsActivity (and its corresponding ImageView) had not been received in handler,
        // then DISPLAY_SIZE will not have been initialized yet, so we send this message to ImageResultActivity
        if (!imageViewReceived) {
            ImageResultActivity.mHandler.obtainMessage(Constants.REQUEST_FOR_IMAGE_VIEW_REFERENCE, mHandler).sendToTarget();
        }

        imageCounter++;

        // readyForNextImage is only true when handler received ImageResultActivity reference
        if(readyForNextImage) {
            if(!imageViewReceived) {
                // Need to release UI thread so it can receive message from ImageResultActivity and set imageViewReceived flag to true
                return; // Though we really should never enter this if-statement
            }
            // Use Executor to execute task (Runnable) that runs object detection on image from SmartEyeGlass camera
            mExecutor.execute(new ProcessImageRunnable(mClassifier, mTracker, mHandler, DISPLAY_SIZE, data, imageCounter));

            // Processing image, so thread is busy and shouldn't accept next camera image. If it did, then the work
            // queue would begin to fill with the next camera images. The object detection thread, after finishing the last
            // image, would move onto the next image in queue, which at that point would be several frames behind where the
            // camera is currently. So we would waste a lot of time processing images that are in the past. Instead, after
            // the thread is finished processing an image, we get the most updated image from camera by only accepting new
            // images when object detection thread is free.
            readyForNextImage = false;
        }

        // While object detection is occurring, we continue to update image view in ImageResultActivity with streamed images
        ImageResultActivity.mHandler.obtainMessage(Constants.STREAMED_IMAGE_READY, Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(data, 0, data.length), INPUT_SIZE, INPUT_SIZE, true)).sendToTarget();
            
        Log.d(Constants.IMAGE_MANAGER_TAG, "Camera frame was received : #" + imageCounter);

        // imageCounter is continuously updated on SmartEyeGlass display
        updateDisplay();
    }

    /**
     * Draw SmartEyeGlass display with updated values
     */
    // TODO: Change display to give user information retrieved from detection results - will need audio version as well
    private void updateDisplay() {
        // Configure bitmap, canvas, and paint to draw on SmartEyeGlass display
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