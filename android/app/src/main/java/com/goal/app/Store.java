package com.goal.app;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The single source of truth for plans, profile and configuration. */
public final class Store {
    public interface Listener {
        void onChanged();
        void onCompleted(Models.Task task, String via);
        void onNotice(String message);
    }

    private static final String TAG = "goal-store";
    private final Context context;
    private final File file;
    private final List<Listener> listeners = new ArrayList<>();
    private Models.AppData data;
    private long lastAutoSave = 0L;

    public Store(Context context) {
        this.context = context.getApplicationContext();
        this.file = new File(this.context.getFilesDir(), "goal-data.json");
        this.data = Storage.load(file);
    }

    public Models.AppData data() {
        return data;
    }

    public File dataFile() {
        return file;
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public List<Models.Task> tasks() {
        List<Models.Task> copy = new ArrayList<>(data.tasks);
        copy.sort(Comparator
                .comparing((Models.Task task) -> task.done)
                .thenComparing(task -> task.dueDate == null || task.dueDate.isEmpty() ? 1 : 0)
                .thenComparing(task -> task.dueDate == null ? "9999-99-99" : task.dueDate)
                .thenComparing((Models.Task task) -> DateTimes.parseMillis(task.createdAt), Comparator.reverseOrder()));
        return copy;
    }

    public int activeCount() {
        int count = 0;
        for (Models.Task task : data.tasks) if (!task.done) count++;
        return count;
    }

    public int doneCount() {
        return data.tasks.size() - activeCount();
    }

    public int percent() {
        return data.tasks.isEmpty() ? 0 : Math.round(doneCount() * 100f / data.tasks.size());
    }

    public Models.Task addTask(String title, String note, String mode, int target, String unit, String dueDate) {
        Models.Task task = new Models.Task();
        task.id = UUID.randomUUID().toString().replace("-", "");
        task.title = cleanTitle(title);
        task.note = cleanNote(note);
        task.mode = validMode(mode);
        task.dueDate = dueDate == null || dueDate.isEmpty() ? null : dueDate;
        task.createdAt = DateTimes.nowIso();
        if ("duration".equals(task.mode)) {
            task.target = Math.max(60, Math.min(24 * 3600, target <= 0 ? 25 * 60 : target));
            task.unit = "分钟";
        } else if ("variation".equals(task.mode)) {
            task.target = Math.max(1, Math.min(100000, target));
            task.unit = unit == null || unit.trim().isEmpty() ? "次" : unit.trim();
        } else {
            task.target = 0;
            task.unit = "次";
        }
        data.tasks.add(task);
        persist();
        notice("计划已添加");
        changed();
        return task;
    }

    public void updateTask(Models.Task task, String title, String note, String mode, int target, String unit, String dueDate) {
        if (task == null) return;
        task.title = cleanTitle(title);
        task.note = cleanNote(note);
        task.mode = validMode(mode);
        task.dueDate = dueDate == null || dueDate.isEmpty() ? null : dueDate;
        if ("duration".equals(task.mode)) {
            task.target = Math.max(60, Math.min(24 * 3600, target));
            task.progress = Math.min(task.progress, task.target);
            task.unit = "分钟";
            task.timerStartedAt = null;
        } else if ("variation".equals(task.mode)) {
            task.target = Math.max(1, Math.min(100000, target));
            task.progress = Math.min(task.progress, task.target);
            task.unit = unit == null || unit.trim().isEmpty() ? "次" : unit.trim();
            task.timerStartedAt = null;
        } else {
            task.target = 0;
            task.progress = 0;
            task.unit = "次";
            task.timerStartedAt = null;
        }
        persist();
        notice("计划已更新");
        changed();
    }

    public void deleteTask(Models.Task task) {
        if (task == null) return;
        if (data.tasks.remove(task)) {
            ProofStore.deleteForTask(context, task.id);
            persist();
            notice("计划已删除");
            changed();
        }
    }

    public void addProofs(Models.Task task, List<Models.TaskProof> proofs) {
        if (task == null || proofs == null || proofs.isEmpty()) return;
        if (task.proofs == null) task.proofs = new ArrayList<>();
        for (Models.TaskProof proof : proofs) {
            if (task.proofs.size() >= ProofStore.MAX_PROOFS) break;
            if (proof != null) task.proofs.add(proof);
        }
        persist();
        notice("已添加 " + task.proofs.size() + " 份材料");
        changed();
    }

    public void toggleDone(Models.Task task) {
        if (task == null) return;
        if (task.done) reopen(task);
        else complete(task, "手动完成");
    }

    public void complete(Models.Task task, String via) {
        if (task == null || task.done) return;
        boolean needsProof = "submit".equals(task.mode)
                && ("提交".equals(via) || "手动完成".equals(via));
        if (needsProof && !task.hasProofs()) {
            notice("请先选择文件、视频、音频或图片，再提交完成。");
            return;
        }
        if ("duration".equals(task.mode)) {
            int elapsed = task.effectiveProgress();
            task.timerStartedAt = null;
            task.progress = Math.max(elapsed, task.target);
        } else if ("variation".equals(task.mode)) {
            task.progress = Math.max(task.progress, task.target);
        }
        task.done = true;
        task.completedAt = DateTimes.nowIso();
        persist();
        String message = "「" + task.title + "」已完成";
        for (Listener listener : snapshot()) listener.onCompleted(task, via);
        notice(message + (via == null || via.isEmpty() ? "" : " · " + via));
        changed();
    }

    public void reopen(Models.Task task) {
        if (task == null || !task.done) return;
        task.done = false;
        task.completedAt = null;
        task.timerStartedAt = null;
        if ("duration".equals(task.mode)) task.progress = Math.min(task.progress, task.target - 1);
        if ("variation".equals(task.mode)) task.progress = Math.min(task.progress, task.target - 1);
        persist();
        notice("计划已重新打开");
        changed();
    }

    public void startTimer(Models.Task task) {
        if (task == null || task.done || !"duration".equals(task.mode) || task.isRunning()) return;
        task.timerStartedAt = DateTimes.nowIso();
        persist();
        changed();
    }

    public void pauseTimer(Models.Task task) {
        if (task == null || !"duration".equals(task.mode) || !task.isRunning()) return;
        int elapsed = task.effectiveProgress();
        task.timerStartedAt = null;
        task.progress = Math.max(task.progress, elapsed);
        if (task.progress >= task.target) {
            complete(task, "时长达成");
            return;
        }
        persist();
        changed();
    }

    public void increment(Models.Task task, int delta) {
        if (task == null || task.done || !"variation".equals(task.mode)) return;
        task.progress = Math.max(0, Math.min(task.target, task.progress + delta));
        if (task.progress >= task.target) {
            complete(task, "变化达成");
            return;
        }
        persist();
        changed();
    }

    /** Called once a second while a timer runs. Persists occasionally. */
    public boolean tick() {
        boolean completedAny = false;
        for (Models.Task task : new ArrayList<>(data.tasks)) {
            if (!"duration".equals(task.mode) || !task.isRunning()) continue;
            if (task.effectiveProgress() >= task.target) {
                complete(task, "时长达成");
                completedAny = true;
            }
        }
        if (!completedAny && System.currentTimeMillis() - lastAutoSave >= 12000L) {
            persist();
            lastAutoSave = System.currentTimeMillis();
        }
        return completedAny;
    }

    public void updateUser(String name, String motto) {
        data.user.name = name == null || name.trim().isEmpty() ? "朋友" : cut(name.trim(), 24);
        data.user.motto = motto == null || motto.trim().isEmpty() ? "把目标，做小一点。" : cut(motto.trim(), 80);
        persist();
        notice("资料已保存");
        changed();
    }

    public void updateTheme(String theme) {
        data.config.theme = theme;
        Theme.load(data.config);
        persist();
        changed();
    }

    public void updateAccent(String accent) {
        String normalized = Theme.normalizeHex(accent);
        if (normalized == null) return;
        data.config.accent = normalized;
        Theme.load(data.config);
        persist();
        changed();
    }

    public void updateDark(boolean dark) {
        data.config.dark = dark;
        Theme.load(data.config);
        persist();
        changed();
    }

    public void updatePrefix(String prefix) {
        String value = prefix == null ? "" : prefix.trim();
        if (value.isEmpty()) value = "https://ghproxy.net/";
        if (!value.startsWith("http://") && !value.startsWith("https://")) return;
        if (!value.endsWith("/")) value += "/";
        data.config.updatePrefix = value;
        persist();
        notice("更新前缀已保存");
        changed();
    }

    public void importData(Models.AppData imported, boolean merge) {
        if (imported == null) return;
        if (!merge) {
            data.user = imported.user;
            data.config = imported.config;
            data.tasks = imported.tasks;
            Theme.load(data.config);
            persist();
            notice("已覆盖导入 " + data.tasks.size() + " 条计划");
        } else {
            Set<String> ids = new HashSet<>();
            for (Models.Task task : data.tasks) ids.add(task.id);
            int added = 0;
            for (Models.Task task : imported.tasks) {
                if (ids.contains(task.id)) continue;
                ids.add(task.id);
                data.tasks.add(task);
                added++;
            }
            persist();
            notice("合并完成，新增 " + added + " 条计划");
        }
        changed();
    }

    public void persist() {
        try {
            Storage.save(file, data);
            lastAutoSave = System.currentTimeMillis();
        } catch (Exception error) {
            Log.w(TAG, "save failed", error);
        }
    }

    private void changed() {
        for (Listener listener : snapshot()) listener.onChanged();
    }

    private void notice(String message) {
        for (Listener listener : snapshot()) listener.onNotice(message);
    }

    private List<Listener> snapshot() {
        return new ArrayList<>(listeners);
    }

    private static String validMode(String mode) {
        return "duration".equals(mode) || "variation".equals(mode) ? mode : "submit";
    }

    private static String cleanTitle(String title) {
        String value = title == null ? "" : title.trim();
        return value.isEmpty() ? "未命名计划" : cut(value, 80);
    }

    private static String cleanNote(String note) {
        return cut(note == null ? "" : note.trim(), 500);
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
