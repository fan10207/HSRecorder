package com.lippi.hsrecorder.utils;/*
package com.lippi.recorder.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Environment;
import android.util.Log;

import com.lippi.recorder.pcm.PcmAudioFormat;
import com.lippi.recorder.pcm.WavFileReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

*/
/**
 * this class use AudioTrack to synchronize audio playing with display chart
 * Created by lippi on 15-4-14.
 *//*

public class AudioTrackPlayback implements Playback {
    private static final String TAG = LogHelper.makeLogTag(AudioTrackPlayback.class);
    public static final String SAMPLE_DEFAULT_DIR = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/sound_recorder";
    public static final String extension = ".wav";
    public static final int FRAME_SIZE = 640;
    private int mState;
    private Callback mCallback;
    private AudioDataCaptured mAudioDataCaptured;
    private EnvelopDataCaptured mEnvelopDataCaptured;

    private volatile boolean mAudioNoisyReceiverRegistered;
    private volatile int mCurrentPosition;
    private volatile int mCurrentMediaId;
    private WavFileReader mWavFileReader;
    private volatile int mMediaLength;
    private int mSampleRate;
    private int numRead;
    private AudioTrack mAudioTrack;
    //     "Now playing" queue:
    private List<String> mPlayingQueue;
    private int mCurrentIndexOnQueue;


    //when volume setted too big, the audio output become noisy
    private IntentFilter mAudioNoisyIntentFilter =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);


    public void setmAudioDataCaptured(AudioDataCaptured mAudioDataCaptured) {
        this.mAudioDataCaptured = mAudioDataCaptured;
    }

    public void setmEnvelopDataCaptured(EnvelopDataCaptured mEnvelopDataCaptured) {
        this.mEnvelopDataCaptured = mEnvelopDataCaptured;
    }

    */
/**
     * interface for displaying audio data
     *//*

    interface AudioDataCaptured{
        void onAudioDataCaptured(short[] audioData);
    }


    */
/**
     * interface for displaying envelop data
     *//*

    interface EnvelopDataCaptured{
        void onEnvelopDataCaptured(short[] envelopData);
    }



    private BroadcastReceiver mAudioNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                if (isPlaying()) {
                    stop(true);
                }
            }
        }
    };

    public AudioTrackPlayback() {
        this.mState = PlaybackState.STATE_NONE;
        this.mPlayingQueue = new ArrayList<>();
    }


    */
/**
     * use delete item from the list, stop the player and reset playing queue
     * @param list  audioList
     *//*

    public void resetPlayList(List<String> list){
        stop(true);
        this.mPlayingQueue = list;
    }

    public void skipToNext() {
        LogHelper.d(TAG, "skipToNext");
        stop(true);
        mCurrentIndexOnQueue++;
        if (mPlayingQueue != null && mCurrentIndexOnQueue >= mPlayingQueue.size()) {
            // This sample's behavior: skipping to next when in last song returns to the
            // first song.
            mCurrentIndexOnQueue = 0;
        }
        start(mCurrentMediaId);
        play(0);
    }

    public void skipToPrevious() {
        LogHelper.d(TAG, "skipToPrevious");
        stop(true);
        mCurrentIndexOnQueue--;
        if (mPlayingQueue != null && mCurrentIndexOnQueue < 0) {
            // This sample's behavior: skipping to previous when in first song restarts the
            // first song.
            mCurrentIndexOnQueue = 0;
        }
        start(mCurrentMediaId);
        play(0);
    }

    public int getmSampleRate() {
        return mSampleRate;
    }

    public int getmMediaLength() {
        return mMediaLength;
    }

    @Override
    public void start(int audioId) {
        mCurrentMediaId = audioId;
        initAudioTrack(mPlayingQueue.get(audioId));
        mState = PlaybackState.STATE_PLAYING;
        mCallback.onPlaybackStatusChanged(PlaybackState.STATE_PLAYING);
        registerAudioNoisyReceiver();
        while (mCurrentPosition < mMediaLength){
            try {
                if(mState == PlaybackState.STATE_STOPPED){
                    break;
                }
                while (mState == PlaybackState.STATE_PAUSED) ;
                int numberToRead = mMediaLength - mCurrentPosition > numRead ? numRead : mMediaLength - mCurrentPosition;
                short[] data = mWavFileReader.getSamplesAsShorts(mCurrentPosition, mCurrentPosition + numberToRead);

                mAudioDataCaptured.onAudioDataCaptured(data);
                mAudioTrack.write(data, 0, numberToRead);
                mCurrentPosition += numRead;
            } catch (IOException e) {
                LogHelper.d(TAG, e, "exception happens when reading audio data");
            }
        }
    }

    //init WavFileReader and AudioTrack according to the encoding of the wav file
    private void initAudioTrack(String fileName){
        try {
            mWavFileReader = new WavFileReader(SAMPLE_DEFAULT_DIR + fileName + extension);
            PcmAudioFormat format = mWavFileReader.getFormat();
            int channels = format.getChannels();
            mMediaLength = mWavFileReader.getSampleCount();
            mSampleRate = format.getSampleRate();
            numRead = 1024 * channels ;
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, format.getSampleRate(),
                    channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT, FRAME_SIZE * channels * 2 , AudioTrack.MODE_STREAM);
            if (mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                LogHelper.d(TAG, "audioTrack initialized");
            } else {
                Log.d(TAG, "audioTrack init failed");
                mAudioTrack.release();
                mAudioTrack = null;
                stop(false);
            }
            mMediaLength = mWavFileReader.getSampleCount();
        } catch (IOException e) {
            LogHelper.d(TAG, e, "unable to read wav file");
        }


    }
    @Override
    public void stop(boolean notifyListeners) {
        unregisterAudioNoisyReceiver();
        mState = PlaybackState.STATE_STOPPED;
    }

    @Override
    public void setState(int state) {
        mState = state;
    }

    @Override
    public int getState() {
        return mState;
    }

    @Override
    public boolean isPlaying() {
        return PlaybackState.STATE_PLAYING == mState;
    }

    @Override
    public int getCurrentStreamPosition() {
        return mCurrentPosition;
    }

    @Override
    public void setCurrentStreamPosition(int pos) {
        this.mCurrentPosition = pos;

    }

    @Override
    public void play(int path) {
        registerAudioNoisyReceiver();
        mState = PlaybackState.STATE_PLAYING;
    }

    @Override
    public void pause() {
        unregisterAudioNoisyReceiver();
        mState = PlaybackState.STATE_PAUSED;
    }

    @Override
    public void seekTo(int position) {
        mCurrentPosition = position;
    }

    @Override
    public void setCurrentMediaId(int mediaId) {
        this.mCurrentMediaId = mediaId;
    }

    @Override
    public int getCurrentMediaId() {
        return mCurrentMediaId;
    }

    @Override
    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    private void registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
//            registerReceiver(mAudioNoisyReceiver, mAudioNoisyIntentFilter);
            mAudioNoisyReceiverRegistered = true;
        }
    }

    private void unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
//            mService.unregisterReceiver(mAudioNoisyReceiver);
            mAudioNoisyReceiverRegistered = false;
        }
    }
}
*/
