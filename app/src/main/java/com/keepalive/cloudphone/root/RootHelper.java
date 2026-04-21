package com.keepalive.cloudphone.root;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class RootHelper {

    private static final String TAG = "CloudPhoneKeepAlive";
    private static volatile Boolean hasRoot = null;
    private static final int ROOT_CHECK_RETRIES = 3;
    private static final int ROOT_CHECK_INTERVAL_MS = 2000;

    public static synchronized boolean hasRoot() {
        if (hasRoot != null) return hasRoot;
        hasRoot = checkRoot();
        return hasRoot;
    }

    public static synchronized void resetRootStatus() {
        hasRoot = null;
    }

    public static Boolean cachedRootStatus() {
        return hasRoot;
    }

    private static boolean checkRoot() {
        for (int i = 0; i < ROOT_CHECK_RETRIES; i++) {
            Process p = null;
            try {
                p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("id\n");
                os.writeBytes("exit\n");
                os.flush();
                os.close();
                boolean ok = p.waitFor(10, TimeUnit.SECONDS);
                if (ok && p.exitValue() == 0) {
                    Log.i(TAG, "Root access granted on attempt " + (i + 1));
                    return true;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Root check attempt " + (i + 1) + " failed: " + t.getMessage());
            } finally {
                if (p != null) p.destroyForcibly();
            }
            if (i < ROOT_CHECK_RETRIES - 1) {
                try { Thread.sleep(ROOT_CHECK_INTERVAL_MS); } catch (InterruptedException ignored) { break; }
            }
        }
        Log.w(TAG, "Root not available after " + ROOT_CHECK_RETRIES + " attempts");
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

        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            os.close();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            boolean ok = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!ok) {
                Log.w(TAG, "Root command timeout: " + cmd);
                return null;
            }

            if (p.exitValue() != 0) {
                BufferedReader errReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                StringBuilder errSb = new StringBuilder();
                while ((line = errReader.readLine()) != null) {
                    errSb.append(line).append("\n");
                }
                errReader.close();
                Log.w(TAG, "Root command failed (exit=" + p.exitValue() + "): " + errSb);
                return null;
            }

            return sb.toString().trim();
        } catch (Throwable t) {
            Log.e(TAG, "Root exec error: " + t.getMessage());
            return null;
        } finally {
            if (p != null) p.destroyForcibly();
        }
    }

    public static boolean execSilent(String cmd) {
        if (!hasRoot()) return false;
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            boolean ok = p.waitFor(10, TimeUnit.SECONDS);
            return ok && p.exitValue() == 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (p != null) p.destroyForcibly();
        }
    }

    private static String shellEscape(String s) {
        return s.replace("'", "'\\''");
    }

    public static String findFile(String name, String searchDir) {
        String result = exec("find '" + shellEscape(searchDir) + "' -name '" + shellEscape(name) + "' -type f 2>/dev/null | head -1", 15);
        if (result != null && !result.isEmpty()) {
            return result.trim();
        }
        return null;
    }

    public static String readFile(String path) {
        return exec("cat '" + shellEscape(path) + "'", 5);
    }

    public static byte[] readFileBinary(String path) {
        String base64 = exec("base64 '" + shellEscape(path) + "'", 10);
        if (base64 == null || base64.isEmpty()) return null;
        try {
            return android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        } catch (Throwable t) {
            Log.e(TAG, "Base64 decode failed: " + t.getMessage());
            return null;
        }
    }

    public static boolean writeFile(String path, String content) {
        return execSilent("echo '" + content.replace("'", "'\\''") + "' > '" + shellEscape(path) + "'");
    }

    public static boolean copyFile(String src, String dst) {
        return execSilent("cp '" + shellEscape(src) + "' '" + shellEscape(dst) + "' && chmod 644 '" + shellEscape(dst) + "'");
    }

    public static boolean mkdirs(String path) {
        return execSilent("mkdir -p '" + shellEscape(path) + "'");
    }

    public static boolean setPermissive() {
        String current = exec("getenforce", 3);
        if ("Enforcing".equals(current)) {
            return execSilent("setenforce 0");
        }
        return true;
    }
}
