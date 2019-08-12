package com.sony.smarteyeglass.extension.cameranavigation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ClientSocketThread extends Thread {
    // Reference for handler in main thread responsible for updating beep frequency
    private Handler mMainHandler;

    // Reference for handler in main thread responsible for sending pictures from camera2api
    public static Handler mPictureHandler;

    // Client socket connection. Sends images to server and receives results
    private Socket mSocket;

    // Sends data to server
    private OutputStream mOutputStream;

    // Reads data from server
    private InputStream mInputStream;

    // Reads results from server
    private BufferedReader mBufferedReader;

    // IP and PORT for server
    // TODO: Change IP to correspond to actual server used
    private final String IP = "10.0.0.30";
    private final int PORT = 9002;

    // Keeps track of whether an image is ready to be sent to server
    private boolean imageReady = false;

    // Stores bytes for image
    private byte[] image = null;

    // Stores the total time in milliseconds that client socket has waited to send size and receive
    // confirmation
    private int sumMillisSizeConf = 0;

    // Sames as sumMillisSizeConf, but to send image and receive confirmation
    private int sumMillisImageConf = 0;

    // Number of images sent and processed
    private int count = 0;

    public ClientSocketThread(Handler handler) {
        mMainHandler = handler;
        mPictureHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 2) {
                    image = (byte[]) msg.obj;
                    imageReady = true;
                }
            }
        };
    }

    public void run() {
        // Try to open socket connection until successful
        while(!openSocketConnection()) {
            // Notify main thread that server is unavailable
            mMainHandler.obtainMessage(Constants.SERVER_UNAVAILABLE).sendToTarget();
            Log.e(Constants.LOG_TAG, "Unable to open client side socket connection");
        }

        // Notify main thread that client is connected to server and ready to send images
        mMainHandler.obtainMessage(Constants.SERVER_AVAILABLE).sendToTarget();

        while (mSocket.isConnected()) {
            if (imageReady) {
                try {
                    // Set to false at beginning so after this iteration we can continue on to next
                    // image that was retrieved during this iteration
                    imageReady = false;
                    count++;

                    // Get size of image
                    int originalSize = image.length;
                    Log.e(Constants.LOG_TAG, "Size of image: " + originalSize);

                    // Create buffer to store original image size and buffer to store the size
                    // returned from server
                    Log.e(Constants.LOG_TAG, "Sending image...");
                    byte[] originalSizeBuff = ByteBuffer.allocate(4).putInt(originalSize).array();
                    byte[] responseSizeBuff = new byte[4];

                    // Time how long it takes to send size and receive confirmation size from server
                    long start = System.nanoTime();

                    // Write the size of image to the output stream for connection
                    mOutputStream.write(originalSizeBuff);

                    // Read the size that the server sends back (this is the confirmation to ensure
                    // that server got the right size)
                    mInputStream.read(responseSizeBuff);

                    // Get elapsed time
                    long end = System.nanoTime();
                    Log.e(Constants.LOG_TAG, "Response time for size send and confirmation: "
                            + (end - start) / 1000000 + " ms");

                    // Update total time waiting for size confirmation
                    sumMillisSizeConf += (end - start) / 1000000;

                    // Convert response from bytes to integer
                    int responseSize = ByteBuffer.wrap(responseSizeBuff).asIntBuffer().get();
                    Log.e(Constants.LOG_TAG, "Response size: " + responseSize);

                    // Only proceed if the original size sent and the response size received are
                    // equal, otherwise break since connection is corrupted somehow
                    if (originalSize != responseSize) break;

                    // Time send and receive for image
                    Log.e(Constants.LOG_TAG, "About to send data");
                    start = System.nanoTime();

                    // Write bytes for image to output stream
                    mOutputStream.write(image);

                    // Read confirmation from server that image was received
                    String ok = mBufferedReader.readLine();

                    // Get elapsed time
                    end = System.nanoTime();
                    Log.e(Constants.LOG_TAG, "Response time for picture send and receive " +
                            "confirmation: " + (end - start) / 1000000 + " ms");

                    // Update time waiting for image confirmation
                    sumMillisImageConf += (end - start) / 1000000;

                    // Only proceed if confirmation was "OK", otherwise break since connection was
                    // corrupted somehow
                    if (!ok.equals("OK")) break;
                    Log.e(Constants.LOG_TAG, "Confirmation message: " + ok);

                    // Read the danger level as an integer (depth information)
                    int dangerLevel = Integer.parseInt(mBufferedReader.readLine());

                    // Read the detections and store them
                    String result;
                    ArrayList<Detection> detections = new ArrayList<>();
                    while ((result = mBufferedReader.readLine()) != null) {
                        if (result.equals("END")) break;
                        detections.add(new Detection(result));
                    }

                    // Send confirmation that results were received back to server
                    mOutputStream.write("OK".getBytes());

                    Log.e(Constants.LOG_TAG, detections.toString());
                    Log.e(Constants.LOG_TAG, "Average size confirmation: " +
                            sumMillisSizeConf / count + " ms");
                    Log.e(Constants.LOG_TAG, "Average image confirmation: " +
                            sumMillisImageConf / count + " ms");

                    // Send message to ImageManager to update beep frequency based on danger level
                    // and send detections
                    switch (dangerLevel) {
                        case 0:
                            mMainHandler.obtainMessage(Constants.BEEP_FREQUENCY_CLEAR,
                                    detections).sendToTarget();
                            break;
                        case 1:
                            mMainHandler.obtainMessage(Constants.BEEP_FREQUENCY_CAREFUL,
                                    detections).sendToTarget();
                            break;
                        case 2:
                            mMainHandler.obtainMessage(Constants.BEEP_FREQUENCY_DANGEROUS,
                                    detections).sendToTarget();
                        default:
                            Log.e(Constants.LOG_TAG, "Something bad happened.");
                    }
                } catch (IOException e) {
                    // Try to re-establish socket connection
                    do {
                        // Notify main thread that server is unavailable
                        mMainHandler.obtainMessage(Constants.SERVER_UNAVAILABLE).sendToTarget();
                        Log.e(Constants.LOG_TAG, "Unable to open client side socket " +
                                "connection");
                    } while(!openSocketConnection());

                    // Notify main thread that server is available again
                    mMainHandler.obtainMessage(Constants.SERVER_AVAILABLE).sendToTarget();
                }
            }
        }
    }

    private boolean openSocketConnection() {
        try {
            // Try to establish a socket connection with server
            mSocket = new Socket(IP, PORT);
            Log.e(Constants.LOG_TAG, "Connected to server");

            // Initialize output and input streams
            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();

            // Initialize reader for input stream
            mBufferedReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
            Log.e(Constants.LOG_TAG, "IO streams initialized, ready to receive results...");
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}