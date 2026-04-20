package com.keepalive.cloudphone.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.keepalive.cloudphone.R;

import androidx.annotation.Nullable;

public class KeepAliveService extends Service {

    private static final String TAG = "CloudPhoneKeepAlive";
    private static final String CHANNEL_ID = "keepalive_main";
    private static final int NOTIFICATION_ID = 2000;

    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        acquireWakeLock();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        Log.i(TAG, "KeepAliveService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent heartbeatIntent = new Intent(this, UdpHeartbeatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(heartbeatIntent);
        } else {
            startService(heartbeatIntent);
        }

        Log.i(TAG, "KeepAliveService started, heartbeat service launched");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        Log.i(TAG, "KeepAliveService destroyed");
        sendBroadcast(new Intent("com.keepalive.cloudphone.RESTART_KEEPALIVE")
                .setPackage(getPackageName()));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, "CloudPhoneKeepAlive::Main");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Exception e) {
            Log.e(TAG, "WakeLock acquire failed: " + e.getMessage());
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_title),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text_service))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setAutoCancel(false);

        return builder.build();
    }
}
