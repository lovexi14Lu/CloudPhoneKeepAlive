package com.keepalive.cloudphone.root;

import android.util.Log;

public class RootKeepAlive {

    private static final String TAG = "CloudPhoneKeepAlive";
    private static final String MODULE_PKG = "com.keepalive.cloudphone";

    public static boolean apply() {
        if (!RootHelper.hasRoot()) {
            Log.w(TAG, "RootKeepAlive: no root");
            return false;
        }

        boolean ok = true;
        ok &= setOomScore();
        ok &= disableFreeze();
        ok &= protectWithIptables();
        ok &= setProcessPriority();
        ok &= setSelinuxPermissive();
        ok &= persistProps();

        if (ok) {
            Log.i(TAG, "RootKeepAlive: all root protections applied");
        }
        return ok;
    }

    private static boolean setOomScore() {
        String pids = RootHelper.exec("pidof " + MODULE_PKG, 5);
        if (pids == null || pids.isEmpty()) {
            Log.w(TAG, "RootKeepAlive: module not running");
            return false;
        }

        boolean ok = true;
        for (String pid : pids.trim().split("\\s+")) {
            pid = pid.trim();
            if (pid.isEmpty()) continue;

            ok &= RootHelper.execSilent("echo -1000 > /proc/" + pid + "/oom_score_adj");
            ok &= RootHelper.execSilent("echo 0 > /proc/" + pid + "/oom_adj");
            ok &= RootHelper.execSilent("chmod 444 /proc/" + pid + "/oom_score_adj");
        }
        return ok;
    }

    private static boolean disableFreeze() {
        boolean ok = true;

        String pids = RootHelper.exec("pidof " + MODULE_PKG, 5);
        if (pids == null || pids.isEmpty()) return false;

        for (String pid : pids.trim().split("\\s+")) {
            pid = pid.trim();
            if (pid.isEmpty()) continue;

            ok &= RootHelper.execSilent("echo 0 > /proc/" + pid + "/freezer_state 2>/dev/null");
            String cgroup = RootHelper.exec("cat /proc/" + pid + "/cgroup 2>/dev/null | grep freezer | head -1", 5);
            if (cgroup != null && cgroup.contains("freezer")) {
                String freezerPath = cgroup.contains(":") ? cgroup.substring(cgroup.lastIndexOf(":") + 1) : "";
                if (!freezerPath.isEmpty()) {
                    ok &= RootHelper.execSilent("echo THAWED > /sys/fs/cgroup/freezer" + freezerPath + "/freezer.state 2>/dev/null");
                    ok &= RootHelper.execSilent("echo THAWED > /sys/fs/cgroup/freezer/freezer.state 2>/dev/null");
                }
            }
        }

        ok &= RootHelper.execSilent("echo 0 > /sys/module/lowmemorykiller/parameters/enable 2>/dev/null || true");

        return ok;
    }

    private static boolean protectWithIptables() {
        boolean ok = true;

        ok &= RootHelper.execSilent("iptables -A OUTPUT -p udp --dport 10003 -j ACCEPT 2>/dev/null");
        ok &= RootHelper.execSilent("ip6tables -A OUTPUT -p udp --dport 10003 -j ACCEPT 2>/dev/null");

        ok &= RootHelper.execSilent("iptables -A OUTPUT -p udp -s 127.0.0.1 -j ACCEPT 2>/dev/null");

        return ok;
    }

    private static boolean setProcessPriority() {
        String pids = RootHelper.exec("pidof " + MODULE_PKG, 5);
        if (pids == null || pids.isEmpty()) return false;

        boolean ok = true;
        for (String pid : pids.trim().split("\\s+")) {
            pid = pid.trim();
            if (pid.isEmpty()) continue;

            ok &= RootHelper.execSilent("renice -20 " + pid + " 2>/dev/null");
            ok &= RootHelper.execSilent("ionice -c1 -n0 -p " + pid + " 2>/dev/null");
        }
        return ok;
    }

    private static boolean setSelinuxPermissive() {
        String current = RootHelper.exec("getenforce 2>/dev/null", 3);
        if ("Enforcing".equals(current)) {
            boolean ok = RootHelper.execSilent("setenforce 0 2>/dev/null");
            if (ok) {
                Log.i(TAG, "RootKeepAlive: SELinux set to permissive");
            }
            return ok;
        }
        return true;
    }

    private static boolean persistProps() {
        boolean ok = true;

        ok &= RootHelper.execSilent("settings put global always_finish_activities 0 2>/dev/null");
        ok &= RootHelper.execSilent("settings put global app_standby_enabled 0 2>/dev/null");
        ok &= RootHelper.execSilent("settings put global adaptive_battery_management_enabled 0 2>/dev/null");

        ok &= RootHelper.execSilent("cmd appops set " + MODULE_PKG + " RUN_IN_BACKGROUND allow 2>/dev/null");
        ok &= RootHelper.execSilent("cmd appops set " + MODULE_PKG + " RUN_ANY_IN_BACKGROUND allow 2>/dev/null");
        ok &= RootHelper.execSilent("cmd appops set " + MODULE_PKG + " WAKE_LOCK allow 2>/dev/null");

        return ok;
    }

    public static boolean copySessionToApp(String appFilesDir) {
        if (!RootHelper.hasRoot()) return false;

        try {
            String sessionDir = appFilesDir + "/session";
            RootHelper.mkdirs(sessionDir);

            String found = RootHelper.findFile("0.bin", "/data/local/tmp");
            if (found == null) {
                found = RootHelper.findFile("0.bin", "/sdcard/Download");
            }
            if (found != null) {
                RootHelper.copyFile(found, sessionDir + "/0.bin");
            }

            found = RootHelper.findFile("udp.json", "/data/local/tmp");
            if (found == null) {
                found = RootHelper.findFile("udp.json", "/sdcard/Download");
            }
            if (found != null) {
                RootHelper.copyFile(found, sessionDir + "/udp.json");
            }

            RootHelper.execSilent("chmod -R 755 " + sessionDir);

            return new java.io.File(sessionDir + "/0.bin").exists();
        } catch (Throwable t) {
            android.util.Log.e(TAG, "copySessionToApp error: " + t.getMessage());
            return false;
        }
    }
}
