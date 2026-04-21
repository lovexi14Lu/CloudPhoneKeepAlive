package com.keepalive.cloudphone.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.keepalive.cloudphone.R;

import androidx.annotation.Nullable;

import com.keepalive.cloudphone.root.RootHelper;
import com.keepalive.cloudphone.root.RootKeepAlive;
import com.keepalive.cloudphone.session.SessionManager;

import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class UdpHeartbeatService extends Service {

    private static final String TAG = "CloudPhoneKeepAlive";
    private static final String CHANNEL_ID = "keepalive_heartbeat";
    private static final int NOTIFICATION_ID = 2001;

    private static final int DEFAULT_INTERVAL = 30;
    private static final int DEFAULT_TIMEOUT = 10;
    private static final int DEFAULT_MAX_RETRY = 5;
    private static final int DEFAULT_RECONNECT_DELAY = 10;
    private static final int ROOT_REFRESH_CYCLES = 20;
    private static final long WAKELOCK_TIMEOUT_MS = 24 * 60 * 60 * 1000L;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger failCount = new AtomicInteger(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failTotalCount = new AtomicLong(0);
    private final AtomicLong reconnectCount = new AtomicLong(0);
    private final AtomicLong startTime = new AtomicLong(0);
    private final AtomicInteger cycleCount = new AtomicInteger(0);

    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private volatile Thread heartbeatThread;

    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("keepalive_config", MODE_PRIVATE);

        SessionManager.getInstance().init(this);

        acquireWakeLock();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        startTime.set(System.currentTimeMillis());
        Log.i(TAG, "UdpHeartbeatService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "Heartbeat already running");
            return START_STICKY;
        }

        new Thread(() -> {
            try {
                if (RootHelper.hasRoot()) {
                    RootKeepAlive.apply();
                    RootKeepAlive.copySessionToApp(getFilesDir().getAbsolutePath());
                }
                loadSession();
            } catch (Throwable t) {
                Log.e(TAG, "Init error: " + t.getMessage());
            }
            startHeartbeatLoop();
        }, "UdpInitThread").start();

        Log.i(TAG, "UdpHeartbeatService started");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running.set(false);
        Thread t = heartbeatThread;
        if (t != null) {
            t.interrupt();
        }
        releaseWakeLock();
        Log.i(TAG, "UdpHeartbeatService destroyed");

        sendBroadcast(new Intent("com.keepalive.cloudphone.RESTART_HEARTBEAT")
                .setPackage(getPackageName()));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void loadSession() {
        SessionManager session = SessionManager.getInstance();

        if (session.isValid()) return;

        if (session.loadFromAssets()) {
            Log.i(TAG, "Session loaded from assets");
        }

        String binPath = prefs.getString("session_bin_path", "");
        if (binPath != null && !binPath.isEmpty() && session.loadFromBin(binPath)) {
            Log.i(TAG, "Session loaded from config: " + binPath);
        } else if (session.loadFromBin(getFilesDir().getAbsolutePath() + "/session/0.bin")) {
            Log.i(TAG, "Session loaded from app files");
        } else if (session.autoLoad()) {
            Log.i(TAG, "Session auto-loaded");
        } else {
            Log.w(TAG, "Failed to load session from any path, will retry");
            return;
        }

        session.setRemoteIp(prefs.getString("udp_remote_ip", "127.0.0.1"));
        session.setRemotePort(prefs.getInt("udp_remote_port", 10003));
        Log.i(TAG, "Session: " + session.getSummary());
    }

    private void startHeartbeatLoop() {
        heartbeatThread = new Thread(() -> {
            Log.i(TAG, "Heartbeat thread started");

            while (running.get()) {
                try {
                    boolean result = sendHeartbeat();

                    if (result) {
                        int prevFails = failCount.getAndSet(0);
                        if (prevFails > 0) {
                            Log.i(TAG, "Heartbeat recovered after " + prevFails + " failures");
                        }
                        successCount.incrementAndGet();
                    } else {
                        int fails = failCount.incrementAndGet();
                        failTotalCount.incrementAndGet();
                        Log.w(TAG, "Heartbeat failed (consecutive: " + fails + ")");

                        int maxRetry = prefs.getInt("max_fail_count", DEFAULT_MAX_RETRY);
                        maxRetry = Math.max(1, maxRetry);
                        if (fails >= maxRetry) {
                            Log.e(TAG, "Max retries reached, reconnecting...");
                            reconnectCount.incrementAndGet();
                            failCount.set(0);
                            int delay = prefs.getInt("reconnect_delay", DEFAULT_RECONNECT_DELAY);
                            delay = Math.max(1, Math.min(delay, 300));
                            Thread.sleep(delay * 1000L);
                            loadSession();
                            continue;
                        }
                    }

                    int interval = calculateInterval();

                    int cycle = cycleCount.incrementAndGet();
                    if (RootHelper.hasRoot() && cycle % ROOT_REFRESH_CYCLES == 0) {
                        try {
                            RootKeepAlive.apply();
                        } catch (Throwable t) {
                            Log.e(TAG, "RootKeepAlive refresh error: " + t.getMessage());
                        }
                    }

                    Thread.sleep(interval * 1000L);

                } catch (InterruptedException e) {
                    Log.i(TAG, "Heartbeat thread interrupted");
                    break;
                } catch (Throwable t) {
                    Log.e(TAG, "Heartbeat error: " + t.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }

            Log.i(TAG, "Heartbeat thread stopped");
        }, "UdpHeartbeatThread");

        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private int calculateInterval() {
        int baseInterval = prefs.getInt("heartbeat_interval", DEFAULT_INTERVAL);
        baseInterval = Math.max(1, baseInterval);
        int fails = failCount.get();

        if (fails > 0) {
            int adaptive = baseInterval / 2;
            return Math.max(adaptive, 5);
        }
        return baseInterval;
    }

    private boolean sendHeartbeat() {
        SessionManager session = SessionManager.getInstance();
        if (!session.isValid()) {
            Log.e(TAG, "Session invalid, cannot send heartbeat");
            return false;
        }

        byte[] packetData = session.buildUdpPacket();
        if (packetData.length == 0) {
            return false;
        }

        String host = session.getRemoteIp();
        int port = session.getRemotePort();
        int timeout = prefs.getInt("heartbeat_timeout", DEFAULT_TIMEOUT);
        timeout = Math.max(1, timeout);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeout * 1000);

            InetAddress address = InetAddress.getByName(host);
            DatagramPacket sendPacket = new DatagramPacket(
                    packetData, packetData.length, address, port);
            socket.send(sendPacket);

            Log.d(TAG, "UDP heartbeat sent: " + packetData.length + " bytes -> " + host + ":" + port);

            try {
                byte[] recvBuf = new byte[4096];
                DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                socket.receive(recvPacket);
                Log.d(TAG, "UDP response received: " + recvPacket.getLength() + " bytes");
            } catch (SocketTimeoutException e) {
                Log.d(TAG, "Heartbeat sent (no response / timeout - normal for UDP)");
            }

            return true;

        } catch (Exception e) {
            Log.w(TAG, "Heartbeat send failed: " + e.getMessage());
            return false;
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, "CloudPhoneKeepAlive::Heartbeat");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(WAKELOCK_TIMEOUT_MS);
                Log.i(TAG, "WakeLock acquired with timeout 24h");
            }
        } catch (Exception e) {
            Log.e(TAG, "WakeLock acquire failed: " + e.getMessage());
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.i(TAG, "WakeLock released");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_title),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notification_text_udp));
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
                .setContentText(getString(R.string.notification_text_udp))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setAutoCancel(false);

        return builder.build();
    }

    public String getStats() {
        long uptime = (System.currentTimeMillis() - startTime.get()) / 1000;
        return String.format("uptime=%ds success=%d fail=%d reconnect=%d consecutive_fail=%d",
                uptime, successCount.get(), failTotalCount.get(),
                reconnectCount.get(), failCount.get());
    }
}
