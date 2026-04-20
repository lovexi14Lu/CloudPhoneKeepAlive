package com.keepalive.cloudphone.root;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class RootHelper {

    private static final String TAG = "CloudPhoneKeepAlive";
    private static volatile Boolean hasRoot = null;

    public static boolean hasRoot() {
        if (hasRoot != null) return hasRoot;
        hasRoot = checkRoot();
        return hasRoot;
    }

    private static boolean checkRoot() {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("id\n");
            os.writeBytes("exit\n");
            os.flush();
            boolean ok = p.waitFor(5, TimeUnit.SECONDS);
            if (ok && p.exitValue() == 0) {
                Log.i(TAG, "Root access granted");
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Root not available: " + t.getMessage());
        }
        return false;
    }

    public static String exec(String cmd) {
        return exec(cmd, 10);
    }

    public static String exec(String cmd, int timeoutSec) {
        if (!hasRoot()) {
            Log.w(TAG, "No root, cannot exec: " + cmd);
            return null;
        }

        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            boolean ok = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!ok) {
                p.destroyForcibly();
                Log.w(TAG, "Root command timeout: " + cmd);
                return null;
            }

            if (p.exitValue() != 0) {
                BufferedReader errReader = new BufferedReader(
                        new InputStreamReader(p.getErrorStream()));
                StringBuilder errSb = new StringBuilder();
                while ((line = errReader.readLine()) != null) {
                    errSb.append(line).append("\n");
                }
                Log.w(TAG, "Root command failed (exit=" + p.exitValue() + "): " + errSb);
                return null;
            }

            return sb.toString().trim();
        } catch (Throwable t) {
            Log.e(TAG, "Root exec error: " + t.getMessage());
            return null;
        }
    }

    public static boolean execSilent(String cmd) {
        if (!hasRoot()) return false;
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            boolean ok = p.waitFor(10, TimeUnit.SECONDS);
            return ok && p.exitValue() == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String findFile(String name, String searchDir) {
        String result = exec("find " + searchDir + " -name " + name + " -type f 2>/dev/null | head -1", 15);
        if (result != null && !result.isEmpty()) {
            return result.trim();
        }
        return null;
    }

    public static String readFile(String path) {
        return exec("cat '" + path + "'", 5);
    }

    public static byte[] readFileBinary(String path) {
        String base64 = exec("base64 '" + path + "'", 10);
        if (base64 == null || base64.isEmpty()) return null;
        try {
            return android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        } catch (Throwable t) {
            Log.e(TAG, "Base64 decode failed: " + t.getMessage());
            return null;
        }
    }

    public static boolean writeFile(String path, String content) {
        return execSilent("echo '" + content.replace("'", "'\\''") + "' > '" + path + "'");
    }

    public static boolean copyFile(String src, String dst) {
        return execSilent("cp '" + src + "' '" + dst + "' && chmod 644 '" + dst + "'");
    }

    public static boolean mkdirs(String path) {
        return execSilent("mkdir -p '" + path + "'");
    }

    public static boolean setPermissive() {
        String current = exec("getenforce", 3);
        if ("Enforcing".equals(current)) {
            return execSilent("setenforce 0");
        }
        return true;
    }
}
