package com.lippi.hsrecorder.pcm;

/**
 * Created by lippi on 14-12-6.
 */
public class DoubleFrame {

    double[] values;

    public DoubleFrame(double[] values) {
        this.values = values;
    }

    public DoubleFrame(int[] intValues, int mix, int max) {
        // do normalization.
    }

}