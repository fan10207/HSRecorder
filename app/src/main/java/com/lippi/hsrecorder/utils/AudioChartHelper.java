package com.lippi.hsrecorder.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYValueSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by lippi on 14-9-28.
 */
public class AudioChartHelper {
    private static final String TAG = AudioChartHelper.class.getSimpleName();
    private XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
    private final XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
    private int addY;
    //所有曲线的计数器
    private int cnt;
    private GraphicalView chart;
    private static final int TIME_SERIES = 1;
    private static final int LINE_SERIES = 0;
    private static final int POINT_NUM = 4000;
    private static final int CHART_UPDATE = 1;
    private SlippingBuffer dataBuffer;
    private volatile boolean stopped = false;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private Handler mHandler = new UpdateHandler();

    private class UpdateHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            chart.invalidate();
            super.handleMessage(msg);
        }
    };

    /**
     * 有两种chart，一种是横坐标为时间轴一直在变，另一种是横坐标的点数固定
     *
     */
    public AudioChartHelper(Context context, LinearLayout linearLayout, SlippingBuffer buffer) {
        this.dataBuffer = buffer;
        //生成图表
        setRenderer("音频曲线", "时间", "幅值", -32768, 32768);
        //renderer.setInScroll(false);
        renderer.setXAxisMax(POINT_NUM);
        renderer.setXAxisMin(1);
        AddSeriesToRender("音频曲线", LINE_SERIES, Color.BLUE, 2, PointStyle.POINT);
        chart = ChartFactory.getLineChartView(context, dataset, renderer);
        linearLayout.addView(chart, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));//设置曲线框的宽度和高度
        //refresh the screen at one second
        executorService.scheduleWithFixedDelay(new UpdateTask(), 0, 500, TimeUnit.MILLISECONDS);
    }

    private class UpdateTask implements Runnable{

        @Override
        public void run() {
           mHandler.obtainMessage(CHART_UPDATE).sendToTarget();
        }
    }
    public void update() {
        stopped = false;
        new UpdateThread().start();
    }

    public void stop() {
        stopped = true;
    }

    /**
     * chart update runnable
     */
    class UpdateThread extends Thread {
        short[] dataArray = new short[640];
        List<Short> envelope;
        XYValueSeries series0;

        @Override
        public void run() {

            while (!stopped) {
                try {
                    dataBuffer.read(dataArray);
                    envelope = new ArrayList<Short>();
                    for (int i = 0; i < dataArray.length; i++) {
                        if (i % 40 == 0) {
                            envelope.add(dataArray[i]);
                        }
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "error when try to read data");
                }
                synchronized (dataset) {
                    series0 = (XYValueSeries) dataset.getSeriesAt(0);
                    int length = series0.getItemCount();
                    //已经没有多余的空间容纳整个数组，直接重新绘图
                    if (length + envelope.size() - POINT_NUM > 0) {
                     /*   for(int i = 0; i < envelope.size(); i++)
                            series0.remove(0);
                        cnt -= envelope.size();*/
                        series0.clear();
                        cnt = 0;
                    }
                    for (int i = 0; i < envelope.size(); i++) {
                        series0.add(cnt, envelope.get(i));
                        ++cnt;
                    }
                    dataset.removeSeries(0);
                    dataset.addSeries(0, series0);
                   // mHandler.obtainMessage(CHART_UPDATE).sendToTarget();
                }
            }
            clear();
        }
    }


    /*public void updateSeries(int seriesNum, int voltage) {
        XYValueSeries series = (XYValueSeries) dataset.getSeriesAt(seriesNum);
        int length = series.getItemCount();
        //数据点数超过pointNum就重新绘图
        if (length >= POINT_NUM) {
            cnt[seriesNum] = 0;
            series.clear();
        }
        //下一个数据
        cnt[seriesNum]++;
        addY = voltage;
        series.add(cnt[seriesNum], addY);
        //在数据集中添加新的点集
        dataset.removeSeries(seriesNum);
        dataset.addSeries(seriesNum, series);
        //曲线更新
        chart.invalidate();
    }*/


    /*public void updataSeries(int seriesNum, short[] values) {
        XYValueSeries series = (XYValueSeries) dataset.getSeriesAt(seriesNum);
        int length = series.getItemCount();
        //已经没有多余的空间容纳整个数组，直接重新绘图
        if (length + values.length - pointNum > 0) {
            cnt[seriesNum] = 0;
            series.clear();
        }
        for (short a : values) {
            series.add(cnt[seriesNum], a);
            ++cnt[seriesNum];
        }
        dataset.removeSeries(seriesNum);
        dataset.addSeries(seriesNum, series);
        chart.invalidate();
    }*/

    public void clear() {
        XYValueSeries series0 = (XYValueSeries) dataset.getSeriesAt(0);
        series0.clear();
        dataset.removeSeries(0);
        dataset.addSeries(0, series0);
        cnt = 0;

//        XYValueSeries series1 = (XYValueSeries) dataset.getSeriesAt(1);
//        series1.clear();
//        dataset.addSeries(1, series1);
//        dataset.removeSeries(1);
//        cnt[1] = 0;

        mHandler.obtainMessage(CHART_UPDATE).sendToTarget();

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
     * @param seriesType   曲线类型，0是横坐标固定的曲线，1是时间曲线
     * @param lineColor    曲线颜色
     * @param lineWidth    曲线线宽
     * @param style        点的风格
     */
    public void AddSeriesToRender(String datasetTitle, int seriesType, int lineColor, int lineWidth, PointStyle style) {
        XYSeriesRenderer r = new XYSeriesRenderer();
        r.setColor(lineColor);
        r.setChartValuesTextSize(1);
        r.setChartValuesSpacing(1);
        r.setPointStyle(style);
        r.setFillPoints(false);
        r.setLineWidth(lineWidth);
        renderer.addSeriesRenderer(r);
        addDataSet(datasetTitle, seriesType);
    }


    /**
     * 增加时间序列
     *
     * @param dataSetTitle 曲线标题
     */
    private void addDataSet(String dataSetTitle, int seriesType) {
        switch (seriesType) {
            case LINE_SERIES:
                XYValueSeries series = new XYValueSeries(dataSetTitle);
                dataset.addSeries(series);
                break;
            case TIME_SERIES:
                TimeSeries series1 = new TimeSeries(dataSetTitle);
                dataset.addSeries(series1);
                break;
            default:
                break;
        }

    }

}
