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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import static android.app.Activity.RESULT_OK;

/**
 * Save photo data.
 */
class SavePhotoTask extends AsyncTask<byte[], String, String> {
    private final String fileName;
    private final File saveFolder;
    private Context photoContext;
    ImageView image;
    String URL ="http://10.66.78.206:5000/upload";
    Bitmap bitmap;
    ProgressDialog progressDialog;


    public SavePhotoTask(File folder,String name, Context context) {
        this.saveFolder = folder;
        this.fileName = name;
        photoContext = context;
    }


    @Override
    protected String doInBackground(byte[]... jpeg) {
        File photo = new File(saveFolder,fileName);

        if (photo.exists()) {
            photo.delete();
        }

        FileOutputStream fileOutputStream = null;
        try {
            long mRequestStart = System.currentTimeMillis();

            final String imageString = Base64.encodeToString(jpeg[0], Base64.DEFAULT);
            //final String requestBody = parameters.toString();

            // actual upload part 2
            StringRequest request = new StringRequest(Request.Method.POST, URL, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    long totalRequestTime = System.currentTimeMillis() - mRequestStart;
                    Log.i(Constants.LOG_TAG, "Objects detected: " + response);
                    Log.i(Constants.LOG_TAG, "Time taken: " + totalRequestTime);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d(Constants.LOG_TAG, "Connection did not work!");
                }
            })
                {
                    @Override
                    public String getBodyContentType() {
                        return "application/x-www-form-urlencoded; charset=UTF-8";
                }

                   /* @Override
                    public byte[] getBody() throws AuthFailureError {
                    try {
                        return requestBody == null ? null : requestBody.getBytes("utf-8");
                    } catch (UnsupportedEncodingException uee) {
                        VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                        return null;
                    }
                }*/@Override
                   protected Map<String,String> getParams(){

                       Map<String, String> parameters = new HashMap<String, String>();
                       parameters.put("username", "cody");
                       parameters.put("password", "123");
                       parameters.put("name", fileName);
                       parameters.put("image", imageString);
                       return parameters;
                   }



                    /*@Override
                    protected Response<String> parseNetworkResponse(NetworkResponse response) {
                    String responseString = "";
                    if (response != null) {
                        responseString = String.valueOf(response.statusCode);
                        Log.i(Constants.LOG_TAG, "saw this: " + response.toString());
                    }
                    return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                }*/
            };

            RequestQueue rQueue = Volley.newRequestQueue(photoContext);
            rQueue.add(request);


            ////////////
            Log.d(Constants.LOG_TAG, "Saved photo to " + fileName);
        }
        catch (Exception e) {
            Log.e(Constants.LOG_TAG, "Exception in saving photo : write", e);
        }

        return(null);
    }

    //originally the code for an image selection process to upload files but we skip that
    /*
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri filePath = data.getData();

            try {
                //getting image from gallery
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), filePath);

                //Setting image to ImageView
                image.setImageBitmap(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    */
}