package com.goal.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Settings: profile, theme, data, update prefix and about. */
public class SettingsPage {
    private final MainActivity activity;
    private final Store store;
    private final Theme.Palette p;
    private final View root;
    private final EditText nameInput;
    private final EditText mottoInput;
    private final LinearLayout themeGrid;
    private final EditText accentInput;
    private final View accentPreview;
    private final Switch darkSwitch;
    private final Switch reminderSwitch;
    private final EditText prefixInput;
    private final TextView updateStatus;
    private final TextView versionText;
    private final TextView dataPath;
    private boolean updating;

    public SettingsPage(MainActivity activity) {
        this.activity = activity;
        this.store = activity.store;
        this.p = Theme.get();
        Context context = activity;

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root = scroll;
        LinearLayout content = Ui.column(context);
        content.setPadding(Ui.dp(context, 20), Ui.dp(context, 22), Ui.dp(context, 20), Ui.dp(context, 30));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(Ui.label(context, "SETTINGS · 设置", 11, p.accent));
        TextView title = Ui.title(context, "设置，让 goal 更像你", 26, p.ink);
        title.setLineSpacing(0, 1.05f);
        LinearLayout.LayoutParams titleParams = Ui.matchWrap();
        titleParams.topMargin = Ui.dp(context, 10);
        content.addView(title, titleParams);
        TextView subtitle = Ui.text(context, "只保存在本机。",
                12.5f, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = Ui.matchWrap();
        subtitleParams.topMargin = Ui.dp(context, 6);
        content.addView(subtitle, subtitleParams);

        // Profile
        LinearLayout profile = addCard(content, "个人资料", "只用于问候和导出。");
        LinearLayout profileRow = Ui.row(context);
        LinearLayout profileLeft = Ui.column(context);
        profileLeft.addView(sectionText("称呼"));
        nameInput = field(store.data().user.name, 1);
        profileLeft.addView(nameInput);
        LinearLayout profileRight = Ui.column(context);
        profileRight.addView(sectionText("一句给自己的话"));
        mottoInput = field(store.data().user.motto, 1);
        profileRight.addView(mottoInput);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        profileRow.addView(profileLeft, leftParams);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f);
        rightParams.leftMargin = Ui.dp(context, 10);
        profileRow.addView(profileRight, rightParams);
        profile.addView(profileRow);
        LinearLayout saveRow = Ui.row(context);
        saveRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams saveRowParams = Ui.matchWrap();
        saveRowParams.topMargin = Ui.dp(context, 12);
        profile.addView(saveRow, saveRowParams);
        TextView saveProfile = button("保存资料", true, v -> saveProfile());
        saveRow.addView(saveProfile, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 40)));

        // Appearance
        LinearLayout appearance = addCard(content, "外观", "选一套配色，或自定义。");
        LinearLayout darkRow = Ui.row(context);
        darkRow.setGravity(Gravity.CENTER_VERTICAL);
        darkRow.addView(Ui.text(context, "深色模式", 13, p.ink, Typeface.NORMAL),
                Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        darkSwitch = new Switch(context);
        darkSwitch.setChecked(store.data().config.dark);
        darkSwitch.setButtonTintList(ColorStateList.valueOf(p.accent));
        darkSwitch.setTrackTintList(ColorStateList.valueOf(Theme.alpha(p.accent, 110)));
        darkSwitch.setContentDescription("深色模式");
        darkRow.addView(darkSwitch);
        appearance.addView(darkRow);
        darkSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (updating) return;
            store.updateDark(checked);
            activity.onThemeChanged();
        });

        appearance.addView(sectionText("主题"));
        themeGrid = Ui.column(context);
        LinearLayout.LayoutParams gridParams = Ui.matchWrap();
        gridParams.topMargin = Ui.dp(context, 8);
        appearance.addView(themeGrid, gridParams);
        buildThemeTiles();

        appearance.addView(sectionText("自定义强调色"));
        LinearLayout accentRow = Ui.row(context);
        accentRow.setGravity(Gravity.CENTER_VERTICAL);
        accentPreview = new View(context);
        accentPreview.setBackground(Ui.background(Theme.parse(store.data().config.accent), 10, context));
        accentRow.addView(accentPreview, new LinearLayout.LayoutParams(Ui.dp(context, 34), Ui.dp(context, 34)));
        accentInput = field(store.data().config.accent, 1);
        accentInput.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        accentParams.leftMargin = Ui.dp(context, 10);
        accentRow.addView(accentInput, accentParams);
        TextView applyAccent = button("应用", false, v -> applyAccent());
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 42));
        applyParams.leftMargin = Ui.dp(context, 8);
        accentRow.addView(applyAccent, applyParams);
        appearance.addView(accentRow);
        TextView accentHint = Ui.text(context, "HEX，例如 #D9653B", 10.5f, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams accentHintParams = Ui.matchWrap();
        accentHintParams.topMargin = Ui.dp(context, 5);
        appearance.addView(accentHint, accentHintParams);

        // Reminder
        LinearLayout reminder = addCard(content, "提醒", "每天 9:00，提醒到期或逾期的计划。");
        LinearLayout reminderRow = Ui.row(context);
        reminderRow.setGravity(Gravity.CENTER_VERTICAL);
        reminderRow.addView(Ui.text(context, "到期提醒", 13, p.ink, Typeface.NORMAL),
                Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        reminderSwitch = new Switch(context);
        reminderSwitch.setChecked(NotificationCenter.isEnabled(activity));
        reminderSwitch.setButtonTintList(ColorStateList.valueOf(p.accent));
        reminderSwitch.setTrackTintList(ColorStateList.valueOf(Theme.alpha(p.accent, 110)));
        reminderSwitch.setContentDescription("到期提醒");
        reminderRow.addView(reminderSwitch);
        reminder.addView(reminderRow);
        reminderSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (updating) return;
            NotificationCenter.setEnabled(activity, checked);
            if (checked) {
                NotificationCenter.requestPermission(activity);
                Toast.makeText(activity, "已开启到期提醒", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "已关闭到期提醒", Toast.LENGTH_SHORT).show();
            }
        });

        // Data
        LinearLayout data = addCard(content, "数据", "可读 JSON。");
        LinearLayout dataRow = Ui.row(context);
        TextView export = button("导出数据", true, v -> activity.exportData());
        TextView importButton = button("导入数据", false, v -> activity.importData());
        dataRow.addView(export, new LinearLayout.LayoutParams(0, Ui.dp(context, 42), 1f));
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(0, Ui.dp(context, 42), 1f);
        importParams.leftMargin = Ui.dp(context, 8);
        dataRow.addView(importButton, importParams);
        data.addView(dataRow);
        TextView dataNote = Ui.text(context,
                "校验后覆盖或合并。插件文件不导出。",
                10.5f, p.muted, Typeface.NORMAL);
        dataNote.setLineSpacing(Ui.dp(context, 2), 1.15f);
        LinearLayout.LayoutParams dataNoteParams = Ui.matchWrap();
        dataNoteParams.topMargin = Ui.dp(context, 8);
        data.addView(dataNote, dataNoteParams);
        dataPath = Ui.text(context, store.dataFile().getAbsolutePath(), 9.5f, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams pathParams = Ui.matchWrap();
        pathParams.topMargin = Ui.dp(context, 6);
        data.addView(dataPath, pathParams);

        // Update
        LinearLayout update = addCard(content, "更新", "仅检查更新时联网。");
        prefixInput = field(store.data().config.updatePrefix, 1);
        prefixInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        update.addView(prefixInput);
        LinearLayout prefixRow = Ui.row(context);
        LinearLayout.LayoutParams prefixRowParams = Ui.matchWrap();
        prefixRowParams.topMargin = Ui.dp(context, 8);
        update.addView(prefixRow, prefixRowParams);
        TextView savePrefix = button("保存前缀", false, v -> savePrefix());
        prefixRow.addView(savePrefix, new LinearLayout.LayoutParams(0, Ui.dp(context, 38), 1f));
        TextView resetPrefix = button("恢复默认", false, v -> resetPrefix());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, Ui.dp(context, 38), 1f);
        resetParams.leftMargin = Ui.dp(context, 8);
        prefixRow.addView(resetPrefix, resetParams);
        TextView check = button("检查更新", true, v -> checkUpdate());
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 40));
        checkParams.topMargin = Ui.dp(context, 8);
        update.addView(check, checkParams);
        updateStatus = Ui.text(context, "尚未检查更新。", 10.5f, p.muted, Typeface.NORMAL);
        updateStatus.setLineSpacing(Ui.dp(context, 2), 1.15f);
        LinearLayout.LayoutParams updateStatusParams = Ui.matchWrap();
        updateStatusParams.topMargin = Ui.dp(context, 7);
        update.addView(updateStatus, updateStatusParams);
        TextView updateNote = Ui.text(context,
                "更新地址 = 前缀 + " + UpdateManager.MANIFEST_PATH, 9.5f, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams updateNoteParams = Ui.matchWrap();
        updateNoteParams.topMargin = Ui.dp(context, 4);
        update.addView(updateNote, updateNoteParams);

        // About
        LinearLayout about = addCard(content, "关于 goal", "把目标，做小一点。");
        versionText = Ui.text(context, "版本 v" + UpdateManager.currentVersion(), 12, p.ink, Typeface.BOLD);
        about.addView(versionText);
        TextView offline = Ui.text(context,
                "免费 · 无广告 · 无账号 · 无云同步。",
                10.5f, p.muted, Typeface.NORMAL);
        offline.setLineSpacing(Ui.dp(context, 2), 1.15f);
        LinearLayout.LayoutParams offlineParams = Ui.matchWrap();
        offlineParams.topMargin = Ui.dp(context, 7);
        about.addView(offline, offlineParams);
    }

    public View root() {
        return root;
    }

    public void refresh() {
        updating = true;
        if (!nameInput.isFocused()) nameInput.setText(store.data().user.name);
        if (!mottoInput.isFocused()) mottoInput.setText(store.data().user.motto);
        darkSwitch.setChecked(store.data().config.dark);
        reminderSwitch.setChecked(NotificationCenter.isEnabled(activity));
        if (!accentInput.isFocused()) {
            accentInput.setText(store.data().config.accent);
            accentPreview.setBackground(Ui.background(Theme.parse(store.data().config.accent), 10, activity));
        }
        if (!prefixInput.isFocused()) prefixInput.setText(store.data().config.updatePrefix);
        updating = false;
    }

    private LinearLayout addCard(LinearLayout parent, String title, String subtitle) {
        Context context = activity;
        LinearLayout card = Ui.column(context);
        card.setBackground(Ui.background(p.surface, 18, p.line, 1f, context));
        card.setPadding(Ui.dp(context, 16), Ui.dp(context, 15), Ui.dp(context, 16), Ui.dp(context, 15));
        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.bottomMargin = Ui.dp(context, 12);
        parent.addView(card, params);
        card.addView(Ui.title(context, title, 16, p.ink));
        TextView sub = Ui.text(context, subtitle, 11, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams subParams = Ui.matchWrap();
        subParams.topMargin = Ui.dp(context, 3);
        subParams.bottomMargin = Ui.dp(context, 10);
        card.addView(sub, subParams);
        return card;
    }

    private TextView sectionText(String text) {
        TextView value = Ui.text(activity, text, 10.5f, p.muted, Typeface.BOLD);
        value.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.topMargin = Ui.dp(activity, 8);
        params.bottomMargin = Ui.dp(activity, 5);
        value.setLayoutParams(params);
        return value;
    }

    private EditText field(String value, int lines) {
        EditText input = new EditText(activity);
        input.setText(value == null ? "" : value);
        input.setTextColor(p.ink);
        input.setTextSize(13);
        input.setBackground(Ui.background(p.surface2, 12, activity));
        input.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10), Ui.dp(activity, 12), Ui.dp(activity, 10));
        input.setSingleLine(lines == 1);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        return input;
    }

    private TextView button(String label, boolean primary, View.OnClickListener listener) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextSize(12);
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(activity, 14), 0, Ui.dp(activity, 14), 0);
        if (primary) {
            view.setTextColor(p.onAccent);
            view.setBackground(Ui.ripple(Ui.background(p.accent, 11, activity), Theme.alpha(0xFFFFFFFF, 40)));
        } else {
            view.setTextColor(p.ink);
            view.setBackground(Ui.ripple(Ui.background(Theme.alpha(p.surface2, 180), 11, p.line, 0.8f, activity),
                    Theme.alpha(p.ink, 20)));
        }
        view.setOnClickListener(listener);
        Ui.pressable(view);
        return view;
    }

    private void buildThemeTiles() {
        themeGrid.removeAllViews();
        List<Theme.Preset> presets = Theme.presets();
        String selected = store.data().config.theme;
        for (int rowIndex = 0; rowIndex * 3 < presets.size(); rowIndex++) {
            LinearLayout row = Ui.row(activity);
            for (int column = 0; column < 3; column++) {
                int index = rowIndex * 3 + column;
                if (index >= presets.size()) break;
                Theme.Preset preset = presets.get(index);
                boolean active = preset.id.equals(selected);
                LinearLayout tile = Ui.column(activity);
                tile.setBackground(active
                        ? Ui.background(p.accentUltra, 12, p.accent, 1.4f, activity)
                        : Ui.background(p.surface2, 12, p.line, 0.8f, activity));
                tile.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 9), Ui.dp(activity, 10), Ui.dp(activity, 9));
                LinearLayout nameRow = Ui.row(activity);
                View dot = new View(activity);
                dot.setBackground(Ui.background(Theme.parse(preset.accent), 4, activity));
                nameRow.addView(dot, new LinearLayout.LayoutParams(Ui.dp(activity, 8), Ui.dp(activity, 8)));
                TextView name = Ui.text(activity, preset.name, 11.5f, active ? p.accent : p.ink, Typeface.BOLD);
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                nameParams.leftMargin = Ui.dp(activity, 6);
                nameRow.addView(name, nameParams);
                tile.addView(nameRow);
                tile.addView(Ui.text(activity, preset.description, 9.5f, p.muted, Typeface.NORMAL));
                tile.setOnClickListener(v -> {
                    if (preset.id.equals(store.data().config.theme)) return;
                    store.updateTheme(preset.id);
                    activity.onThemeChanged();
                });
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                        0, Ui.dp(activity, 58), 1f);
                if (column > 0) tileParams.leftMargin = Ui.dp(activity, 7);
                if (rowIndex > 0) tileParams.topMargin = Ui.dp(activity, 7);
                row.addView(tile, tileParams);
            }
            themeGrid.addView(row);
        }
    }

    private void saveProfile() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(activity, "称呼不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        store.updateUser(name, mottoInput.getText().toString().trim());
        Toast.makeText(activity, "资料已保存", Toast.LENGTH_SHORT).show();
    }

    private void applyAccent() {
        String normalized = Theme.normalizeHex(accentInput.getText().toString());
        if (normalized == null) {
            Toast.makeText(activity, "请输入合法的 HEX 颜色，例如 #D9653B", Toast.LENGTH_SHORT).show();
            return;
        }
        store.updateAccent(normalized);
        activity.onThemeChanged();
    }

    private void savePrefix() {
        String prefix = prefixInput.getText().toString().trim();
        if (!prefix.startsWith("http://") && !prefix.startsWith("https://")) {
            Toast.makeText(activity, "前缀需要以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show();
            return;
        }
        String normalized = UpdateManager.normalizePrefix(prefix);
        store.updatePrefix(normalized);
        prefixInput.setText(store.data().config.updatePrefix);
        updateStatus.setText("更新前缀已保存。");
        Toast.makeText(activity, "更新前缀已保存", Toast.LENGTH_SHORT).show();
    }

    private void resetPrefix() {
        store.updatePrefix("https://ghproxy.net/");
        prefixInput.setText(store.data().config.updatePrefix);
        updateStatus.setText("已恢复默认更新前缀。");
    }

    private void checkUpdate() {
        String prefix = store.data().config.updatePrefix;
        updateStatus.setText("正在检查更新…");
        new Thread(() -> {
            UpdateManager.Result result = UpdateManager.check("android", prefix);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                if (result.error != null) {
                    updateStatus.setText(result.error);
                    return;
                }
                if (result.hasUpdate) {
                    updateStatus.setText("发现新版本 v" + result.latestVersion + "。");
                    new UpdateDialog(activity, result, prefix).showDialog();
                } else {
                    updateStatus.setText("已是最新版本 v" + UpdateManager.currentVersion() + "。");
                }
            });
        }, "goal-update-check").start();
    }
}
