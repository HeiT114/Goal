package com.goal.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Plugin page: notice, installed packages, install and run. */
public class PluginsPage {
    private final MainActivity activity;
    private final PluginManager manager;
    private final Theme.Palette p;
    private final View root;
    private final LinearLayout list;
    private final TextView countText;

    public PluginsPage(MainActivity activity) {
        this.activity = activity;
        this.manager = activity.pluginManager;
        this.p = Theme.get();
        Context context = activity;

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root = scroll;

        LinearLayout content = Ui.column(context);
        content.setPadding(Ui.dp(context, 20), Ui.dp(context, 22), Ui.dp(context, 20), Ui.dp(context, 32));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(Ui.label(context, "PLUGINS · 插件", 11, p.accent));
        TextView title = Ui.title(context, "给 goal 加一点自己的东西", 26, p.ink);
        title.setLineSpacing(0, 1.05f);
        LinearLayout.LayoutParams titleParams = Ui.matchWrap();
        titleParams.topMargin = Ui.dp(context, 10);
        content.addView(title, titleParams);
        TextView subtitle = Ui.text(context, "默认关闭。第三方内容，请自行判断。",
                12.5f, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = Ui.matchWrap();
        subtitleParams.topMargin = Ui.dp(context, 6);
        content.addView(subtitle, subtitleParams);

        LinearLayout actions = Ui.row(context);
        LinearLayout.LayoutParams actionParams = Ui.matchWrap();
        actionParams.topMargin = Ui.dp(context, 16);
        content.addView(actions, actionParams);
        TextView add = Ui.text(context, "+  添加插件", 12, p.onAccent, Typeface.BOLD);
        add.setGravity(Gravity.CENTER);
        add.setBackground(Ui.ripple(Ui.background(p.accent, 12, context), Theme.alpha(0xFFFFFFFF, 40)));
        add.setPadding(Ui.dp(context, 16), Ui.dp(context, 10), Ui.dp(context, 16), Ui.dp(context, 10));
        add.setOnClickListener(v -> activity.pickPluginPackage());
        Ui.pressable(add);
        actions.addView(add);
        TextView hint = Ui.text(context, "本地 .zip 插件包", 10.5f, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.leftMargin = Ui.dp(context, 10);
        actions.addView(hint, hintParams);

        LinearLayout section = Ui.row(context);
        section.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sectionParams = Ui.matchWrap();
        sectionParams.topMargin = Ui.dp(context, 20);
        content.addView(section, sectionParams);
        section.addView(Ui.title(context, "已安装", 16, p.ink));
        countText = Ui.text(context, "0 个", 11, p.muted, Typeface.NORMAL);
        countText.setBackground(Ui.background(p.surface2, 9, context));
        countText.setPadding(Ui.dp(context, 8), Ui.dp(context, 3), Ui.dp(context, 8), Ui.dp(context, 3));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.leftMargin = Ui.dp(context, 8);
        section.addView(countText, countParams);
        section.addView(new View(context), Ui.lp(0, 1, 1f));
        TextView defaultOff = Ui.text(context, "安装后默认关闭", 10.5f, p.muted, Typeface.NORMAL);
        section.addView(defaultOff);

        list = Ui.column(context);
        LinearLayout.LayoutParams listParams = Ui.matchWrap();
        listParams.topMargin = Ui.dp(context, 10);
        content.addView(list, listParams);

        TextView notice = Ui.text(context, PluginManager.NOTICE, 10.5f, p.muted, Typeface.NORMAL);
        notice.setLineSpacing(Ui.dp(context, 2), 1.16f);
        notice.setAlpha(0.72f);
        LinearLayout.LayoutParams noticeParams = Ui.matchWrap();
        noticeParams.topMargin = Ui.dp(context, 16);
        content.addView(notice, noticeParams);

        refresh();
    }

    public View root() {
        return root;
    }

    public void refresh() {
        List<Models.InstalledPlugin> plugins = manager.list();
        countText.setText(plugins.size() + " 个");
        list.removeAllViews();
        if (plugins.isEmpty()) {
            LinearLayout empty = Ui.column(activity);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setBackground(Ui.background(p.surface, 16, p.line, 1f, activity));
            empty.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 28), Ui.dp(activity, 20), Ui.dp(activity, 28));
            GlyphView icon = new GlyphView(activity, GlyphView.GRID);
            icon.color(p.muted).size(26);
            empty.addView(icon);
            TextView headline = Ui.title(activity, "还没有添加插件", 15, p.ink);
            LinearLayout.LayoutParams headlineParams = Ui.matchWrap();
            headlineParams.topMargin = Ui.dp(activity, 10);
            empty.addView(headline, headlineParams);
            TextView text = Ui.text(activity,
                    "插件可以从 GitHub 等渠道获取，goal 不会主动连接插件市场，也不会替你下载。",
                    11.5f, p.muted, Typeface.NORMAL);
            text.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams textParams = Ui.matchWrap();
            textParams.topMargin = Ui.dp(activity, 6);
            empty.addView(text, textParams);
            list.addView(empty);
            return;
        }
        for (Models.InstalledPlugin plugin : plugins) {
            View card = pluginCard(plugin);
            LinearLayout.LayoutParams params = Ui.matchWrap();
            params.bottomMargin = Ui.dp(activity, 10);
            list.addView(card, params);
        }
    }

    private View pluginCard(Models.InstalledPlugin plugin) {
        Context context = activity;
        Models.PluginManifest manifest = plugin.manifest;
        LinearLayout card = Ui.column(context);
        card.setBackground(Ui.background(p.surface, 16, p.line, 1f, context));
        card.setPadding(Ui.dp(context, 14), Ui.dp(context, 13), Ui.dp(context, 14), Ui.dp(context, 12));

        LinearLayout head = Ui.row(context);
        TextView badge = Ui.title(context,
                manifest.name == null || manifest.name.isEmpty() ? "?" : manifest.name.substring(0, 1),
                17, p.accent);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.background(p.surface2, 12, context));
        head.addView(badge, new LinearLayout.LayoutParams(Ui.dp(context, 42), Ui.dp(context, 42)));

        LinearLayout texts = Ui.column(context);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = Ui.dp(context, 11);
        head.addView(texts, textsParams);
        texts.addView(Ui.title(context, manifest.name, 15, p.ink));
        texts.addView(Ui.text(context,
                "v" + manifest.version + " · " + manifest.author, 10.5f, p.muted, Typeface.NORMAL));
        texts.addView(Ui.text(context, "网络：" + manifest.networkLabel(), 10.5f, p.muted, Typeface.NORMAL));

        TextView state = Ui.text(context, plugin.enabled ? "已启用" : "未启用", 10,
                plugin.enabled ? p.accent : p.muted, Typeface.BOLD);
        state.setBackground(Ui.background(plugin.enabled ? p.accentUltra : p.surface2, 8, context));
        state.setPadding(Ui.dp(context, 8), Ui.dp(context, 3), Ui.dp(context, 8), Ui.dp(context, 3));
        head.addView(state);
        card.addView(head);

        String description = manager.readDescription(plugin);
        description = stripMarkdown(description);
        if (description.length() > 170) description = description.substring(0, 170) + "…";
        TextView desc = Ui.text(context, description, 11.5f, p.muted, Typeface.NORMAL);
        desc.setMaxLines(4);
        desc.setLineSpacing(Ui.dp(context, 2), 1.15f);
        LinearLayout.LayoutParams descParams = Ui.matchWrap();
        descParams.topMargin = Ui.dp(context, 9);
        card.addView(desc, descParams);

        LinearLayout actions = Ui.row(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = Ui.matchWrap();
        actionsParams.topMargin = Ui.dp(context, 9);
        card.addView(actions, actionsParams);

        if (plugin.enabled) {
            TextView open = chipButton("打开", true);
            open.setOnClickListener(v -> openPanel(plugin));
            actions.addView(open);
            TextView disable = chipButton("停用", false);
            disable.setOnClickListener(v -> {
                manager.setEnabled(plugin, false);
                Toast.makeText(activity, "已停用「" + manifest.name + "」", Toast.LENGTH_SHORT).show();
                refresh();
            });
            LinearLayout.LayoutParams disableParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            disableParams.leftMargin = Ui.dp(context, 8);
            actions.addView(disable, disableParams);
        } else {
            TextView enable = chipButton("启用", true);
            enable.setOnClickListener(v -> confirmEnable(plugin));
            actions.addView(enable);
        }

        actions.addView(new View(context), Ui.lp(0, 1, 1f));
        TextView remove = Ui.text(context, "卸载", 11, p.danger, Typeface.NORMAL);
        remove.setPadding(Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 6), Ui.dp(context, 8));
        remove.setOnClickListener(v -> confirmUninstall(plugin));
        Ui.pressable(remove);
        actions.addView(remove);
        return card;
    }

    private TextView chipButton(String label, boolean primary) {
        TextView button = Ui.text(activity, label, 11, primary ? p.accent : p.muted, Typeface.BOLD);
        button.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 7), Ui.dp(activity, 12), Ui.dp(activity, 7));
        button.setBackground(Ui.ripple(Ui.background(primary ? p.accentSoft : p.surface2, 8, activity),
                Theme.alpha(p.ink, 20)));
        Ui.pressable(button);
        return button;
    }

    private void confirmEnable(Models.InstalledPlugin plugin) {
        String body = "即将启用「" + plugin.manifest.name + "」。\n\n"
                + "网络权限：" + plugin.manifest.networkLabel() + "\n\n"
                + PluginManager.NOTICE;
        new ConfirmDialog(activity, "启用插件", body, "我已知晓，启用", false, confirmed -> {
            if (!confirmed) return;
            manager.setEnabled(plugin, true);
            Toast.makeText(activity, "已启用「" + plugin.manifest.name + "」", Toast.LENGTH_SHORT).show();
            refresh();
        }).showDialog();
    }

    private void confirmUninstall(Models.InstalledPlugin plugin) {
        new ConfirmDialog(activity, "卸载插件？",
                "「" + plugin.manifest.name + "」及其文件将从本机删除，无法撤销。",
                "卸载", true, confirmed -> {
                    if (!confirmed) return;
                    manager.uninstall(plugin);
                    Toast.makeText(activity, "已卸载", Toast.LENGTH_SHORT).show();
                    refresh();
                }).showDialog();
    }

    private void openPanel(Models.InstalledPlugin plugin) {
        try {
            Models.Panel panel = manager.readPanel(plugin);
            new PluginPanelDialog(activity, manager, plugin, panel).showDialog();
        } catch (Exception error) {
            new ConfirmDialog(activity, "无法打开插件",
                    error.getMessage() == null ? "插件面板读取失败。" : error.getMessage(),
                    "知道了", false, confirmed -> {}).showDialog();
        }
    }

    public void onPluginPicked(File file) {
        try {
            PluginManager.Preview preview = manager.inspectZip(file);
            new PluginInstallDialog(activity, manager, preview, plugin -> refresh()).showDialog();
        } catch (Exception error) {
            new ConfirmDialog(activity, "无法添加插件",
                    error.getMessage() == null ? "这不像是有效的 .zip 插件包。" : error.getMessage(),
                    "知道了", false, confirmed -> {}).showDialog();
        }
    }

    private static String stripMarkdown(String raw) {
        if (raw == null) return "";
        String text = raw.replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("[*_>]", "")
                .replaceAll("\\[(.*?)\\]\\(.*?\\)", "$1")
                .replaceAll("\\s+", " ")
                .trim();
        return text;
    }
}
