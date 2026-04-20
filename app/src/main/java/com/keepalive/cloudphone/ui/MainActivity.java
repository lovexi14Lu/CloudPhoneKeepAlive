package com.keepalive.cloudphone.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.keepalive.cloudphone.R;
import com.keepalive.cloudphone.root.RootHelper;
import com.keepalive.cloudphone.root.RootKeepAlive;
import com.keepalive.cloudphone.service.KeepAliveService;
import com.keepalive.cloudphone.service.UdpHeartbeatService;
import com.keepalive.cloudphone.session.SessionManager;

public class MainActivity extends Activity {

    private static final String TAG = "CloudPhoneKeepAlive";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private SharedPreferences prefs;
    private Handler handler;
    private TextView tvStatus;
    private TextView tvSessionInfo;
    private TextView tvStats;
    private TextView tvLog;
    private ScrollView scrollLog;
    private Switch switchAutoStart;
    private EditText etRemoteIp;
    private EditText etRemotePort;
    private EditText etInterval;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("keepalive_config", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());

        setContentViewFromLayout();
        initViews();
        loadConfig();
        requestPermissions();

        startStatusUpdater();
    }

    private void setContentViewFromLayout() {
        try {
            setContentView(R.layout.activity_main);
        } catch (Exception e) {
            setContentView(createSimpleLayout());
        }
    }

    private android.view.View createSimpleLayout() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("Cloud Phone Keep Alive");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 24);
        layout.addView(title);

        tvStatus = new TextView(this);
        tvStatus.setText("Status: Unknown");
        tvStatus.setPadding(0, 0, 0, 16);
        layout.addView(tvStatus);

        tvSessionInfo = new TextView(this);
        tvSessionInfo.setText("Session: Not loaded");
        tvSessionInfo.setPadding(0, 0, 0, 16);
        layout.addView(tvSessionInfo);

        tvStats = new TextView(this);
        tvStats.setText("Stats: N/A");
        tvStats.setPadding(0, 0, 0, 16);
        layout.addView(tvStats);

        Button btnStart = new Button(this);
        btnStart.setText("Start Service");
        btnStart.setOnClickListener(v -> startKeepAliveService());
        layout.addView(btnStart);

        Button btnStop = new Button(this);
        btnStop.setText("Stop Service");
        btnStop.setOnClickListener(v -> stopKeepAliveService());
        layout.addView(btnStop);

        Button btnHeartbeat = new Button(this);
        btnHeartbeat.setText("Send Heartbeat");
        btnHeartbeat.setOnClickListener(v -> sendManualHeartbeat());
        layout.addView(btnHeartbeat);

        tvLog = new TextView(this);
        tvLog.setText("Log:");
        tvLog.setPadding(0, 24, 0, 0);
        layout.addView(tvLog);

        return layout;
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvSessionInfo = findViewById(R.id.tv_session_info);
        tvStats = findViewById(R.id.tv_stats);
        tvLog = findViewById(R.id.tv_log);
        scrollLog = findViewById(R.id.scroll_log);
        switchAutoStart = findViewById(R.id.switch_auto_start);
        etRemoteIp = findViewById(R.id.et_remote_ip);
        etRemotePort = findViewById(R.id.et_remote_port);
        etInterval = findViewById(R.id.et_interval);

        Button btnStart = findViewById(R.id.btn_start);
        Button btnStop = findViewById(R.id.btn_stop);
        Button btnHeartbeat = findViewById(R.id.btn_heartbeat);
        Button btnLoadSession = findViewById(R.id.btn_load_session);
        Button btnSaveConfig = findViewById(R.id.btn_save_config);

        if (btnStart != null) btnStart.setOnClickListener(v -> startKeepAliveService());
        if (btnStop != null) btnStop.setOnClickListener(v -> stopKeepAliveService());
        if (btnHeartbeat != null) btnHeartbeat.setOnClickListener(v -> sendManualHeartbeat());
        if (btnLoadSession != null) btnLoadSession.setOnClickListener(v -> loadSession());
        if (btnSaveConfig != null) btnSaveConfig.setOnClickListener(v -> saveConfig());
    }

    private void loadConfig() {
        if (etRemoteIp != null) {
            etRemoteIp.setText(prefs.getString("udp_remote_ip", "127.0.0.1"));
        }
        if (etRemotePort != null) {
            etRemotePort.setText(String.valueOf(prefs.getInt("udp_remote_port", 10003)));
        }
        if (etInterval != null) {
            etInterval.setText(String.valueOf(prefs.getInt("heartbeat_interval", 30)));
        }
        if (switchAutoStart != null) {
            switchAutoStart.setChecked(prefs.getBoolean("auto_start", true));
        }
    }

    private void saveConfig() {
        SharedPreferences.Editor editor = prefs.edit();

        if (etRemoteIp != null) {
            editor.putString("udp_remote_ip", etRemoteIp.getText().toString());
        }
        if (etRemotePort != null) {
            try {
                editor.putInt("udp_remote_port", Integer.parseInt(etRemotePort.getText().toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (etInterval != null) {
            try {
                editor.putInt("heartbeat_interval", Integer.parseInt(etInterval.getText().toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (switchAutoStart != null) {
            editor.putBoolean("auto_start", switchAutoStart.isChecked());
        }

        editor.apply();
        Toast.makeText(this, "Config saved", Toast.LENGTH_SHORT).show();
    }

    private void loadSession() {
        SessionManager session = SessionManager.getInstance();

        if (RootHelper.hasRoot()) {
            RootKeepAlive.copySessionToApp(getFilesDir().getAbsolutePath());
        }

        boolean loaded = false;

        if (session.loadFromBin(getFilesDir().getAbsolutePath() + "/session/0.bin")) {
            loaded = true;
            appendLog("Session loaded from app files");
        } else if (session.autoLoad()) {
            loaded = true;
            appendLog("Session auto-loaded" + (RootHelper.hasRoot() ? " (via root)" : ""));
        }

        if (loaded) {
            updateSessionInfo();
        } else {
            appendLog("Session not found");
            Toast.makeText(this, "No session found. Grant root or copy 0.bin manually", Toast.LENGTH_LONG).show();
        }
    }

    private void startKeepAliveService() {
        saveConfig();
        Intent intent = new Intent(this, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();
        appendLog("KeepAlive service started");
    }

    private void stopKeepAliveService() {
        stopService(new Intent(this, UdpHeartbeatService.class));
        stopService(new Intent(this, KeepAliveService.class));
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
        appendLog("KeepAlive service stopped");
    }

    private void sendManualHeartbeat() {
        new Thread(() -> {
            SessionManager session = SessionManager.getInstance();
            if (!session.isValid()) {
                loadSession();
            }
            if (session.isValid()) {
                byte[] data = session.buildUdpPacket();
                try {
                    java.net.DatagramSocket socket = new java.net.DatagramSocket();
                    socket.setSoTimeout(10000);
                    java.net.InetAddress addr = java.net.InetAddress.getByName(session.getRemoteIp());
                    java.net.DatagramPacket packet = new java.net.DatagramPacket(
                            data, data.length, addr, session.getRemotePort());
                    socket.send(packet);
                    socket.close();
                    handler.post(() -> appendLog("Manual heartbeat sent successfully"));
                } catch (Exception e) {
                    handler.post(() -> appendLog("Manual heartbeat failed: " + e.getMessage()));
                }
            } else {
                handler.post(() -> appendLog("Session not loaded, cannot send heartbeat"));
            }
        }).start();
    }

    private void updateSessionInfo() {
        SessionManager session = SessionManager.getInstance();
        if (tvSessionInfo != null) {
            tvSessionInfo.setText("Session: " + session.getSummary());
        }
    }

    private void startStatusUpdater() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isServiceRunning()) {
                    String rootStatus = RootHelper.hasRoot() ? " [ROOT]" : " [No Root]";
                    if (tvStatus != null) tvStatus.setText("Status: Running" + rootStatus);
                } else {
                    if (tvStatus != null) tvStatus.setText("Status: Stopped");
                }
                handler.postDelayed(this, 3000);
            }
        }, 1000);
    }

    private boolean isServiceRunning() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    getSystemService(ACTIVITY_SERVICE);
            for (android.app.ActivityManager.RunningServiceInfo svc :
                    am.getRunningServices(Integer.MAX_VALUE)) {
                if (KeepAliveService.class.getName().equals(svc.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void appendLog(String msg) {
        if (tvLog != null) {
            String current = tvLog.getText().toString();
            String[] lines = current.split("\n");
            StringBuilder sb = new StringBuilder();
            sb.append(java.text.DateFormat.getTimeInstance().format(new java.util.Date()))
                    .append(" ").append(msg);
            int keepLines = Math.min(lines.length, 50);
            for (int i = Math.max(0, lines.length - keepLines); i < lines.length; i++) {
                sb.append("\n").append(lines[i]);
            }
            tvLog.setText(sb.toString());
            if (scrollLog != null) {
                scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
            }
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
