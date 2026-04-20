package com.keepalive.cloudphone.session;

import com.keepalive.cloudphone.root.RootHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean autoLoad() {
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
                "/data/local/tmp/0.bin",
                "/sdcard/Download/session/0.bin",
                "/sdcard/Download/0.bin",
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

        String found = RootHelper.findFile("0.bin", "/data/data");
        if (found != null && !found.isEmpty()) {
            byte[] data = RootHelper.readFileBinary(found);
            if (data != null && data.length > 100 && data.length < 8192) {
                if (loadFromBin(data)) {
                    try {
                        String appDir = getFilesDirForPkg(found);
                        if (appDir != null) {
                            String jsonPath = appDir + "/session/udp.json";
                            String json = RootHelper.readFile(jsonPath);
                            if (json != null) {
                                String ip = extractJsonValue(json, "remoteIp");
                                if (ip != null && !ip.isEmpty()) remoteIp = ip;
                                String port = extractJsonValue(json, "remotePort");
                                if (port != null && !port.isEmpty()) remotePort = Integer.parseInt(port);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    return true;
                }
            }
        }

        found = RootHelper.findFile("0.bin", "/data/local/tmp");
        if (found != null && !found.isEmpty()) {
            byte[] data = RootHelper.readFileBinary(found);
            if (data != null && data.length > 100 && data.length < 8192) {
                if (loadFromBin(data)) return true;
            }
        }

        found = RootHelper.findFile("udp.json", "/data/data");
        if (found != null && !found.isEmpty()) {
            String json = RootHelper.readFile(found);
            if (json != null) {
                String ip = extractJsonValue(json, "remoteIp");
                if (ip != null && !ip.isEmpty()) remoteIp = ip;
                String port = extractJsonValue(json, "remotePort");
                if (port != null && !port.isEmpty()) remotePort = Integer.parseInt(port);
            }
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
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(jsonPath));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            String json = sb.toString();
            String ip = extractJsonValue(json, "remoteIp");
            if (ip != null && !ip.isEmpty()) {
                remoteIp = ip;
            }
            String port = extractJsonValue(json, "remotePort");
            if (port != null && !port.isEmpty()) {
                remotePort = Integer.parseInt(port);
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
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
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

    public boolean isValid() {
        return sessionId != null && !sessionId.isEmpty()
                && certificate != null && !certificate.isEmpty();
    }

    public String getSessionId() {
        return sessionId != null ? sessionId : "";
    }

    public String getPlatform() {
        return platform != null ? platform : "";
    }

    public String getUserId() {
        return userId != null ? userId : "";
    }

    public String getVersion() {
        return version != null ? version : "";
    }

    public String getCertificate() {
        return certificate != null ? certificate : "";
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public long getSessionTimestamp() {
        return sessionTimestamp;
    }

    public String getSummary() {
        return String.format("sid=%s... platform=%s uid=%s ver=%s ip=%s:%d",
                sessionId != null && sessionId.length() > 16 ? sessionId.substring(0, 16) : sessionId,
                platform, userId, version, remoteIp, remotePort);
    }
}
