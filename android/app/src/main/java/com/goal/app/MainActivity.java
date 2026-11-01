package com.goal.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Single activity shell: bottom navigation, page cache and system integrations. */
public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 2101;
    private static final int REQUEST_IMPORT = 2102;
    private static final int REQUEST_PLUGIN_ZIP = 2103;
    private static final int REQUEST_INSTALL_PERMISSION = 2104;
    private static final int REQUEST_PROOF = 2105;

    public Store store;
    public PluginManager pluginManager;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Theme.Palette p;
    private FrameLayout pageHost;
    private LinearLayout navBar;
    private final List<LinearLayout> navItems = new ArrayList<>();
    private final List<GlyphView> navIcons = new ArrayList<>();
    private final List<TextView> navLabels = new ArrayList<>();
    private int currentTab = 0;
    private HomePage homePage;
    private PluginsPage pluginsPage;
    private SettingsPage settingsPage;
    private File pendingInstall;
    private Models.Task pendingProofTask;
    private boolean resumed;

    private final Store.Listener storeListener = new Store.Listener() {
        @Override
        public void onChanged() {
            refreshVisiblePage();
        }

        @Override
        public void onCompleted(Models.Task task, String via) {
            Toast.makeText(MainActivity.this, "「" + task.title + "」已完成 · " + via, Toast.LENGTH_SHORT).show();
            refreshVisiblePage();
        }

        @Override
        public void onNotice(String message) {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!resumed) return;
            boolean completed = store.tick();
            if (homePage != null) homePage.tick();
            if (completed) refreshVisiblePage();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GoalApp app = GoalApp.instance();
        store = app.store();
        pluginManager = new PluginManager(this);
        p = Theme.get();
        store.addListener(storeListener);
        if (savedInstanceState != null) {
            currentTab = Math.max(0, Math.min(2, savedInstanceState.getInt("goal.tab", 0)));
        }
        setContentView(buildShell());
        if (NotificationCenter.isEnabled(this)) {
            NotificationCenter.requestPermission(this);
            NotificationCenter.schedule(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2201) NotificationCenter.schedule(this);
    }

    private View buildShell() {
        p = Theme.get();
        applySystemBars();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(p.bg);

        AuraView aura = new AuraView(this);
        aura.setAccent(p.accent);
        root.addView(aura, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = Ui.column(this);
        pageHost = new FrameLayout(this);
        column.addView(pageHost, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        column.addView(buildNavigation());
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        navItems.clear();
        navIcons.clear();
        navLabels.clear();
        homePage = null;
        pluginsPage = null;
        settingsPage = null;
        showTab(currentTab);
        return root;
    }

    private View buildNavigation() {
        LinearLayout wrap = Ui.column(this);
        wrap.setBackgroundColor(p.surface);
        View line = new View(this);
        line.setBackgroundColor(p.line);
        wrap.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));

        navBar = Ui.row(this);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        String[] labels = new String[]{"主页", "插件", "设置"};
        int[] icons = new int[]{GlyphView.HOME, GlyphView.GRID, GlyphView.SLIDERS};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout item = Ui.column(this);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
            GlyphView glyph = new GlyphView(this).type(icons[i]).color(p.muted).size(21);
            TextView label = Ui.text(this, labels[i], 10, p.muted, Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            item.addView(glyph);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = Ui.dp(this, 3);
            labelParams.gravity = Gravity.CENTER_HORIZONTAL;
            item.addView(label, labelParams);
            item.setOnClickListener(v -> showTab(index));
            Ui.pressable(item);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f);
            itemParams.leftMargin = Ui.dp(this, 3);
            itemParams.rightMargin = Ui.dp(this, 3);
            navBar.addView(item, itemParams);
            navItems.add(item);
            navIcons.add(glyph);
            navLabels.add(label);
        }
        wrap.addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 62)));
        wrap.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottom = insets.getSystemWindowInsetBottom();
            view.setPadding(0, 0, 0, Math.max(0, bottom));
            return insets;
        });
        return wrap;
    }

    private void showTab(int tab) {
        if (tab < 0 || tab > 2) return;
        currentTab = tab;
        if (pageHost == null) return;
        View page;
        if (tab == 0) {
            if (homePage == null) homePage = new HomePage(this);
            page = homePage.root();
            homePage.refresh();
        } else if (tab == 1) {
            if (pluginsPage == null) pluginsPage = new PluginsPage(this);
            page = pluginsPage.root();
            pluginsPage.refresh();
        } else {
            if (settingsPage == null) settingsPage = new SettingsPage(this);
            page = settingsPage.root();
            settingsPage.refresh();
        }
        pageHost.removeAllViews();
        page.setAlpha(0f);
        page.setTranslationY(Ui.dp(this, 7));
        pageHost.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.animate().alpha(1f).translationY(0f).setDuration(200).start();
        updateNavigation();
    }

    private void updateNavigation() {
        if (navItems.isEmpty()) return;
        for (int i = 0; i < navItems.size(); i++) {
            boolean selected = i == currentTab;
            LinearLayout item = navItems.get(i);
            if (selected) {
                GradientDrawable background = Ui.background(p.accentUltra, 14, this);
                item.setBackground(background);
                navIcons.get(i).color(p.accent);
                navLabels.get(i).setTextColor(p.accent);
            } else {
                item.setBackground(null);
                navIcons.get(i).color(p.muted);
                navLabels.get(i).setTextColor(p.muted);
            }
        }
    }

    private void refreshVisiblePage() {
        if (homePage != null) homePage.refresh();
        if (pluginsPage != null) pluginsPage.refresh();
        if (settingsPage != null) settingsPage.refresh();
    }

    /** Called by settings after a theme, dark-mode or accent change. */
    public void onThemeChanged() {
        p = Theme.get();
        currentTab = Math.max(0, Math.min(2, currentTab));
        setContentView(buildShell());
    }

    private void applySystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(p.bg);
        window.setNavigationBarColor(p.bg);
        int flags = p.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!p.dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 1000L);
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(ticker);
        if (store != null) store.persist();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (store != null) store.removeListener(storeListener);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK && data != null) {
            writeExport(data.getData());
        } else if (requestCode == REQUEST_IMPORT && resultCode == RESULT_OK && data != null) {
            readImport(data.getData());
        } else if (requestCode == REQUEST_PLUGIN_ZIP && resultCode == RESULT_OK && data != null) {
            copyPlugin(data.getData());
        } else if (requestCode == REQUEST_PROOF && resultCode == RESULT_OK && data != null) {
            handleProofResult(data);
        } else if (requestCode == REQUEST_INSTALL_PERMISSION) {
            if (pendingInstall != null && canInstallPackages()) {
                File apk = pendingInstall;
                pendingInstall = null;
                installApk(apk);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("goal.tab", currentTab);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (currentTab != 0) {
            showTab(0);
            return;
        }
        super.onBackPressed();
    }

    public void pickProofs(Models.Task task) {
        if (task == null) return;
        pendingProofTask = task;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, REQUEST_PROOF);
        } catch (Exception error) {
            Toast.makeText(this, "无法打开系统文件选择器。", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleProofResult(Intent data) {
        Models.Task task = pendingProofTask;
        pendingProofTask = null;
        if (task == null) return;
        List<Uri> uris = new ArrayList<>();
        android.content.ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) uris.add(uri);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;
        Toast.makeText(this, "正在添加材料…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<Models.TaskProof> proofs = ProofStore.copy(this, task, uris);
                runOnUiThread(() -> afterProofs(task, proofs));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        "材料添加失败：" + friendly(error), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void afterProofs(Models.Task task, List<Models.TaskProof> proofs) {
        if (proofs == null || proofs.isEmpty()) {
            Toast.makeText(this, "没有添加材料。", Toast.LENGTH_SHORT).show();
            return;
        }
        store.addProofs(task, proofs);
        new ConfirmDialog(this, "材料已添加",
                "已添加 " + proofs.size() + " 份材料，现在就提交完成？", "提交完成", false,
                confirmed -> {
                    if (confirmed) store.complete(task, "提交");
                }).showDialog();
    }

    public void showProofs(Models.Task task) {
        if (task == null) return;
        List<Models.TaskProof> proofs = task.proofs;
        if (proofs == null || proofs.isEmpty()) {
            Toast.makeText(this, "还没有材料。", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[proofs.size()];
        for (int i = 0; i < proofs.size(); i++) {
            Models.TaskProof proof = proofs.get(i);
            labels[i] = proof.name + " · " + ProofStore.kindLabel(proof.kind) + " · " + sizeLabel(proof.size);
        }
        new AlertDialog.Builder(this)
                .setTitle("材料 · " + proofs.size() + " 份（点击打开）")
                .setItems(labels, (dialog, which) -> openProof(task, proofs.get(which)))
                .setPositiveButton("关闭", null)
                .show();
    }

    private void openProof(Models.Task task, Models.TaskProof proof) {
        if (task == null || proof == null) return;
        try {
            java.io.File file = new java.io.File(ProofStore.directory(this, task.id), proof.storedName);
            if (!file.isFile()) {
                Toast.makeText(this, "材料文件已不存在。", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(ProofProvider.uriFor(task.id, proof.storedName),
                    ProofProvider.mimeOf(proof.storedName));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "没有可以打开此材料的应用。", Toast.LENGTH_SHORT).show();
        }
    }

    private static String sizeLabel(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public void showTaskEditor(Models.Task task) {
        new TaskEditorDialog(this, task, (title, note, mode, target, unit, dueDate) -> {
            if (task == null) {
                store.addTask(title, note, mode, target, unit, dueDate);
            } else {
                store.updateTask(task, title, note, mode, target, unit, dueDate);
            }
        }).showDialog();
    }

    public void exportData() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, Storage.exportFileName());
            startActivityForResult(intent, REQUEST_EXPORT);
        } catch (Exception error) {
            Toast.makeText(this, "无法打开系统文件保存器。", Toast.LENGTH_SHORT).show();
        }
    }

    private void writeExport(Uri uri) {
        if (uri == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new Exception("无法写入文件");
            byte[] bytes = Storage.exportJson(store.data()).getBytes(StandardCharsets.UTF_8);
            output.write(bytes);
            output.flush();
            Toast.makeText(this, "数据已导出", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "导出失败：" + friendly(error), Toast.LENGTH_LONG).show();
        }
    }

    public void importData() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, REQUEST_IMPORT);
        } catch (Exception error) {
            Toast.makeText(this, "无法打开系统文件选择器。", Toast.LENGTH_SHORT).show();
        }
    }

    private void readImport(Uri uri) {
        if (uri == null) return;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new Exception("无法读取文件");
            String json = Storage.readStream(input, 8 * 1024 * 1024);
            Models.AppData imported = Storage.parse(json);
            String name = displayName(uri);
            new ImportChoiceDialog(this, imported, name, choice -> {
                if ("cancel".equals(choice)) return;
                store.importData(imported, "merge".equals(choice));
                onThemeChanged();
            }).showDialog();
        } catch (Exception error) {
            new ConfirmDialog(this, "导入失败", friendly(error), "我知道了", false,
                    result -> { }).showDialog();
        }
    }

    public void pickPluginPackage() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/zip", "application/x-zip-compressed", "application/octet-stream"});
            startActivityForResult(intent, REQUEST_PLUGIN_ZIP);
        } catch (Exception error) {
            Toast.makeText(this, "无法打开系统文件选择器。", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyPlugin(Uri uri) {
        if (uri == null) return;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new Exception("无法读取插件包");
            File target = new File(getCacheDir(), "plugin-pick-" + System.currentTimeMillis() + ".zip");
            Storage.copy(input, target);
            if (currentTab != 1) showTab(1);
            if (pluginsPage == null) pluginsPage = new PluginsPage(this);
            pluginsPage.onPluginPicked(target);
        } catch (Exception error) {
            new ConfirmDialog(this, "插件包读取失败", friendly(error), "我知道了", false,
                    result -> { }).showDialog();
        }
    }

    public void installApk(File apk) {
        if (apk == null || !apk.isFile()) {
            Toast.makeText(this, "安装包不存在。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!canInstallPackages()) {
            pendingInstall = apk;
            new ConfirmDialog(this, "允许安装更新",
                    "Android 需要你允许 goal 安装未知来源应用，才能继续安装更新。",
                    "前往设置", false, result -> {
                if (result) openUnknownSourcesSettings();
            }).showDialog();
            return;
        }
        try {
            Uri uri = Uri.parse("content://" + ApkProvider.AUTHORITY + "/" + Uri.encode(apk.getName()));
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "无法启动安装器：" + friendly(error), Toast.LENGTH_LONG).show();
        }
    }

    private boolean canInstallPackages() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        return getPackageManager().canRequestPackageInstalls();
    }

    private void openUnknownSourcesSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_INSTALL_PERMISSION);
        } catch (Exception error) {
            Toast.makeText(this, "请手动在系统设置中允许安装未知来源应用。", Toast.LENGTH_LONG).show();
        }
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        return name == null || name.isEmpty() ? "goal-data.json" : name;
    }

    private static String friendly(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "操作失败，请稍后重试。";
        return message;
    }
}
