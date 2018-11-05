package com.lippi.hsrecorder.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import static android.content.ContentValues.TAG;

/**
 * note: you must invoke initChartView before use it
 * Created by lippi on 15-3-14.
 */
public class ChartView extends SurfaceView implements AudioRecorder.OnDataCapturedListener{
    private int POINT_NUM = 8000;
    //to draw line
    private float[] mPoints;
    private int mSampleRate =4000;
    private int numSeconds = 3;
    private int channels = 2;
    private int mScale;
    private int count = 0;
    private Rect mRect = new Rect();
    private float[] last;
    private int lastX=0;
    private int getcurrentX=0;
    public float currentX = 0;
    public float currentY = 0;
    private SurfaceHolder sfh;
    private Paint mForePaint = new Paint();
    private boolean mCycleColor = false;
    private int mdrawstate = DRAW_MOVE;
    public static final int DRAW_MOVE = 0;
    public static final int SET_START_END = 2;
    public static final int FINISHEDALL=2;
    public static final int FINISHED=1;
    public static final int UNFINISHED=0;
    public float start_Coordinate = 0;
    public float end_Coordinate = 0;
    public float start_Coordinate2 = 0;
    public float end_Coordinate2 = 0;
    public float startPos = 0;
    public float endPos = 0;
    private Paint p = new Paint();
    private int finishPoint=UNFINISHED;

    public ChartView(Context context) {
        this(context, null, false);
    }

    public ChartView(Context context, AttributeSet attrs) {
        this(context, attrs, false);
    }

    public ChartView(Context context, AttributeSet attrs, boolean mCycleColor) {
        super(context, attrs);
        this.mCycleColor = mCycleColor;
        this.setZOrderOnTop(true);
        sfh = getHolder();
        sfh.setFormat(PixelFormat.TRANSPARENT);
        sfh.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                drawBack(surfaceHolder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

            }
        });
        Log.e("`123", "initChartView: "+mScale );
        initChartView();
    }


    private void initChartView() {
        mPoints = new float[POINT_NUM * 4];
        //mScale = mSampleRate * channels * numSeconds / POINT_NUM;
        mScale =2;
                Log.e("`123", "initChartView: "+mScale );
        mForePaint.setStrokeWidth(2f);
        p.setStrokeWidth(5f);
        mForePaint.setAntiAlias(true);
        mForePaint.setColor(Color.BLACK);
        last = new float[2];
    }

    public void setmSampleRate(int mSampleRate) {
        this.mSampleRate = mSampleRate;
        Log.e("`123", "initChartView: "+mScale );
        initChartView();

    }

    public void setNumSeconds(int numSeconds) {
        this.numSeconds = numSeconds;
        initChartView();
    }

    public void setChannels(int channels) {
        this.channels = channels;
        initChartView();
    }

    public void setPOINT_NUM(int POINT_NUM) {
        this.POINT_NUM = POINT_NUM;
        initChartView();
    }

    public void updateChart(short[] shorts) {
        Log.d("chart", "chart......!!!!");
        if (shorts == null || shorts.length == 0)
            return;
        if (count + shorts.length / mScale > POINT_NUM) {
            count = 0;
        }

        if (count == 0) {
            drawBack(sfh);
            drawBack(sfh);
        }
        lastX=getWidth() * count / (POINT_NUM - 1);

        mPoints = new float[shorts.length  * 4];


        for (int i = 0 , a=0; i < shorts.length - mScale; a++, i += mScale) {
            mPoints[a * 4] = getWidth() * count / (POINT_NUM - 1);
            mPoints[a * 4 + 1] = getHeight() / 2
                    - (shorts[i] / 40000f * (getHeight() / 2));
            mPoints[a * 4 + 2] = getWidth() * (count + 1) / (POINT_NUM - 1);
            mPoints[a * 4 + 3] =getHeight() / 2
                    - (shorts[i + mScale] / 40000f * (getHeight()/ 2));
            ++count;
        }
        getcurrentX=getWidth() * count / (POINT_NUM - 1);

        Canvas canvas = sfh.lockCanvas(new Rect(lastX, 0, getcurrentX, getHeight()));
        canvas.drawLines(mPoints, mForePaint);
        sfh.unlockCanvasAndPost(canvas);

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
        drawBack(sfh);
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

    public int getPOINT_NUM() {
        return POINT_NUM;
    }

    private void drawBack(SurfaceHolder surfaceHolder) {
        Canvas canvas = surfaceHolder.lockCanvas();
       // canvas.drawColor(Color.WHITE);
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        p.setStrokeWidth(2);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
       /* canvas.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2, p);*/
        canvas.drawLine(0, 0, 0, getHeight() - 10, p);
        surfaceHolder.unlockCanvasAndPost(canvas);
        //重新锁一次，使背景持久化
        surfaceHolder.lockCanvas(new Rect(0, 0, 0, 0));
        surfaceHolder.unlockCanvasAndPost(canvas);

    }


/*
    public boolean onTouchEvent(MotionEvent event) {

        if (mdrawstate == SET_START_END) {
            this.currentX = event.getX();
            this.currentY = event.getY();
            //获取按下位置坐标

            if (start_Coordinate == 0 | end_Coordinate == 0) {
                if (start_Coordinate == 0) {
                    p.setColor(Color.GREEN);
                    startPos = currentX;
                    start_Coordinate = currentX / getWidth() * POINT_NUM;
                   // Log.e(TAG, "onTouchEvent:currentX "+currentX );
                    Canvas canvas = sfh.lockCanvas();
                    canvas.drawLine(currentX, 0, currentX, getHeight(), p);
                    sfh.unlockCanvasAndPost(canvas);
                    sfh.lockCanvas(new Rect(0, 0, 0, 0));
                    sfh.unlockCanvasAndPost(canvas);

                } else if (end_Coordinate == 0 && ((currentX - startPos) > 20)) {
                   // Log.e(TAG, "onTouchEvent:currentX2 "+currentX );
                    endPos = currentX;
                    end_Coordinate = currentX / getWidth() * POINT_NUM;
                    Canvas canvas = sfh.lockCanvas();
                    canvas.drawLine(startPos, 0, startPos, getHeight(), p);
                    p.setColor(Color.RED);
                    canvas.drawLine(currentX, 0, currentX, getHeight(), p);
                    sfh.unlockCanvasAndPost(canvas);
                    sfh.lockCanvas(new Rect(0, 0, 0, 0));
                    sfh.unlockCanvasAndPost(canvas);
                    finishPoint = FINISHED;
                }
            }
            if (finishPoint == FINISHED&&currentX-endPos>20) {
                if (start_Coordinate2 == 0 | end_Coordinate2 == 0) {
                    if (start_Coordinate2 == 0) {
                        p.setColor(Color.GREEN);
                        startPos = currentX;
                        start_Coordinate2 = currentX / getWidth() * POINT_NUM;
                        // Log.e(TAG, "onTouchEvent:currentX "+currentX );
                        Canvas canvas = sfh.lockCanvas();
                        canvas.drawLine(currentX, 0, currentX, getHeight(), p);
                        sfh.unlockCanvasAndPost(canvas);
                        sfh.lockCanvas(new Rect(0, 0, 0, 0));
                        sfh.unlockCanvasAndPost(canvas);

                    } else if (end_Coordinate2 == 0 && ((currentX - startPos) > 20)) {
                        // Log.e(TAG, "onTouchEvent:currentX2 "+currentX );
                        endPos = currentX;
                        end_Coordinate = currentX / getWidth() * POINT_NUM;
                        Canvas canvas = sfh.lockCanvas();
                        canvas.drawLine(startPos, 0, startPos, getHeight(), p);
                        p.setColor(Color.RED);
                        canvas.drawLine(currentX, 0, currentX, getHeight(), p);
                        sfh.unlockCanvasAndPost(canvas);
                        sfh.lockCanvas(new Rect(0, 0, 0, 0));
                        sfh.unlockCanvasAndPost(canvas);
                        finishPoint = FINISHEDALL;
                    }
                }
            }
        }
        return true;
    }
*/
    public void setDrawstate(int i) {
        mdrawstate = i;
    }

    public void stopDrawing() {

        Log.e(TAG, "stopDrawing: " );
        setDrawstate(SET_START_END);
       // stopUpdate=true;
    }


}
