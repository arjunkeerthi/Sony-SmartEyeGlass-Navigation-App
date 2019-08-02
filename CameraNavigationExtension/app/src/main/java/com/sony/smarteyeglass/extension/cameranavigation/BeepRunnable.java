package com.sony.smarteyeglass.extension.cameranavigation;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

// Responsible for emitting beep
public class BeepRunnable implements Runnable {
    private final int BEEP_FREQUENCY = 900; // TODO: Adjust frequency and duration
    private final double BEEP_DURATION = 0.15;

    @Override
    public void run() {
        double duration = BEEP_DURATION * 44100;
        // AudioTrack definition
        int mBufferSize = AudioTrack.getMinBufferSize(44100,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_8BIT);

        AudioTrack mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                mBufferSize, AudioTrack.MODE_STREAM);

        int new_duration = (int) duration;

        // Sine wave
        double[] mSound = new double[new_duration]; // 44100
        short[] mBuffer = new short[new_duration];
        for (int i = 0; i < mSound.length; i++) {
            mSound[i] = Math.sin((2.0*Math.PI * i/(44100.0/BEEP_FREQUENCY)));
            mBuffer[i] = (short) (mSound[i]*Short.MAX_VALUE);
        }

        mAudioTrack.setStereoVolume(AudioTrack.getMaxVolume(), AudioTrack.getMaxVolume());
        mAudioTrack.play();

        mAudioTrack.write(mBuffer, 0, mSound.length);
        mAudioTrack.stop();
        mAudioTrack.release();
    }
}
