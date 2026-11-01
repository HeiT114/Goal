package com.goal.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;

/** Read-only access to copied submit materials so the system can view them. */
public class ProofProvider extends ContentProvider {
    public static final String AUTHORITY = "com.goal.app.proofs";

    public static Uri uriFor(String taskId, String storedName) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(taskId).appendPath(storedName).build();
    }

    public static String mimeOf(String storedName) {
        String name = storedName == null ? "" : storedName.toLowerCase(Locale.US);
        if (name.matches(".*\\.(png|jpg|jpeg|webp|gif|bmp|heic|heif)$")) return "image/*";
        if (name.matches(".*\\.(mp4|mov|mkv|avi|webm|3gp|m4v)$")) return "video/*";
        if (name.matches(".*\\.(mp3|wav|m4a|aac|flac|ogg|opus)$")) return "audio/*";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".json")) return "text/plain";
        return "application/octet-stream";
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (uri == null || uri.getPathSegments() == null || uri.getPathSegments().size() != 2)
            throw new FileNotFoundException("无效的材料路径。");
        String taskId = uri.getPathSegments().get(0);
        String stored = uri.getPathSegments().get(1);
        if (!safeSegment(taskId) || !safeSegment(stored))
            throw new FileNotFoundException("无效的材料路径。");
        if (getContext() == null) throw new FileNotFoundException("应用上下文不可用。");
        File root = new File(getContext().getFilesDir(), "proofs");
        File file = new File(new File(root, taskId), stored);
        if (!file.isFile()) throw new FileNotFoundException("材料不存在。");
        return file;
    }

    private static boolean safeSegment(String value) {
        return value != null && !value.isEmpty() && !".".equals(value) && !"..".equals(value)
                && value.matches("[A-Za-z0-9._-]+");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File file = resolve(uri);
            String[] columns = projection != null ? projection
                    : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor cursor = new MatrixCursor(columns, 1);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
                else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
                else row[i] = null;
            }
            cursor.addRow(row);
            return cursor;
        } catch (FileNotFoundException error) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        try {
            return mimeOf(resolve(uri).getName());
        } catch (FileNotFoundException error) {
            return "application/octet-stream";
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = resolve(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
