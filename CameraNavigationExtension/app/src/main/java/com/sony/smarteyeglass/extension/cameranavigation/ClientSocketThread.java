package com.sony.smarteyeglass.extension.cameranavigation;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ClientSocketThread extends Thread {
    // Reference for handler in main thread responsible for sending pictures from camera2api
    public static Handler mPictureHandler;

    // Client socket connection. Sends images to server and receives results
    private Socket mSocket;

    // Reference for handler in main thread responsible for updating beep frequency
    private Handler mMainHandler;

    // Reads results from server
    private BufferedReader mBufferedReader;

    // IP and PORT for server
    // TODO: Change IP to correspond to actual server used
    private final String IP = "192.168.1.2";
    private final int PORT = 9002;

    // Sends data to server
    private OutputStream mOutputStream;

    // Keeps track of whether an image is ready to be sent to server
    private boolean imageReady = false;

    // Stores bytes for image
    private byte[] image = null;

    // Stores the total time in milliseconds that client socket has waited to send size and receive
    // confirmation
    private int sumMillisSizeConf = 0;

    // Sames as sumMillisSizeConf, but to send image and receive confirmation
    private int sumMillisImageConf = 0;

    private int count = 0;

    //private int originalSize = 0;

    private boolean serverReady = false;

    private boolean isConnected = false;

    private boolean imageSent = true;

    private byte[] copyImage;

    public ClientSocketThread(Handler handler) {
        mMainHandler = handler;
        mPictureHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case Constants.STREAMED_IMAGE_READY_FOR_SERVER:
                        Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Image updated");
                        image = (byte[]) msg.obj;
                        imageReady = true;
                        break;
                    case Constants.SERVER_UNAVAILABLE:
                        serverReady = false;
                        isConnected = false;
                    case Constants.SERVER_AVAILABLE:
                        serverReady = true;
                }
            }
        };
    }

    public void run() {
        // Try to open socket connection until successful
        while(!openSocketConnection()) {
            isConnected = false;
            //Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Unable to open client side socket connection");
        }

        isConnected = true;

        while(!serverReady) {
            // Wait (though at this point serverReady should be true)
        }

        while (true) {
            try {
                if(!serverReady || !isConnected) {
                    throw new IOException();
                }
                if (imageReady && imageSent) {
                    Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Entered if to send size");
                    // Update image that will be sent
                    copyImage = image;
                    count++;
                    Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Size of image: " + copyImage.length);

                    // Create buffer to store original image size
                    Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Sending size of image...");
                    byte[] sizeBuff = ByteBuffer.allocate(4).putInt(copyImage.length).array();

                    // Write the size of image to the output stream for connection
                    mOutputStream.write(sizeBuff);
                    Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Wrote size");
                    imageReady = false;
                    imageSent = false;
                }

                String type = "NOTHING";
                if(mBufferedReader.ready()) {
                    type = mBufferedReader.readLine();
                    Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "TYPE: " + type);
                    if(type.equals("CLOSED")) { // Write "CLOSED" server-side when closing connection
                        throw new IOException();
                    }
                } else {
                    //Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "TYPE: " + "ERROR")
;                }
                switch (type) {
                    case "SIZE":
                        // Read the size that the server sends back (this is the confirmation to ensure
                        // that server got the right size)
                        String rSize = mBufferedReader.readLine();
                        if(rSize.equals("CLOSED")) {
                            throw new IOException();
                        }
                        int responseSize = Integer.parseInt(rSize);
                        Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Response size: " + responseSize);

                        // Only proceed if the original size sent and the response size received are
                        // equal, otherwise break since connection is corrupted somehow (and try to re-connect)
                        if (copyImage.length != responseSize) {
                            throw new IOException();
                        }

                        // Write bytes for image to output stream
                        mOutputStream.write(copyImage);
                        imageSent = true;
                        break;
                    case "OK":
                        // Only proceed if confirmation was "OK", otherwise break since connection was
                        // corrupted somehow
                        serverReady = true;
                        Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Confirmation message: " + type);
                        break;
                    case "RESULT":
                        // Read the danger level as an integer (depth information)
                        String rLevel = mBufferedReader.readLine();
                        if(rLevel.equals("CLOSED")) {
                            throw new IOException();
                        }
                        int dangerLevel = Integer.parseInt(rLevel);
                        Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Danger level: " + dangerLevel);

                        String dangerSide = mBufferedReader.readLine();
                        if(dangerSide.equals("CLOSED")) {
                            throw new IOException();
                        }

                        // Read the detections and store them
                        String result;
                        ArrayList<Detection> detections = new ArrayList<>();
                        while ((result = mBufferedReader.readLine()) != null) {
                            if(result.equals("CLOSED")) throw new IOException();
                            if (result.equals("END")) break;
                            if (!result.isEmpty()) {
                                detections.add(new Detection(result));
                            }
                        }

                        /*Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, detections.toString());
                        Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Average size confirmation: " +
                                sumMillisSizeConf / count + " ms");
                        Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Average image confirmation: " +
                                sumMillisImageConf / count + " ms");*/

                        // Send message to ImageManager to update beep frequency based on danger level
                        // and send detections
                        switch (dangerLevel) {
                            case 0:
                                mMainHandler.obtainMessage(Constants.BEEP_FREQUENCY_CLEAR,
                                        new Data(dangerSide, detections)).sendToTarget();
                                break;
                            case 1:
                                mMainHandler.obtainMessage(Constants.BEEP_FREQUENCY_CAREFUL,
                                        new Data(dangerSide, detections)).sendToTarget();
                                break;
                            case 2:
                                mMainHandler.obtainMessage(Constants.BEEP_FREQUENCY_DANGEROUS,
                                        new Data(dangerSide, detections)).sendToTarget();
                                break;
                            default:
                                Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Something bad happened.");
                                break;
                        }
                        break;
                    default:
                        //Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "PROBLEM!!!: " + type);
                        break;
                }
            } catch (IOException e) {
                Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Unable to open client side socket " +
                        "connection");
                // Try to re-establish socket connection
                do {
                    isConnected = false;
                    //Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Unable to open client side socket connection");
                } while (!openSocketConnection());
                imageSent = true;
                isConnected = true;
            }
        }
    }

    private boolean openSocketConnection() {
        try {
            // Try to establish a socket connection with server
            mSocket = new Socket(IP, PORT);
            Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Connected to server");

            // Initialize output and input streams
            mOutputStream = mSocket.getOutputStream();
            //mInputStream = mSocket.getInputStream();

            // Initialize reader for input stream
            mBufferedReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
            Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "IO streams initialized, ready to receive results...");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public class Data {
        public String mDangerSide;
        public ArrayList<Detection> mDetections;
        public Data(String side, ArrayList<Detection> detections) {
            mDangerSide = side;
            mDetections = detections;
        }
    }
}