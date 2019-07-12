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

// Added an exit link so we could switch from the Preferences page to the View displaying the streamed
// camera images and the bounding boxes for detected object (for debugging/testing purposes, won't be
// in an actual final product).

package com.sony.smarteyeglass.extension.cameranavigation;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

/**
 * The Sample Camera Preference activity handles the preferences for
 * the Sample Camera Extension.
 */
public final class AppPreferenceActivity extends PreferenceActivity {

    /** The ID for the Read Me dialog. */
    private static final int DIALOG_READ_ME = 1;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Loads the preferences from an XML resource.
        addPreferencesFromResource(R.xml.preference);

        // Handles Read Me.
        Preference pref =
                findPreference(getText(R.string.preference_key_read_me));
        pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference pref) {
                showDialog(DIALOG_READ_ME);
                return true;
            }
        });

        Preference exit_button = (Preference) getPreferenceManager().findPreference("exit_link");
        if(exit_button != null) {
            exit_button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference p) {
                    if(ContextCompat.checkSelfPermission(AppPreferenceActivity.this, "com.sonyericsson.extras.liveware.aef.EXTENSION_PERMISSION") != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(AppPreferenceActivity.this, "com.sony.smarteyeglass.permission.SMARTEYEGLASS") != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(AppPreferenceActivity.this,  "com.sony.smarteyeglass.permission.CAMERA") != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(AppPreferenceActivity.this, new String[]{"com.sonyericsson.extras.liveware.aef.EXTENSION_PERMISSION", "com.sony.smarteyeglass.permission.SMARTEYEGLASS", "com.sony.smarteyeglass.permission.CAMERA"}, 1);
                    } else {
                        Intent intent = new Intent(AppPreferenceActivity.this, ImageResultActivity.class);
                        startActivity(intent);
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        if (id != DIALOG_READ_ME) {
            Log.w(Constants.LOG_TAG, "Not a valid dialog id: " + id);
            return null;
        }
        OnClickListener listener = new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                dialog.cancel();
            }
        };
        return new AlertDialog.Builder(this)
                .setMessage(R.string.preference_option_read_me_txt)
                .setTitle(R.string.preference_option_read_me)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(android.R.string.ok, listener)
                .create();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if(requestCode == 1) {
            int numGranted = 0;
            for(int i = 0; i < grantResults.length; i++) {
                if(grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    numGranted++;
                    Log.e("AppPreferenceActivity", "Permission granted for: " + permissions[i]);
                } else {
                    Log.e("AppPreferenceActivity", "Permission NOT granted for: " + permissions[i]);
                }
            }
            if(numGranted == 0) {
                Log.e("AppPreferenceActivity", "No permissions granted.");
            }
        }
    }

}