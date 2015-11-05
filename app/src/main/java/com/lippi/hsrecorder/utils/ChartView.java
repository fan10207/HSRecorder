package com.lippi.hsrecorder.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * note: you must invoke initChartView before use it
 * Created by lippi on 15-3-14.
 */
public class ChartView extends View implements AudioRecorder.OnDataCapturedListener{
    private static final int POINT_NUM = 4000;
    //to draw line
    private float[] mPoints;
    private int mSampleRate = 8000;
    private int numSeconds = 4;
    private static final int CHANNELS = 2;
    private int mScale;
    private int count = 0;
    private Rect mRect = new Rect();

    private Paint mForePaint = new Paint();
    private boolean mCycleColor = false;

    public ChartView(Context context) {
        this(context, null, false);
    }

    public ChartView(Context context, AttributeSet attrs) {
        this(context, attrs, false);
    }

    public ChartView(Context context, AttributeSet attrs, boolean mCycleColor) {
        super(context, attrs);
        this.mCycleColor = mCycleColor;
        initChartView();
    }

    private void initChartView() {
        mPoints = new float[POINT_NUM * 4];
        mScale = mSampleRate * CHANNELS * numSeconds / POINT_NUM;
        mForePaint.setStrokeWidth(2f);
        mForePaint.setAntiAlias(true);
        mForePaint.setColor(Color.BLACK);
    }

    public void setmSampleRate(int mSampleRate) {
        this.mSampleRate = mSampleRate;
        initChartView();

    }

    public void updateChart(short[] shorts) {
        if (shorts == null || shorts.length == 0)
            return;
        if (count + shorts.length / mScale > POINT_NUM) {
            mPoints = new float[POINT_NUM * 4];
            count = 0;
        }

        for (int i = 0; i < shorts.length - mScale; i += mScale) {
            mPoints[count * 4] = mRect.width() * count / (POINT_NUM - 1);
            mPoints[count * 4 + 1] = mRect.height() / 2
                    - (shorts[i] / 32768f * (mRect.height() / 2));
            mPoints[count * 4 + 2] = mRect.width() * (count + 1) / (POINT_NUM - 1);
            mPoints[count * 4 + 3] = mRect.height() / 2
                    - (shorts[i + mScale] / 32768f * (mRect.height() / 2));
            ++count;
        }

        invalidate();
    }

    /**
     * display the data received from MediaPlayer
     * the data is in the form of byte
     *
     *//*
    public void updateVisualizerInBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return;
        if (count + bytes.length / mScale > POINT_NUM) {
            mPoints = new float[POINT_NUM * 4];
            count = 0;
        }


        for (int i = 0; i < bytes.length - mScale; i += mScale) {
            mPoints[count * 4] = mRect.width() * count / (POINT_NUM - 1);
            mPoints[count * 4 + 1] = mRect.height() / 2
                    + ((byte) (bytes[i] + 128)) * (mRect.height() / 2) / 128;
            mPoints[count * 4 + 2] = mRect.width() * (count + 1) / (POINT_NUM - 1);
            mPoints[count * 4 + 3] = mRect.height() / 2+
                    ((byte) (bytes[i + mScale] + 128)) * (mRect.height() / 2) / 128;
            count ++;
        }

        invalidate();
    }*/

    public void cleanChart() {
        mPoints = new float[POINT_NUM * 4];
        count = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mCycleColor) {
            cycleColor();
        }
        mRect.set(0, 0, getWidth(), getHeight());

        canvas.drawLines(mPoints, mForePaint);
    }

    private float colorCounter = 0;

    private void cycleColor() {
        int r = (int) Math.floor(128 * (Math.sin(colorCounter + 3)));
        int g = (int) Math.floor(128 * (Math.sin(colorCounter + 1) + 1));
        int b = (int) Math.floor(128 * (Math.sin(colorCounter + 7) + 1));
        mForePaint.setColor(Color.argb(128, r, g, b));
        colorCounter += 0.03;
    }

    @Override
    public void onDataCaptured(short[] data) {
        updateChart(data);
    }
}
