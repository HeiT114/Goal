package com.goal.app;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Update manifest check and APK download. Used only for updates, never in the background. */
public final class UpdateManager {
    public static final String MANIFEST_PATH = "https://raw.githubusercontent.com/heit-233/goal/main/update.json";

    public interface Progress {
        void onProgress(double fraction);
    }

    public static final class Result {
        public final boolean hasUpdate;
        public final String latestVersion;
        public final String notes;
        public final String downloadUrl;
        public final String error;

        Result(boolean hasUpdate, String latestVersion, String notes, String downloadUrl, String error) {
            this.hasUpdate = hasUpdate;
            this.latestVersion = latestVersion;
            this.notes = notes;
            this.downloadUrl = downloadUrl;
            this.error = error;
        }
    }

    private UpdateManager() {}

    public static String currentVersion() {
        try {
            return BuildConfig.VERSION_NAME;
        } catch (Throwable ignored) {
            return "1.0.0";
        }
    }

    public static String normalizePrefix(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return "";
        if (!text.startsWith("http://") && !text.startsWith("https://")) return text;
        return text.endsWith("/") ? text : text + "/";
    }

    public static String buildUrl(String prefix, String target) {
        String clean = normalizePrefix(prefix);
        return clean.isEmpty() ? target : clean + target;
    }

    public static Result check(String platform, String prefix) {
        HttpURLConnection connection = null;
        try {
            String url = buildUrl(prefix, MANIFEST_PATH);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "goal/" + currentVersion());
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return fail("更新服务器返回 HTTP " + code + "。");
            String body = readAll(connection.getInputStream(), 512 * 1024);
            JSONObject root = new JSONObject(body);
            JSONObject target;
            if (root.optJSONObject(platform) != null) {
                target = root.optJSONObject(platform);
            } else if (root.has("version")) {
                target = root;
            } else {
                return fail("更新清单中没有 " + platform + " 平台的信息。");
            }
            String latest = target.optString("version", "").trim();
            if (latest.startsWith("v") || latest.startsWith("V")) latest = latest.substring(1);
            String notes = target.optString("notes", "");
            String download = target.optString("url", "");
            if (!validVersion(latest)) return fail("更新清单中的版本号无效。");
            return new Result(compare(latest, currentVersion()) > 0, latest, notes, download, null);
        } catch (Exception error) {
            return fail("检查更新失败：" + friendly(error));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static File download(String prefix, String target, File destination, Progress progress,
                                java.util.concurrent.atomic.AtomicBoolean cancelled) throws IOException {
        HttpURLConnection connection = null;
        try {
            String url = buildUrl(prefix, target);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(10 * 60 * 1000);
            connection.setRequestProperty("User-Agent", "goal/" + currentVersion());
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("下载失败，HTTP " + code + "。");
            long total = connection.getContentLengthLong();
            File parent = destination.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                throw new IOException("无法创建下载目录。");
            long read = 0;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[81920];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (cancelled != null && cancelled.get()) throw new IOException("下载已取消。");
                    output.write(buffer, 0, count);
                    read += count;
                    if (total > 0 && progress != null) progress.onProgress((double) read / total);
                }
            }
            return destination;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Result fail(String message) {
        return new Result(false, currentVersion(), "", "", message);
    }

    private static String readAll(InputStream input, int limit) throws IOException {
        return Storage.readStream(input, limit);
    }

    private static boolean validVersion(String value) {
        if (value == null || value.isEmpty()) return false;
        for (String part : value.split("\\.")) {
            if (part.isEmpty()) return false;
            for (int i = 0; i < part.length(); i++) if (!Character.isDigit(part.charAt(i))) return false;
        }
        return true;
    }

    public static int compare(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
            int x = i < a.length ? parseInt(a[i]) : 0;
            int y = i < b.length ? parseInt(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String friendly(Exception error) {
        if (error instanceof java.net.UnknownHostException) return "无法连接更新服务器，请检查网络或加速前缀。";
        if (error instanceof java.net.SocketTimeoutException) return "连接超时。";
        if (error instanceof java.io.IOException) {
            String message = error.getMessage();
            return message == null || message.isEmpty() ? "网络连接失败。" : message;
        }
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
