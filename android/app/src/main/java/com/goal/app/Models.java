package com.goal.app;

import java.util.ArrayList;
import java.util.List;

/** Plain data objects. Field names and JSON shape match the Windows client. */
public final class Models {
    private Models() {}

    public static final class User {
        public String name = "朋友";
        public String motto = "把目标，做小一点。";
    }

    public static final class Config {
        public String theme = "sand";
        public String accent = "#D9653B";
        public boolean dark = false;
        public String updatePrefix = "https://ghproxy.net/";
    }

    public static final class Task {
        public String id = "";
        public String title = "";
        public String note = "";
        public String mode = "submit";
        public int target = 0;
        public int progress = 0;
        public String unit = "次";
        public String dueDate = null;
        public boolean done = false;
        public String completedAt = null;
        public String timerStartedAt = null;
        public String createdAt = "";
        public List<TaskProof> proofs = new ArrayList<>();

        public boolean isRunning() {
            return timerStartedAt != null && !done;
        }

        public int effectiveProgress() {
            if (done) return target > 0 ? target : progress;
            if ("duration".equals(mode) && isRunning()) {
                long started = DateTimes.parseMillis(timerStartedAt);
                if (started > 0) {
                    long delta = Math.max(0, (System.currentTimeMillis() - started) / 1000L);
                    return (int) Math.min(Integer.MAX_VALUE, progress + delta);
                }
            }
            return progress;
        }

        public int percent() {
            if (target <= 0) return done ? 100 : 0;
            return Math.max(0, Math.min(100, Math.round(effectiveProgress() * 100f / target)));
        }

        public String modeLabel() {
            if ("duration".equals(mode)) return "时长";
            if ("variation".equals(mode)) return "变化";
            return "提交";
        }

        public boolean hasProofs() {
            return proofs != null && !proofs.isEmpty();
        }

        public String proofLabel() {
            return hasProofs() ? proofs.size() + " 份材料" : "提交材料后完成";
        }

        public String proofSummary() {
            if (!hasProofs()) return "还没有添加材料";
            StringBuilder sb = new StringBuilder();
            int limit = Math.min(proofs.size(), 4);
            for (int i = 0; i < limit; i++) {
                if (sb.length() > 0) sb.append("、");
                sb.append(proofs.get(i).name);
            }
            if (proofs.size() > limit) sb.append(" 等 ").append(proofs.size()).append(" 份");
            return sb.toString();
        }

        public String progressLabel() {
            if ("duration".equals(mode)) {
                return done ? "时长已达成" : DateTimes.clock(effectiveProgress()) + " / " + DateTimes.clock(target);
            }
            if ("variation".equals(mode)) {
                return done ? "变化已达成" : effectiveProgress() + " / " + target + " " + unit;
            }
            return done ? "已完成" : proofLabel();
        }
    }

    /** A local material attached to a submit-style plan. Files never leave the device. */
    public static final class TaskProof {
        public String name = "";
        public String storedName = "";
        public String kind = "file";
        public long size = 0L;
        public String addedAt = "";
    }

    public static final class AppData {
        public String app = "goal";
        public int schemaVersion = 1;
        public String appVersion = "1.0.0";
        public String exportedAt = DateTimes.nowIso();
        public User user = new User();
        public Config config = new Config();
        public List<Task> tasks = new ArrayList<>();
    }

    public static final class PluginManifest {
        public String id = "";
        public String name = "";
        public String version = "";
        public String author = "";
        public String entry = "";
        public String description = "";
        public List<String> network = new ArrayList<>();
        public String homepage = "";

        public String networkLabel() {
            if (network == null || network.isEmpty()) return "无网络权限";
            StringBuilder sb = new StringBuilder();
            for (String item : network) {
                if (sb.length() > 0) sb.append("、");
                sb.append(item);
            }
            return sb.toString();
        }
    }

    public static final class InstalledPlugin {
        public PluginManifest manifest = new PluginManifest();
        public String directory = "";
        public boolean enabled = false;
        public String installedAt = "";
    }

    public static final class PanelBlock {
        public String type = "";
        public String text = "";
        public String title = "";
        public String label = "";
        public String url = "";
        public String taskTitle = "";
        public String taskMode = "submit";
        public String unit = "次";
        public Integer target = null;
        public List<String> items = new ArrayList<>();
    }

    public static final class Panel {
        public String type = "goal-panel";
        public String title = "";
        public String subtitle = "";
        public String accent = "#D9653B";
        public List<PanelBlock> blocks = new ArrayList<>();
    }
}
