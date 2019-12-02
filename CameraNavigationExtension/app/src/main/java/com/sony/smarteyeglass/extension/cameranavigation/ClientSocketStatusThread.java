package com.sony.smarteyeglass.extension.cameranavigation;

import android.os.Handler;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

public class ClientSocketStatusThread extends Thread {
    private Handler mMainHandler;
    // Client socket connection. Sends images to server and receives results
    private Socket mSocket;
    // Reads results from server
    private BufferedReader mBufferedReader;
    // Writes out status check to server
    private OutputStream mOutputStream;
    // IP and PORT for server
    // TODO: Change IP to correspond to actual server used
    private final String IP = "192.168.1.2";
    private final int PORT = 9003;

    public ClientSocketStatusThread(Handler handler) {
        mMainHandler = handler;
    }

    public void run() {
        // Try to open socket connection until successful
        while(!openSocketConnection()) {
            //Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Unable to open client side socket connection");
        }

        try {
            String readyConfirmation = mBufferedReader.readLine();
            if (readyConfirmation.equals("READY")) {
                // Notify ClientSocketThread and Main thread that server is ready
                ClientSocketThread.mPictureHandler.obtainMessage(Constants.SERVER_AVAILABLE).sendToTarget();
                mMainHandler.obtainMessage(Constants.SERVER_AVAILABLE).sendToTarget();
                //mMainHandler.obtainMessage(Constants.PLAY_BEEP_SOUND).sendToTarget();
            }
            Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Ready Confirmation: " + readyConfirmation);
        } catch(IOException e) {
            e.printStackTrace();
        }

        while(mSocket.isConnected()) {
            // Read the size that the server sends back (this is the confirmation to ensure
            // that server got the right size)
            try {
                mOutputStream.write("Hello".getBytes());
            } catch (IOException e) {
                Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Unable to open client side socket " +
                        "connection");

                ClientSocketThread.mPictureHandler.obtainMessage(Constants.SERVER_UNAVAILABLE).sendToTarget();
                mMainHandler.obtainMessage(Constants.SERVER_UNAVAILABLE).sendToTarget();
                // Try to re-establish socket connection
                do {
                    //Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Unable to open client side socket connection");
                } while (!openSocketConnection());

                try {
                    String readyConfirmation = mBufferedReader.readLine();
                    if (readyConfirmation.equals("READY")) {
                        // Notify threads that server is ready
                        ClientSocketThread.mPictureHandler.obtainMessage(Constants.SERVER_AVAILABLE).sendToTarget();
                        mMainHandler.obtainMessage(Constants.SERVER_AVAILABLE).sendToTarget();
                        //mMainHandler.obtainMessage(Constants.PLAY_BEEP_SOUND).sendToTarget();
                    }
                    Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Ready Confirmation: " + readyConfirmation);
                } catch(IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
    }

    private boolean openSocketConnection() {
        try {
            // Try to establish a socket connection with server
            mSocket = new Socket(IP, PORT);
            Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "Connected to server");

            // Initialize output streams
            mOutputStream = mSocket.getOutputStream();

            // Initialize reader for input stream
            mBufferedReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
            Log.e(Constants.CLIENT_SOCKET_THREAD_TAG, "IO streams initialized, ready to receive results...");
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
