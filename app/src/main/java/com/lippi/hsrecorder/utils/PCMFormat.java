package com.lippi.hsrecorder.utils;

import android.media.AudioFormat;

/**
 * AudioRecording设置了每隔Frame_Count个Frame会通知DataEncodeThread处理数据
 * 所以必须保证缓冲区的大小是Frame_Count的整数倍
 */
public enum PCMFormat {
    PCM_8BIT (1, AudioFormat.ENCODING_PCM_8BIT),
    PCM_16BIT (2, AudioFormat.ENCODING_PCM_16BIT);

    private int bytesPerFrame;
    private int audioFormat;

    PCMFormat(int bytesPerFrame, int audioFormat) {
        this.bytesPerFrame = bytesPerFrame;
        this.audioFormat = audioFormat;
    }

    public int getBytesPerFrame() {
        return bytesPerFrame;
    }


    public int getAudioFormat() {
        return audioFormat;
    }

}
