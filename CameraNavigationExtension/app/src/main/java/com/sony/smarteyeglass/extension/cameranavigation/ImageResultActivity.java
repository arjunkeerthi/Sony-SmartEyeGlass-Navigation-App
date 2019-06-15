package com.sony.smarteyeglass.extension.cameranavigation;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

public class ImageResultActivity extends AppCompatActivity {

    public static Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_result);

        final ImageView imageView = (ImageView) findViewById(R.id.camera_image);
        final ImageView overlay = (ImageView) findViewById(R.id.overlay);

        mHandler = new Handler(Looper.getMainLooper()) {
            // Receive camera images from SmartEyeGlass
            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case Constants.STREAMED_IMAGE_READY:
                        imageView.setImageBitmap((Bitmap)msg.obj);
                        Log.d(Constants.IMAGE_RESULT_ACTIVITY_TAG, "Set background to image stream");
                        break;
                    case Constants.BOUNDING_BOXES_READY:
                        overlay.setImageBitmap((Bitmap)msg.obj);
                        Log.d(Constants.IMAGE_RESULT_ACTIVITY_TAG, "Drew bounding boxes in overlay");
                        break;
                    case Constants.REQUEST_FOR_ACTIVITY_REFERENCE:
                        ImageManager.mHandler.obtainMessage(msg.what, ImageResultActivity.this).sendToTarget();
                        Log.d(Constants.IMAGE_RESULT_ACTIVITY_TAG, "Reference to ImageResultActivity passed to ImageManager");
                        break;
                    default:
                        Log.e(Constants.IMAGE_RESULT_ACTIVITY_TAG, "Message status not recognized, ignoring frame");
                        break;
                }
            }
        };
    }
}
