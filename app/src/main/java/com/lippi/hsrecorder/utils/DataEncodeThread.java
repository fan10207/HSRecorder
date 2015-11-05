package com.lippi.hsrecorder.utils;


import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;


import com.lippi.hsrecorder.pcm.PcmAudioHelper;
import com.lippi.hsrecorder.pcm.RiffHeaderData;
import com.lippi.hsrecorder.pcm.WavAudioFormat;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;

/**数据编码线程，默认编码为wav格式，先写入44字节的头文件，参考https://ccrma.stanford.edu/courses/422/projects/WaveFormat/
 * Audio那里设置每隔Frame_Count帧就通知注册的监听器有数据可读，
 * audioRecord.setRecordPositionUpdateListener(encodeThread, encodeThread.getHandler());
 * audioRecord.setPositionNotificationPeriod(FRAME_COUNT);
 * 最后记得要修改首部中chunkSize和subChunkSize
 * Created by lippi on 14-12-4.
 */
public class  DataEncodeThread extends Thread /*implements AudioRecord.OnRecordPositionUpdateListener*/{
    private static final String TAG = DataEncodeThread.class.getSimpleName();
    public static final int STOP_PROCESS = 1;
    private StopHandler handler;
    private SlippingBuffer slippingBuffer;
    private static File file;
    private static DataOutputStream dos;
    private byte[] buffer;
    private int bufferSize;
    private byte [] innerBuffer;
    private static int totalSize = 0;
    private WavAudioFormat wavAudioFormat;
    private CountDownLatch handlerInitLatch = new CountDownLatch(1);

    static class StopHandler extends Handler{
        WeakReference<DataEncodeThread> encodeThread;

        public StopHandler(DataEncodeThread thread){
            encodeThread = new WeakReference<DataEncodeThread>(thread);
        }

        @Override
        public void handleMessage(Message msg) {
            if(msg.what == STOP_PROCESS){
                DataEncodeThread thread = encodeThread.get();
                //process all data in SlippingBuffer and flush left data to file
                /*while(thread.processData() > 0);*/
                //finish and save file
                try {
                    finishEncode();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Cancel any event left in the queue
                removeCallbacksAndMessages(null);
                getLooper().quit();
            }
            super.handleMessage(msg);

        }
    }

    public DataEncodeThread(WavAudioFormat wavAudioFormat, SlippingBuffer slippingBuffer, File file, int bufferSize){
        this.wavAudioFormat = wavAudioFormat;
        this.slippingBuffer = slippingBuffer;
        this.file = file;
        this.bufferSize = bufferSize;
        buffer = new byte[bufferSize];
    }

    @Override
    public void run() {
        Looper.prepare();
        //attach handler to thread
        handler = new StopHandler(this);
        //write header data to file
        try {
            dos = new DataOutputStream(new FileOutputStream(file));
            dos.write(new RiffHeaderData(wavAudioFormat, 0).asByteArray());
        } catch (IOException e) {
            Log.d(TAG, "unable to write header to file");
        }
        //send message to the thread getting handler
        handlerInitLatch.countDown();
        Looper.loop();
    }

    /**
     * return the handler attach to this thread
     * @return
     */
    public Handler getHandler(){
        try {
            //等待handler初始化完成
            handlerInitLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            Log.e(TAG, "error when waiting handler to init");
        }
        return handler;
    }
/*
    @Override
    public void onMarkerReached(AudioRecord recorder) {
        //do nothing
    }*/

    /**
     * 录音到达一定Frames时主线程会调用这个方法
     * @param recorder
     */
    /*@Override
    public void onPeriodicNotification(AudioRecord recorder) {
        processData();
    }
*/
   /* private int processData() {
        int bytes = slippingBuffer.read(buffer, bufferSize);
        Log.d(TAG, "read size: " + bytes);
        if(bytes > 0){
            innerBuffer = new byte[bytes];
            //turn buffer into Little_endian
            ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).get(innerBuffer);
        }
        try {
            dos.write(innerBuffer);
            totalSize += bytes;
        } catch (IOException e) {
            Log.e(TAG, "unable to write to file");
        }
        return bytes;
    }
*/
    /**
     * update the size information of the header
     * @throws IOException
     */
    private static void finishEncode() throws IOException {
        dos.close();
        PcmAudioHelper.modifyRiffSizeData(file, totalSize);
    }
}
