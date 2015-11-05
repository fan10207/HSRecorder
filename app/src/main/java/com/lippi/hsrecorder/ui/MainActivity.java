package com.lippi.hsrecorder.ui;


import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.util.FloatMath;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ikimuhendis.ldrawer.ActionBarDrawerToggle;
import com.ikimuhendis.ldrawer.DrawerArrowDrawable;
import com.lippi.hsrecorder.R;
import com.lippi.hsrecorder.dialogplus.DialogPlus;
import com.lippi.hsrecorder.dialogplus.OnDismissListener;
import com.lippi.hsrecorder.dialogplus.OnItemClickListener;
import com.lippi.hsrecorder.dialogplus.ViewHolder;
import com.lippi.hsrecorder.iirfilterdesigner.designers.AbstractIIRDesigner;
import com.lippi.hsrecorder.iirfilterdesigner.designers.Chebyshev1IIRDesigner;
import com.lippi.hsrecorder.iirfilterdesigner.exceptions.BadFilterParametersException;
import com.lippi.hsrecorder.iirfilterdesigner.model.FilterCoefficients;
import com.lippi.hsrecorder.iirfilterdesigner.model.FilterType;
import com.lippi.hsrecorder.utils.AudioRecorder;
import com.lippi.hsrecorder.utils.ChartView;
import com.lippi.hsrecorder.utils.RecordNameEditText;
import com.lippi.hsrecorder.utils.blueeffect.Blur;
import com.lippi.hsrecorder.utils.helper.LogHelper;

import java.util.Arrays;
import java.util.HashSet;

import roboguice.activity.RoboActivity;
import roboguice.inject.InjectView;


/**
 * Created by lippi on 14-12-3.
 */
public class MainActivity extends RoboActivity implements View.OnClickListener, AudioRecorder.OnStateChangedListener {
    private static final String TAG = LogHelper.makeLogTag(MainActivity.class);

    private static final int SEEK_BAR_MAX = 100000;
    private static final String BLURRED_IMG_PATH = "blurred_image.png";
    //录音的时候接到电话被打断
    private boolean mSampleInterrupted = false;
    // Some error messages are displayed
    private String mErrorUiMessage = null;
    //播放进度条的时间格式
    private String mTimerFormat;

    private int mSampleRate = 8000;

    private AudioRecorder mAudioRecorder;

    private HashSet<String> mSavedRecord;

    private long mLastClickTime;

    private int mLastButtonId;

    @InjectView(R.id.drawer_layout)
    private DrawerLayout mDrawerLayout;
    //  左边侧栏的设置菜单
    @InjectView(R.id.navdrawer)
    private ListView mDrawerList;
    //实现抽屉效果
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerArrowDrawable drawerArrow;

    private PopupWindow popupWindow;

    private static final Integer[] SAMPLE_RATES = new Integer[]{8000, 11025, 16000, 22050, 44100};

    private static final String[] FILTER_TYPES = new String[]{"低通滤波器", "高通滤波器", "带通滤波器", "带阻滤波器"};

    private final Handler mHandler = new Handler();

    private Runnable mUpdateTimer = new Runnable() {
        public void run() {
            updateTimerView();
        }
    };

    private Runnable mUpdateSeekBar = new Runnable() {
        @Override
        public void run() {
            updateSeekBar();
        }
    };

    @InjectView(R.id.newButton)
    private ImageButton mNewButton;

    @InjectView(R.id.recordButton)
    private ImageButton mRecordButton;

    @InjectView(R.id.stopButton)
    private ImageButton mStopButton;

    @InjectView(R.id.playButton)
    private ImageButton mPlayButton;

    @InjectView(R.id.pauseButton)
    private ImageButton mPauseButton;

    @InjectView(R.id.deleteButton)
    private ImageButton mDeleteButton;

    @InjectView(R.id.file_name)
    private RecordNameEditText mFileNameEditText;

    @InjectView(R.id.time_calculator)
    private LinearLayout mTimerLayout;

    @InjectView(R.id.visualizer_view)
    private ChartView chartView;

    @InjectView(R.id.play_seek_bar_layout)
    private LinearLayout mSeekBarLayout;

    @InjectView(R.id.starttime)
    private TextView mStartTime;

    @InjectView(R.id.totaltime)
    private TextView mTotalTime;

    @InjectView(R.id.play_seek_bar)
    private SeekBar mPlaySeekBar;

    private BroadcastReceiver mSDCardMountEventReceiver = null;

    private boolean mDrawerOpened = false;

    private volatile boolean mRecorderParaChanged = false;

    private volatile boolean mFilterParaChanged = false;

    private AbstractIIRDesigner mIirDesigner = new Chebyshev1IIRDesigner();

    private int mFilterType = 0;
    private int mPassFreq = 800;
    private int mStopFreq = 1600;
    private double mPassWave = 0.5;
    private double mStopDec = 50;
    private FilterCoefficients coefficients;
    @InjectView(R.id.layout)
    private LinearLayout layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.drawer_layout);
        ActionBar ab = getActionBar();
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setHomeButtonEnabled(true);
        initDrawer();
//        layout = (LinearLayout) this.findViewById(R.id.layout);
        setBackground(layout, R.drawable.background_03);

//        chartView = (ChartView) findViewById(R.id.visualizer_view);
        chartView.setOnTouchListener(new TwoFingerTouchListener());
        mAudioRecorder = new AudioRecorder(mSampleRate, /*recordingBuffer*/ chartView);
        mAudioRecorder.setOnStateChangedListener(this);
        mSavedRecord = new HashSet<String>();
        initResourceRefs();
        registerExternalStorageListener();
        //set the  mediaplater Volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }


    private void initDrawer() {
//        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
//        mDrawerList = (ListView) findViewById(R.id.navdrawer);

        drawerArrow = new DrawerArrowDrawable(this) {
            @Override
            public boolean isLayoutRtl() {
                return false;
            }
        };
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                drawerArrow, R.string.drawer_open,
                R.string.drawer_close) {

            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                mDrawerOpened = false;
                invalidateOptionsMenu();
            }

            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu();
                mDrawerOpened = true;
                mAudioRecorder.stop();
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();

        String[] values = new String[]{
                "录音采样率",
                "滤波器类型",
                "通带截止频率",
                "阻带截止频率",
                "通带最大衰减(db)",
                "阻带最小衰减(db)"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                R.layout.slide_list_item, R.id.textView, values);
        mDrawerList.setAdapter(adapter);


        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TextView title = new TextView(MainActivity.this);
                title.setTextSize(25);
                title.setTextColor(getResources().getColor(R.color.black));

                final EditText editText = (EditText) LayoutInflater.from(MainActivity.this).inflate(R.layout.edit_text_layout, null).findViewById(R.id.edit_text);
                switch (position) {

                    case 0:
                        ArrayAdapter<Integer> sampleRate_adapter = new ArrayAdapter<Integer>(MainActivity.this,
                                R.layout.dialog_list_item, R.id.textView1, SAMPLE_RATES);
                        title.setText("选择录音采样率：");
                        final DialogPlus dialog = new DialogPlus.Builder(MainActivity.this)
                                .setAdapter(sampleRate_adapter)
                                .setCancelable(true)
                                .setGravity(DialogPlus.Gravity.CENTER)
                                .setMargins(50, 0, 150, 0)
                                .setHeader(title)
                                .setBackgroundColorResourceId(R.color.pink)
                                .setOnItemClickListener(new OnItemClickListener() {
                                    @Override
                                    public void onItemClick(DialogPlus dialogPlus, Object item, View view, int position) {
                                        int new_sampleRate = (Integer) item;
                                        if (mSampleRate != new_sampleRate) {
                                            mSampleRate = (Integer) item;
                                            onRecorderParasChanged();
                                        }
                                        dialogPlus.dismiss();
                                    }
                                }).create();
                        dialog.show();

                        break;
                    case 1:
                        title.setText("选择滤波器类型：");
                        ArrayAdapter<String> filter_adapter = new ArrayAdapter<String>(MainActivity.this,
                                R.layout.dialog_list_item, R.id.textView1, FILTER_TYPES);

                        final DialogPlus filter_dialog = new DialogPlus.Builder(MainActivity.this)
                                .setAdapter(filter_adapter)
                                .setCancelable(true)
                                .setGravity(DialogPlus.Gravity.CENTER)
                                .setMargins(50, 0, 150, 0)
                                .setHeader(title)
                                .setBackgroundColorResourceId(R.color.pink)
                                .setOnItemClickListener(new OnItemClickListener() {
                                    @Override
                                    public void onItemClick(DialogPlus dialogPlus, Object item, View view, int position) {
                                        if (mFilterType != (position - 1)) {
                                            mFilterType = position - 1;
                                            onFilterParasChanged();
                                            System.out.println("filter position:" + position);
                                        }
                                        dialogPlus.dismiss();
                                    }
                                }).create();
                        filter_dialog.show();

                        break;

                    case 2:
                        title.setText("设置通带截止频率");
                        editText.setText(mPassFreq + "");
                        editText.setHint("输入截止频率:");
                        ViewHolder holder = new ViewHolder(editText);
                        final DialogPlus dialogPlus = new DialogPlus.Builder(MainActivity.this)
                                .setContentHolder(holder)
                                .setCancelable(true)
                                .setGravity(DialogPlus.Gravity.CENTER)
                                .setMargins(50, 0, 150, 0)
//                                .setHeader(title)
//                                .setFooter(R.layout.fancy_button)
                                .setBackgroundColorResourceId(R.color.pink)
                                .setOnDismissListener(new OnDismissListener() {
                                    @Override
                                    public void onDismiss(DialogPlus dialog) {
                                        String text = editText.getText() + "";
                                        if (!TextUtils.isEmpty(text)) {
                                            int freq = Integer.parseInt(text);
                                            if (mPassFreq != freq) {
                                                mPassFreq = freq;
                                                Log.d(TAG, "pass sample: " + mPassFreq);
                                                onFilterParasChanged();
                                            }
                                        }
                                    }
                                }).create();
                        dialogPlus.show();
                        break;
                    case 3:
                        title.setText("设置阻带截止频率");
                        editText.setText(mStopFreq + "");
                        editText.setHint("输入截止频率:");
                        ViewHolder holder2 = new ViewHolder(editText);
                        final DialogPlus dialogPlus2 = new DialogPlus.Builder(MainActivity.this)
                                .setContentHolder(holder2)
                                .setCancelable(true)
                                .setGravity(DialogPlus.Gravity.CENTER)
                                .setMargins(50, 0, 150, 0)
//                                .setHeader(title)
//                                .setFooter(R.layout.fancy_button)
                                .setBackgroundColorResourceId(R.color.pink)
                                .setOnDismissListener(new OnDismissListener() {
                                    @Override
                                    public void onDismiss(DialogPlus dialog) {
                                        String text = editText.getText() + "";
                                        if (!TextUtils.isEmpty(text)) {
                                            int freq = Integer.parseInt(text);
                                            if (mStopFreq != freq) {
                                                mStopFreq = freq;
                                                Log.d(TAG, "stop sample: " + mStopFreq);
                                                onFilterParasChanged();
                                            }
                                        }
                                    }
                                }).create();
                        dialogPlus2.show();
                        break;
                    case 4:
                        title.setText("设置通带最大衰减");
                        editText.setText(mPassWave + "");
                        editText.setHint("输入通带纹波:");
                        ViewHolder holder3 = new ViewHolder(editText);
                        final DialogPlus dialogPlus3 = new DialogPlus.Builder(MainActivity.this)
                                .setContentHolder(holder3)
                                .setCancelable(true)
                                .setGravity(DialogPlus.Gravity.CENTER)
                                .setMargins(50, 0, 150, 0)
//                                .setHeader(title)
//                                .setFooter(R.layout.fancy_button)
                                .setBackgroundColorResourceId(R.color.pink)
                                .setOnDismissListener(new OnDismissListener() {
                                    @Override
                                    public void onDismiss(DialogPlus dialog) {
                                        String text = editText.getText() + "";
                                        if (!TextUtils.isEmpty(text)) {
                                            double passWave = Double.parseDouble(text);
                                            if (mPassWave != passWave) {
                                                mPassWave = passWave;
                                                Log.d(TAG, "passWave: " + mStopFreq);
                                                onFilterParasChanged();
                                            }
                                        }
                                    }
                                }).create();
                        dialogPlus3.show();
                        break;

                    case 5:
                        title.setText("设置阻带最小衰减");
                        editText.setText(mStopDec + "");
                        editText.setHint("输入阻带衰减增益:");
                        ViewHolder holder4 = new ViewHolder(editText);
                        final DialogPlus dialogPlus4 = new DialogPlus.Builder(MainActivity.this)
                                .setContentHolder(holder4)
                                .setCancelable(true)
                                .setGravity(DialogPlus.Gravity.CENTER)
                                .setMargins(50, 0, 150, 0)
//                                .setHeader(title)
//                                .setFooter(R.layout.fancy_button)
                                .setBackgroundColorResourceId(R.color.pink)
                                .setOnDismissListener(new OnDismissListener() {
                                    @Override
                                    public void onDismiss(DialogPlus dialog) {
                                        String text = editText.getText() + "";
                                        if (!TextUtils.isEmpty(text)) {
                                            double stopDec = Double.parseDouble(text);
                                            if (mStopDec != stopDec) {
                                                mStopDec = stopDec;
                                                Log.d(TAG, "stop sample: " + mStopFreq);
                                                onFilterParasChanged();
                                            }
                                        }
                                    }
                                }).create();
                        dialogPlus4.show();
                        break;


                    default:
                        break;
                }
            }
        });


    }

    private void initResourceRefs() {
//        mNewButton = (ImageButton) findViewById(R.id.newButton);
//        mRecordButton = (ImageButton) findViewById(R.id.recordButton);
//        mStopButton = (ImageButton) findViewById(R.id.stopButton);
//        mPlayButton = (ImageButton) findViewById(R.id.playButton);
//        mPauseButton = (ImageButton) findViewById(R.id.pauseButton);
//        mDeleteButton = (ImageButton) findViewById(R.id.deleteButton);
        CheckBox record_listening_switch = (CheckBox) findViewById(R.id.record_play_switch);
        mNewButton.setOnClickListener(this);
        mRecordButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mPlayButton.setOnClickListener(this);
        mPauseButton.setOnClickListener(this);
        mDeleteButton.setOnClickListener(this);
        record_listening_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mAudioRecorder.startPlaying();
                } else {
                    mAudioRecorder.stopPlaying();
                }
            }
        });

//        mFileNameEditText = (RecordNameEditText) findViewById(R.id.file_name);
        resetFileNameEditText();
        mFileNameEditText.setNameChangeListener(new RecordNameEditText.OnNameChangeListener() {
            @Override
            public void onNameChanged(String name) {
                if (!TextUtils.isEmpty(name)) {
                    mAudioRecorder.renameSampleFile(name);
                }
            }
        });

//        mTimerLayout = (LinearLayout) findViewById(R.id.time_calculator);
//        mSeekBarLayout = (LinearLayout) findViewById(R.id.play_seek_bar_layout);
//        mStartTime = (TextView) findViewById(R.id.starttime);
//        mTotalTime = (TextView) findViewById(R.id.totaltime);
//        mPlaySeekBar = (SeekBar) findViewById(R.id.play_seek_bar);
        mPlaySeekBar.setMax(SEEK_BAR_MAX);

        mPlaySeekBar.setOnSeekBarChangeListener(mSeekBarChangeListener);

        mTimerFormat = getResources().getString(R.string.timer_format);
        mLastClickTime = 0;
        mLastButtonId = 0;

       /* // Try to find the blurred image
        final File blurredImage = new File(getFilesDir() + BLURRED_IMG_PATH);
        final int screenWidth = ImageUtils.getScreenWidth(this);
        if (!blurredImage.exists()) {

            // launch the progressbar in ActionBar
            setProgressBarIndeterminateVisibility(true);

            new Thread(new Runnable() {

                @Override
                public void run() {

                    // No image found => let's generate it!
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    Bitmap image = BitmapFactory.decodeResource(getResources(), R.drawable.image, options);
                    Bitmap newImg = Blur.fastblur(MainActivity.this, image, 12);
                    ImageUtils.storeImage(newImg, blurredImage);
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            updateView(screenWidth);

                            // And finally stop the progressbar
                            setProgressBarIndeterminateVisibility(false);
                        }
                    });

                }
            }).start();

        } else {

            // The image has been found. Let's update the view
            updateView(screenWidth);

        }*/

    }

    /**
     * 显示修改文件名对话框
     */
    private void resetFileNameEditText() {

        // for audio which is used for mms, we can only use english file name
        // mShowFinishButon indicates whether this is an audio for mms
        mFileNameEditText.initFileName(mAudioRecorder.getRecordDir(), AudioRecorder.extension, true);
    }

    /*
    * Make sure we're not recording music playing in the background, ask the
    * MediaPlaybackService to pause playback.
    */
    private void stopAudioPlayback() {
        // Shamelessly copied from MediaPlaybackService.java, which
        // should be public, but isn't.
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        sendBroadcast(i);
    }


    /*
   * Handle the "back" hardware key.
   */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {


        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mDrawerOpened) {
                return false;
            } else {
                switch (mAudioRecorder.getState()) {
                    case AudioRecorder.IDLE_STATE:
                    case AudioRecorder.PLAYING_PAUSED_STATE:
                        if (mAudioRecorder.sampleLength() > 0)
                            saveSample();
                        finish();
                        break;
                    case AudioRecorder.PLAYING_STATE:
                        mAudioRecorder.stop();
                        saveSample();
                        break;
                    case AudioRecorder.RECORDING_STATE:
                        mAudioRecorder.clear();
                        finish();
                        break;
                }
                return true;
            }
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            coefficients = mIirDesigner.designDigitalFilter(mSampleRate, FilterType.LOWPASS, new double[]{mPassFreq},
                    new double[]{mStopFreq}, mPassWave, mStopDec);
            System.out.println("filter order: " + coefficients.getFilterOrder());
            mAudioRecorder.setFilterCoefs(coefficients);
            chartView.setmSampleRate(mSampleRate);
        } catch (BadFilterParametersException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //这里设置录音参数和滤波器参数发生变化后相应逻辑
        if (mFilterParaChanged) {
            try {
                switch (mFilterType) {
                    case 0:
                        coefficients = mIirDesigner.designDigitalFilter(mSampleRate, FilterType.LOWPASS, new double[]{mPassFreq},
                                new double[]{2 * mPassFreq}, mPassWave, mStopDec);
                        break;
                    case 1:
                        coefficients = mIirDesigner.designDigitalFilter(mSampleRate, FilterType.HIGHPASS, new double[]{mPassFreq},
                                new double[]{2 * mPassFreq}, mPassWave, mStopDec);
                        break;
                    case 2:
                        coefficients = mIirDesigner.designDigitalFilter(mSampleRate, FilterType.BANDPASS, new double[]{mPassFreq, mStopFreq},
                                new double[]{0.5 * mPassFreq, 2 * mStopFreq}, mPassWave, mStopDec);
                        break;
                    case 3:
                        coefficients = mIirDesigner.designDigitalFilter(mSampleRate, FilterType.BANDSTOP, new double[]{0.5 * mPassFreq, 2 * mStopFreq},
                                new double[]{mPassFreq, mStopFreq}, mPassWave, mStopDec);
                        break;
                    default:
                        break;
                }

                System.out.println("filter order: " + coefficients.getFilterOrder() + "order, filter A paras" +
                        Arrays.toString(coefficients.getACoefficients()) + "filter B paras" + Arrays.toString(coefficients.getBCoefficients()));
                Toast.makeText(this, "数字滤波器为" + coefficients.getFilterOrder() + "阶", Toast.LENGTH_SHORT).show();
                mAudioRecorder.setFilterCoefs(coefficients);
            } catch (BadFilterParametersException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), "滤波器参数设置错误，请重新设置", Toast.LENGTH_LONG).show();
            }

        }

        if (mRecorderParaChanged) {
            mAudioRecorder.setSampleRate(mSampleRate);
            mAudioRecorder.reset();
            mAudioRecorder.setFilterCoefs(coefficients);
            resetFileNameEditText();
        }

        updateUI();

    }


    @Override
    protected void onPause() {
        if (mAudioRecorder.getState() != AudioRecorder.RECORDING_STATE) {
            mAudioRecorder.stop();
            saveSample();
            mFileNameEditText.clearFocus();
        }

        super.onPause();
    }


    /*
     * Called on destroy to unregister the SD card mount event receiver.
     */
    @Override
    public void onDestroy() {
        if (mSDCardMountEventReceiver != null) {
            unregisterReceiver(mSDCardMountEventReceiver);
            mSDCardMountEventReceiver = null;
        }

        super.onDestroy();
    }

    /*
    * Handle the buttons.
    */
    public void onClick(View button) {
        if (System.currentTimeMillis() - mLastClickTime < 300) {
            // in order to avoid user click bottom too quickly
            return;
        }


        if (button.getId() == mLastButtonId && button.getId() != R.id.newButton) {
            // as the recorder state is async with the UI
            // we need to avoid launching the duplicated action
            return;
        }

        if (button.getId() == R.id.stopButton && System.currentTimeMillis() - mLastClickTime < 1500) {
            // it seems that the media recorder is not robust enough
            // sometime it crashes when stop recording right after starting
            return;
        }

        mLastClickTime = System.currentTimeMillis();
        mLastButtonId = button.getId();

        switch (button.getId()) {
            case R.id.newButton:
                mFileNameEditText.clearFocus();
                resetFileNameEditText();
                backToRecordUI();
                break;
            case R.id.recordButton:
                showOverwriteConfirmDialogIfConflicts();
                break;
            case R.id.stopButton:
                mAudioRecorder.stop();
//                mAudioChart.stop();
//                updateUI();
                break;
            case R.id.playButton:
                mAudioRecorder.startPlayback(mAudioRecorder.playProgress());
                break;
            case R.id.pauseButton:
                mAudioRecorder.pausePlayback();
                break;
            case R.id.deleteButton:
                showDeleteConfirmDialog();
                break;
        }
    }

    private void showOverwriteConfirmDialogIfConflicts() {

        String fileName = mFileNameEditText.getText().toString() + ".wav";

        if (mAudioRecorder.isRecordExisted(fileName)) {
            // file already existed and it's not a recording request from other
            // app
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
            dialogBuilder.setIcon(android.R.drawable.ic_dialog_alert);
            dialogBuilder.setTitle(getString(R.string.overwrite_dialog_title, fileName));
            dialogBuilder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startRecording();
                        }
                    });
            dialogBuilder.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mLastButtonId = 0;
                        }
                    });
            dialogBuilder.show();
        } else {
            startRecording();
        }
    }

    private void showDeleteConfirmDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setIcon(android.R.drawable.ic_dialog_alert);
        dialogBuilder.setTitle(R.string.delete_dialog_title);
        dialogBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mAudioRecorder.delete();
            }
        });
        dialogBuilder.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLastButtonId = 0;
                    }
                });
        dialogBuilder.show();
    }

    /*
   * If we have just recorded a sample, this adds it to the media data base
   * and sets the result to the sample's URI.
   */
    private void saveSample() {
        if (mAudioRecorder.sampleLength() == 0)
            return;
        if (!mSavedRecord.contains(mAudioRecorder.sampleFile().getAbsolutePath())) {
            mSavedRecord.add(mAudioRecorder.sampleFile().getAbsolutePath());
        }
    }


    private void startRecording() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            mSampleInterrupted = true;
            mErrorUiMessage = getResources().getString(R.string.insert_sd_card);
            updateUI();
        } else {
            stopAudioPlayback();
            //处理用户偏好设置
            mAudioRecorder.startRecording(mFileNameEditText.getText().toString());
            /*mAudioChart.update();*/
        }
    }

    /*
    * Registers an intent to listen for
    * ACTION_MEDIA_EJECT/ACTION_MEDIA_UNMOUNTED/ACTION_MEDIA_MOUNTED
    * notifications.
    */
    private void registerExternalStorageListener() {
        if (mSDCardMountEventReceiver == null) {
            mSDCardMountEventReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    mSampleInterrupted = false;
                    resetFileNameEditText();
                    updateUI();
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mSDCardMountEventReceiver, iFilter);
        }
    }

    private ImageView getTimerImage(char number) {
        ImageView image = new ImageView(this);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (number != ':') {
            image.setBackgroundResource(R.drawable.background_number);
        }
        switch (number) {
            case '0':
                image.setImageResource(R.drawable.number_0);
                break;
            case '1':
                image.setImageResource(R.drawable.number_1);
                break;
            case '2':
                image.setImageResource(R.drawable.number_2);
                break;
            case '3':
                image.setImageResource(R.drawable.number_3);
                break;
            case '4':
                image.setImageResource(R.drawable.number_4);
                break;
            case '5':
                image.setImageResource(R.drawable.number_5);
                break;
            case '6':
                image.setImageResource(R.drawable.number_6);
                break;
            case '7':
                image.setImageResource(R.drawable.number_7);
                break;
            case '8':
                image.setImageResource(R.drawable.number_8);
                break;
            case '9':
                image.setImageResource(R.drawable.number_9);
                break;
            case ':':
                image.setImageResource(R.drawable.colon);
                break;
        }
        image.setLayoutParams(lp);
        return image;
    }

    /**
     * Update the big MM:SS timer. If we are in playback, also update the
     * progress bar.
     */
    private void updateTimerView() {
        int state = mAudioRecorder.getState();

        boolean ongoing = (state == AudioRecorder.RECORDING_STATE || state == AudioRecorder.PLAYING_STATE);

        long time = mAudioRecorder.progress();
        String timeStr = String.format(mTimerFormat, time / 60, time % 60);
        mTimerLayout.removeAllViews();
        for (int i = 0; i < timeStr.length(); i++) {
            mTimerLayout.addView(getTimerImage(timeStr.charAt(i)));
        }

        mHandler.postDelayed(mUpdateTimer, 500);

    }

    private void setTimerView(float progress) {
        long time = (long) (progress * mAudioRecorder.sampleLength());
        String timeStr = String.format(mTimerFormat, time / 60, time % 60);
        mTimerLayout.removeAllViews();
        for (int i = 0; i < timeStr.length(); i++) {
            mTimerLayout.addView(getTimerImage(timeStr.charAt(i)));
        }
    }

    private void updateSeekBar() {
        mPlaySeekBar.setProgress((int) (mAudioRecorder.playProgress() * mPlaySeekBar.getMax()));
        if (mAudioRecorder.getState() == AudioRecorder.PLAYING_STATE) {
            mHandler.postDelayed(mUpdateSeekBar, 10);
        }
    }

    /**
     * 在录音播放界面按下添加按钮后返回录音界面
     */
    private void backToRecordUI() {
        mNewButton.setEnabled(true);
        mNewButton.setVisibility(View.VISIBLE);
        mRecordButton.setVisibility(View.VISIBLE);
        mStopButton.setVisibility(View.GONE);
        mPlayButton.setVisibility(View.GONE);
        mPauseButton.setVisibility(View.GONE);
        mDeleteButton.setEnabled(true);
        mRecordButton.requestFocus();

        mSeekBarLayout.setVisibility(View.GONE);
    }

    /**
     * Shows/hides the appropriate child views for the new state.
     */
    private void updateUI() {
        switch (mAudioRecorder.getState()) {
            case AudioRecorder.IDLE_STATE:
                mLastButtonId = 0;
            case AudioRecorder.PLAYING_PAUSED_STATE:
                if (mAudioRecorder.sampleLength() == 0) {
                    mNewButton.setEnabled(true);
                    mNewButton.setVisibility(View.VISIBLE);
                    mRecordButton.setVisibility(View.VISIBLE);
                    mStopButton.setVisibility(View.GONE);
                    mPlayButton.setVisibility(View.GONE);
                    mPauseButton.setVisibility(View.GONE);
                    mDeleteButton.setEnabled(false);
                    mRecordButton.requestFocus();

                    mSeekBarLayout.setVisibility(View.GONE);
                } else {
                    mNewButton.setEnabled(true);
                    mNewButton.setVisibility(View.VISIBLE);
                    mRecordButton.setVisibility(View.GONE);
                    mStopButton.setVisibility(View.GONE);
                    mPlayButton.setVisibility(View.VISIBLE);
                    mPauseButton.setVisibility(View.GONE);
                    mDeleteButton.setEnabled(true);
                    mPauseButton.requestFocus();

                    mSeekBarLayout.setVisibility(View.VISIBLE);
                    mStartTime.setText(String.format(mTimerFormat, 0, 0));
                    mTotalTime.setText(String.format(mTimerFormat, mAudioRecorder.sampleLength() / 60,
                            mAudioRecorder.sampleLength() % 60));
                }
                mFileNameEditText.setEnabled(true);
                mFileNameEditText.clearFocus();

                if (mAudioRecorder.sampleLength() > 0) {
                    mPlaySeekBar.setProgress(0);
                }

                // we allow only one toast at one time
                if (mSampleInterrupted && mErrorUiMessage == null) {
                    Toast.makeText(this, R.string.recording_stopped, Toast.LENGTH_SHORT).show();
                }

                if (mErrorUiMessage != null) {
                    Toast.makeText(this, mErrorUiMessage, Toast.LENGTH_SHORT).show();
                }

                break;
            case AudioRecorder.RECORDING_STATE:
                mNewButton.setEnabled(false);
                mNewButton.setVisibility(View.VISIBLE);
                mRecordButton.setVisibility(View.GONE);
                mStopButton.setVisibility(View.VISIBLE);
                mPlayButton.setVisibility(View.GONE);
                mPauseButton.setVisibility(View.GONE);
                mDeleteButton.setEnabled(false);
                mStopButton.requestFocus();

                mSeekBarLayout.setVisibility(View.GONE);

                mFileNameEditText.setEnabled(false);

                break;
            case AudioRecorder.RECORDING_STOPPED:
                mNewButton.setEnabled(true);
                mNewButton.setVisibility(View.VISIBLE);
                mRecordButton.setVisibility(View.GONE);
                mStopButton.setVisibility(View.GONE);
                mPlayButton.setVisibility(View.VISIBLE);
                mPlayButton.setEnabled(true);
                mPlayButton.requestFocus();
                mPauseButton.setVisibility(View.GONE);
                mDeleteButton.setEnabled(true);

                mSeekBarLayout.setVisibility(View.VISIBLE);
//                mPlaySeekBar.setMax(mAudioRecorder.sampleLength() * SEEK_BAR_MAX);
                mStartTime.setText(String.format(mTimerFormat, 0, 0));
                mTotalTime.setText(String.format(mTimerFormat, mAudioRecorder.sampleLength() / 60,
                        mAudioRecorder.sampleLength() % 60));
                mFileNameEditText.setEnabled(false);

                break;
            case AudioRecorder.PLAYING_STATE:
                mNewButton.setEnabled(false);
                mNewButton.setVisibility(View.VISIBLE);
                mRecordButton.setVisibility(View.GONE);
                mStopButton.setVisibility(View.GONE);
                mPlayButton.setVisibility(View.GONE);
                mPauseButton.setVisibility(View.VISIBLE);
                mDeleteButton.setEnabled(false);
                mPauseButton.requestFocus();

                mSeekBarLayout.setVisibility(View.VISIBLE);

                mFileNameEditText.setEnabled(false);

                break;
        }

        updateTimerView();
        updateSeekBar();

    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateUI();
        mDrawerToggle.onConfigurationChanged(newConfig);
    }


    @Override
    public void onStateChanged(int state) {
        if (state == AudioRecorder.RECORDING_STATE) {
            mSampleInterrupted = false;
            mErrorUiMessage = null;
        } else if (state == AudioRecorder.PLAYING_STATE) {
            mSampleInterrupted = false;
            mErrorUiMessage = null;
        }

        updateUI();
    }

    @Override
    public void onError(int error) {
        Resources res = getResources();

        String message = null;
        switch (error) {
            case AudioRecorder.STORAGE_ACCESS_ERROR:
                message = res.getString(R.string.error_sdcard_access);
                break;
            case AudioRecorder.IN_CALL_RECORD_ERROR:
                // TODO: update error message to reflect that the recording
                // could not be
                // performed during a call.
            case AudioRecorder.INTERNAL_ERROR:
                message = res.getString(R.string.error_app_internal);
                break;
        }
        if (message != null) {
            new AlertDialog.Builder(this).setTitle(R.string.app_name).setMessage(message)
                    .setPositiveButton(R.string.button_ok, null).setCancelable(false).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mAudioRecorder.getState() == AudioRecorder.RECORDING_STATE
                || mAudioRecorder.getState() == AudioRecorder.PLAYING_STATE) {
            return false;
        } else {
            getMenuInflater().inflate(R.menu.main_manu, menu);
            return true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case android.R.id.home:
                if (mDrawerLayout.isDrawerOpen(mDrawerList)) {
                    mDrawerLayout.closeDrawer(mDrawerList);
                } else {
                    mDrawerLayout.openDrawer(mDrawerList);
                }
                break;
            case R.id.menu_record_list:
                intent = new Intent(this, AudioListActivity.class);
                startActivity(intent);
                break;
            /*case R.id.menu_setting:
                intent = new Intent(this, AudioRecorderPreferenceActivity.class);
                startActivity(intent);
                break;*/
            case R.id.menu_exit:
                switch (mAudioRecorder.getState()) {
                    case AudioRecorder.IDLE_STATE:
                    case AudioRecorder.PLAYING_PAUSED_STATE:
                        if (mAudioRecorder.sampleLength() > 0)
                            saveSample();
                        finish();
                        break;
                    case AudioRecorder.PLAYING_STATE:
                        mAudioRecorder.stop();
                        saveSample();
                        break;
                    case AudioRecorder.RECORDING_STATE:
                        mAudioRecorder.clear();
                        finish();
                        break;
                }
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    private SeekBar.OnSeekBarChangeListener mSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mAudioRecorder.startPlayback((float) seekBar.getProgress() / seekBar.getMax());
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mAudioRecorder.pausePlayback();
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }
    };

    /**
     * calculate the distance between two fingers
     *
     * @param event
     * @return
     */
    private float Spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);

        return (float) Math.sqrt(x * x + y * y);
    }

    private class TwoFingerTouchListener implements View.OnTouchListener {

        int mode;
        float distance = 0;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    mode = 1;
                    break;
                case MotionEvent.ACTION_UP:
                    mode = 0;
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    mode -= 1;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    mode += 1;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mode >= 2) {
                        float newDistance = Spacing(event);
                        //放大
                        if (newDistance > distance + 2) {
                            mAudioRecorder.zoomIn();
                            //缩小
                        } else if (newDistance < distance - 2) {
                            mAudioRecorder.zoomOut();
                        }
                        distance = newDistance;
                    }
                    break;
            }
            return true;
        }
    }

    /**
     * show popupWindow below position
     *
     * @param position    position to place
     * @param contentView target view to show
     */
    private void showWindow(View position, View contentView) {
        popupWindow = new PopupWindow(contentView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        // 设置一个透明的背景，不然无法实现点击弹框外，弹框消失
        popupWindow.setBackgroundDrawable(new BitmapDrawable(getResources(), (Bitmap) null));
        // 设置点击弹框外部，弹框消失
        popupWindow.setOutsideTouchable(true);
        popupWindow.setTouchable(true);
        // 设置弹框出现的位置
        popupWindow.showAsDropDown(position);
    }

    /**
     * 改变了录音参数后调用
     */
    private void onRecorderParasChanged() {
        mRecorderParaChanged = true;
    }

    /**
     * 改变滤波器参数后调用
     */
    private void onFilterParasChanged() {
        mFilterParaChanged = true;
    }

    /**
     * 设置毛玻璃背景
     *
     * @param id 背景图片id
     */
    @SuppressWarnings("deprecation")
    private void setBackground(final LinearLayout layout, int id) {
        //从资源文件中得到图片，并生成Bitmap图片
        Bitmap bmp = BitmapFactory.decodeResource(getResources(), id);
        //0-25，表示模糊值
        final Bitmap blurBmp = Blur.fastblur(MainActivity.this, bmp, 10);
        // 将Bitmap转换为Drawable
        final Drawable newBitmapDrawable = new BitmapDrawable(blurBmp);
        layout.post(new Runnable()  //调用UI线程
        {
            @Override
            public void run() {
                layout.setBackgroundDrawable(newBitmapDrawable);//设置背景
            }
        });
    }

    //update imageView background
    private void updateView(final int screenWidth) {
        Bitmap bmpBlurred = BitmapFactory.decodeFile(getFilesDir() + BLURRED_IMG_PATH);
        bmpBlurred = Bitmap.createScaledBitmap(bmpBlurred, screenWidth, (int) (bmpBlurred.getHeight()
                * ((float) screenWidth) / (float) bmpBlurred.getWidth()), false);

//        mBlurredImage.setImageBitmap(bmpBlurred);

    }


}
