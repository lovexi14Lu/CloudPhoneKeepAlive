package com.keepalive.cloudphone.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.keepalive.cloudphone.service.KeepAliveService;
import com.keepalive.cloudphone.service.UdpHeartbeatService;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "CloudPhoneKeepAlive";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        Log.i(TAG, "BootReceiver: " + action);

        boolean shouldStart = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.keepalive.cloudphone.RESTART_KEEPALIVE".equals(action)
                || "com.keepalive.cloudphone.RESTART_HEARTBEAT".equals(action);

        if (shouldStart) {
            startKeepAliveServices(context);
        }
    }

    private void startKeepAliveServices(Context context) {
        try {
            Intent mainIntent = new Intent(context, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(mainIntent);
            } else {
                context.startService(mainIntent);
            }
            Log.i(TAG, "KeepAliveService started by BootReceiver");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start KeepAliveService: " + e.getMessage());
        }

        try {
            Intent heartbeatIntent = new Intent(context, UdpHeartbeatService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(heartbeatIntent);
            } else {
                context.startService(heartbeatIntent);
            }
            Log.i(TAG, "UdpHeartbeatService started by BootReceiver");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start UdpHeartbeatService: " + e.getMessage());
        }
    }
}
