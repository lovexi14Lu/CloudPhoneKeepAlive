package com.keepalive.cloudphone.session;

import com.keepalive.cloudphone.root.RootHelper;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class SessionManager {

    private byte[] rawSessionData;
    private String sessionId;
    private String platform;
    private String userId;
    private String version;
    private String certificate;

    private String remoteIp = "127.0.0.1";
    private int remotePort = 10003;
    private long sessionTimestamp;

    private Context appContext;

    private static volatile SessionManager instance;

    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    private SessionManager() {
    }

    public void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public synchronized boolean loadFromAssets() {
        if (appContext == null) return false;

        try {
            AssetManager am = appContext.getAssets();
            File sessionDir = new File(appContext.getFilesDir(), "session");
            try {
                if (!sessionDir.exists()) sessionDir.mkdirs();
            } catch (Throwable t) {
                appendLog("mkdirs failed: " + t.getMessage());
            }

            try {
                InputStream is = am.open("0.bin");
                byte[] data = readStream(is);
                if (data != null && data.length > 100 && data.length < 8192) {
                    try {
                        File binFile = new File(sessionDir, "0.bin");
                        writeFile(binFile, data);
                    } catch (Throwable t) {
                        appendLog("write 0.bin failed: " + t.getMessage());
                    }
                    if (loadFromBin(data)) {
                        appendLog("0.bin loaded from assets");
                    }
                }
            } catch (IOException e) {
                appendLog("0.bin not in assets: " + e.getMessage());
            }

            if (!isValid()) {
                try {
                    InputStream bis = am.open("0_20260418_213917.bin");
                    byte[] backupData = readStream(bis);
                    if (backupData != null && backupData.length > 100 && backupData.length < 8192) {
                        try {
                            File backupFile = new File(sessionDir, "0_20260418_213917.bin");
                            writeFile(backupFile, backupData);
                        } catch (Throwable t) {
                            appendLog("write backup bin failed: " + t.getMessage());
                        }
                        if (loadFromBin(backupData)) {
                            appendLog("0_20260418_213917.bin loaded from assets");
                        }
                    }
                } catch (IOException ignored) {
                }
            }

            try {
                InputStream jis = am.open("udp.json");
                byte[] jsonData = readStream(jis);
                if (jsonData != null) {
                    try {
                        File jsonFile = new File(sessionDir, "udp.json");
                        writeFile(jsonFile, jsonData);
                    } catch (Throwable t) {
                        appendLog("write udp.json failed: " + t.getMessage());
                    }
                    String json = new String(jsonData, "UTF-8");
                    String ip = extractJsonValue(json, "remoteIp");
                    if (ip != null && !ip.isEmpty()) remoteIp = ip;
                    String port = extractJsonValue(json, "remotePort");
                    if (port != null && !port.isEmpty()) {
                        try { remotePort = Integer.parseInt(port); } catch (NumberFormatException ignored) {}
                    }
                    appendLog("udp.json loaded from assets");
                }
            } catch (IOException ignored) {
            }

            try {
                InputStream uis = am.open("udp_replay");
                byte[] replayData = readStream(uis);
                if (replayData != null) {
                    try {
                        File replayFile = new File(appContext.getFilesDir(), "udp_replay");
                        writeFile(replayFile, replayData);
                        replayFile.setExecutable(true, false);
                    } catch (Throwable t) {
                        appendLog("write udp_replay failed: " + t.getMessage());
                    }
                    appendLog("udp_replay extracted from assets");
                }
            } catch (IOException ignored) {
            }

            return isValid();
        } catch (Throwable t) {
            appendLog("loadFromAssets error: " + t.getMessage());
            return false;
        }
    }

    private byte[] readStream(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) != -1) {
            bos.write(buf, 0, len);
        }
        is.close();
        return bos.toByteArray();
    }

    private void writeFile(File file, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }

    private void appendLog(String msg) {
        if (appContext != null) {
            android.util.Log.d("SessionManager", msg);
        }
    }

    public synchronized boolean loadFromBin(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return false;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            rawSessionData = bos.toByteArray();
            parseSessionData();
            return isValid();
        } catch (Throwable t) {
            appendLog("loadFromBin error: " + t.getMessage());
            return false;
        }
    }

    public synchronized boolean autoLoad() {
        try {
            return doAutoLoad();
        } catch (Throwable t) {
            appendLog("autoLoad error: " + t.getMessage());
            return false;
        }
    }

    private boolean doAutoLoad() {
        String[] jsonPaths = {
                "/data/local/tmp/session/udp.json",
                "/data/local/tmp/udp.json",
                "/sdcard/Download/session/udp.json",
                "/sdcard/Download/udp.json"
        };

        for (String path : jsonPaths) {
            loadFromJson(path);
        }

        String[] binPaths = {
                "/data/local/tmp/session/0.bin",
                "/data/local/tmp/session/0_20260418_213917.bin",
                "/data/local/tmp/0.bin",
                "/data/local/tmp/0_20260418_213917.bin",
                "/sdcard/Download/session/0.bin",
                "/sdcard/Download/session/0_20260418_213917.bin",
                "/sdcard/Download/0.bin",
                "/sdcard/Download/0_20260418_213917.bin",
                "/sdcard/0.bin"
        };

        for (String path : binPaths) {
            if (loadFromBin(path)) {
                return true;
            }
        }

        if (rootAutoLoad()) {
            return true;
        }

        try {
            File tmpDir = new File("/data/local/tmp");
            if (tmpDir.exists() && tmpDir.isDirectory()) {
                if (searchSessionFile(tmpDir, 3)) return true;
            }
            File download = new File("/sdcard/Download");
            if (download.exists() && download.isDirectory()) {
                if (searchSessionFile(download, 2)) return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private boolean rootAutoLoad() {
        if (!RootHelper.hasRoot()) return false;

        try {
            String found = RootHelper.findFile("0.bin", "/data/local/tmp");
            if (found != null && !found.isEmpty()) {
                byte[] data = RootHelper.readFileBinary(found);
                if (data != null && data.length > 100 && data.length < 8192) {
                    if (loadFromBin(data)) return true;
                }
            }

            found = RootHelper.findFile("udp.json", "/data/local/tmp");
            if (found != null && !found.isEmpty()) {
                String json = RootHelper.readFile(found);
                if (json != null) {
                    String ip = extractJsonValue(json, "remoteIp");
                    if (ip != null && !ip.isEmpty()) remoteIp = ip;
                    String port = extractJsonValue(json, "remotePort");
                    if (port != null && !port.isEmpty()) {
                        try { remotePort = Integer.parseInt(port); } catch (NumberFormatException ignored) {}
                    }
                }
            }

            found = RootHelper.findFile("0.bin", "/sdcard/Download");
            if (found != null && !found.isEmpty()) {
                byte[] data = RootHelper.readFileBinary(found);
                if (data != null && data.length > 100 && data.length < 8192) {
                    if (loadFromBin(data)) return true;
                }
            }
        } catch (Throwable t) {
            appendLog("rootAutoLoad error: " + t.getMessage());
        }

        return false;
    }

    private String getFilesDirForPkg(String binPath) {
        if (binPath.contains("/data/data/")) {
            String[] parts = binPath.split("/");
            if (parts.length >= 4) {
                return "/data/data/" + parts[3] + "/files";
            }
        }
        if (binPath.contains("/data/user/0/")) {
            String[] parts = binPath.split("/");
            if (parts.length >= 5) {
                return "/data/user/0/" + parts[4] + "/files";
            }
        }
        return null;
    }

    private boolean loadFromJson(String jsonPath) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(jsonPath))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            String json = sb.toString();
            String ip = extractJsonValue(json, "remoteIp");
            if (ip != null && !ip.isEmpty()) {
                remoteIp = ip;
            }
            String port = extractJsonValue(json, "remotePort");
            if (port != null && !port.isEmpty()) {
                try { remotePort = Integer.parseInt(port); } catch (NumberFormatException ignored) {}
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        start++;
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private boolean searchSessionFile(File dir, int maxDepth) {
        if (maxDepth <= 0) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;

        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                if (searchSessionFile(f, maxDepth - 1)) return true;
            } else if (f.getName().equals("0.bin") || f.getName().endsWith(".bin")) {
                if (f.length() > 100 && f.length() < 8192) {
                    if (loadFromBin(f.getAbsolutePath())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public synchronized boolean loadFromBin(byte[] data) {
        rawSessionData = data;
        parseSessionData();
        return sessionId != null && !sessionId.isEmpty();
    }

    private void parseSessionData() {
        if (rawSessionData == null || rawSessionData.length < 16) {
            return;
        }

        if (rawSessionData.length > 8) {
            sessionTimestamp = ((rawSessionData[4] & 0xFFL))
                    | ((rawSessionData[5] & 0xFFL) << 8)
                    | ((rawSessionData[6] & 0xFFL) << 16)
                    | ((rawSessionData[7] & 0xFFL) << 24);
        }

        int offset = 16;

        if (offset < rawSessionData.length && rawSessionData[offset] == 0x01) {
            offset++;
            if (offset < rawSessionData.length && rawSessionData[offset] == 0x60) {
                offset++;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < rawSessionData.length; i++) {
            byte b = rawSessionData[i];
            if (b == 0x00) {
                if (sb.length() > 0) break;
                continue;
            }
            if (b >= 0x20 && b <= 0x7E) {
                sb.append((char) b);
            } else if (b < 0x20 && sb.length() > 0) {
                break;
            }
        }

        String mainPart = sb.toString();

        if (mainPart.contains("|")) {
            String[] parts = mainPart.split("\\|", -1);
            if (parts.length >= 1) sessionId = parts[0].trim();
            if (parts.length >= 2) platform = parts[1].trim();
            if (parts.length >= 3) userId = parts[2].trim();
            if (parts.length >= 4) {
                String verPart = parts[3].trim();
                if (verPart.contains("+")) {
                    version = verPart.substring(0, verPart.indexOf('+'));
                } else {
                    version = verPart;
                }
            }
        }

        int certStart = findCertificateStart();
        if (certStart > 0 && certStart < rawSessionData.length) {
            StringBuilder certSb = new StringBuilder();
            for (int i = certStart; i < rawSessionData.length; i++) {
                byte b = rawSessionData[i];
                if (b == 0x00) continue;
                if (b >= 0x20 && b < 0x7F) {
                    certSb.append((char) b);
                }
            }
            certificate = certSb.toString().trim();
        }
    }

    private int findCertificateStart() {
        if (rawSessionData == null) return -1;
        String marker = "MIGb";
        for (int i = 0; i < rawSessionData.length - marker.length(); i++) {
            boolean found = true;
            for (int j = 0; j < marker.length(); j++) {
                if (rawSessionData[i + j] != marker.charAt(j)) {
                    found = false;
                    break;
                }
            }
            if (found) return i;
        }
        return -1;
    }

    public synchronized byte[] buildUdpPacket() {
        if (rawSessionData == null) return new byte[0];
        int end = rawSessionData.length;
        while (end > 0 && rawSessionData[end - 1] == 0x00) {
            end--;
        }
        if (end == 0) return new byte[0];
        return Arrays.copyOf(rawSessionData, end);
    }

    public synchronized boolean isValid() {
        return sessionId != null && !sessionId.isEmpty()
                && certificate != null && !certificate.isEmpty();
    }

    public synchronized String getSessionId() {
        return sessionId != null ? sessionId : "";
    }

    public synchronized String getPlatform() {
        return platform != null ? platform : "";
    }

    public synchronized String getUserId() {
        return userId != null ? userId : "";
    }

    public synchronized String getVersion() {
        return version != null ? version : "";
    }

    public synchronized String getCertificate() {
        return certificate != null ? certificate : "";
    }

    public synchronized String getRemoteIp() {
        return remoteIp;
    }

    public synchronized void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }

    public synchronized int getRemotePort() {
        return remotePort;
    }

    public synchronized void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public synchronized long getSessionTimestamp() {
        return sessionTimestamp;
    }

    public synchronized String getSummary() {
        String sid = sessionId != null ? sessionId : "null";
        return String.format("sid=%s... platform=%s uid=%s ver=%s ip=%s:%d",
                sid.length() > 16 ? sid.substring(0, 16) : sid,
                platform != null ? platform : "null",
                userId != null ? userId : "null",
                version != null ? version : "null",
                remoteIp, remotePort);
    }
}
