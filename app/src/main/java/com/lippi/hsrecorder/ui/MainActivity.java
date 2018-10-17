package com.lippi.hsrecorder.ui;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.lippi.hsrecorder.utils.helper.LogHelper;
import com.lippi.hsrecorder.utils.helper.bluetooth.BluetoothService;
import com.lippi.hsrecorder.utils.helper.bluetooth.DeviceListActivity;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.holder.StringHolder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;
import com.mikepenz.materialdrawer.util.RecyclerViewCacheUtil;
import com.lippi.hsrecorder.utils.helper.bluetooth.AppFragment;

import java.io.IOException;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.InjectView;

import static com.lippi.hsrecorder.utils.helper.bluetooth.AppFragment.D;
import static com.lippi.hsrecorder.utils.helper.bluetooth.AppFragment.DEVICE_NAME;
import static com.lippi.hsrecorder.utils.helper.bluetooth.AppFragment.MESSAGE_DEVICE_NAME;
import static com.lippi.hsrecorder.utils.helper.bluetooth.AppFragment.MESSAGE_READ;
import static com.lippi.hsrecorder.utils.helper.bluetooth.AppFragment.MESSAGE_STATE_CHANGE;
import static com.lippi.hsrecorder.utils.helper.bluetooth.AppFragment.MESSAGE_TEXT;
import static com.lippi.hsrecorder.utils.helper.bluetooth.AppFragment.MESSAGE_TOAST;
import static com.lippi.hsrecorder.utils.helper.bluetooth.AppFragment.TOAST;
import static com.lippi.hsrecorder.utils.helper.bluetooth.AppFragment.mmHandler;


/**
 * Created by lippi on 14-12-3.
 */
public class MainActivity extends RoboActionBarActivity implements View.OnClickListener, AudioRecorder.OnStateChangedListener {
    private static final String TAG = LogHelper.makeLogTag(MainActivity.class);
    private static final int PROFILE_SETTING = 1;
    private static final int SEEK_BAR_MAX = 100000;
    private static final String BLURRED_IMG_PATH = "blurred_image.png";
    public static final String HEART_RATE = "heart rate";
    public static final String HEART_SOUND_DATA = "heart sound data";
    //录音的时候接到电话被打断
    private boolean mSampleInterrupted = false;
    // Some error messages are displayed
    private String mErrorUiMessage = null;
    //播放进度条的时间格式
    private String mTimerFormat;

    private int mSampleRate = 22050;

    private AudioRecorder mAudioRecorder;

    private HashSet<String> mSavedRecord;

    private long mLastClickTime;

    private int mLastButtonId;


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

    @InjectView(R.id.edit_filename)
    private ImageButton editFileNameButton;


    @InjectView(R.id.ill)
    private ImageButton ill;

    @InjectView(R.id.scan_bluetooth)
    private ImageButton scan_bluetooth;

    @InjectView(R.id.record_play_switch)
    private CheckBox record_play_switch;


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

    @InjectView(R.id.heartRate)
    private TextView heartRate;

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
    private Drawer result;
    @InjectView(R.id.toolbar)
    private Toolbar toolbar;
    private AccountHeader header;//头像
    private Handler resultHandler = new ResultHandler();
    private int countNum = 0;


    public static final int REQUEST_ENABLE_BT = 0;
    public static final int REQUEST_CONNECT_DEVICE = 1;
    private BluetoothAdapter mBluetoothAdapter;
    protected Context context;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    protected BluetoothService mChatService = null;
    private AppFragment mappFrament;
    private Writer writerOne;
    public String mConnectedDeviceName = null;
    //protected static Handler mmHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);

        setSupportActionBar(toolbar);
        chartView.setmSampleRate(mSampleRate / 2);

//        setBackground(layout, R.drawable.background_03);
        // chartView.setOnTouchListener(new TwoFingerTouchListener());

        mAudioRecorder = new AudioRecorder(mSampleRate, resultHandler, chartView);
        mAudioRecorder.setOnStateChangedListener(this);
        mSavedRecord = new HashSet<String>();
        mappFrament=new AppFragment();
        Context ctx = MainActivity.this;
        sharedPreferences = ctx.getSharedPreferences("address", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mmHandler = new MyHandler(this);
        mChatService = new BluetoothService(context, mmHandler);


        initResourceRefs();
        registerExternalStorageListener();
        //set the  mediaplater Volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Create a few sample profile
        // NOTE you have to define the loader logic too. See the CustomApplication for more details
        final IProfile profile1 = new ProfileDrawerItem().withName("梅长苏").withEmail("changshu.mei@gmail.com")
                .withIcon(R.mipmap.profile2).withIdentifier(100);
        final IProfile profile2 = new ProfileDrawerItem().withName("靖王")
                .withEmail("king@gmail.com").withIcon(R.mipmap.profile3).withIdentifier(101);


        // Create the AccountHeader
        header = new AccountHeaderBuilder()
                .withActivity(this)
                .withHeaderBackground(R.mipmap.header)
                .addProfiles(
                        profile1,
                        profile2,
                        //don't ask but google uses 14dp for the add account icon in gmail but 20dp for the normal icons (like manage account)
                        new ProfileSettingDrawerItem().withName("添加账户")
                                .withIcon(new IconicsDrawable(this, GoogleMaterial.Icon.gmd_add).actionBar().paddingDp(5)
                                        .colorRes(R.color.material_drawer_primary_text)).withIdentifier(PROFILE_SETTING),
                        new ProfileSettingDrawerItem().withName("管理账户").withIcon(GoogleMaterial.Icon.gmd_settings)
                                .withIdentifier(2)
                )
                .withOnAccountHeaderListener(new AccountHeader.OnAccountHeaderListener() {
                    @Override
                    public boolean onProfileChanged(View view, IProfile profile, boolean current) {
                        switch (profile.getIdentifier()) {
                            case PROFILE_SETTING:
                                int count = 100 + header.getProfiles().size() + 1;
                                IProfile newProfile = new ProfileDrawerItem()
                                        .withNameShown(true).withName("Batman" + count).withEmail("batman" + count + "@gmail.com")
                                        .withIcon(R.mipmap.profile5).withIdentifier(count);
                                if (header.getProfiles() != null) {
                                    //we know that there are 2 setting elements. set the new profile above them ;)
                                    header.addProfile(newProfile, header.getProfiles().size() - 2);
                                } else {
                                    header.addProfiles(newProfile);
                                }
                                break;
                            default:
                                Intent intent = new Intent(MainActivity.this, AudioListActivity.class);
                                startActivity(intent);
                                break;
                        }

                        //false if you have not consumed the event and it should close the drawer
                        return false;
                    }

                })
                .withSavedInstance(savedInstanceState)
                .build();

        //Create the drawer
        result = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(toolbar)
                .withHasStableIds(true)
                .withAccountHeader(header) //set the AccountHeader we created earlier for the header
                .addDrawerItems(
                        new PrimaryDrawerItem().withName("录音采样率").withDescription("")
                                .withIcon(GoogleMaterial.Icon.gmd_wb_sunny).withIdentifier(1).withSelectable(true),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem().withName("滤波器类型").withDescription("")
                                .withIcon(GoogleMaterial.Icon.gmd_airline_seat_legroom_normal).withIdentifier(2).withSelectable(true),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem().withName("通带截止频率").withDescription("")
                                .withIcon(GoogleMaterial.Icon.gmd_wb_cloudy).withIdentifier(3).withSelectable(true),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem().withName("阻带截止频率").withDescription("")
                                .withIcon(GoogleMaterial.Icon.gmd_cake).withIdentifier(4).withSelectable(true),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem().withName("通带最大衰减(db)").withDescription("")
                                .withIcon(GoogleMaterial.Icon.gmd_battery_charging_full).withIdentifier(5).withSelectable(true),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem().withName("阻带最小衰减(db)").withDescription("")
                                .withIcon(GoogleMaterial.Icon.gmd_business).withIdentifier(6).withSelectable(true)
                ) // add the items we want to use with our Drawer
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        //check if the drawerItem is set.
                        //there are different reasons for the drawerItem to be null
                        //--> click on the header
                        //--> click on the footer
                        //those items don't contain a drawerItem
                        TextView title = new TextView(MainActivity.this);
                        title.setTextSize(25);
                        title.setTextColor(getResources().getColor(R.color.black));

                        final EditText editText = (EditText) LayoutInflater.from(MainActivity.this).inflate(R.layout.edit_text_layout, null).findViewById(R.id.edit_text);
                        if (drawerItem != null) {
                            switch (drawerItem.getIdentifier()) {
                                case 1:
                                    ArrayAdapter<Integer> sampleRate_adapter = new ArrayAdapter<Integer>(MainActivity.this,
                                            R.layout.dialog_list_item, R.id.textView1, SAMPLE_RATES);
                                    title.setText("选择录音采样率：");
                                    final DialogPlus dialog = new DialogPlus.Builder(MainActivity.this)
                                            .setAdapter(sampleRate_adapter)
                                            .setCancelable(true)
                                            .setGravity(DialogPlus.Gravity.CENTER)
                                            .setMargins(50, 0, 150, 0)
                                            .setHeader(title)
                                            .setBackgroundColorResourceId(R.color.material_drawer_background)
                                            .setOnItemClickListener(new OnItemClickListener() {
                                                @Override
                                                public void onItemClick(DialogPlus dialogPlus, Object item, View view, int position) {
                                                    int new_sampleRate = (Integer) item;
                                                    if (mSampleRate != new_sampleRate) {
                                                        mSampleRate = (Integer) item;
                                                        Toast.makeText(getApplicationContext(), "Samplerate=" + mSampleRate, Toast.LENGTH_SHORT).show();
                                                        onRecorderParasChanged();
                                                        onResume();
                                                    }
                                                    dialogPlus.dismiss();
                                                }
                                            }).create();
                                    dialog.show();

                                    break;
                                case 2:
                                    title.setText("选择滤波器类型：");
                                    ArrayAdapter<String> filter_adapter = new ArrayAdapter<String>(MainActivity.this,
                                            R.layout.dialog_list_item, R.id.textView1, FILTER_TYPES);

                                    final DialogPlus filter_dialog = new DialogPlus.Builder(MainActivity.this)
                                            .setAdapter(filter_adapter)
                                            .setCancelable(true)
                                            .setGravity(DialogPlus.Gravity.CENTER)
                                            .setMargins(50, 0, 150, 0)
                                            .setHeader(title)
                                            .setBackgroundColorResourceId(R.color.material_drawer_background)
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

                                case 3:
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
                                            .setBackgroundColorResourceId(R.color.material_drawer_background)
                                            .setOnDismissListener(new OnDismissListener() {
                                                @Override
                                                public void onDismiss(DialogPlus dialog) {
                                                    String text = editText.getText() + "";
                                                    if (!TextUtils.isEmpty(text)) {
                                                        int freq = Integer.parseInt(text);
                                                        if (mPassFreq != freq) {
                                                            mPassFreq = freq;
                                                            Log.e(TAG, "pass sample: " + mPassFreq);
                                                            onFilterParasChanged();
                                                        }
                                                    }
                                                }
                                            }).create();
                                    dialogPlus.show();
                                    break;
                                case 4:
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
                                            .setBackgroundColorResourceId(R.color.material_drawer_background)
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
                                case 5:
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
                                            .setBackgroundColorResourceId(R.color.material_drawer_background)
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

                                case 6:
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
                                            .setBackgroundColorResourceId(R.color.material_drawer_background)
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
                        return true;
                    }
                })
                .withSavedInstance(savedInstanceState)
                .withShowDrawerOnFirstLaunch(true)
                .build();

        //if you have many different types of DrawerItems you can magically pre-cache those items to get a better scroll performance
        //make sure to init the cache after the DrawerBuilder was created as this will first clear the cache to make sure no old elements are in
        RecyclerViewCacheUtil.getInstance().withCacheSize(2).init(result);

        //only set the active selection or active profile if we do not recreate the activity
        if (savedInstanceState == null) {
            // set the selection to the item with the identifier 11
            result.setSelection(1, false);

            //set the active profile
            header.setActiveProfile(profile1);
        }

//        result.updateBadge(4, new StringHolder(10 + ""));
    }

    private class ResultHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case AudioRecorder.BEGIN_CALCULATE_HEART_RATE:
                    //getSupportActionBar().setTitle("开始计算心率...");
                    break;
                case AudioRecorder.PERIOD_GOT:
                    int i = (int) msg.obj;
                    Log.e(TAG, "handleMessage: " + i);
                    setHeartRate(i);
                    break;
                default:
                    break;
            }
        }
    }

    ;

    private void initResourceRefs() {
        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        //  audioManager.setMode(AudioManager.STREAM_MUSIC);
        // audioManager.setSpeakerphoneOn(false);
        // audioManager.setMicrophoneMute(true);

        mNewButton.setOnClickListener(this);
        mRecordButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mPlayButton.setOnClickListener(this);
        mPauseButton.setOnClickListener(this);
        mDeleteButton.setOnClickListener(this);
        editFileNameButton.setOnClickListener(this);
        ill.setOnClickListener(this);
        scan_bluetooth.setOnClickListener(this);

        record_play_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
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
                    mFileNameEditText.setFileName(name);
                    //  mAudioRecorder.renameSampleFile(name);
                }
            }
        });


        mPlaySeekBar.setMax(SEEK_BAR_MAX);

        mPlaySeekBar.setOnSeekBarChangeListener(mSeekBarChangeListener);

        mTimerFormat = getResources().getString(R.string.timer_format);
        mLastClickTime = 0;
        mLastButtonId = 0;

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

            if (result != null && result.isDrawerOpen()) {
                result.closeDrawer();
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

            int length = coefficients.getFilterOrder();
            for (int i = 0; i < length; i++) {
                Log.e(TAG, "onStart: filter" + i +
                        "---   " + coefficients.getACoefficients()[i] + "    ----" + coefficients.getBCoefficients()[i]);

            }
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
        getSupportActionBar().setTitle("HSRecorder");
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
            Toast.makeText(this, "修改采样率", Toast.LENGTH_SHORT).show();
            mAudioRecorder.setSampleRate(mSampleRate);
            chartView.setmSampleRate(mSampleRate);
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
        Log.e(TAG, "onClick: edit" + button.toString());
        if (System.currentTimeMillis() - mLastClickTime < 300) {
            // in order to avoid user click bottom too quickly
            return;
        }


        if (button.getId() == mLastButtonId && button.getId() != R.id.newButton && button.getId() != R.id.edit_filename) {
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

        switch (mLastButtonId) {
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
            case R.id.edit_filename:
                Log.e(TAG, "onClick: edit");
                setEditFileNameButton();

                break;
            case R.id.ill:
                setIllPromble();
                break;
            case R.id.scan_bluetooth:
                mAudioRecorder.clear();
                Intent serverIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                break;
        }
    }

    private class MyHandler extends Handler {
        private WeakReference<MainActivity> mweakActivty;
    public MyHandler(MainActivity activity) {
        mweakActivty= new WeakReference<MainActivity>(activity);
    }


    public void handleMessage(Message msg) {
        {
            Log.e(TAG, "aaa start" );

            //根据蓝牙传过来的数据进行处理
            switch (msg.what) {
                case MESSAGE_READ:
                    float[] readBufOne = new float[msg.arg1];
                    float[] readBuf = (float[]) msg.obj;
                    int count = msg.arg1;
                    Log.e(TAG, "arg1=..."+msg.arg1 );

                    for (int i = 0; i < count; i++) {
                       // readBuf[i] = readBuf[i];

                       /* try {
                            writerOne.append(readBuf[i] + "" + "\n");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }*/

                        readBufOne[i] = readBuf[i]; //出现 数组越界？
                        Log.e(TAG, "readbuf" + readBufOne[i]);

                    }
                    Intent intent = new Intent();
                    intent.setClass(MainActivity.this, AudioRecorder.class);
                    Bundle bundle = new Bundle();
                    bundle.putFloatArray("buffer", readBufOne);
                    intent.putExtras(bundle);
                    startActivity(intent);
                    //mDataOneChart.updateFloats(readBufOne);
                    break;

                case MESSAGE_STATE_CHANGE:
                    if (D)
                        Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        //以下几个情况主要用于显示蓝牙状态的改变，因为取消了标题栏，所以没有添加状态变化的提示
                        case BluetoothService.STATE_CONNECTED:
                              /*  setStatus(getString(R.string.title_connected_to,
                                        mConnectedDeviceName));*/
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            /*    setStatus(R.string.title_connecting);*/
                            break;
                        case BluetoothService.STATE_LISTEN:

                        case BluetoothService.STATE_NONE:
                            /*    setStatus(R.string.title_not_connected);*/
                            break;

                    }
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Log.e(TAG,"mConnectedDeviceName="+mConnectedDeviceName);
                    Toast.makeText(getApplicationContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TEXT:
                    break;
                default:
                    break;
            }
        }
    }
}

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    Log.e(TAG, "bluetoothHandle: connect");
                    connectDevice(data);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    // setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.e(TAG, "BT not enabled");
                    Toast.makeText(context, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                }
        }
    }
   public  BluetoothService getmChatService() {
        return mChatService;
    }

    public void connectDevice(Intent data) {
        String address = data.getExtras().getString(
                DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        editor.putString("address", address);
        editor.commit();
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
       // mappFrament.getmChatService().connect(device);
        getmChatService().connect(device);
    }

    public void setEditFileNameButton() {
        PopupMenu popup = new PopupMenu(this, editFileNameButton);
        getMenuInflater().inflate(R.menu.editfilename_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

            public boolean onMenuItemClick(MenuItem item) {
                if (!TextUtils.isEmpty(item.toString()))
                    mFileNameEditText.setFileName(item.toString());
            /*    switch (item.getItemId()) {
                    case R.id.position1:
                        if (!TextUtils.isEmpty(item.toString()))
                            mFileNameEditText.setFileName(item.toString());

                        break;
                    case R.id.position2:
                        break;
                    case R.id.position3:
                        break;
                    case R.id.position4:
                        break;
                    case R.id.position5:
                        break;
                    default: break;
                }*/
                return true;
            }
        });

        popup.show();

    }


    private void setIllPromble() {
        PopupMenu popupMenu = new PopupMenu(this, ill);
        getMenuInflater().inflate(R.menu.ill_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                mAudioRecorder.changeDir( item.toString());

                return true;
            }
        });
        popupMenu.show();
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

    /**
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
            image.setBackgroundResource(R.mipmap.background_number);
        }
        switch (number) {
            case '0':
                image.setImageResource(R.mipmap.number_0);
                break;
            case '1':
                image.setImageResource(R.mipmap.number_1);
                break;
            case '2':
                image.setImageResource(R.mipmap.number_2);
                break;
            case '3':
                image.setImageResource(R.mipmap.number_3);
                break;
            case '4':
                image.setImageResource(R.mipmap.number_4);
                break;
            case '5':
                image.setImageResource(R.mipmap.number_5);
                break;
            case '6':
                image.setImageResource(R.mipmap.number_6);
                break;
            case '7':
                image.setImageResource(R.mipmap.number_7);
                break;
            case '8':
                image.setImageResource(R.mipmap.number_8);
                break;
            case '9':
                image.setImageResource(R.mipmap.number_9);
                break;
            case ':':
                image.setImageResource(R.mipmap.colon);
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
        mFileNameEditText.setEnabled(true);
        record_play_switch.setChecked(false);



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
            case R.id.synchronized_data:
                break;
            case R.id.check_update:
                break;
            case R.id.menu_exit:
                switch (mAudioRecorder.getState()) {
                    case AudioRecorder.IDLE_STATE:
                    case AudioRecorder.RECORDING_STOPPED:

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
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //add the values which need to be saved from the drawer to the bundle
        outState = result.saveInstanceState(outState);
        //add the values which need to be saved from the accountHeader to the bundle
        outState = header.saveInstanceState(outState);
        super.onSaveInstanceState(outState);
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

    private void setHeartRate(int i) {
        if (i > 220) {
            return;
        }
        if (i > 120 || i < 50) {
            countNum++;
            if (countNum < 3) {
                return;
            }
        }

        heartRate.setText(" " + i);
        countNum = 0;
    }


}
