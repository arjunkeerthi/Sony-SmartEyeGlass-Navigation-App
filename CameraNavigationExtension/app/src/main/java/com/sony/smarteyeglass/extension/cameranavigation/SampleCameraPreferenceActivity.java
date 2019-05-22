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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.util.Log;

/**
 * The Sample Camera Preference activity handles the preferences for
 * the Sample Camera Extension.
 */
public final class SampleCameraPreferenceActivity extends PreferenceActivity {

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
}
