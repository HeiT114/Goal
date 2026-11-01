package com.goal.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs declarative goal-panel plugins. Plugin code is never executed. */
public final class PluginManager {
    public static final String NOTICE =
            "插件由第三方作者独立发布，与《goal》原作者无关；用户添加插件后，应自行判断插件内容及浏览信息是否合规，原作者不承担由此产生的任何责任。";

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$");
    private static final Pattern HOST_PATTERN = Pattern.compile("^(\\*\\.)?[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?$");
    private static final long MAX_ZIP = 15L * 1024 * 1024;
    private static final long MAX_EXTRACTED = 12L * 1024 * 1024;

    public static final class Preview {
        public final File zipFile;
        public final Models.PluginManifest manifest;
        public final String description;

        Preview(File zipFile, Models.PluginManifest manifest, String description) {
            this.zipFile = zipFile;
            this.manifest = manifest;
            this.description = description;
        }
    }

    private final File root;
    private final File stateFile;

    public PluginManager(android.content.Context context) {
        root = new File(context.getFilesDir(), "plugins");
        stateFile = new File(context.getFilesDir(), "plugins-state.json");
    }

    public File root() {
        if (!root.isDirectory() && !root.mkdirs()) {
            // Best effort; callers will surface an error if installation fails.
        }
        return root;
    }

    public List<Models.InstalledPlugin> list() {
        List<Boolean> unused = Collections.emptyList();
        JSONObject enabled = readState();
        List<Models.InstalledPlugin> plugins = new ArrayList<>();
        File[] dirs = root().listFiles();
        if (dirs == null) return plugins;
        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            try {
                File manifestFile = new File(dir, "manifest.json");
                if (!manifestFile.isFile()) continue;
                Models.PluginManifest manifest = parseManifest(readText(manifestFile, 100_000));
                validateManifest(manifest);
                File entry = new File(dir, manifest.entry);
                File description = new File(dir, manifest.description);
                if (!entry.isFile() || !description.isFile()) continue;
                Models.InstalledPlugin plugin = new Models.InstalledPlugin();
                plugin.manifest = manifest;
                plugin.directory = dir.getAbsolutePath();
                plugin.enabled = enabled.optBoolean(manifest.id, false);
                plugin.installedAt = readInstalledAt(enabled, manifest.id);
                plugins.add(plugin);
            } catch (Exception ignored) {
                // Invalid plugin folders are ignored, never executed.
            }
        }
        plugins.sort(Comparator.comparing((Models.InstalledPlugin p) -> p.manifest.name, String.CASE_INSENSITIVE_ORDER));
        return plugins;
    }

    public Preview inspectZip(File zipFile) throws IOException {
        if (zipFile == null || !zipFile.isFile()) throw new IOException("找不到插件包。");
        if (zipFile.length() > MAX_ZIP) throw new IOException("插件包超过 15 MB。");
        File temp = extract(zipFile);
        try {
            File manifestFile = findManifest(temp);
            if (manifestFile == null) throw new IOException("插件包根目录缺少 manifest.json。");
            File base = manifestFile.getParentFile();
            Models.PluginManifest manifest = parseManifest(readText(manifestFile, 100_000));
            validateManifest(manifest);
            File entry = new File(base, manifest.entry);
            File description = new File(base, manifest.description);
            if (!entry.isFile()) throw new IOException("入口文件 " + manifest.entry + " 不存在。");
            if (!description.isFile()) throw new IOException("说明文件 " + manifest.description + " 不存在。");
            if (entry.length() > 300_000) throw new IOException("插件入口文件超过 300 KB。");
            validatePanel(readText(entry, 300_000));
            return new Preview(zipFile, manifest, readText(description, 200_000).trim());
        } finally {
            deleteTree(temp);
        }
    }

    public Models.InstalledPlugin install(Preview preview) throws IOException {
        if (preview == null) throw new IOException("插件包尚未读取。");
        File temp = extract(preview.zipFile);
        try {
            File manifestFile = findManifest(temp);
            if (manifestFile == null) throw new IOException("插件包根目录缺少 manifest.json。");
            File base = manifestFile.getParentFile();
            Models.PluginManifest manifest = parseManifest(readText(manifestFile, 100_000));
            validateManifest(manifest);
            if (!manifest.id.equals(preview.manifest.id))
                throw new IOException("插件包内容与预览不一致，已停止安装。");
            File entry = new File(base, manifest.entry);
            File description = new File(base, manifest.description);
            if (!entry.isFile() || !description.isFile())
                throw new IOException("插件缺少声明的入口或说明文件。");
            validatePanel(readText(entry, 300_000));
            File destination = new File(root(), manifest.id);
            if (destination.exists()) throw new IOException("已安装同 ID 的插件，请先卸载旧版本。");
            copyTree(base, destination);
            JSONObject state = readState();
            try {
                state.put(manifest.id, false);
            } catch (org.json.JSONException error) {
                throw new IOException("无法保存插件状态。", error);
            }
            writeState(state);

            Models.InstalledPlugin plugin = new Models.InstalledPlugin();
            plugin.manifest = manifest;
            plugin.directory = destination.getAbsolutePath();
            plugin.enabled = false;
            plugin.installedAt = DateTimes.nowIso();
            return plugin;
        } finally {
            deleteTree(temp);
        }
    }

    public void setEnabled(Models.InstalledPlugin plugin, boolean value) {
        if (plugin == null) return;
        JSONObject state = readState();
        try {
            state.put(plugin.manifest.id, value);
            writeState(state);
            plugin.enabled = value;
        } catch (Exception ignored) {
        }
    }

    public void uninstall(Models.InstalledPlugin plugin) {
        if (plugin == null) return;
        deleteTree(new File(plugin.directory));
        JSONObject state = readState();
        state.remove(plugin.manifest.id);
        try {
            writeState(state);
        } catch (Exception ignored) {
        }
    }

    public String readDescription(Models.InstalledPlugin plugin) {
        try {
            return readText(new File(plugin.directory, plugin.manifest.description), 200_000);
        } catch (Exception error) {
            return "（说明文件读取失败）";
        }
    }

    public Models.Panel readPanel(Models.InstalledPlugin plugin) throws IOException {
        if (plugin == null) throw new IOException("插件不存在。");
        if (!plugin.enabled) throw new IOException("请先在插件页面启用此插件。");
        File entry = new File(plugin.directory, plugin.manifest.entry);
        if (!entry.isFile()) throw new IOException("插件入口文件不存在。");
        Models.Panel panel = parsePanel(readText(entry, 300_000));
        if (panel.title == null || panel.title.trim().isEmpty()) panel.title = plugin.manifest.name;
        if (panel.title.length() > 80) panel.title = panel.title.substring(0, 80);
        if (panel.subtitle == null) panel.subtitle = "";
        if (panel.subtitle.length() > 220) panel.subtitle = panel.subtitle.substring(0, 220);
        if (panel.blocks.size() > 60) throw new IOException("插件面板内容过多。");
        return panel;
    }

    /** Returns null on success, or a human-readable reason. */
    public String openExternalLink(Models.InstalledPlugin plugin, String url) {
        if (plugin == null || !plugin.enabled) return "请先启用插件。";
        android.net.Uri uri = android.net.Uri.parse(url == null ? "" : url);
        String scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) return "插件给出的链接不是有效的 http(s) 地址。";
        String host = uri.getHost();
        if (host == null || !hostAllowed(plugin.manifest.network, host))
            return "插件清单未声明访问 " + host + " 的网络权限，已阻止打开。";
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, uri);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            GoalApp.instance().startActivity(intent);
            return null;
        } catch (Exception error) {
            return "无法打开链接：" + error.getMessage();
        }
    }

    private boolean hostAllowed(List<String> rules, String host) {
        String target = host.toLowerCase(Locale.ROOT);
        if (rules == null) return false;
        for (String raw : rules) {
            String rule = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (rule.isEmpty()) continue;
            if (rule.startsWith("*.")) {
                String suffix = rule.substring(1);
                if (target.endsWith(suffix) || target.equals(rule.substring(2))) return true;
            } else if (target.equals(rule)) {
                return true;
            }
        }
        return false;
    }

    private File extract(File zipFile) throws IOException {
        File temp = new File(GoalApp.instance().getCacheDir(), "plugin-" + System.currentTimeMillis());
        if (!temp.mkdirs()) throw new IOException("无法创建临时目录。");
        long total = 0;
        int count = 0;
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                count++;
                if (count > 300) throw new IOException("插件包文件数量过多。");
                String relative = safeRelative(entry.getName());
                if (relative.isEmpty()) continue;
                File target = new File(temp, relative);
                String canonical = target.getCanonicalPath();
                if (!canonical.startsWith(temp.getCanonicalPath() + File.separator))
                    throw new IOException("插件包包含不安全的路径。");
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                    throw new IOException("无法解压插件包。");
                try (FileOutputStream output = new FileOutputStream(target)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_EXTRACTED) throw new IOException("插件包解压后超过 12 MB。");
                        output.write(buffer, 0, read);
                    }
                }
            }
        } catch (IOException error) {
            deleteTree(temp);
            if (error.getMessage() != null && (error.getMessage().contains("插件包") || error.getMessage().contains("路径")))
                throw error;
            throw new IOException("这不像是有效的 .zip 插件包。", error);
        }
        return temp;
    }

    private static File findManifest(File root) {
        List<File> found = new ArrayList<>();
        collectManifests(root, found);
        if (found.isEmpty()) return null;
        found.sort(Comparator.comparingInt((File f) -> f.getAbsolutePath().split(Pattern.quote(File.separator)).length)
                .thenComparingInt(f -> f.getAbsolutePath().length()));
        return found.get(0);
    }

    private static void collectManifests(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) collectManifests(file, out);
            else if (file.getName().equalsIgnoreCase("manifest.json")) out.add(file);
        }
    }

    private static String safeRelative(String raw) throws IOException {
        String normalized = raw.replace('\\', '/').trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isEmpty()) return "";
        String[] parts = normalized.split("/");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if ("..".equals(part) || ".".equals(part)) throw new IOException("插件包含不安全的相对路径。");
            if (out.length() > 0) out.append('/');
            out.append(part);
        }
        return out.toString();
    }

    private Models.PluginManifest parseManifest(String text) throws IOException {
        try {
            JSONObject json = new JSONObject(text);
            Models.PluginManifest manifest = new Models.PluginManifest();
            manifest.id = json.optString("id", "").trim();
            manifest.name = json.optString("name", "").trim();
            manifest.version = json.optString("version", "").trim();
            manifest.author = json.optString("author", "").trim();
            manifest.entry = normalizeRelative(json.optString("entry", ""));
            manifest.description = normalizeRelative(json.optString("description", ""));
            manifest.homepage = json.optString("homepage", "").trim();
            JSONArray network = json.optJSONArray("network");
            manifest.network = new ArrayList<>();
            if (network != null) {
                for (int i = 0; i < network.length(); i++) {
                    String value = network.optString(i, "").trim().toLowerCase(Locale.ROOT);
                    if (!value.isEmpty() && !manifest.network.contains(value)) manifest.network.add(value);
                }
            }
            return manifest;
        } catch (org.json.JSONException error) {
            throw new IOException("manifest.json 格式不正确：" + error.getMessage());
        }
    }

    private void validateManifest(Models.PluginManifest manifest) throws IOException {
        if (!ID_PATTERN.matcher(manifest.id).matches())
            throw new IOException("插件 id 只能包含字母、数字、点、下划线或连字符，长度 3–64。");
        if (manifest.name.isEmpty() || manifest.name.length() > 40) throw new IOException("插件名称应为 1–40 个字符。");
        if (manifest.version.isEmpty() || manifest.version.length() > 24) throw new IOException("插件版本应为 1–24 个字符。");
        if (manifest.author.isEmpty() || manifest.author.length() > 40) throw new IOException("插件作者应为 1–40 个字符。");
        if (manifest.entry.isEmpty() || !manifest.entry.toLowerCase(Locale.ROOT).endsWith(".json"))
            throw new IOException("entry 必须指向插件包内的 .json 入口文件。");
        String desc = manifest.description.toLowerCase(Locale.ROOT);
        if (manifest.description.isEmpty() || !(desc.endsWith(".md") || desc.endsWith(".txt")))
            throw new IOException("description 必须指向插件包内的 .md 或 .txt 说明文件。");
        if (manifest.network.size() > 20) throw new IOException("网络权限声明最多 20 条。");
        for (String host : manifest.network) {
            if (!HOST_PATTERN.matcher(host).matches() || host.length() > 100)
                throw new IOException("network 中只能填写域名或 *.域名，不能填写 URL。");
        }
        if (!manifest.homepage.isEmpty()) {
            android.net.Uri home = android.net.Uri.parse(manifest.homepage);
            if (!"https".equals(home.getScheme())) manifest.homepage = "";
        }
    }

    private Models.Panel parsePanel(String text) throws IOException {
        Models.Panel panel = new Models.Panel();
        try {
            JSONObject json = new JSONObject(text);
            panel.type = json.optString("type", "");
            if (!"goal-panel".equals(panel.type)) throw new IOException("插件入口必须是 goal-panel 类型。");
            panel.title = json.optString("title", "");
            panel.subtitle = json.optString("subtitle", "");
            panel.accent = json.optString("accent", "#D9653B");
            JSONArray blocks = json.optJSONArray("blocks");
            if (blocks != null) {
                if (blocks.length() > 60) throw new IOException("插件面板内容过多。");
                for (int i = 0; i < blocks.length(); i++) {
                    JSONObject b = blocks.optJSONObject(i);
                    if (b == null) continue;
                    Models.PanelBlock block = new Models.PanelBlock();
                    block.type = normalizeBlockType(b.optString("type", ""));
                    block.text = cut(b.optString("text", ""), 2000);
                    block.title = cut(b.optString("title", ""), 120);
                    block.label = cut(b.optString("label", ""), 80);
                    block.url = cut(b.optString("url", ""), 2000);
                    block.taskTitle = cut(b.optString("taskTitle", ""), 80);
                    block.taskMode = b.optString("taskMode", "submit");
                    if (!"duration".equals(block.taskMode) && !"variation".equals(block.taskMode) && !"submit".equals(block.taskMode))
                        block.taskMode = "submit";
                    block.unit = cut(b.optString("unit", "次"), 8);
                    if (!b.isNull("target") && b.has("target")) block.target = b.optInt("target", 0);
                    JSONArray items = b.optJSONArray("items");
                    if (items != null) {
                        for (int j = 0; j < Math.min(30, items.length()); j++) block.items.add(cut(items.optString(j, ""), 500));
                    }
                    panel.blocks.add(block);
                }
            }
            return panel;
        } catch (org.json.JSONException error) {
            throw new IOException("插件入口 JSON 格式不正确：" + error.getMessage());
        }
    }

    private void validatePanel(String text) throws IOException {
        parsePanel(text);
    }

    private static String normalizeBlockType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if ("addtask".equals(value)) return "addTask";
        return value;
    }

    private static String normalizeRelative(String value) throws IOException {
        String normalized = safeRelative(value == null ? "" : value);
        if (normalized.endsWith(".json") || normalized.endsWith(".md") || normalized.endsWith(".txt")) return normalized;
        return normalized;
    }

    private static String readText(File file, long limit) throws IOException {
        if (file.length() > limit) throw new IOException("插件中的文本文件过大。");
        try (FileInputStream input = new FileInputStream(file)) {
            return Storage.readStream(input, (int) Math.min(Integer.MAX_VALUE, limit));
        }
    }

    private JSONObject readState() {
        try {
            if (!stateFile.isFile()) return new JSONObject();
            JSONObject root = new JSONObject(Storage.readFile(stateFile));
            JSONArray plugins = root.optJSONArray("plugins");
            JSONObject result = new JSONObject();
            if (plugins != null) {
                for (int i = 0; i < plugins.length(); i++) {
                    JSONObject item = plugins.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id", "");
                    if (!id.isEmpty()) result.put(id, item.optBoolean("enabled", false));
                }
            }
            return result;
        } catch (Exception error) {
            return new JSONObject();
        }
    }

    private void writeState(JSONObject enabled) throws IOException {
        try {
            JSONArray plugins = new JSONArray();
            java.util.Iterator<String> keys = enabled.keys();
            while (keys.hasNext()) {
                String id = keys.next();
                JSONObject item = new JSONObject();
                item.put("id", id);
                item.put("enabled", enabled.optBoolean(id, false));
                plugins.put(item);
            }
            JSONObject root = new JSONObject();
            root.put("plugins", plugins);
            File tmp = new File(stateFile.getPath() + ".tmp");
            try (FileOutputStream output = new FileOutputStream(tmp)) {
                output.write(root.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (stateFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                stateFile.delete();
            }
            if (!tmp.renameTo(stateFile)) throw new IOException("无法保存插件状态。");
        } catch (org.json.JSONException error) {
            throw new IOException("无法保存插件状态。", error);
        }
    }

    private static String readInstalledAt(JSONObject enabled, String id) {
        return "";
    }

    private static void copyTree(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.isDirectory() && !target.mkdirs()) throw new IOException("无法写入插件目录。");
            File[] children = source.listFiles();
            if (children == null) return;
            for (File child : children) copyTree(child, new File(target, child.getName()));
        } else {
            try (FileInputStream input = new FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static String cut(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
