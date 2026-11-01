package com.goal.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Copies submit materials into app-private storage. Source files are never moved or uploaded. */
public final class ProofStore {
    public static final long MAX_FILE_BYTES = 200L * 1024 * 1024;
    public static final int MAX_PROOFS = 20;
    private ProofStore() {}

    public static File directory(Context context, String taskId) {
        return new File(new File(context.getFilesDir(), "proofs"), safe(taskId));
    }

    public static List<File> files(Context context, Models.Task task) {
        List<File> result = new ArrayList<>();
        if (task == null || !task.hasProofs()) return result;
        File dir = directory(context, task.id);
        for (Models.TaskProof proof : task.proofs) {
            File file = new File(dir, safe(proof.storedName));
            if (file.isFile()) result.add(file);
        }
        return result;
    }

    public static List<Models.TaskProof> copy(Context context, Models.Task task, List<Uri> uris) throws IOException {
        if (task == null) throw new IOException("计划不存在。");
        if (uris == null || uris.isEmpty()) throw new IOException("没有选择材料。");
        int room = MAX_PROOFS - (task.proofs == null ? 0 : task.proofs.size());
        if (room <= 0) throw new IOException("每份计划最多 " + MAX_PROOFS + " 份材料。");
        File dir = directory(context, task.id);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法创建材料目录。");
        List<Models.TaskProof> added = new ArrayList<>();
        try {
            int count = Math.min(room, uris.size());
            for (int i = 0; i < count; i++) {
                Uri uri = uris.get(i);
                if (uri == null) continue;
                added.add(copyOne(context, dir, uri));
            }
            if (added.isEmpty()) throw new IOException("没有读到可用的材料。");
            return added;
        } catch (IOException error) {
            for (Models.TaskProof proof : added) {
                //noinspection ResultOfMethodCallIgnored
                new File(dir, proof.storedName).delete();
            }
            throw error;
        }
    }

    private static Models.TaskProof copyOne(Context context, File dir, Uri uri) throws IOException {
        String display = displayName(context, uri);
        String mime = context.getContentResolver().getType(uri);
        String extension = extension(display, mime);
        String stored = UUID.randomUUID().toString().replace("-", "") + extension;
        File target = new File(dir, stored);
        long total = 0L;
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("无法读取「" + display + "」。");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_BYTES) throw new IOException("单个材料不能超过 200 MB。");
                output.write(buffer, 0, read);
            }
            output.flush();
            output.getFD().sync();
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw error;
        }
        Models.TaskProof proof = new Models.TaskProof();
        proof.name = display.length() > 120 ? display.substring(0, 120) : display;
        proof.storedName = stored;
        proof.kind = kindOf(mime, display);
        proof.size = total;
        proof.addedAt = DateTimes.nowIso();
        return proof;
    }

    public static void deleteForTask(Context context, String taskId) {
        deleteTree(directory(context, taskId));
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    public static String kindLabel(String kind) {
        if ("image".equals(kind)) return "图片";
        if ("video".equals(kind)) return "视频";
        if ("audio".equals(kind)) return "音频";
        return "文件";
    }

    private static String kindOf(String mime, String name) {
        String type = mime == null ? "" : mime.toLowerCase(Locale.US);
        if (type.startsWith("image/")) return "image";
        if (type.startsWith("video/")) return "video";
        if (type.startsWith("audio/")) return "audio";
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.matches(".*\\.(png|jpg|jpeg|webp|gif|bmp|heic|heif)$")) return "image";
        if (lower.matches(".*\\.(mp4|mov|mkv|avi|webm|3gp|m4v)$")) return "video";
        if (lower.matches(".*\\.(mp3|wav|m4a|aac|flac|ogg|opus)$")) return "audio";
        return "file";
    }

    private static String extension(String name, String mime) {
        if (name != null && name.matches(".*\\.[A-Za-z0-9]{1,8}$")) {
            String value = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.US);
            if (!value.isEmpty()) return "." + value;
        }
        String type = mime == null ? "" : mime.toLowerCase(Locale.US);
        if (type.startsWith("image/")) return ".img";
        if (type.startsWith("video/")) return ".mp4";
        if (type.startsWith("audio/")) return ".audio";
        return ".dat";
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) return value.trim();
                }
            }
        } catch (Exception ignored) {
        }
        String fallback = uri.getLastPathSegment();
        return fallback == null || fallback.trim().isEmpty() ? "材料" : fallback.trim();
    }

    private static String safe(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) {
            cleaned = UUID.randomUUID().toString().replace("-", "");
        }
        return cleaned;
    }
}
