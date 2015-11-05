package com.lippi.hsrecorder.utils;

import android.util.Log;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by lippi on 14-12-4.
 * 使用滑动窗口建立的缓冲区,参考了arrayBlockingQueue的实现，单队列满时写入数据会阻塞
 */
public class SlippingBuffer {
    private static final String TAG = SlippingBuffer.class.getSimpleName();
    private short[] buffer;
    private int capacity;
    private int countEachRead;
    private int readMark;
    private int writeMark;
    private ReentrantLock lock;
    private Condition notFull;
    private Condition notEmpty;

    /**
     * 阻塞队列
     * @param capacity  队列的容量
     * @param readCount 每次读取多少字节
     * @param fair  是否公平锁
     */
    public SlippingBuffer(int capacity, int readCount, boolean fair){
        this.capacity = capacity;
        this.countEachRead = readCount;
        buffer = new short[capacity];
        readMark = writeMark = 0;
        lock = new ReentrantLock(fair);
        notFull = lock.newCondition();
        notEmpty = lock.newCondition();
    }

    /**
     * 是否有足够的数据可读
     * @return
     */
    private boolean checkReadSpace(){
        int bytesLeft;
        if(writeMark > readMark){
            bytesLeft = writeMark - readMark;
        }else if(writeMark < readMark){
            bytesLeft = capacity - readMark + writeMark;
        }else {
            bytesLeft = 0;
        }
        return (bytesLeft > countEachRead);
    }

    /**
     * 剩下多少空间可写
     * @return
     */
    private boolean checkWriteSpace(){
        int bytesLeft;
        if(writeMark > readMark){
            bytesLeft = capacity - writeMark + readMark;
        }else if(writeMark < readMark){
            bytesLeft = readMark - writeMark;
        }else {
            bytesLeft = capacity;
        }

        return bytesLeft > countEachRead;
    }

    /**
     * 从缓存中读取最多bytes个字节到buffer
     * @param buffer
     * @return
     */
    public void read(short[] buffer) throws InterruptedException {
        lock.lock();
        try {
            while (!checkReadSpace()) {
                Log.d(TAG, "no enough data!");
                notEmpty.await();
            }
            for (int i = 0; i < countEachRead; i++) {
                buffer[i] = this.buffer[readMark++];
                if (readMark == capacity) readMark = 0;
            }
            notFull.signal();
        }finally {
            lock.unlock();
        }
    }

    public void write(short[] buffer) throws InterruptedException {
        lock.lock();
        try {
            while ((!checkWriteSpace())) {
                Log.e(TAG, "buffer is full, no data will be written");
                notFull.await();
            }
            for (int i = 0; i < countEachRead; i++) {
                this.buffer[writeMark++] = buffer[i];
                if (writeMark == capacity) writeMark = 0;
            }
            notEmpty.signal();
        }finally {
            lock.unlock();
        }
    }

}
