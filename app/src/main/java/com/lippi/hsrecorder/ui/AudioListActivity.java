package com.lippi.hsrecorder.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;


import com.lippi.hsrecorder.R;
import com.lippi.hsrecorder.utils.AudioRecorder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Lippi on 2014/12/28.
 */
public class AudioListActivity extends Activity implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    private ListView audioListView;
    //录音文件列表
    private List<String> audioList;
    private AudioItemsAdapter adapter;

    //states
    public static final int IDLE_STATE = 0;

    public static final int PLAYING_STATE = 2;

    public static final int PLAYING_PAUSED_STATE = 3;

    private MediaPlayer mPlayer;
    // operation started
    private File mSampleDir;
    private File mSampleFile = null;
    private int mState;
    private boolean isPopup = false;
    private LinearLayout select_all_layout;
    private Button seleletAllButton;
    private Button disSelectAllButton;
    private Button cancelSelectButton;
    private Button deleteButton;
    //选中的数量
    private int checkNum;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_list);
        select_all_layout = (LinearLayout) findViewById(R.id.line);
        seleletAllButton = (Button) findViewById(R.id.bt_selectall);
        disSelectAllButton = (Button) findViewById(R.id.bt_disselectall);
        cancelSelectButton = (Button) findViewById(R.id.bt_cancelSelectall);
        deleteButton = (Button) findViewById(R.id.delete_items);
        audioList = new ArrayList<String>();
        addDataToList();
        audioListView = (ListView) findViewById(R.id.audio_list);
        adapter = new AudioItemsAdapter(this, audioList);
        audioListView.setAdapter(adapter);

       seleletAllButton.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               //遍历list的长度，将Adapter的map值全设置为true
               for(int i =0; i < audioList.size(); i++){
                   adapter.getIsSelected().set(i, true);
               }
               checkNum = audioList.size();
               adapter.notifyDataSetChanged();
           }
       });
       disSelectAllButton.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               for(int i = 0; i < audioList.size(); i++){
                   if(adapter.getIsSelected().get(i)){
                       adapter.getIsSelected().set(i, false);
                       checkNum --;
                   }else {
                       adapter.getIsSelected().set(i, true);
                       checkNum ++;
                   }
               }
               adapter.notifyDataSetChanged();
           }
       });
       cancelSelectButton.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               for(int i = 0; i < audioList.size(); i++){
                   if(adapter.getIsSelected().get(i)){
                       adapter.getIsSelected().set(i, false);
                       checkNum --;
                   }
               }

               adapter.notifyDataSetChanged();
           }
       });

       deleteButton.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               new AlertDialog.Builder(AudioListActivity.this).setTitle("删除选中的文件？")
                       .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                           @Override
                           public void onClick(DialogInterface dialog, int which) {
                               for(int i = 0; i < audioList.size();){
                                   if(adapter.getIsSelected().get(i)){
                                       mSampleFile = new File(mSampleDir + "/" + audioList.get(i));
                                        audioList.remove(i);
                                        adapter.getIsSelected().remove(i);
                                       checkNum --;
                                       //这时候列表的数据发生了整体平移的情况，需要重新从0开始
                                       if(mSampleFile.exists()){
                                           mSampleFile.delete();
                                       }
                                       mSampleFile = null;
                                       continue;
                                   }
                                   i ++;
                               }

                               adapter.notifyDataSetChanged();

                           }
                       }).setNegativeButton("取消",null).show();
           }
       });
    }

    private void addDataToList() {
        mSampleDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                + AudioRecorder.SAMPLE_DEFAULT_DIR);
        if (!mSampleDir.exists()) {
            mSampleDir.mkdirs();
        }
        File[] files = mSampleDir.listFiles();
        for(File f : files){
            audioList.add(f.getName());
        }
    }

    public int getState() {
        return mState;
    }

    public void setState(int state) {
        this.mState = state;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        stopPlayback();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        stopPlayback();
        return true;
    }

    class AudioItemsAdapter extends BaseAdapter{
       private Context context;
       private List<String> data;
        //save the state of checkbox
       private List<Boolean> isSelected;
       public AudioItemsAdapter(Context context, List<String> data){
           this.context = context;
           this.data = data;
           isSelected = new ArrayList<Boolean>();
           initData();

       }

       public void initData(){
           for(int i = 0; i < data.size(); i++){
               isSelected.add(false);
           }
       }
       @Override
       public int getCount() {
           return data.size();
       }

       @Override
       public Object getItem(int position) {
           return data.get(position);
       }

       @Override
       public long getItemId(int position) {
           return position;
       }

       public List<Boolean> getIsSelected(){
           return isSelected;
       }

       class ViewHolder {
           TextView fileName;
           CheckBox checkBox;
           ImageButton playButton;
           ImageButton pauseButton;
       }

       @Override
       public View getView(final int position, View convertView, final ViewGroup parent) {
           final ViewHolder holder;
           if(convertView == null){
               holder = new ViewHolder();
               convertView = LayoutInflater.from(context).inflate(R.layout.audio_item, null);
               holder.fileName = (TextView) convertView.findViewById(R.id.file_name);
               holder.checkBox = (CheckBox) convertView.findViewById(R.id.file_check);
               holder.playButton = (ImageButton) convertView.findViewById(R.id.list_item_play);
               holder.pauseButton = (ImageButton) convertView.findViewById(R.id.list_item_pause);
               convertView.setTag(holder);
           }else{
               holder = (ViewHolder) convertView.getTag();
           }
           holder.fileName.setText(data.get(position));
           registerForContextMenu(holder.fileName);
           holder.fileName.setOnClickListener(new View.OnClickListener() {
               @Override
               public void onClick(View v) {
                   mSampleFile = new File(mSampleDir + "/" + holder.fileName.getText());
                   if (!isPopup) {
                       //其他元素全部收起
                       ViewHolder tempHolder;
                       for(int i = 0; i < parent.getChildCount(); i ++){
                           tempHolder = (ViewHolder) parent.getChildAt(i).getTag();
                           tempHolder.playButton.setVisibility(View.GONE);
                           tempHolder.pauseButton.setVisibility(View.GONE);
                       }
                       holder.playButton.setVisibility(View.VISIBLE);

                       isPopup = true;
                   } else {
                       holder.playButton.setVisibility(View.GONE);
                       holder.pauseButton.setVisibility(View.GONE);
                       isPopup = false;
                   }
               }
           });
           holder.checkBox.setChecked(isSelected.get(position));
           holder.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
               @Override
               public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                   if(isChecked){
                       isSelected.set(position, true);
                       select_all_layout.setVisibility(View.VISIBLE);
                       checkNum ++;
                   }else{
                       isSelected.set(position, false);
                       checkNum --;
                       if(checkNum == 0){
                           select_all_layout.setVisibility(View.GONE);
                       }
                   }
               }
           });
           holder.fileName.setOnLongClickListener(new View.OnLongClickListener() {
               @Override
               public boolean onLongClick(View v) {
                   mSampleFile = new File(mSampleDir + "/" + holder.fileName.getText());
                   return false;
               }
           });
           holder.playButton.setOnClickListener(new View.OnClickListener() {
               @Override
               public void onClick(View v) {
                   holder.playButton.setVisibility(View.GONE);
                   holder.pauseButton.setVisibility(View.VISIBLE);
                   startPlayback(playProgress());
               }
           });
           holder.pauseButton.setOnClickListener(new View.OnClickListener() {
               @Override
               public void onClick(View v) {
                   holder.pauseButton.setVisibility(View.GONE);
                   holder.playButton.setVisibility(View.VISIBLE);
                   pausePlayback();
               }
           });
           return convertView;
       }
   }


    /**
     * 播放录音
     * @param percentage
     */
    public void startPlayback(float percentage) {
        if (getState() == PLAYING_PAUSED_STATE) {
            mPlayer.seekTo((int) (percentage * mPlayer.getDuration()));
            mPlayer.start();
            setState(PLAYING_STATE);
        } else {
            stopPlayback();
            mPlayer = new MediaPlayer();
            try {
                mPlayer.setDataSource(mSampleFile.getAbsolutePath());
                mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mPlayer.setOnCompletionListener(this);
                mPlayer.setOnErrorListener(this);
                mPlayer.prepare();
                mPlayer.seekTo((int) (percentage * mPlayer.getDuration()));
                mPlayer.start();
            } catch (IllegalArgumentException e) {
                mPlayer = null;
                return;
            } catch (IOException e) {
                mPlayer = null;
                return;
            }
            setState(PLAYING_STATE);
        }
    }

    public void pausePlayback() {
        if (mPlayer == null) {
            return;
        }
        mPlayer.pause();
        setState(PLAYING_PAUSED_STATE);
    }

    public void stopPlayback() {
        if (mPlayer == null) // we were not in playback
            return;
        mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
        setState(IDLE_STATE);
    }

    public float playProgress() {
        if (mPlayer != null) {
            return ((float) mPlayer.getCurrentPosition()) / mPlayer.getDuration();
        }
        return 0.0f;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.context_menu, menu);
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.item_delete_menu:
                 String fileName = mSampleFile.getName();
                 audioList.remove(fileName);
                 adapter.notifyDataSetChanged();
                 mSampleFile.delete();
                 return true;
            case R.id.item_share_menu:
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(mSampleFile));
                shareIntent.setType("audio/x-wav");
                startActivity(Intent.createChooser(shareIntent,"选择发送方式"));
            default:
                return super.onContextItemSelected(item);
        }
    }

}
