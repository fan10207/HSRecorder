package com.lippi.hsrecorder.utils;


import android.os.Environment;
import android.util.Log;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.Callable;

/**
 * @author lippi
 */
public class PerioudCalTask implements Callable<Integer> {
    float[] shannonEnergy;
    int sampleRate;
    private static final int BEGIN_ENERGY = 8000;
    private static final String TAG = "period calculate task";

    public FileWriter writerOne;
    public FileWriter writerOne1;

    public PerioudCalTask(float[] data,int samleRate) {
        this.shannonEnergy = data;
        this.sampleRate = samleRate;
    }

    @Override
    public Integer call() {
       /* File sd = Environment.getExternalStorageDirectory();
        String p = sd.getPath() + "/100";
        File f = new File(p);

        if (!f.exists())
            f.mkdir();

        String path = "/mnt/sdcard/100/"  + "1.txt";
        final File fileOne = new File(path);
        String path2 = "/mnt/sdcard/100/"  + "2.txt";
        final File fileOne1 = new File(path2);
        try {
            writerOne = new FileWriter(fileOne, true);
            writerOne1=new FileWriter(fileOne1, true);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        float[] fdata = shannonEnergy;
        fdata = prehandle(fdata);
        float[] data = SelfCorrelation(fdata);

        data = nonLinear(data);

        float[]  win = enFrame(data);
        int length = win.length;
        float max = win[0];

        max*=0.2;

        int[] star = new int[5];
        int[] end = new int[5];

        int num = 0;

        for (int i=0;i<length;i++) {
            Log.e(TAG, "call: "+i+"   "+win[i] );
            if (win[i] > max  ) {
                if (star[num] == 0) {
                    star[num] = i;
                   i= i+20;
                }

            } else {
                if (star[num]==0&&end[num]==0)
                    continue;

                    end[num] = i;
                    num++;
                    if (num > 3) {
                        break;
                    }

            }
        }

        Log.e(TAG, "call s and e"+star[1]+"  "+end[1] );


        float[] test = new float[end[1] - star[1]+20];
        System.arraycopy(win, star[1], test, 0, end[1]-star[1]+20);

        int a = (int) max(test)[1];
        a = a + star[1]+1;
        int rate=0;
        rate = 60 * sampleRate /a/100;
        Log.e(TAG, "call: "+rate );
        return rate;



    }


    public float[] SelfCorrelation(float[] a) {
        int length = a.length;

        float[] Rx = new float[length];
        for (int i = 0; i < length; i++) {

            for (int j = 0; i + j < length; j++) {
                Rx[i] =Rx[i]+ a[j]*a[i+j];
            }

            Rx[i] = Rx[i] / (length - i);
           /* try {
                writerOne1.append(Rx[i] + " " + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        }

        return Rx;
    }

    public float[] enFrame(float[] a) {
        int length = a.length;
        int len = (int) length / 10;
        float[] result = new float[len];
        for (int i = 0; i < len; i++) {
            for (int n = 0; n < 10; n++) {
                result[i] += a[i * 10 + n];
            }

        }

        return result;
    }

    public float[] nonLinear(float[] a) {
        int length = a.length;
        int lenb = (int) Math.floor(length / 5);
        int lens =100 ;
        float[] b = new float[lenb];
        for (int h=0;h<lenb;h++) {
            b[h] = a[lens + h];
            Log.e(TAG, "nonLinear: call"+b[h] );
        }
        float var = max(b)[0]*3/5;
        for (int i=0;i<length;i++) {
            if (a[i] < var) {
                a[i]=0;
            }
        }

        return a;
    }

    private float[] max(float[] a) {
        float[] max=new float[2];
        for (int i=0;i<a.length;i++) {
            if (a[i] > max[0]) {
                max[0] = a[i];
                max[1] = i;
            }
        }

        return max;
    }

    private float[] prehandle(float[] floats) {
        float a = max(floats)[0]/10;
        for (int i=0;i<floats.length;i++) {
          /*  try {
                writerOne.append(floats[i] + " " + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }*/
            if (floats[i] < a) {
                floats[i]=0;
            }
        }
        return floats;
    }
}