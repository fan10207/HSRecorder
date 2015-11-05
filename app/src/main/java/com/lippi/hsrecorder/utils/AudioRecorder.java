package com.lippi.hsrecorder.utils;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;


import com.lippi.hsrecorder.iirfilterdesigner.model.FilterCoefficients;
import com.lippi.hsrecorder.pcm.PcmAudioHelper;
import com.lippi.hsrecorder.pcm.RiffHeaderData;
import com.lippi.hsrecorder.pcm.WavAudioFormat;
import com.lippi.hsrecorder.pcm.WavFileReader;
import com.lippi.hsrecorder.utils.helper.LogHelper;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by lippi on 14-12-4.
 */
public class AudioRecorder /*implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener*/{

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

    public static final int RECORDING_STOPPED = 4;

    public static final int PLAYING_STATE = 2;

    public static final int PLAYING_PAUSED_STATE = 3;

    //设置AudioRecorder每隔FrameCount帧就通知线程有数据可读
    private static final int FRAME_COUNT = 640;

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
    private LinkedList<short[]> PlayBuffer;

    //format
    private WavAudioFormat wavAudioFormat;

    // time at which latest record or play operation started
    private long mSampleStart = 0;

    // length of current sample
    private int mSampleLength = 0;

    //自适应放大倍数
    private int scale = 1;

    //the position we are playing
//    private int mFramePosition = 0;

//    private WavFileReader wavFileReader;

    private volatile int mState = IDLE_STATE;

    private ChartView chartView;

    //android built-in Visualizer
//    private Visualizer mVisualizer;

    //buffer for the filtered data
//    private SlippingBuffer filterBuffer;

    //filter param
    private int filter_order;

    //录音的缓冲区
    private short[] buffer;

    private double[] pythonA;

    private double[] pythonB;

    //X(n-k)
    private List<Short> xn_k;

    //用于数字滤波的保存输出历史值
    List<Double> yn_k;

    private RecordingThread recordingThread;

    //you can choose to play the record when recording
    private volatile boolean playWhenRecording = false;

    private static ExecutorService executors;

    private static final int AUDIO_RECORD_DATA_COMMING = 0;

    private static final int AUDIO_PLAYER_FINISHED = 1;


    private class UpdateHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AUDIO_RECORD_DATA_COMMING:
                    short[] shorts = (short[]) msg.obj;
                    chartView.updateChart(shorts);
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

    private OnDataCapturedListener dataCapturedListener;

    public void setDataCapturedListener(OnDataCapturedListener dataCapturedListener) {
        this.dataCapturedListener = dataCapturedListener;
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
        executors = Executors.newFixedThreadPool(2);
    }

    //provide a interface for callback for the main activity
    public interface OnStateChangedListener {
        public void onStateChanged(int state);

        public void onError(int error);
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
    public AudioRecorder(int sampleRate, /*SlippingBuffer filterBuffer*/
                         ChartView view) {

        this.sampleRate = sampleRate;
        chartView = view;
        setDataCapturedListener(chartView);
        /*this.filterBuffer = filterBuffer;*/
        PlayBuffer = new LinkedList<short[]>();
    }

    public void setFilterCoefs(FilterCoefficients filterCoefs) {
        filter_order = filterCoefs.getFilterOrder();
        //to plus one because of A[0],B[0]
        xn_k = new ArrayList<Short>(filter_order + 1);
        yn_k = new ArrayList<Double>(filter_order + 1);

        pythonA = filterCoefs.getACoefficients();
        pythonB = filterCoefs.getBCoefficients();

    }


/*
    private void setupVisualizerFxAndUI() {
        // Create the Visualizer object and attach it to our media player.
        mVisualizer = new Visualizer(mPlayer.getAudioSessionId());
        mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
            public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes,
                                              int samplingRate) {
                System.out.println("bytes: " + bytes.length);
                mHandler.obtainMessage(MEDIA_PLAYER_DATA_COMMING, bytes).sendToTarget();
            }

            public void onFftDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
            }
        }, Visualizer.getMaxCaptureRate() / 2, true, false);
    }
*/

    /**
     * 重新錄音前清除Xn_K
     */
    public void clearXnk() {
        xn_k.clear();
        yn_k.clear();
        for (int i = 0; i < filter_order + 1; i++) {
            xn_k.add(i, (short) 0);
            yn_k.add(i, 0D);
        }
    }

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

    public void startRecording(final String fileName) {
        stop();
        recordingThread = new RecordingThread();
        if (getState() == RECORDING_STATE) return;
        Log.d(TAG, "start recording");
        try {
            initAudioRecorder();
//            mRecordFile = new File(mSampleDir, fileName + "origin" + extension);
            mFilterFile = new File(mSampleDir, fileName + "filter" + extension);
//            dataOutputStream = new DataOutputStream(new FileOutputStream(mRecordFile));
            filterOutputStream = new DataOutputStream(new FileOutputStream(mFilterFile));
            //write 44 bytes header data for wav file
//            dataOutputStream.write(new RiffHeaderData(wavAudioFormat, 0).asByteArray());
            filterOutputStream.write(new RiffHeaderData(wavAudioFormat, 0).asByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }

        audioRecord.startRecording();
        setState(RECORDING_STATE);
        mSampleStart = System.currentTimeMillis();
        recordingThread.start();
        clearXnk();

        //开始播放
        if (playWhenRecording) startPlaying();

    }

    /**
     * this is the Recording Thread , thread read data from AudioRecord and transfer the data to FilterThread
     */
    public class RecordingThread extends Thread {

        @Override
        public void run() {

            while (AudioRecorder.this.getState() == RECORDING_STATE) {
                int sizeInShort = audioRecord.read(buffer, 0, bufferSizeInBytes / 2);
                //zoomIn and zoomOut the data
                if (sizeInShort > 0) {
                    for (int i = 0; i < sizeInShort; i++) {
                        buffer[i] *= scale;
                    }
                    //transfer data to filter Thread
                    executors.execute(new FilterTask(buffer));
                    // write origin data into file
                    /**
                    byte[] innerBuffer = new byte[buffer.length * 2];
                    //turn buffer into Little_endian byte array
                    ByteBuffer.wrap(innerBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            .put(buffer);
                    try {
                        dataOutputStream.write(innerBuffer);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.d(TAG, "unable to write data into file");
                    }**/
                }
                super.run();

            }
            //recording stopped,release and finalize audioRecord
            audioRecord.stop();
            int size;
            //finish reading audio data
            while ((size = audioRecord.read(buffer, 0, bufferSizeInBytes / 2)) > 0) {
                for (int i = 0; i < size; i++) {
                    buffer[i] *= scale;
                }
                // write origin data into file
                /**
                byte[] innerBuffer = new byte[buffer.length * 2];
                //turn buffer into Little_endian byte array
                ByteBuffer.wrap(innerBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        .put(buffer);
                try {
                    dataOutputStream.write(innerBuffer);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "unable to write data into file");
                }**/
                executors.execute(new FilterTask(buffer));

            }
            audioRecord.release();
            audioRecord = null;
            mHandler.postDelayed(new ClearTask(), 200);
            try {
                finishEncode();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
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
                //digital filter code
                for (int i = 0; i < data.length; i++) {
                    xn_k.remove(filter_order);
                    xn_k.add(0, data[i]);
                    double temp = 0;
                    for (int j = 0; j < filter_order + 1; j++) {
                        temp += xn_k.get(j) * pythonB[j];
                        temp -= yn_k.get(j) * pythonA[j];
                    }
                    yn_k.remove(filter_order);
                    yn_k.add(1, temp);
                    filterData[i] = (short) temp;
                }
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
        System.out.println("" +
                "bufferSizeInBytes" + bufferSizeInBytes);
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
        //播放线程
        if (getState() == RECORDING_STATE) {
            new Thread() {
                @Override
                public void run() {
                    audioTrack.play();
                    short[] outBuffer;
                    while (playWhenRecording) {
                        if (PlayBuffer.size() != 0) {
                            outBuffer = PlayBuffer.removeFirst();
                            audioTrack.write(outBuffer, 0, outBuffer.length);
                        }
                    }
                    audioTrack.stop();
                    //audioTrack.release();
                    // audioTrack = null;
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
//        dataOutputStream.close();
        filterOutputStream.close();
//        PcmAudioHelper.modifyRiffSizeData(mRecordFile, totalSizeInBytes);
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
                return (mCurrentPosition/sampleRate/2);
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
                String extension = oldName.substring(oldName.lastIndexOf('.'));
                File newFile = new File(mFilterFile.getParent() + "/" + name + extension);
                File newFilterFile = new File(mFilterFile.getParent() + "/" + name + "filter" + extension);
                if (!TextUtils.equals(oldName, newFile.getAbsolutePath())) {
                    if (mFilterFile.renameTo(newFile)) {
                        mFilterFile.renameTo(newFilterFile);
                        mFilterFile = newFile;
                        mFilterFile = newFilterFile;
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
            mCurrentPosition = (int) (percentage * totalSizeInBytes / 2);
            setState(PLAYING_STATE);
            new Thread(){
                @Override
                public void run() {
                    int numberToRead = 0;
                    short[] data;
                    while (mCurrentPosition < mTotalSampleLength) {
                        try {
                            //file deleted
                            if ( mState == PLAYING_PAUSED_STATE || mState == IDLE_STATE) {
                                audioTrack.stop();
                                break;
                            }
                            audioTrack.play();
                            numberToRead = mTotalSampleLength - mCurrentPosition > mPlayBufferSize ?
                                    mPlayBufferSize : 1;
                            data = mWavFileReader.getSamplesAsShorts(mCurrentPosition, mCurrentPosition + numberToRead);

                            mHandler.obtainMessage(AUDIO_RECORD_DATA_COMMING, data).sendToTarget();
                            audioTrack.write(data, 0, numberToRead);
                            mCurrentPosition += numberToRead;
                        } catch (IOException e) {
                            LogHelper.d(TAG, e, "exception happens when reading audio data");
                        }
                    }
                    //play finished,clear display and set mCurrentPosition = 0
                    if(mState == PLAYING_STATE || mState == IDLE_STATE){
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
        if(mWavFileReader == null){
            return;
        }
        setState(PLAYING_PAUSED_STATE);
        /*if (mPlayer == null) {
            return;
        }

        mPlayer.pause();
        setState(PLAYING_PAUSED_STATE);*/
    }

    public void stopPlayback() {
        if(mWavFileReader == null){
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
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        stop();
        setError(STORAGE_ACCESS_ERROR);
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        stop();
    }
**/
    public void onCompletetion() {
        stop();
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



}
