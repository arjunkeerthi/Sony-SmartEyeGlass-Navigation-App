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

import android.content.Context;
import android.content.res.Resources;

/**
 * Provides screen size.
 *
 * <p>This class is immutable.</p>
 */
public final class ScreenSize {

    /** The screen width. */
    private final int width;

    /** The screen height. */
    private final int height;

    /** An application context. */
    private final Context context;

    /**
     * Creates a new instance.
     *
     * @param context An application context.
     */
    public ScreenSize(final Context context) {
        this.context = context;
        Resources res = context.getResources();
        width = res.getDimensionPixelSize(R.dimen.smarteyeglass_control_width);
        height = res.getDimensionPixelSize(
                R.dimen.smarteyeglass_control_height);
    }

    /**
     * Returns an application context.
     *
     * @return An application context.
     */
    public Context getContext() {
        return context;
    }

    /**
     * Returns the screen width.
     *
     * @return The screen width.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the screen height.
     *
     * @return The screen height.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Compares this size to the specified size.
     *
     * @param width
     *            The width of size.
     * @param height
     *            The height of size.
     * @return {@code true} if the specified size is equal to this,
     *         {@code false} otherwise.
     */
    public boolean equals(final int width, final int height) {
        return this.width == width && this.height == height;
    }
}
