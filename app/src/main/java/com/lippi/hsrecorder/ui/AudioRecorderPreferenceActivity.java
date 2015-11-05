/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lippi.hsrecorder.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.lippi.hsrecorder.R;
import com.lippi.hsrecorder.iirfilterdesigner.designers.AbstractIIRDesigner;
import com.lippi.hsrecorder.iirfilterdesigner.designers.ButterworthIIRDesigner;
import com.lippi.hsrecorder.iirfilterdesigner.designers.Chebyshev1IIRDesigner;


public class AudioRecorderPreferenceActivity extends PreferenceActivity {
    private static final String RECORD_TYPE = "pref_key_record_type";
    private static final String SAMPLE_RATE = "pref_key_sample_rate";
    private static final String FILTER_TYPE = "pref_key_filter_type";
    private static final String PASS_FREQ = "pref_key_filter_pass_freq";
    private static final String STOP_FREQ = "pref_key_filter_stop_freq";
    private static final String PASS_WAVE = "pref_key_filter_pass_wave";
    private static final String STOP_DEC = "pref_key_filter_stop_decrease";
    private static final String FILTER_CATEGORY = "pref_key_filter_type";


    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.preferences);
    }


    public static String getRecordType(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return settings.getString(RECORD_TYPE, context.getString(R.string.prefDefault_recordType));
    }

    public static int getSampleRate(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(settings.getString(SAMPLE_RATE, "8000"));
    }

    public static int getFilterCategory(Context context){
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return  Integer.parseInt(settings.getString(FILTER_CATEGORY, "0"));
    }

    public static AbstractIIRDesigner getFilterType(Context context){
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return (Integer.parseInt(settings.getString(FILTER_TYPE, "0"))) == 0 ? new Chebyshev1IIRDesigner() : new ButterworthIIRDesigner();
    }

    public static int getPassFreq(Context context){
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(settings.getString(PASS_FREQ, "800"));
    }

    public static int getStopFreq(Context context){
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(settings.getString(STOP_FREQ, "1600"));
    }

    public static double getPassWace(Context context){
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return Double.parseDouble(settings.getString(PASS_WAVE, "0.5"));
    }

    public static double getStopDec(Context context){
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        return Double.parseDouble(settings.getString(STOP_DEC, "50"));
    }
}
