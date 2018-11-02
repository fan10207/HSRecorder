package com.lippi.hsrecorder.utils;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;


import com.lippi.hsrecorder.iirfilterdesigner.model.FilterCoefficients;
import com.lippi.hsrecorder.pcm.PcmAudioHelper;
import com.lippi.hsrecorder.pcm.RiffHeaderData;
import com.lippi.hsrecorder.pcm.WavAudioFormat;
import com.lippi.hsrecorder.pcm.WavFileReader;
import com.lippi.hsrecorder.utils.helper.LogHelper;
import com.lippi.hsrecorder.utils.helper.bluetooth.AppFragment;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import roboguice.activity.RoboActionBarActivity;

/**
 * Created by lippi on 14-12-4.
 */
public class AudioRecorder /*implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener*/ {

    private static final String TAG = LogHelper.makeLogTag(AudioRecorder.class);

    public static final String SAMPLE_DEFAULT_DIR = "/sound_recorder";

    public static final String extension = ".wav";

    //errors
    public static final int STORAGE_ACCESS_ERROR = 1;

    public static final int INTERNAL_ERROR = 2;

    public static final int IN_CALL_RECORD_ERROR = 3;

    //states
    public static final int IDLE_STATE = 0;

    public static final int RECORDING_STATE = 1;

    public static final int PLAYING_STATE = 2;

    public static final int PLAYING_PAUSED_STATE = 3;

    public static final int RECORDING_STOPPED = 4;


    //设置AudioRecorder每隔FrameCount帧就通知线程有数据可读
    private static final int FRAME_COUNT = 640;
    public static final int BEGIN_CALCULATE_HEART_RATE = 1001;

    private int sampleRate;

    private static AudioRecord audioRecord;

    private AudioTrack audioTrack;

//    private MediaPlayer mPlayer = null;

    //buffer size of the recorder and AudioTrack
    private int bufferSizeInBytes;


    //the dir to store
    private static File mSampleDir = null;

    //存储文件名
//    private File mRecordFile = null;
    private File mFilterFile = null;

    //    private DataOutputStream dataOutputStream;
    private DataOutputStream filterOutputStream;

    private WavFileReader mWavFileReader;

    //  save current play position
    private volatile int mCurrentPosition;

    private int mTotalSampleLength;

    private int mPlayBufferSize = 1024;

    //the total size of the recorder, you have to modify the size in the header of the wav file
    private int totalSizeInBytes = 0;

    //双缓冲队列,用于播放
    private volatile LinkedList<short[]> PlayBuffer;

    //format
    private WavAudioFormat wavAudioFormat;

    // time at which latest record or play operation started
    private long mSampleStart = 0;

    // length of current sample
    private int mSampleLength = 0;

    //自适应放大倍数
    private int scale = 1;

    int count=1;
    private boolean isFirst=true;
    private float heartrate;

    //the position we are playing
//    private int mFramePosition = 0;

//    private WavFileReader wavFileReader;

    private volatile int mState = IDLE_STATE;

    private ChartView chartView;

    private String prefixF ="normal";

    //android built-in Visualizer
//    private Visualizer mVisualizer;

    //buffer for the filtered data
//    private SlippingBuffer filterBuffer;

    //filter param
    private int filter_order;
    volatile short[] resampleData = new short[8000];
    int index = 0;
    //录音的缓冲区
    private short[] buffer;
    private short[] databuffer;
    private short[] databuffer1;
    // filter coefficient Ak
    private double[] pythonA;
    // filter coefficient Bk
    private double[] pythonB;

    //X(n-k) X(n-1) X(n-2)
    private List<Short> xn_k;
    private List<Short> xn_k1;
    //save digital filter output
    List<Double> yn_k;
    List<Double> yn_k1;

    private RecordingThread recordingThread;

    //you can choose to play the record when recording
    private volatile boolean playWhenRecording = false;

    private ExecutorService executors = Executors.newFixedThreadPool(5);

    private static final int AUDIO_RECORD_DATA_COMMING = 0;

    private static final int AUDIO_PLAYER_FINISHED = 1;

    private static final int PINK_THRESHOULD = 10;
    private volatile int pink_detcted = 0;
    private volatile LinkedList<short[]> reSampledDataList;
    private Handler mainHandler;
    public static final int PERIOD_GOT = 1000;
    private int num = 1;


    private class UpdateHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AUDIO_RECORD_DATA_COMMING:
                    short[] shorts = (short[]) msg.obj;
                    int length = shorts.length;
                    short[] data = new short[length];
                    for (int i = 0; i < length; i++) {
                        data[i] = shorts[i];
                    }
                    chartView.updateChart(data);
                    break;
                case AUDIO_PLAYER_FINISHED:
                    onCompletetion();
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    }


    /**
     * clear the real time chart
     */
    private class ClearTask implements Runnable {

        @Override
        public void run() {
            chartView.cleanChart();
        }
    }

    private UpdateHandler mHandler = new UpdateHandler();

    static {
        mSampleDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                + SAMPLE_DEFAULT_DIR);
        if (!mSampleDir.exists()) {
            mSampleDir.mkdirs();
        }

    }

    //provide a interface for callback for the main activity
    public interface OnStateChangedListener {
        void onStateChanged(int state);

        void onError(int error);
    }

    private OnStateChangedListener mOnStateChangedListener = null;

    public void setOnStateChangedListener(OnStateChangedListener listener) {
        mOnStateChangedListener = listener;
    }






    /**
     * construct a Recorder,use PCM_16Bits as default encoder, bacause most android phones support it
     *
     * @param sampleRate
     */
    public AudioRecorder(int sampleRate, Handler handler,
                         ChartView view) {

        this.sampleRate = sampleRate;
        this.mainHandler = handler;
        chartView = view;
        PlayBuffer = new LinkedList<short[]>();
        reSampledDataList = new LinkedList<short[]>();
    }

    public void setFilterCoefs(FilterCoefficients filterCoefs) {
        filter_order = filterCoefs.getFilterOrder();
        //to plus one because of A[0],B[0]
        xn_k = new ArrayList<Short>(filter_order + 1);
        yn_k = new ArrayList<Double>(filter_order + 1);
        xn_k1 = new ArrayList<Short>(filter_order + 1);
        yn_k1 = new ArrayList<Double>(filter_order + 1);
        pythonA = filterCoefs.getACoefficients();
        pythonB = filterCoefs.getBCoefficients();
    }


    /**
     * 重新錄音前清除Xn_K
     */
    public void clearXnk() {
        xn_k.clear();
        yn_k.clear();
        xn_k1.clear();
        yn_k1.clear();
        PlayBuffer = new LinkedList<short[]>();
        reSampledDataList = new LinkedList<short[]>();
        for (int i = 0; i < filter_order + 1; i++) {
            xn_k.add(i, (short) 0);
            yn_k.add(i, 0D);
            xn_k1.add(i, (short) 0);
            yn_k1.add(i, 0D);
        }
    }

    /**
     * zoom In the chart
     */
    public void zoomIn() {
        this.scale *= 2;
    }

    public void zoomOut() {
        this.scale /= 2;
        if (scale == 0) scale = 1;
    }

    public void setSampleRate(int sampleRate) {
        stop();
        this.sampleRate = sampleRate;
    }



    public void updatefloat(short[] buf) {
       /* while (AudioRecorder.this.getState() == RECORDING_STATE) {
        if (buf == null || buf.length == 0)
            return;
            databuffer = new short[buf.length];
            for (int i = 0; i <= buf.length - 1; i++) {
                databuffer[i] = (short) buf[i];
                Log.e(TAG, "databuffer...." + databuffer[i]);
            }
            //chartView.updateChart(databuffer);
           // executors.execute(new FilterTask(databuffer));
        }*/
           int flag=0;
            databuffer = new short[buf.length];
            for (int i = 0; i <= buf.length - 1; i++) {
                databuffer[i] = (short) buf[i];
                // Log.e(TAG, "databuffer...." + databuffer[i]);
            }

            executors.execute(new FilterTask(databuffer));

        /*mHandler.postDelayed(new ClearTask(), 200);
        try {
            finishEncode();
        } catch (IOException e) {
            e.printStackTrace();
        }
*/
    }
    public void startRecording(final String fileName) {
        Log.e(TAG,"aaa start");

        stop();
        clearXnk();
        totalSizeInBytes=0;
        /*databuffer1=updatefloat(databuffer);
        for(int i=0;i<=databuffer1.length-1;i++){
            Log.e(TAG,"databuffer1...."+databuffer1[i]);
        }*/
        //recordingThread = new RecordingThread();
        if (getState() == RECORDING_STATE) return;
        Log.e(TAG, "start recording");
        try {
            initAudioRecorder();
//            mRecordFile = new File(mSampleDir, fileName + "origin" + extension);
            SimpleDateFormat dataFormat = new SimpleDateFormat("MM月dd日HH点mm分ss秒", Locale.CHINA);
            String time=dataFormat.format(Calendar.getInstance().getTime());

            mFilterFile = new File(mSampleDir, fileName+prefixF+time + extension);
//            dataOutputStream = new DataOutputStream(new FileOutputStream(mRecordFile));
            filterOutputStream = new DataOutputStream(new FileOutputStream(mFilterFile));
            //write 44 bytes header data for wav file
//            dataOutputStream.write(new RiffHeaderData(wavAudioFormat, 0).asByteArray());
            filterOutputStream.write(new RiffHeaderData(wavAudioFormat, 0).asByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }

        //audioRecord.startRecording();
        //executors.execute(new HeartSoundProcessTask());
        setState(RECORDING_STATE);

        mSampleStart = System.currentTimeMillis();
       // recordingThread.start();


        //开始播放
        if (playWhenRecording)
            startPlaying();

    }


    /**
     * this is the Recording Thread , thread read data from AudioRecord and transfer the data to FilterThread
     */
    public class RecordingThread extends Thread {
        /*public short[] ceshi;
        public RecordingThread(short[] ceshi){
            this.ceshi=ceshi;
        }
*/
        @Override
        public void run() {

           while (AudioRecorder.this.getState() == RECORDING_STATE) {
                int sizeInShort = audioRecord.read(buffer, 0, bufferSizeInBytes / 2);
               Log.e(TAG,"sizeInShort"+sizeInShort);
                //zoomIn and zoomOut the data
                if (sizeInShort > 0) {
                    for (int i = 0; i < sizeInShort; i++) {
                        buffer[i] *= scale*0.9;
                        buffer[i]+=(short)i;
                    }
                   /*for(int i=0;i<ceshi.length;i++){
                       buffer[i]=(short)ceshi[i];
                   }
                   Log.e(TAG,"buffercahngdu"+buffer.length);*/
                   /*for(int i=0;i<buffer.length;i++){
                       Log.e(TAG,"buffer"+buffer[i]);

                   }*/
                }
                executors.execute(new FilterTask(buffer));

                super.run();

            }
            Log.e(TAG,"abcde4");
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            Log.e(TAG,"abcde5");
           /* mHandler.postDelayed(new ClearTask(), 200);
            Log.e(TAG,"abcde6");
            try {
                finishEncode();
                Log.e(TAG,"abcde7");
            } catch (IOException e) {
                e.printStackTrace();
            }*/



        }
    }

    private  synchronized short[] myRemoveFirst() {
        return PlayBuffer.removeFirst();
    }

    /**
     * this is the main class of Digital Filter Thread, Thread first clone the data from the recording Thread,
     * then use the digital filter algorithm, fill the data into the playList and write to the recording file
     * remember that the filter task should run in sequence because the data from the recording thread is in sequence,
     * you may use the Synchronized  lock
     */
    private class FilterTask implements Runnable {

        short[] data;


        FilterTask(short[] dat) {
            data = dat.clone();
        }

        @Override
        public void run() {

            Log.d(TAG, "filter task start");
            short[] filterData = new short[data.length];
            synchronized (AudioRecorder.class) {
                short[] left = new short[data.length / 2];
                short[] right = new short[data.length / 2];
                for(int i=0;i<data.length/2;i++) {
                    left[i] = data[2*i];
                    right[i] = data[2 * i + 1];
                }
                //digital filter code
                for (int i = 0; i < data.length/2; i++) {
                    xn_k.remove(filter_order);
                    xn_k.add(0, left[i]);
                    xn_k1.remove(filter_order);
                    xn_k1.add(0, right[i]);
                    double temp = 0;
                    double temp1 = 0;
                    for (int j = 0; j < filter_order + 1; j++) {
                        temp += xn_k.get(j) * pythonB[j];
                        temp -= yn_k.get(j) * pythonA[j];
                        temp1 += xn_k1.get(j) * pythonB[j];
                        temp1 -= yn_k1.get(j) * pythonA[j];
                    }
                    yn_k.remove(filter_order);
                    yn_k.add(1, temp);
                    yn_k1.remove(filter_order);
                    yn_k1.add(1, temp1);
                    //filterData[i] =0;
                    if (temp > Short.MAX_VALUE || temp < Short.MIN_VALUE) {
                        if (temp > Short.MAX_VALUE) {
                            filterData[2 * i] = Short.MAX_VALUE;
                        }

                        else{
                            filterData[2 * i] = Short.MIN_VALUE; }

                    } else {
                        filterData[2*i] = (short) temp;
                    }
                    if (temp1 > Short.MAX_VALUE || temp1 < Short.MIN_VALUE) {
                        if (temp1 > Short.MAX_VALUE) {
                            filterData[2 * i] = Short.MAX_VALUE;
                        }

                        else{
                            filterData[2 * i+1] = Short.MIN_VALUE; }

                    } else {
                        filterData[2*i+1] = (short) temp1;
                    }


                    // Log.e(TAG, "run: go"+"   "+i +"    "+temp+"     "+filterData[i]);
                }


          /*      for (int i=0;i<left.length/(8);i++) {
                    resampleData[index++] = left[i*(8)];
                    if (8000 == index) {
                        if (isFirst) {
                            isFirst = false;
                        } else {
                            reSampledDataList.add(resampleData);
                        }
                        if (reSampledDataList.size() > 2) {
                            reSampledDataList.removeFirst();
                        }
                        resampleData = new short[8000];
                        index = 0;
                    }
                }
*/


                try {
                    mHandler.obtainMessage(AUDIO_RECORD_DATA_COMMING, filterData).sendToTarget();
                    byte[] innerBuffer = new byte[data.length * 2];
                    //turn buffer into Little_endian byte array
                    ByteBuffer.wrap(innerBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            .put(filterData);
                    PlayBuffer.add(filterData);
                    if (PlayBuffer.size() > 2) PlayBuffer.removeFirst();
                    filterOutputStream.write(innerBuffer);
                    totalSizeInBytes += data.length * 2;

                } catch (IOException e) {
                    Log.d(TAG, "error write fileOutputStream");
                }
            }
        }
    }

    /**
     * calculate shannon energy , pink detection,
     */
    private class HeartSoundProcessTask implements Runnable {

        @Override
        public void run() {

            short[] data;
            while (getState() == RECORDING_STATE) {
                if (reSampledDataList.size() > 1) {
                    data=reSampledDataList.removeFirst();
                    float[] normData = normalize(shortArrayToFloat(data, data.length));
                    float heartRate = heartCaculate(normData);
                    Log.e(TAG, "run: heartrate"+heartRate );
                 int   heartRate1 = (int) heartRate;
                    mainHandler.obtainMessage(PERIOD_GOT, heartRate1).sendToTarget();
                  //  mainHandler.obtainMessage(PERIOD_GOT, heartRate, -1).sendToTarget();
                }

            }

       /*

            short[] data;
            while (getState() == RECORDING_STATE) {
                if (reSampledDataList.size() > 2) {
                    Log.i(TAG, "begin to calculate");
                    reSampledDataList.removeFirst();
                    data = reSampledDataList.removeFirst();
                    float[] normData = normalize(shortArrayToFloat(data, data.length));
                    float[] shannonEnergy = enframe(normData);
                    Log.i(TAG, "shannon energy " + Arrays.toString(shannonEnergy));
                    List<Integer> pinks = pinkDetect(shannonEnergy, shannonEnergy.length);
                    if (pinks.size() == 0) {
                        pink_detcted = 0;
                    } else {
                        pink_detcted += pinks.size();
                        //if pinks is bigger than 10, begin to calculate period
                        if (pink_detcted > PINK_THRESHOULD) {
                            mainHandler.obtainMessage(BEGIN_CALCULATE_HEART_RATE).sendToTarget();

                            FutureTask<Integer> task = new FutureTask<Integer>(new PerioudCalTask(normData, sampleRate));
                            if (num > 0) {
                                executors.submit(task);
                                num--;
                            }


                            //frames a heart sound period has
                            int period_frames;
                            try {
                                Log.e(TAG, "run: start");
                                period_frames = task.get();

                                int heartRate = period_frames;
                                float lowLimit = 0.25f * period_frames;
                                float highLimit = 0.6f * period_frames;
                                int[] S1S2 = getS1S2(pinks, lowLimit, highLimit);
                                short[] periodData = getPerioudData(data, shannonEnergy, S1S2[0], period_frames);
                                mainHandler.obtainMessage(PERIOD_GOT, heartRate, -1, periodData).sendToTarget();
                                break;
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (ExecutionException e) {
                                e.printStackTrace();
                            }

                        }
                    }
                }
            }*/
        }
    }


    private float heartCaculate(float[] data) {

        data = preHandle(data,0.1F );
        data = selfCorr(data);

        data = nonLinear(data,  0.8F);

        float[] winData = addWin(data);
        int[] index = findIndex(winData);
        float[] testdata = new float[(index[1] - index[0]) * 10];
        Log.e(TAG, "heartCaculate: "+index[0] +  "   "+index[1]);
        for (int i = 0; i < testdata.length; i++) {
            testdata[i] = data[index[0]*10+i ];
        }
        int secondM = secondExtreme(testdata);
        secondM = index[0] * 10 + secondM;


        heartrate=60*2205/secondM;

        return heartrate;

    }

    public int secondExtreme(float[] data) {
        if (data.length == 0) {
            return 1;
        }
        float door = Math.abs(data[0]);

        int index = 1;
        int length = data.length;
        for (int i = 0; i < length; i++) {

            if (Math.abs(data[i]) > door) {
                door = Math.abs(data[i]);
                index=i+1;
            }
        }

        return index;
    }

    private float[] preHandle(float[] data, float val) {
        float door = max(data);
        val = door*val;
        for (int i = 0; i < data.length; i++) {
            if (data[i] < val) {
                data[i] = 0;
            }
        }

        return data;
    }

    private float[] nonLinear(float[] data, float val) {
        float door = 0;
        float[] t1 = new float[data.length / 3];
        float[] t2 = new float[data.length / 3];
        for (int i=0;i<data.length/3;i++) {
            t1[i] = data[i + 200];
            t2[i] = data[i + data.length / 3 * 2];
        }
        if (max(t1) > max(t2)) {
            door = max(t2);
        } else {
            door = max(t1);
        }

        val = door * val;
        for (int i = 0; i < data.length; i++) {
            if (data[i] < val) {
                data[i] = 0;
            } else {
                data[i] = data[i];
            }

        }

        return data;
    }

    private float[] selfCorr(float[] data) {
        float[] result = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = 0;
            for (int a = 0; a < data.length-i; a++) {

                    result[i] += data[a] * data[a + i];

            }
            result[i] = result[i] / (data.length - i);

        }

        return result;
    }

    private float[] addWin(float[] data) {
        int len = data.length / 10;
        float[] result = new float[len];
        for (int i = 0; i < len; i++) {
            result[i]=0;
            for (int a = 0; a < 10; a++) {
                result[i] += data[i*10 + a];
            }
            Log.e(TAG, "preHandle: "+i+"   "+result[i] );
        }
        return result;
    }

    private int[] findIndex(float[] data) {
        int num = 0;
        int[] start = new int[2];
        int[] end = new int[2];
        int length = data.length;
        float val = data[0] * 2 / 10;
      //  Log.e(TAG, "findIndex:val "+val );

        for (int i = 0; i < length; i++) {
         //   Log.e(TAG, "findIndex: data"+i+"  " +data[i]);

            if (data[i] > val  && start[num] == 0&& end[num] == 0) {

                start[num] = i+1;
                i=i+20;
            } else {
                if (data[i] < val&&start[num]!=0) {

                    end[num] = i;
                    num++;
                    if (num == 2) {
                        break;
                    }
                }
            }
        }
        int[] result = new int[2];
        if (start[1]==0) {
         int[]   result1={1,2};
            return result1;
        }
        result[0] = start[1]-1;
        result[1] = end[1]+20;
        return result;
    }

    /**
     * 计算数组的绝对值最大值
     *
     * @param data 数组
     * @return
     */
    public float max(float[] data) {
        float max = Math.abs(data[0]);

        int length = data.length;
        for (int i = 0; i < length; i++) {

            if (Math.abs(data[i]) > max) {
                max = Math.abs(data[i]);
            }
        }

        return max;
    }

    /**
     * 对数值进行归一化处理
     *
     * @param data
     * @return 归一化的数据
     */
    public float[] normalize(float[] data) {
        float[] norm = new float[data.length];
        float max = max(data);
        for (int i = 0; i < data.length; i++) {
            norm[i] = data[i] / max;
        }
        // log.info(TAG,Arrays.toString(norm));
        return norm;
    }

    public float[] shortArrayToFloat(short[] shorts, int size) {
        float[] floats = new float[size];
        for (int i = 0; i < size; i++) {
            floats[i] = shorts[i];
        }
        return floats;
    }


    /**
     * 对信号分帧，每一帧长0.2s，帧移0.1s 这里没有进行加窗处理 计算每一帧的平均香农能量 e = -x^2 * log(x^2)
     *
     * @param origin 原始信号
     * @return 每一帧的平均香农能量
     */
    public float[] enframe(float[] origin) {
        int frameLength = (int) (0.02f * 2000);
        int window = frameLength / 2;
        int frameNum = (int) Math.floor((origin.length - frameLength + window)
                / window);
        float[] energy = new float[frameNum];
        for (int i = 0; i < frameNum; i++) {
            float temp = 0, temp2;
            for (int j = 0; j < frameLength; j++) {
                // 由于数字进行了归一化处理，所以需要对数据进行适当放大，这里取16384*8
                temp2 = (float) Math.pow(origin[i * window + j], 2);
                if (temp2 <= 0) temp2 = Float.MIN_VALUE;
                temp -= 16384 * 8 * Math.pow(origin[i * window + j], 2)
                        * Math.log(temp2);
//                temp += Math.abs(origin[i * window + j]);
            }
            temp /= frameLength;
            energy[i] = temp;
        }
        return energy;
    }

    /**
     * pink detect
     * energy should be bigger than 5000
     *
     * @param energy shannon energy for every frame
     * @param length frame number
     * @return all the pink saticisify
     */
    public List<Integer> pinkDetect(float[] energy, int length) {
        List<Integer> result = new ArrayList<>();
        for (int i = 1; i < length - 1; i++) {
            if (energy[i] > 15000 && energy[i] > energy[i - 1] && energy[i] > energy[i + 1]) {
                result.add(i + 1);
            }
        }
        return result;
    }

    /**
     * get first S1 and S2 frame
     *
     * @param pinks
     * @param lowLimit
     * @param highLimit
     * @return
     */
    public int[] getS1S2(List<Integer> pinks, float lowLimit, float highLimit) {
        int[] S1S2 = new int[2];
        int P0 = 0, P1;
        for (int i = 1; i < pinks.size(); i++) {
            P1 = i;
            if (pinks.get(P1) - pinks.get(P0) > lowLimit && pinks.get(P1) - pinks.get(P0) < highLimit) {
                S1S2[0] = pinks.get(P0);
                S1S2[1] = pinks.get(P1);
                break;
            } else if (pinks.get(P1) - pinks.get(P0) > highLimit) {
                P0 = P1;
            }
        }

        return S1S2;
    }

    /**
     * get a perioud data of heart sound
     * calculate the start of HS by shannonEnergy and S1 Pink
     * get a period
     *
     * @param data
     * @param shannonEnergy
     * @param s1Frame
     * @param perioudFrame
     * @return
     */
    public short[] getPerioudData(short[] data, float[] shannonEnergy, int s1Frame, int perioudFrame) {
        int perioudCnt = perioudFrame * 20 + 20;

        int low = s1Frame;
        while (low > 0 && shannonEnergy[low] > 5000) {
            low--;
        }
        int start = low * 20;
        if (perioudCnt > data.length - start) {
            perioudCnt = data.length - start;
        }
        short[] perioudData = new short[perioudCnt];
        System.arraycopy(data, start, perioudData, 0, perioudCnt);
        return perioudData;
    }

    /**
     * 初始化录音模块
     *
     * @throws FileNotFoundException
     */
    private void initAudioRecorder() throws FileNotFoundException {

        wavAudioFormat = new WavAudioFormat.Builder().sampleRate(sampleRate)
                .channels(2)
                .sampleSizeInBits(16).build();
        //使用PCM_16bit编码
        int bytesPerFrame = 2;
        //get the frame count of the given config
        int frameSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT) / bytesPerFrame;
        System.out.println("frameSize" + frameSize);

        if (frameSize % FRAME_COUNT != 0) {
            frameSize = frameSize + (FRAME_COUNT - frameSize % FRAME_COUNT);
            Log.d(TAG, "frameSize:" + frameSize);
        }

        bufferSizeInBytes = frameSize * bytesPerFrame;

        Log.e(TAG, "initAudioRecorder: bufferSizeInBytes"+bufferSizeInBytes );
        //setup audioRecorder
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes);

        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes, AudioTrack.MODE_STREAM);

        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            Log.d(TAG, "audio recorder initialized");
            Log.d(TAG, "audio track initialized");
        } else {
            Log.d(TAG, "audio recorder init failed");
            audioRecord.release();
            audioRecord = null;
            audioTrack.release();
            audioTrack = null;
        }

        buffer = new short[bufferSizeInBytes / 2];

//        audioRecord.setRecordPositionUpdateListener(encodeThread, encodeThread.getHandler());
//        audioRecord.setPositionNotificationPeriod(FRAME_COUNT);
    }

    public void stopRecording() {
        Log.d(TAG, "stop recording");
        if (getState() == RECORDING_STATE) {
            setState(RECORDING_STOPPED);
            mHandler.postDelayed(new ClearTask(), 200);
        try {
            finishEncode();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.e(TAG,"abcde3");
            recordingThread = null;
            mSampleLength = (int) ((System.currentTimeMillis() - mSampleStart) / 1000);
            if (mSampleLength == 0) {
                // round up to 1 second if it's too short
                mSampleLength = 1;
            }
        }
    }

    /**
     * 边录边听
     */
    public void startPlaying() {
        playWhenRecording = true;
      /*  audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes, AudioTrack.MODE_STREAM);*/
        //播放线程
        if (getState() == RECORDING_STATE) {
            Log.e(TAG, "startPlaying: play" );
            new Thread() {
                @Override
                public void run() {
                    audioTrack.play();
                    short[] outBuffer;
                    while (playWhenRecording) {
                        if (PlayBuffer.size()>1) {
                            Log.e(TAG, "wj2    "+PlayBuffer.size() );
                            outBuffer = PlayBuffer.removeFirst();
                            short[] playData = outBuffer.clone();
                            audioTrack.write(playData, 0, playData.length);
                        }
                    }
                    audioTrack.stop();
                   /* audioTrack.release();
                     audioTrack = null;*/
                }
            }.start();
        }
    }



    /**
     * 录音的时候停止播放
     */
    public void stopPlaying() {
        playWhenRecording = false;
    }

    /**
     * update the size information of the header
     *
     * @throws IOException
     */
    private void finishEncode() throws IOException {
        filterOutputStream.flush();
        filterOutputStream.close();
        Log.e(TAG,"abcde8");
        PcmAudioHelper.modifyRiffSizeData(mFilterFile, totalSizeInBytes);
    }


    public String getRecordDir() {
        return mSampleDir.getAbsolutePath();
    }

    public int getState() {
        return mState;
    }

    public int progress() {
        if (mState == RECORDING_STATE) {
            return (int) ((System.currentTimeMillis() - mSampleStart) / 1000);
        } else if (mState == PLAYING_STATE || mState == PLAYING_PAUSED_STATE) {
            if (mWavFileReader != null) {
                return (mCurrentPosition / sampleRate / 2);
            }
        }

        return 0;
    }

    public float playProgress() {
        if (mWavFileReader != null) {
            return (float) mCurrentPosition / mTotalSampleLength;
        }
        return 0.0f;
    }

    public int sampleLength() {
        return mSampleLength;
    }

    public File sampleFile() {
        return mFilterFile;
    }

    public void renameSampleFile(String name) {
        if (mFilterFile != null && mState != RECORDING_STATE && mState != PLAYING_STATE) {
            if (!TextUtils.isEmpty(name)) {

                String oldName = mFilterFile.getAbsolutePath();
                Log.e(TAG, "renameSampleFile: " + oldName);
                String extension = oldName.substring(oldName.lastIndexOf('.'));
                File newFile = new File(mFilterFile.getParent() + "/" + name + extension);
                //      File newFilterFile = new File(mFilterFile.getParent() + "/" + name + "filter" + extension);
                if (!TextUtils.equals(oldName, newFile.getAbsolutePath())) {
                    Log.e(TAG, "renameSampleFile: 1");

                    if (mFilterFile.renameTo(newFile)) {
                        Log.e(TAG, "renameSampleFile: 12");

                        //      mFilterFile.renameTo(newFilterFile);
                        mFilterFile = newFile;
                        //      mFilterFile = newFilterFile;
                    }
                }
            }
        }
    }

    /**
     * Resets the recorder state. If a sample was recorded, the file is deleted.
     */
    public void delete() {
        stop();

        if (mFilterFile != null)
            mFilterFile.delete();
        if (mFilterFile != null)
            mFilterFile.delete();
        mFilterFile = null;
        mFilterFile = null;
        mSampleLength = 0;
        mState = IDLE_STATE;
        signalStateChanged(IDLE_STATE);
    }

    /**
     * Resets the recorder state. If a sample was recorded, the file is left on
     * disk and will be reused for a new recording.
     */
    public void clear() {
        stop();
        mSampleLength = 0;
        mState = IDLE_STATE;
        signalStateChanged(IDLE_STATE);
    }

    public void reset() {
        stop();
        mSampleLength = 0;
//        mRecordFile = null;
        mFilterFile = null;
        mState = IDLE_STATE;
        try {
            initAudioRecorder();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        signalStateChanged(IDLE_STATE);
    }

    /**
     * 判断某个录音文件是否存在
     *
     * @param path
     * @return
     */
    public boolean isRecordExisted(String path) {
        if (!TextUtils.isEmpty(path)) {
            Log.e(TAG, "isRecordExisted: 123");
            File file = new File(mSampleDir.getAbsolutePath() + "/" + path);
            return file.exists();
        }
        return false;
    }

    /**
     * 播放录音,之前用的MediaPLayer来播放，但是这样无法获取音频数据来显示，因此建议使用AudioTrack
     *
     * @param percentage
     */
    public void startPlayback(float percentage) {
        //这里采用AudioTrack来播放录音,可以保持录音播放和曲线显示的同步
        try {
            mWavFileReader = new WavFileReader(mFilterFile);
            mTotalSampleLength = mWavFileReader.getSampleCount();
        } catch (IOException e) {
            LogHelper.d(TAG, e, "unable to read file");
        }
        Log.e(TAG,"abcde9");
        mCurrentPosition = (int) (percentage * totalSizeInBytes / 2);
        setState(PLAYING_STATE);
        Log.e(TAG,"abcde10");
        new Thread() {
            @Override
            public void run() {
                int numberToRead = 0;
                short[] data;
                Log.e(TAG,"abcde13");
                while (mCurrentPosition < mTotalSampleLength) {
                    try {
                        //file deleted
                        if (mState == PLAYING_PAUSED_STATE || mState == IDLE_STATE) {
                            audioTrack.stop();
                            break;
                        }
                        audioTrack.play();
                        numberToRead = mTotalSampleLength - mCurrentPosition > mPlayBufferSize ?
                                mPlayBufferSize : 1;
                        data = mWavFileReader.getSamplesAsShorts(mCurrentPosition, mCurrentPosition + numberToRead);

                        mHandler.obtainMessage(AUDIO_RECORD_DATA_COMMING, data).sendToTarget();
                        audioTrack.write(data, 0, numberToRead);
                        Log.e(TAG,"abcde14");
                        mCurrentPosition += numberToRead;
                    } catch (IOException e) {
                        LogHelper.d(TAG, e, "exception happens when reading audio data");
                    }
                }
                //play finished,clear display and set mCurrentPosition = 0
                if (mState == PLAYING_STATE || mState == IDLE_STATE) {
                    mHandler.obtainMessage(AUDIO_PLAYER_FINISHED).sendToTarget();
                }


            }
        }.start();

/*
        if (getState() == PLAYING_PAUSED_STATE) {
            mSampleStart = System.currentTimeMillis() - mPlayer.getCurrentPosition();
            mPlayer.seekTo((int) (percentage * mPlayer.getDuration()));
            mPlayer.start();
            setState(PLAYING_STATE);

        } else {
            stop();
            mPlayer = new MediaPlayer();
            try {
                mPlayer.setDataSource(mRecordFile.getAbsolutePath());
                mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mPlayer.setOnCompletionListener(this);
                mPlayer.setOnErrorListener(this);
                mPlayer.prepare();
                mPlayer.seekTo((int) (percentage * mPlayer.getDuration()));
                mPlayer.start();
                setupVisualizerFxAndUI();
                // Make sure the visualizer is enabled only when you actually want to receive data, and
                // when it makes sense to receive data.
                mVisualizer.setEnabled(true);

            } catch (IllegalArgumentException e) {
                setError(INTERNAL_ERROR);
                mPlayer = null;
                return;
            } catch (IOException e) {
                setError(STORAGE_ACCESS_ERROR);
                mPlayer = null;
                return;
            }

            mSampleStart = System.currentTimeMillis();
            setState(PLAYING_STATE);
        }
*/
    }

    public void pausePlayback() {
        if (mWavFileReader == null) {
            return;
        }
        setState(PLAYING_PAUSED_STATE);
        chartView.stopDrawing();
        /*if (mPlayer == null) {
            return;
        }

        mPlayer.pause();
        setState(PLAYING_PAUSED_STATE);*/
    }

    public void stopPlayback() {
        if (mWavFileReader == null) {
            return;
        }
        mWavFileReader = null;
        audioTrack.stop();
        mCurrentPosition = 0;


        setState(IDLE_STATE);
        mHandler.postDelayed(new ClearTask(), 200);

        /*if (mPlayer == null) // we were not in playback
            return;

        mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
        mHandler.postDelayed(new ClearTask(), 200);
        mVisualizer.setEnabled(false);
        mVisualizer.release();
        mVisualizer = null;

        setState(IDLE_STATE);*/

    }


    public void stop() {
        stopRecording();
        stopPlayback();
    }

    /**
     * @Override public boolean onError(MediaPlayer mp, int what, int extra) {
     * stop();
     * setError(STORAGE_ACCESS_ERROR);
     * return true;
     * }
     * @Override public void onCompletion(MediaPlayer mp) {
     * stop();
     * }
     **/
    public void onCompletetion() {
        stop();
        chartView.cleanChart();
    }

    public void setState(int state) {
        if (state == mState)
            return;

        mState = state;
        signalStateChanged(mState);
    }

    private void signalStateChanged(int state) {
        if (mOnStateChangedListener != null)
            mOnStateChangedListener.onStateChanged(state);
    }

    public void setError(int error) {
        if (mOnStateChangedListener != null)
            mOnStateChangedListener.onError(error);
    }

    /**
     * invoked when data need to be displayed
     */
    interface OnDataCapturedListener {
        void onDataCaptured(short[] data);
    }

    public void changeDir(String s) {
       prefixF=s;
    }


}
