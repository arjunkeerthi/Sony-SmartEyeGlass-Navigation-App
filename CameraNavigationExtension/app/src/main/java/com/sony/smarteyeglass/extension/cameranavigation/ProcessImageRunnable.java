package com.sony.smarteyeglass.extension.cameranavigation;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.Log;

import com.sony.smarteyeglass.extension.cameranavigation.tflite.Classifier;
import com.sony.smarteyeglass.extension.cameranavigation.tflite.TFLiteObjectDetectionAPIModel;

import java.io.IOException;
import java.util.List;

public class ProcessImageRunnable implements Runnable {

    private static final String MODEL_FILE = "detect.tflite";
    private static final String LABELS_FILE = "file:///android_asset/labelmap.txt";
    private static final int INPUT_SIZE = 300;
    private static final boolean QUANTIZED = true;
    private static final float MINIMUM_CONFIDENCE_LEVEL = 0.5f; // Check where to actually use this (may need to move to ImageManager)
    private static final int IMAGE_PROCESSING_FAILED = -1;
    private static final int IMAGE_PROCESSING_COMPLETE = 1;

    private Handler mHandler;
    private Classifier mClassifier;

    private byte[] mData;

    public ProcessImageRunnable(AssetManager assets, Handler handler, byte[] data) throws IOException {
        this.mHandler = handler;
        this.mData = data;
        try {
            mClassifier = TFLiteObjectDetectionAPIModel.create(
                    assets,
                    MODEL_FILE,
                    LABELS_FILE,
                    INPUT_SIZE,
                    QUANTIZED
            );
        } catch(IOException e) {
            Log.e(Constants.LOG_TAG, "ProcessImageRunnable(): Unable to create Classifier. Error: \n" + e.toString());
            throw new IOException("Rethrowing exception from create()");
        }
    }

    public void run() {
        try {
            List<Classifier.Recognition> mRecognitions =
                    mClassifier.recognizeImage(
                            Bitmap.createScaledBitmap(
                                    BitmapFactory.decodeByteArray(mData, 0, mData.length),
                                    INPUT_SIZE,
                                    INPUT_SIZE,
                                    true)); // Check performance when setting bilinear filtering to false
            mHandler.obtainMessage(IMAGE_PROCESSING_COMPLETE, mRecognitions).sendToTarget();
        } catch(Exception e) {
            Log.e(Constants.LOG_TAG, "ProcessImageRunnable run(): Unable to create bitmap. Error msg: \n" + e.toString());
            mHandler.obtainMessage(IMAGE_PROCESSING_FAILED).sendToTarget();
        }
    }
}