package com.ms_debug.screenrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.os.Process;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import java.io.IOException;

import static android.app.Activity.RESULT_OK;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
import static android.os.Environment.DIRECTORY_MOVIES;

/**
 * RecordService Class
 *
 * Background service for recording the device screen.
 * Listens for commands to stop or start recording by the user
 * and by screen locked/unlock events. Notification in the
 * notification center informs user about the running recording
 * service.
 */
public final class RecordService extends Service {

    private ServiceHandler mServiceHandler;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaRecorder mMediaRecorder;
    private int resultCode;
    private Intent data;
    private BroadcastReceiver mScreenStateReceiver;

    private static final String TAG = "RECORDERSERVICE";
    private static final String EXTRA_RESULT_CODE = "resultcode";
    private static final String EXTRA_DATA = "data";
    private static final int ONGOING_NOTIFICATION_ID = 23;

    /*
     *
     */
    static Intent newIntent(Context context, int resultCode, Intent data) {
        Intent intent = new Intent(context, RecordService.class);
        intent.putExtra(EXTRA_RESULT_CODE, resultCode);
        intent.putExtra(EXTRA_DATA, data);
        return intent;
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        CharSequence name = getString(R.string.channel_name);
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        final String CHANNEL_ID = "123";
        final String TAG_NOTIFICATION = getString(R.string.app_name);
        NotificationChannel channel =
            new NotificationChannel(
                CHANNEL_ID, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);

        // notificationManager.notify(TAG_NOTIFICATION, 1, builder.build());

        Notification notification = new Notification.
            Builder(getApplicationContext(), CHANNEL_ID)
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentTitle(name)
            .build();

        startForeground(12312, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch(action){
                case Intent.ACTION_SCREEN_ON:
                    startRecording(resultCode, data);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    stopRecording();
                    break;
                case Intent.ACTION_CONFIGURATION_CHANGED:
                    stopRecording();
                    startRecording(resultCode, data);
                    break;
            }
        }
    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            if (resultCode == RESULT_OK) {
                startRecording(resultCode, data);
            }
        }
    }

    @Override
    public void onCreate() {
        // register receiver to check if the phone screen is on or off
        createNotificationChannel();
        mScreenStateReceiver = new MyBroadcastReceiver();
        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenStateFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(mScreenStateReceiver, screenStateFilter);

        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        Looper mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "Starting recording service", Toast.LENGTH_SHORT).show();
        resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        data = intent.getParcelableExtra(EXTRA_DATA);

        if (resultCode == 0 || data == null) {
            throw new IllegalStateException("Result code or data missing.");
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        mServiceHandler.sendMessage(msg);

        return START_REDELIVER_INTENT;
    }

    private void startRecording(int resultCode, Intent data) {

        MediaProjectionManager mProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService (Context.MEDIA_PROJECTION_SERVICE);
        mMediaRecorder = new MediaRecorder();

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);

        int mScreenDensity = metrics.densityDpi;
        int displayWidth = metrics.widthPixels;
        int displayHeight = metrics.heightPixels;

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoEncodingBitRate(8 * 1000 * 1000);
        mMediaRecorder.setVideoFrameRate(15);
        mMediaRecorder.setVideoSize(displayWidth, displayHeight);

        String videoDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_MOVIES).getAbsolutePath();
        Long timestamp = System.currentTimeMillis();

        String orientation = "portrait";
        if( displayWidth > displayHeight ) {
            orientation = "landscape";
        }
        String filePathAndName = videoDir + "/time_" + timestamp.toString() + "_mode_" + orientation + ".mp4";

        mMediaRecorder.setOutputFile( filePathAndName );

        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        Surface surface = mMediaRecorder.getSurface();
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("MainActivity",
                displayWidth, displayHeight, mScreenDensity, VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                surface, null, null);
        mMediaRecorder.start();

        Log.v(TAG, "Started recording");
    }

    private void stopRecording() {
        mMediaRecorder.stop();
        mMediaProjection.stop();
        mMediaRecorder.release();
        mVirtualDisplay.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        stopRecording();
        unregisterReceiver(mScreenStateReceiver);
        stopSelf();
        Toast.makeText(this, "Recorder service stopped", Toast.LENGTH_SHORT).show();
    }
}