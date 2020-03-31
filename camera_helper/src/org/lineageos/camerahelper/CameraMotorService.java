/*
 * Copyright (C) 2019 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.camerahelper;

import android.annotation.NonNull;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

public class CameraMotorService extends Service implements Handler.Callback {
    private static final boolean DEBUG = true;
    private static final String TAG = "CameraMotorService";
    public static final String POPUP_SOUND_PATH = "/system/media/audio/ui/";

    public static final int CAMERA_EVENT_DELAY_TIME = 100; // ms

    public static final String FRONT_CAMERA_ID = "1";

    public static final int MSG_CAMERA_CLOSED = 1000;
    public static final int MSG_CAMERA_OPEN = 1001;

    private Handler mHandler = new Handler(this);

    private int[] mSounds;
    private long mClosedEvent;
    private long mOpenEvent;

    private PopupCameraPreferences mPopupCameraPreferences;

    private SoundPool mSoundPool;

    private CameraManager.AvailabilityCallback mAvailabilityCallback =
            new CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraAvailable(@NonNull String cameraId) {
                    super.onCameraAvailable(cameraId);

                    if (cameraId.equals(FRONT_CAMERA_ID)) {
                        mClosedEvent = SystemClock.elapsedRealtime();
                        if (SystemClock.elapsedRealtime() - mOpenEvent < CAMERA_EVENT_DELAY_TIME
                                && mHandler.hasMessages(MSG_CAMERA_OPEN)) {
                            mHandler.removeMessages(MSG_CAMERA_OPEN);
                        }
                        mHandler.sendEmptyMessageDelayed(MSG_CAMERA_CLOSED,
                                CAMERA_EVENT_DELAY_TIME);
                    }
                }

                @Override
                public void onCameraUnavailable(@NonNull String cameraId) {
                    super.onCameraAvailable(cameraId);

                    if (cameraId.equals(FRONT_CAMERA_ID)) {
                        mOpenEvent = SystemClock.elapsedRealtime();
                        if (SystemClock.elapsedRealtime() - mClosedEvent < CAMERA_EVENT_DELAY_TIME
                                && mHandler.hasMessages(MSG_CAMERA_CLOSED)) {
                            mHandler.removeMessages(MSG_CAMERA_CLOSED);
                        }
                        mHandler.sendEmptyMessageDelayed(MSG_CAMERA_OPEN,
                                CAMERA_EVENT_DELAY_TIME);
                    }
                }
            };

    @Override
    public void onCreate() {
        CameraMotorController.calibrate();

        CameraManager cameraManager = getSystemService(CameraManager.class);
        cameraManager.registerAvailabilityCallback(mAvailabilityCallback, null);

        mPopupCameraPreferences = new PopupCameraPreferences(this);
        mSoundPool = new SoundPool.Builder().setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()).build();
        String[] soundNames = getResources().getStringArray(R.array.popupcamera_effects_names);
        mSounds = new int[soundNames.length];
        for (int i = 0; i < soundNames.length; i++) {
            mSounds[i] = mSoundPool.load(POPUP_SOUND_PATH + soundNames[i], 1);
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_CAMERA_CLOSED:
                CameraMotorController.setMotorDirection(CameraMotorController.DIRECTION_DOWN);
                CameraMotorController.setMotorEnabled();
                playSoundEffect(MSG_CAMERA_CLOSED);
                break;
            case MSG_CAMERA_OPEN:
                CameraMotorController.setMotorDirection(CameraMotorController.DIRECTION_UP);
                CameraMotorController.setMotorEnabled();
                playSoundEffect(MSG_CAMERA_OPEN);
                break;
        }
        return true;
    }

    private void playSoundEffect(int state) {
        AudioManager audioManager =
                (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        if (audioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            return;
        }
        int soundEffect = Integer.parseInt(mPopupCameraPreferences.getSoundEffect());
        if (soundEffect != -1) {
            if (state == MSG_CAMERA_CLOSED) {
                soundEffect++;
            }
            mSoundPool.play(mSounds[soundEffect], 1.0f, 1.0f, 0, 0, 1.0f);
        }
    }
}
