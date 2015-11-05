package com.lippi.hsrecorder.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.chart.TimeChart;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.model.XYValueSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by lippi on 15-3-12.
 */
public class XYChart {
    private static final String TAG = TimeChart.class.getSimpleName();
    public static final int POINT_NUM = 2000;
    private XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
    private static final XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
    private static GraphicalView chart;
    private static SlippingBuffer slippingBuffer;
    private static boolean stopped = false;
    private ChartUpdateThread updateThread;
    private static long[] xCache;
    private static double[] yCache;
    private Timer timer = new Timer();
    private TimerTask timerTask;
    /**
     *
     * @param context
     * @param linearLayout
     * @param buffer
     */
    public XYChart(Context context, LinearLayout linearLayout, SlippingBuffer buffer) {
        slippingBuffer = buffer;
        //生成图表
        setRenderer("音频曲线", "时间", "幅值", -32768, 32768);
        //renderer.setInScroll(false);
//        renderer.setXAxisMax(POINT_NUM);
//        renderer.setXAxisMin(1);
        AddSeriesToRender("滤波曲线", Color.RED, 2, PointStyle.POINT);
        chart = ChartFactory.getLineChartView(context, dataset, renderer);
        linearLayout.addView(chart, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));//设置曲线框的宽度和高度

        xCache = new long[POINT_NUM];
        yCache = new double[POINT_NUM];

        timerTask = new TimerTask() {
            @Override
            public void run() {
                mHandler.obtainMessage(0).sendToTarget();
            }
        };


    }
    private UpdateHandle mHandler = new UpdateHandle();

    private class UpdateHandle extends Handler{
        @Override
        public void handleMessage(Message msg) {
            chart.invalidate();
            super.handleMessage(msg);
        }
    };


    public void stop() {
        timer.cancel();
        stopped = true;
        updateThread = null;
    }

    public void update() {
        stopped = false;
        updateThread = new ChartUpdateThread();
        updateThread.start();
        timer.schedule(timerTask, 0, 1000);
        Log.d(TAG, "update thread start");
    }

    private class ChartUpdateThread extends Thread {

        @Override
        public void run() {

            short[] dataArray = new short[640];
            List<Short> envelope = new ArrayList<Short>();
            TimeSeries series;
            System.out.println("start to display chart");
            while (!stopped) {
                try {
                    slippingBuffer.read(dataArray);
                    for (int i = 0; i < 10; i++) {
                       // if (i % 16 == 0) {
                            envelope.add(dataArray[i]);
                        //}
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "error when try to read data");
                }
                synchronized (dataset) {
                    series = (TimeSeries) dataset.getSeriesAt(0);
                    int length = series.getItemCount();
                    if(length >= POINT_NUM) length = POINT_NUM;

                    //将前面的数据加入缓存
                    for(int i = 0; i < length; i++){
                        xCache[i] = (long) series.getX(i);
                        yCache[i] = series.getY(i);
                    }
                    series.clear();
                    //添加新的点
                    for (int i = 0; i < envelope.size(); i++) {
                        series.add(new Date(), envelope.get(i));
                    }
                    for(int j = 0; j < length; j++)
                        series.add(new Date(xCache[j]), yCache[j]);

                    dataset.removeSeries(0);
                    dataset.addSeries(0, series);
                }
            }
            clear();

        }

    }

    public  void clear() {
        TimeSeries series0 = (TimeSeries) dataset.getSeriesAt(0);
        series0.clear();
        dataset.removeSeries(0);
        dataset.addSeries(0, series0);

        mHandler.obtainMessage(0).sendToTarget();

    }

    /**
     * 设定曲线框的样式
     *
     * @param title  标题
     * @param xTitle 横坐标
     * @param yTitle 纵坐标
     * @param yMin   纵坐标最小值
     * @param yMax   纵坐标最大值
     */
    public void setRenderer(String title, String xTitle, String yTitle, int yMin, int yMax) {

        //外面图形框的设置
        renderer.setChartTitle(title);//标题
        renderer.setChartTitleTextSize(15);//标题字体大小
        renderer.setXTitle(xTitle);    //x轴说明
        renderer.setYTitle(yTitle);
        // renderer.setBackgroundColor(Color.WHITE);
        //renderer.setApplyBackgroundColor(true);
        renderer.setAxisTitleTextSize(15);//坐标轴字体大小
        renderer.setLegendTextSize(15);    //图例字体大小
        renderer.setShowLabels(false);
        renderer.setShowAxes(false);
        renderer.setShowLegend(false);   //显示图例
        // renderer.setBackgroundColor(Color.GRAY);
        renderer.setXLabels(15);//横轴字体
        renderer.setYLabels(15);
        renderer.setAxesColor(Color.LTGRAY);
        renderer.setLabelsColor(Color.BLACK);
        renderer.setXLabelsAlign(Paint.Align.RIGHT);
        renderer.setYLabelsAlign(Paint.Align.RIGHT);
        renderer.setZoomButtonsVisible(false);
        renderer.setPanEnabled(false, false);
        renderer.setPanLimits(new double[]{-10, 20, -10, 40});
        renderer.setZoomLimits(new double[]{-10, 20, -10, 40});
//        renderer.setMargins(new int[] {50, 40, 40, 0});//top,left,bottom,right
        renderer.setMargins(new int[]{0, 0, 0, 0});//top,left,bottom,right

        renderer.setMarginsColor(Color.rgb(50, 63, 120));
        renderer.setShowGrid(false);
        renderer.setYAxisMax(yMax);//设置Y轴范围
        renderer.setYAxisMin(yMin);

        renderer.setInScroll(false);  //调整大小

    }

    /**
     * 添加一条曲线
     *
     * @param datasetTitle 曲线标题
     * @param lineColor    曲线颜色
     * @param lineWidth    曲线线宽
     * @param style        点的风格
     */
    public void AddSeriesToRender(String datasetTitle, int lineColor, int lineWidth, PointStyle style) {

        XYSeriesRenderer r = new XYSeriesRenderer();
        r.setColor(lineColor);
        r.setChartValuesTextSize(1);
        r.setChartValuesSpacing(1);
        r.setPointStyle(style);
        r.setFillPoints(false);
        r.setLineWidth(lineWidth);
        renderer.addSeriesRenderer(r);

        TimeSeries series = new TimeSeries(datasetTitle);
        dataset.addSeries(series);


    }

}
