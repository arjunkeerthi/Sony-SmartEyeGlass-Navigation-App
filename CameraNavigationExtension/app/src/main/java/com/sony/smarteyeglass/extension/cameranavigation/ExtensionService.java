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

import android.util.Log;

import com.sonyericsson.extras.liveware.extension.util.control.ControlExtension;
import com.sonyericsson.extras.liveware.extension.util.registration.DeviceInfoHelper;

/**
 * The Sample Extension Service handles registration and keeps track of all
 * accessories.
 */
public final class ExtensionService extends com.sonyericsson.extras.liveware.extension.util.ExtensionService {

    /** Creates a new instance. */
    public ExtensionService() {
        super(Constants.EXTENSION_KEY);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(Constants.LOG_TAG, "CameraNavigationExtension : onCreate");
    }

    @Override
    protected com.sonyericsson.extras.liveware.extension.util.registration.RegistrationInformation getRegistrationInformation() {
        return new RegistrationInformation(this);
    }

    @Override
    protected boolean keepRunningWhenConnected() {
        return false;
    }

    @Override
    public ControlExtension createControlExtension(
        final String hostAppPackageName) {
        boolean isApiSupported = DeviceInfoHelper
            .isSmartEyeglassScreenSupported(this, hostAppPackageName);
        if (isApiSupported) {
            return new ImageManager(this, hostAppPackageName);
        } else {
            Log.d(Constants.LOG_TAG, "Service: not supported, exiting");
            throw new IllegalArgumentException(
                "No control for: " + hostAppPackageName);
        }
    }
}
