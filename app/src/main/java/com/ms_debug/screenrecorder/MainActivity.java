package com.ms_debug.screenrecorder;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DATA RECORDER";
    private static final int PERMISSION_CODE = 1;
    private MediaProjectionManager mProjectionManager;
    private ToggleButton mToggleButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mProjectionManager = (MediaProjectionManager) getSystemService (Context.MEDIA_PROJECTION_SERVICE);
        mToggleButton = (ToggleButton) findViewById(R.id.toggle);

        boolean isRecording = isServiceRunning(RecordService.class);
        if(isRecording){
            Log.v(TAG, "recording is happening");
            mToggleButton.setChecked(true);
        }

        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleScreenShare(v);
            }
        });
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "destroy method called");super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode == RESULT_OK) {
            Log.v(TAG, " starting recording service ");
            startRecordingService(resultCode, data);
        } else {
            Toast.makeText(this, "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
            mToggleButton.setChecked(false);
            return;
        }
    }

    public void onToggleScreenShare(View view) {
        if ( ((ToggleButton)view).isChecked() ) {
            // ask for permission to capture screen and act on result after
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), PERMISSION_CODE);
            Log.v(TAG, "onToggleScreenShare");
        } else {
            Log.v(TAG, "onToggleScreenShare: Recording Stopped");
            stopRecordingService();
        }
    }

    private void startRecordingService(int resultCode, Intent data){
        Intent intent = RecordService.newIntent(this, resultCode, data);
        Log.v(TAG, "starting service");
        startService(intent);
    }

    private void stopRecordingService(){
        Intent intent = new Intent(this, RecordService.class);
        Log.v(TAG, "stopping service");
        stopService(intent);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}