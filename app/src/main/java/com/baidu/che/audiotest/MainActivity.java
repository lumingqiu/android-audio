package com.baidu.che.audiotest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.Manifest;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AudioTest";

    private Button mBtnPlay;
    private Button mBtnRecord;
    private Handler mHandler = new Handler();
    private volatile boolean playOver = false;
    private volatile boolean playing = false;
    private volatile boolean recordOver = false;
    private volatile boolean recording = false;
    private HandlerThread mHandlerThread = new HandlerThread("audio");

    {
        mHandlerThread.start();
    }

    final AudioManager.OnAudioFocusChangeListener afChangeListener = focusChange -> {
        Log.d(TAG, "USB focus: change ");

        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                audioTrackPlay();
                Log.d(TAG, "USB focus:AUDIOFOCUS_GAIN ");
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                Log.d(TAG, "USB focus:AUDIOFOCUS_LOSS ");
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                Log.d(TAG, "USB focus:AUDIOFOCUS_LOSS_TRANSIENT ");
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Log.d(TAG, "USB focus:AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ");
                break;
            default:
                break;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBtnPlay = findViewById(R.id.play);
        mBtnRecord = findViewById(R.id.record);

        mBtnPlay.setOnClickListener(v -> {
            if (playing) {
                stopPlay();
                mBtnPlay.setText("play");
            } else {
                playProxy();
                mBtnPlay.setText("stop play");
            }
        });
        mBtnRecord.setOnClickListener(v -> {
            if (recording) {
                stopRecord();
                mBtnRecord.setText("record");
            } else {
                startRecordProxy();
                mBtnRecord.setText("stop record");
            }
        });
    }

    private void playProxy() {
        playing = true;
        requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                110);
    }

    private void stopPlay() {
        playOver = true;
    }

    private void stopRecord() {
        recordOver = true;
    }

    private void startRecordProxy() {
        recording = true;
        requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                111);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!gotPermission(permissions, grantResults)) {
            return;
        }
        switch (requestCode) {
            case 110:
                play();
                break;
            case 111:
                startRecord();
                break;
        }
    }

    private boolean gotPermission(String[] permissions, int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            int grantResult = grantResults[i];
            Log.e("grantResults", grantResult + "");

            if (grantResult == 0) {
                continue;
            }
            switch (permissions[i]) {
                case Manifest.permission.RECORD_AUDIO:
                    toast("没有录音权限");
                    return false;
                case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                    toast("没有读写权限");
                    return false;
            }
        }
        return true;
    }

    private void play() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Handler handler = new Handler(mHandlerThread.getLooper());
            final AudioAttributes PlaybackAttributes_usb = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            final AudioFocusRequest usbFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(PlaybackAttributes_usb)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(afChangeListener, handler)
                    .build();
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int i = am.requestAudioFocus(usbFocusRequest);
            Log.d(TAG, "AudioManager  requestAudioFocus");
            handler.post(this::audioTrackPlay);
        } else {
            Thread thread = new Thread(this::audioTrackPlay);
            thread.start();
        }
    }

    private void audioTrackPlay() {
        playOver = false;
        final AudioAttributes PlaybackAttributes_usb = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(48000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        int bufSize = AudioTrack.getMinBufferSize(
                48000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
//        int bufSize = 1024;
//
        AudioTrack trackplayer = new AudioTrack(
                PlaybackAttributes_usb,
                format,
                bufSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );
//        AudioTrack trackplayer = new AudioTrack(AudioManager.STREAM_MUSIC,
//                48000,
//                AudioFormat.CHANNEL_OUT_MONO,
//                AudioFormat.ENCODING_PCM_16BIT,
//                bufSize,
//                AudioTrack.MODE_STREAM);
        trackplayer.play();
        FileInputStream fileInputStream = null;
        InputStream is = null;

        try {
            File fileDir1 = new File("/sdcard/AudioTest/");
            if (!fileDir1.exists()) {
                fileDir1.mkdirs();

            }
            final String path = "/sdcard/AudioTest/read.wav";
            //1、得到数据文件
            File file = new File(path);
            //判断是否有read.wav文件，默认播放48kn.wav
            if (file.exists()) {
                //2、建立数据通道
                fileInputStream = new FileInputStream(file);
                byte[] bufferPlay = new byte[bufSize];
                //            int length = 0;
                //循环读取文件内容，输入流中将最多buf.length个字节的数据读入一个buf数组中,返回类型是读取到的字节数。
                //当文件读取到结尾时返回 -1,循环结束。
                //            while((length = fileInputStream.read(buf)) != -1){
                //	            System.out.print(new String(buf,0,length));
                //            }
                int read;
                while ((read = fileInputStream.read(bufferPlay)) > 0) {
                    if (!playOver) {
                        trackplayer.write(bufferPlay, 0, read);
                    } else {
                        trackplayer.stop();
                    }

                }


                fileInputStream.close();
            }else{
                //写死在包的文件代码块
                is = getAssets().open("48kn.wav");
                byte[] bufferPlay = new byte[bufSize];
                int read;
                while ((read = is.read(bufferPlay)) > 0) {
                    if (!playOver) {
                        trackplayer.write(bufferPlay, 0, read);
                    } else {
                        trackplayer.stop();
                    }
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        playing = false;
        mHandler.post(() -> mBtnPlay.setText("play"));
        toast("播放结束");
        trackplayer.release();
    }

    private void startRecord() {
        Thread thread = new Thread(() -> {
            int bufferSize = AudioRecord.getMinBufferSize(
                    64000,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    64000,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);
            audioRecord.startRecording();
            recordOver = false;
            byte[] bufferRecord = new byte[bufferSize];

            audioRecord.startRecording();
            int bufferRead;
            File fileDir = new File("/sdcard/AudioTest/");
            if (!fileDir.exists()) {
                fileDir.mkdirs();

            }
            OutputStream outputStream = null;
            try {
                String recordFile = getNowTime() + ".pcm";
                File file = new File(fileDir, recordFile);
                boolean newFile = file.createNewFile();
                Log.d(TAG, "mkdir recordFile" + newFile);
                outputStream = new FileOutputStream(file);
                while (!recordOver) {
                    bufferRead = audioRecord.read(bufferRecord, 0, bufferRecord.length);
                    if (bufferRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        break;
                    } else if (bufferRead == AudioRecord.ERROR_BAD_VALUE) {
                        break;
                    } else if (bufferRead > 0) {
                        outputStream.write(bufferRecord, 0, bufferRead);
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.flush();
                        outputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            audioRecord.release();
            toast("录音结束");
            recording = false;
        });
        thread.start();

    }

    private void toast(final String text) {
        mHandler.post(() -> Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show());
    }

    public static String getNowTime() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        Date date = new Date(System.currentTimeMillis());
        return simpleDateFormat.format(date);
    }

}
