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

    // We only keep detections with a confidence of at least 0.45 (further testing of this value could be helpful)
    private final float MINIMUM_CONFIDENCE_LEVEL = 0.5f;

    // Reference to ImageManager handler so we can send new detections
    private Handler mHandler;

    // Responsible for running object detection
    private Classifier mClassifier;

    // Responsible for drawing bounding boxes
    private MultiBoxTracker mTracker;

    // Byte array for image
    private byte[] mData;

    // This image's count out of all streamed images thus far
    private int mImageCounter;

    // Dimensions of ImageView in which streamed images are displayed and on which bounding boxes are drawn
    private Point mDisplaySize;

    public ProcessImageRunnable(Classifier classifier, MultiBoxTracker tracker, Handler handler, Point displaySize, byte[] data, int imageCounter) {
        this.mClassifier = classifier;
        this.mTracker = tracker;
        this.mHandler = handler;
        this.mDisplaySize = displaySize;
        this.mData = data;
        this.mImageCounter = imageCounter;
    }

    public void run() {
        try {
            // TODO: Check performance when setting bilinear filtering to false
            // Convert image in byte array form to bitmap
            Bitmap bitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(mData, 0, mData.length), ImageManager.INPUT_SIZE, ImageManager.INPUT_SIZE, true);

            // Run object detection on image, recording time taken for processing
            long startTime = System.nanoTime();
            List<Classifier.Recognition> mRecognitions = mClassifier.recognizeImage(bitmap);
            long endTime = System.nanoTime();
            Log.d(Constants.PROCESS_IMAGE_RUNNABLE_TAG, "Detection on frame #" + mImageCounter);
            Log.d(Constants.PROCESS_IMAGE_RUNNABLE_TAG, "Object detection time: " + (endTime - startTime) / 1000000 + "ms");

            // Removing detections with confidence less than MINIMUM_CONFIDENCE_LEVEL, recording final total time for processing
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

            // Creates new bitmap and draws bounding boxes (note: bitmap has dimensions of ImageView that bounding boxes will
            // be displayed on. mTracker takes care of scaling from detections in 300x300 frame to display frame
            bitmap = Bitmap.createBitmap(mDisplaySize.x, mDisplaySize.y, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            mTracker.processResults(mRecognitions);
            mTracker.draw(canvas);

            // Send bitmap with bounding boxes in correct locations to ImageResultActivity to be displayed over streamed images
            ImageResultActivity.mHandler.obtainMessage(Constants.BOUNDING_BOXES_READY, bitmap).sendToTarget();
        } catch(Exception e) {
            Log.e(Constants.PROCESS_IMAGE_RUNNABLE_TAG, "run(): " + e.toString());
            mHandler.obtainMessage(Constants.IMAGE_PROCESSING_FAILED).sendToTarget();
        }
    }
}