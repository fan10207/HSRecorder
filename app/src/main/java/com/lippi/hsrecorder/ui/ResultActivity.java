package com.lippi.hsrecorder.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.TextView;

import com.lippi.hsrecorder.R;
import com.lippi.hsrecorder.utils.ChartView;
import com.romainpiel.shimmer.Shimmer;
import com.romainpiel.shimmer.ShimmerTextView;

import net.frakbot.jumpingbeans.JumpingBeans;

import java.util.Arrays;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.InjectView;

/**
 * Created by lippi on 2015/11/7.
 */
public class ResultActivity extends RoboActionBarActivity {
    @InjectView(R.id.hs_view)
    private ChartView mChartView;
    @InjectView(R.id.result_toolbar)
    private Toolbar toolbar;
    @InjectView(R.id.heartRate_text)
    private TextView heartRateText;
    @InjectView(R.id.result_text)
    private ShimmerTextView shimmerTextView;
//    private short[] data = {73, -16, 23, 13, 66, -47, -17, -112, 63, 95, -16, -50, 148, 160, -103, -77, 64, 34, -27, -19, 77, 42, 46, -104, -19, 133, 2, -69, -79, -23, -63, 8, 7, 34, 1, -24, -118, 131, 102, 61, -30, -23, 155, -25, -163, -86, 29, 50, 78, 151, -35, -4, 110, 93, 94, -11, 22, -76, -81, -44, 17, -99, -55, -69, -26, 55, 144, 77, 29, -4, 115, -25, -49, -72, -21, 83, -39, -12, 22, 0, 48, 101, -43, -79, 70, 153, -71, -173, -42, -25, -78, 56, -55, 4, 23, 42, -32, -91, -105, 54, -37, -49, 48, 61, -144, -23, 46, 138, 118,94,32,-38,-75, -52, 50, 68, 32, -7, -88, 63, 177, 78, -69, -112, -61, -22, -35, -60, -96, -42, 3, 144, 109, 158, -8, -9, -81, -26, 27, -86, -147, -42, 139, 35, -148, 32, 83, 112, 52, -121, -25, 82, 152, -84, -14, 155, 94, -85, -78, 15, 34, 1, -92, -69, 15, 46, -148, -80, 50, 183, 103, -186, -86, 37, 25, -39, 83, -28, 9, -31, -84, -30, 92, 148, 0, -67, -47, -14, 17, 5, 41, 44, -64, 18, -26, -54, -18, -4, -7, 52, 66, 6, -13, -65, 78, -16, 15, 72, -34, -6, -133, 108, 31, -174, -46, 24, 33, 49, 68, 86, 54, -24, 47, -19, -39, -87, -51, 98, 91, 11, 24, 26, -45, 61, -80, 4, -112, 25, -16, 70, -13, 5, -159, 22, -71, 26, 14, -34, 3, 67, 40, 35, -185, 25, 31, 105, -163, -122, 40, 65, -55, -31, 78, 78, -42, -103, -47, -62, 68, 83, -14, 53, 160, 5, -149, -58, 83, -67, 0, -82, -12, 117, 117, 105, 10, 25, 112, -10, -26, -29, -54, -20, 119, -47, -148, -143, 1, 70, 92, 0, -142, 172, 156, 165, -7, -87, 28, -51, 19, -35, -139, -151, 11, -65, -19, 98, -76, -54, 49, -120, -15, 29, -23, -51, 11, 94, -21, -198, -12, 81, -20, -59, 94, -146, -37, -68, 73, 12, 79, -30, 39, 137, 0, 29, -148, -72, -25, 52, 71, -9, 35, 11, -4, 195, 3, 52, 27, -106, -46, 81, 26, -73, -24, 112, 37, -19, 47, 121, 166, 15, 52, -22, -32, -33, -60, -43, -69, -1, -39, -156, 44, 151, 53, 48, 41, -104, -72, 42, -74, -81, -111, -102, 74, 31, -94, 112, 71, -82, -156, 104, -55, -44, 174, -83, -53, -46, 5, -19, 27, 13, -2, -68, 94, -47, 18, -38, -29, 79, -34, 7, 94, 72, -2, 74, 78, 106, -129, 8, 168, -110, -9, 39, -36, 142, -31, -21, -4, 124, 26, -39, -104, 59, -93, -109, -131, -61, 70, -41, -115, 13, 129, 204, -53, 6, 14, 13, 66, -250, -76, 96, -15, 6, -155, -12, 45, -25, 67, 34, 74, -79, 83, -113, -237, 49, 59, -109, -96, 61, 70, -48, -33, 104, 184, 24, -16, -93, 4, 74, 133, -28, -58, 95, 57, 2, 10, 77, -7, 46, -69, -101, -31, 134, 6, 21, 83, 127, -69, -24, -100, 44, -42, -52, -219, -90, -111, 91, 128, 62, 18, 68, 32, -45, 12, -120, -115, 11, -2, -31, -72, -80, -67, -99, 91, 106, -7, 26, -34, -13, 48, -30, -24, -38, -36, -46, 64, 3, -34, 84, 133, 3, -61, -85, 5, 113, 140, -20, -105, -17, 116, -2, -50, -8, -53, -44, -32, 118, 107, 120, 34, 16, -100, -115, -132, 95, -24, -22, -63, 24, -36, 13, 8, 77, 25, -104, 42, 91, -132, 27, 87, 97, -53, -141, -20, 78, -77, -52, 22, 21, -91, -8, 122, 79, 64, 5, -3, 37, -7, -67, -63, 4, -4, -144, -12, 40, -21, 5, 204, 39, 62, 92, -98, -56, 47, 34, 60, -11, -95, -23, 7, 102, -20, -54, 65, 131, 102, -56, -69, 0, -81, 39, -5, 24, -52, -37, 65, -20, -55, 23, 66, 22, -42, 59, 44, -18, -125, -46, -60, 3, 33, 104, 60, -59, -32, -133, -215, -73, 82, 118, 71, -105, -11, -85, 55, 68, -8, -123, -69, -59, -33, 0, 106, 43, -25, 66, 76, 52, 4, 108, 67, -134, -3, 95, -72, -82, 21, 150, -131, 90, 95, -26, -48, 123, -10, -59, 60, 100, -29, -16, 8, -48, -44, -46, 8, -152, -143, -71, 158, 116, 105, 49, 5, 107, 132, -72, -180, -42, 6, -77, -36, 19, 52, 45, 125, 76, -168, -133, 43, -31, -84, -156, -67, 33, 13, 23, -52, 110, -25, -43, -63, 22, -75, 64, -16, -12, -11, 172, 77, 119, 17, -48, 2, -121, 3, -1, 80, 28, 7, 120, 36, 54, 30, 111, -50, -100, -64, -38, -65, 5, -54, 19, 45, 132, 67, -27, 85, -29, -23, 68, -63, -129, -79, 117, 18, -33, -59, -88, -25, -10, 9, -10, 2, 21, -95, 31, -17, 12, 26, -114, -34, -30, -140, -45, -20, -102, -119, 98, 69, -120, 42, 17, -45, 103, 32, -12, -89, 5, -19, 67, -36, 33, 46, 136, 28, 35, 2, 38, -19, 71, 3, 53, 20, -82, -49, 118, 166, -24, 21, -34, -13, 8, 101, 43, -62, -52, 98, 21, -162, 76, 77, -175, -52, 74, 10, -4, -55, 57, -19, 7, -5, -66, -96, 28, 65, -109, -121, -28, -4};
    private int heartRate;
    private short[] data;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.result_layout);
        setSupportActionBar(toolbar);
        mChartView.setmSampleRate(2000);
        mChartView.setChannels(1);
        mChartView.setNumSeconds(1);
        mChartView.setPOINT_NUM(2000);
        Intent intent = getIntent();
        heartRate = intent.getExtras().getInt(MainActivity.HEART_RATE);
        data = intent.getExtras().getShortArray(MainActivity.HEART_SOUND_DATA);
        heartRateText.setText(heartRate + " ");
        startAnnimition();
        mHandler.sendEmptyMessageDelayed(1,200);
    }

private Handler mHandler = new Handler(){
    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what){
            case 1:
                if (data.length > mChartView.getPOINT_NUM()) {
                    mChartView.setPOINT_NUM(data.length);
                }
                mChartView.updateChart(data);
                break;
            default:
                break;
        }
    }
};

    private void startAnnimition(){
        JumpingBeans jumpingBeans1 = JumpingBeans.with(heartRateText)
                .makeTextJump(0, heartRateText.getText().toString().indexOf(' '))
                .setIsWave(false)
                .setLoopDuration(1000)  // ms
                .build();
        Shimmer shimmer = new Shimmer();
        shimmer.start(shimmerTextView);
    }
}
