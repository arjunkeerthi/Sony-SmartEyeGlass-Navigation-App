package com.sony.smarteyeglass.extension.cameranavigation;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Handler;
import android.util.Log;
import com.sony.smarteyeglass.extension.cameranavigation.tflite.Classifier;
import com.sony.smarteyeglass.extension.cameranavigation.tflite.MultiBoxTracker;
import java.util.Iterator;
import java.util.List;

public class ProcessImageRunnable implements Runnable {

    private final float MINIMUM_CONFIDENCE_LEVEL = 0.45f;

    private Handler mHandler;
    private Classifier mClassifier;
    private MultiBoxTracker mTracker;
    private byte[] mData;
    private int mImageCounter;
    private Point mDisplaySize;

    public ProcessImageRunnable(Classifier classifier, MultiBoxTracker tracker, Handler handler, int imageCounter, Point displaySize, byte[] data) {
        this.mHandler = handler;
        this.mData = data;
        this.mImageCounter = imageCounter;
        this.mDisplaySize = displaySize;
        mClassifier = classifier;
        mClassifier.setNumThreads(Runtime.getRuntime().availableProcessors());
        this.mTracker = tracker;
    }

    public void run() {
        try {
            // Check performance when setting bilinear filtering to false
            Bitmap bitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(mData, 0, mData.length), ImageManager.INPUT_SIZE, ImageManager.INPUT_SIZE, true);

            // Run object detection on image, recording time taken for processing
            long startTime = System.nanoTime();
            List<Classifier.Recognition> mRecognitions = mClassifier.recognizeImage(bitmap);
            long endTime = System.nanoTime();
            Log.d(Constants.PROCESS_IMAGE_RUNNABLE_TAG, "Detection on frame #" + mImageCounter);
            Log.d(Constants.PROCESS_IMAGE_RUNNABLE_TAG, "Object detection time: " + (endTime - startTime) / 1000000 + "ms");

            // Removing detections with confidence < 0.45, recording final total time for processing
            Iterator<Classifier.Recognition> iterator = mRecognitions.iterator();
            while(iterator.hasNext()) {
                if (iterator.next().getConfidence() < MINIMUM_CONFIDENCE_LEVEL) {
                    iterator.remove();
                }
            }
            endTime = System.nanoTime();
            Log.d(Constants.PROCESS_IMAGE_RUNNABLE_TAG, "Total processing time: " + (endTime - startTime) / 1000000 + "ms");

            // Send detections back to ImageManager in UI thread to be interpreted and used to inform user of obstacles ahead
            mHandler.obtainMessage(Constants.IMAGE_PROCESSING_COMPLETED, mRecognitions).sendToTarget();

            // Creates new bitmap and draws bounding boxes
            bitmap = Bitmap.createBitmap(mDisplaySize.x, mDisplaySize.y, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            mTracker.processResults(mRecognitions);
            mTracker.draw(canvas);

            // Send bitmap with bounding boxes in correct locations to ImageResultActivity to be displayed over streamed images
            ImageResultActivity.mHandler.obtainMessage(Constants.BOUNDING_BOXES_READY, bitmap).sendToTarget();
        } catch(Exception e) { // TODO: Improve try-catch block so it better isolates potential exception-throwing points
            Log.e(Constants.PROCESS_IMAGE_RUNNABLE_TAG, "run(): " + e.toString());
            mHandler.obtainMessage(Constants.IMAGE_PROCESSING_FAILED).sendToTarget();
        }
    }
}