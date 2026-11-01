package com.goal.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** JSON shape, validation and atomic local writes. Mirrors the Windows client. */
public final class Storage {
    private Storage() {}

    private static final int MAX_IMPORT_BYTES = 8 * 1024 * 1024;
    private static final DateTimeFormatter[] DUE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("M-d")
    };

    public static Models.AppData createSeed() {
        Models.AppData data = new Models.AppData();
        Models.Task focus = new Models.Task();
        focus.title = "给今天留出一段不被打扰的时间";
        focus.note = "25 分钟，从一件事开始。";
        focus.mode = "duration";
        focus.target = 25 * 60;
        focus.unit = "分钟";
        focus.createdAt = DateTimes.nowIso();
        data.tasks.add(focus);

        Models.Task first = new Models.Task();
        first.title = "写下今天最重要的一件事";
        first.note = "做到后提交材料，才算完成。";
        first.mode = "submit";
        first.dueDate = LocalDate.now().toString();
        first.createdAt = DateTimes.nowIso();
        data.tasks.add(first);

        Models.Task read = new Models.Task();
        read.title = "读完计划里的 10 页";
        read.note = "数值达到目标后自动完成。";
        read.mode = "variation";
        read.target = 10;
        read.unit = "页";
        read.createdAt = DateTimes.nowIso();
        data.tasks.add(read);
        return data;
    }

    public static Models.AppData load(File file) {
        if (!file.isFile()) return createSeed();
        try {
            String json = readFile(file);
            return parse(json);
        } catch (Exception error) {
            try {
                File broken = new File(file.getParentFile(),
                        "goal-data.broken." + System.currentTimeMillis() + ".json");
                copy(file, broken);
            } catch (Exception ignored) {
            }
            return createSeed();
        }
    }

    public static String readFile(File file) throws IOException {
        long length = file.length();
        if (length > MAX_IMPORT_BYTES) throw new IOException("文件过大（上限 8 MB）。");
        try (InputStream input = new java.io.FileInputStream(file)) {
            return readStream(input, MAX_IMPORT_BYTES);
        }
    }

    public static String readStream(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IOException("文件过大（上限 8 MB）。");
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    public static void save(File file, Models.AppData data) throws IOException {
        data.app = "goal";
        data.schemaVersion = 1;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("无法创建数据目录。");
        String json = toJson(data, true);
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(tmp)) {
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
        if (file.exists()) {
            File backup = new File(file.getPath() + ".bak");
            copy(file, backup);
        }
        if (!tmp.renameTo(file)) {
            copy(tmp, file);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    public static String exportJson(Models.AppData data) {
        return toJson(data, false);
    }

    public static String toJson(Models.AppData data, boolean touchExportedAt) {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "goal");
            root.put("schemaVersion", 1);
            root.put("appVersion", data.appVersion == null ? "1.0.0" : data.appVersion);
            root.put("exportedAt", touchExportedAt || data.exportedAt == null ? DateTimes.nowIso() : data.exportedAt);

            Models.User user = data.user == null ? new Models.User() : data.user;
            JSONObject u = new JSONObject();
            u.put("name", user.name);
            u.put("motto", user.motto);
            root.put("user", u);

            Models.Config config = data.config == null ? new Models.Config() : data.config;
            JSONObject c = new JSONObject();
            c.put("theme", config.theme);
            c.put("accent", config.accent);
            c.put("dark", config.dark);
            c.put("updatePrefix", config.updatePrefix);
            root.put("config", c);

            JSONArray tasks = new JSONArray();
            if (data.tasks != null) {
                for (Models.Task task : data.tasks) {
                    JSONObject t = new JSONObject();
                    t.put("id", task.id);
                    t.put("title", task.title);
                    t.put("note", task.note);
                    t.put("mode", task.mode);
                    t.put("target", task.target);
                    t.put("progress", task.progress);
                    t.put("unit", task.unit);
                    if (task.dueDate != null) t.put("dueDate", task.dueDate);
                    t.put("done", task.done);
                    if (task.completedAt != null) t.put("completedAt", task.completedAt);
                    if (task.timerStartedAt != null) t.put("timerStartedAt", task.timerStartedAt);
                    t.put("createdAt", task.createdAt);
                    JSONArray proofs = new JSONArray();
                    if (task.proofs != null) {
                        for (Models.TaskProof proof : task.proofs) {
                            if (proof == null) continue;
                            JSONObject item = new JSONObject();
                            item.put("name", proof.name);
                            item.put("storedName", proof.storedName);
                            item.put("kind", proof.kind);
                            item.put("size", proof.size);
                            if (proof.addedAt != null) item.put("addedAt", proof.addedAt);
                            proofs.put(item);
                        }
                    }
                    t.put("proofs", proofs);
                    tasks.put(t);
                }
            }
            root.put("tasks", tasks);
            return root.toString(2);
        } catch (JSONException error) {
            throw new IllegalStateException("无法生成备份数据。", error);
        }
    }

    public static Models.AppData parse(String json) {
        if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("文件是空的。");
        if (json.length() > MAX_IMPORT_BYTES) throw new IllegalArgumentException("文件过大（上限 8 MB）。");
        final JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException error) {
            throw new IllegalArgumentException("JSON 格式不正确：" + error.getMessage());
        }
        Models.AppData data = new Models.AppData();
        String app = root.optString("app", "");
        if (!"goal".equalsIgnoreCase(app))
            throw new IllegalArgumentException("这不是 goal 的备份文件（app 字段应为 goal）。");
        int schema = root.optInt("schemaVersion", 1);
        if (schema < 1 || schema > 1)
            throw new IllegalArgumentException("暂不支持 schemaVersion " + schema + "。");
        data.schemaVersion = schema;
        data.appVersion = root.optString("appVersion", "1.0.0");
        data.exportedAt = root.optString("exportedAt", DateTimes.nowIso());

        Models.User user = new Models.User();
        JSONObject u = root.optJSONObject("user");
        if (u != null) {
            String name = trim(u.optString("name", ""));
            String motto = trim(u.optString("motto", ""));
            user.name = name.isEmpty() ? "朋友" : cut(name, 24);
            user.motto = motto.isEmpty() ? "把目标，做小一点。" : cut(motto, 80);
        }
        data.user = user;

        Models.Config config = new Models.Config();
        JSONObject c = root.optJSONObject("config");
        if (c != null) {
            String theme = trim(c.optString("theme", "sand"));
            config.theme = theme.isEmpty() ? "sand" : theme;
            String accent = Theme.normalizeHex(c.optString("accent", "#D9653B"));
            config.accent = accent == null ? "#D9653B" : accent;
            config.dark = c.optBoolean("dark", false);
            String prefix = trim(c.optString("updatePrefix", "https://ghproxy.net/"));
            config.updatePrefix = prefix.isEmpty() ? "https://ghproxy.net/" : prefix;
            if (!config.updatePrefix.startsWith("http://") && !config.updatePrefix.startsWith("https://"))
                throw new IllegalArgumentException("更新加速前缀必须是 http(s) 地址。");
            if (!config.updatePrefix.endsWith("/")) config.updatePrefix += "/";
        }
        data.config = config;

        JSONArray tasks = root.optJSONArray("tasks");
        data.tasks = new ArrayList<>();
        if (tasks != null) {
            if (tasks.length() > 5000) throw new IllegalArgumentException("任务数量超过 5000 条。");
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject t = tasks.optJSONObject(i);
                if (t == null) continue;
                Models.Task task = parseTask(t, ids);
                data.tasks.add(task);
            }
        }
        return data;
    }

    private static Models.Task parseTask(JSONObject t, Set<String> ids) {
        Models.Task task = new Models.Task();
        String id = trim(t.optString("id", ""));
        if (id.isEmpty() || !ids.add(id)) {
            id = java.util.UUID.randomUUID().toString().replace("-", "");
            ids.add(id);
        }
        task.id = id;
        String title = trim(t.optString("title", ""));
        task.title = title.isEmpty() ? "未命名计划" : cut(title, 80);
        task.note = cut(trim(t.optString("note", "")), 500);
        String mode = t.optString("mode", "submit");
        task.mode = "duration".equals(mode) || "variation".equals(mode) || "submit".equals(mode) ? mode : "submit";
        task.done = t.optBoolean("done", false);
        task.completedAt = nullable(t, "completedAt");
        task.timerStartedAt = nullable(t, "timerStartedAt");
        task.dueDate = parseDue(nullable(t, "dueDate"));
        task.createdAt = t.optString("createdAt", DateTimes.nowIso());
        task.proofs = new ArrayList<>();
        JSONArray proofs = t.optJSONArray("proofs");
        if (proofs != null) {
            int limit = Math.min(proofs.length(), ProofStore.MAX_PROOFS);
            for (int i = 0; i < limit; i++) {
                JSONObject item = proofs.optJSONObject(i);
                if (item == null) continue;
                Models.TaskProof proof = new Models.TaskProof();
                String name = cut(trim(item.optString("name", "")), 120);
                String stored = trim(item.optString("storedName", ""));
                if (stored.isEmpty()) continue;
                stored = new File(stored).getName();
                if (stored.isEmpty() || "..".equals(stored) || ".".equals(stored)) continue;
                proof.name = name.isEmpty() ? stored : name;
                proof.storedName = cut(stored, 160);
                String kind = trim(item.optString("kind", "file"));
                proof.kind = "image".equals(kind) || "video".equals(kind)
                        || "audio".equals(kind) ? kind : "file";
                proof.size = Math.max(0L, item.optLong("size", 0L));
                proof.addedAt = nullable(item, "addedAt");
                task.proofs.add(proof);
            }
        }

        if ("duration".equals(task.mode)) {
            task.target = clamp(t.optInt("target", 25 * 60), 1, 24 * 3600);
            task.progress = clamp(t.optInt("progress", 0), 0, task.target);
            task.unit = "分钟";
        } else if ("variation".equals(task.mode)) {
            task.target = clamp(t.optInt("target", 1), 1, 100000);
            task.progress = clamp(t.optInt("progress", 0), 0, task.target);
            String unit = trim(t.optString("unit", "次"));
            task.unit = unit.isEmpty() ? "次" : cut(unit, 8);
        } else {
            task.target = 0;
            task.progress = 0;
            task.unit = "次";
            task.dueDate = parseDue(nullable(t, "dueDate"));
        }
        if (task.done && task.completedAt == null) task.completedAt = DateTimes.nowIso();
        if (!task.done) task.completedAt = null;
        if (task.timerStartedAt != null && (!"duration".equals(task.mode) || task.done))
            task.timerStartedAt = null;
        if (task.isRunning()) {
            long started = DateTimes.parseMillis(task.timerStartedAt);
            if (started <= 0) task.timerStartedAt = null;
        }
        return task;
    }

    private static String parseDue(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        for (DateTimeFormatter formatter : DUE_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(trimmed, formatter);
                int year = trimmed.length() <= 5 ? LocalDate.now().getYear() : date.getYear();
                if (trimmed.length() <= 5 && date.getMonthValue() < LocalDate.now().getMonthValue() - 1) year++;
                return LocalDate.of(year, date.getMonthValue(), date.getDayOfMonth()).toString();
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static String nullable(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) return null;
        String value = object.optString(key, null);
        return value == null || value.isEmpty() ? null : value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static String exportFileName() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return String.format(Locale.US, "goal-backup-%04d%02d%02d-%02d%02d.json",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute());
    }

    private static void copy(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("无法创建目录。");
        try (java.io.FileInputStream input = new java.io.FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
    }

    public static void copy(InputStream input, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("无法创建目录。");
        try (FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 15L * 1024 * 1024) throw new IOException("插件包过大（上限 15 MB）。");
                output.write(buffer, 0, read);
            }
        }
    }

    public static List<Models.Task> copyTasks(List<Models.Task> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }
}
