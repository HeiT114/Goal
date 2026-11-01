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

import java.util.List;

/** Renders a declarative plugin panel. Plugin code is never executed. */
public class PluginPanelDialog extends BaseCardDialog {
    private final PluginManager manager;
    private final Models.InstalledPlugin plugin;
    private final Models.Panel panel;

    public PluginPanelDialog(Context context, PluginManager manager, Models.InstalledPlugin plugin,
                             Models.Panel panel) {
        super(context);
        this.manager = manager;
        this.plugin = plugin;
        this.panel = panel;
        header(panel.title == null || panel.title.isEmpty() ? plugin.manifest.name : panel.title,
                panel.subtitle == null || panel.subtitle.isEmpty()
                        ? plugin.manifest.author + " · v" + plugin.manifest.version
                        : panel.subtitle);

        View stripe = new View(context);
        int accent = p.accent;
        if (panel.accent != null && !panel.accent.isEmpty()) accent = Theme.parse(panel.accent);
        stripe.setBackground(Ui.background(accent, 2, context));
        LinearLayout.LayoutParams stripeParams = new LinearLayout.LayoutParams(Ui.dp(context, 38), Ui.dp(context, 3));
        stripeParams.topMargin = Ui.dp(context, 14);
        body.addView(stripe, stripeParams);

        LinearLayout content = Ui.column(context);
        content.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 4));
        for (Models.PanelBlock block : panel.blocks) {
            if (block == null) continue;
            addBlock(content, block);
        }
        if (panel.blocks.isEmpty()) {
            TextView empty = Ui.text(context, "这个插件没有可展示的内容。", 12, p.muted, Typeface.NORMAL);
            empty.setPadding(0, Ui.dp(context, 16), 0, Ui.dp(context, 16));
            content.addView(empty);
        }

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 360));
        scrollParams.topMargin = Ui.dp(context, 10);
        body.addView(scroll, scrollParams);

        TextView footer = Ui.text(context, PluginManager.NOTICE, 10, p.muted, Typeface.NORMAL);
        footer.setLineSpacing(Ui.dp(context, 1), 1.15f);
        LinearLayout.LayoutParams footerParams = Ui.matchWrap();
        footerParams.topMargin = Ui.dp(context, 12);
        body.addView(footer, footerParams);
    }

    private void addBlock(LinearLayout content, Models.PanelBlock block) {
        String type = block.type == null ? "" : block.type;
        switch (type) {
            case "heading": {
                TextView view = Ui.title(context, safe(block.text), 16, p.ink);
                LinearLayout.LayoutParams params = Ui.matchWrap();
                params.topMargin = Ui.dp(context, 14);
                content.addView(view, params);
                break;
            }
            case "text": {
                TextView view = Ui.text(context, safe(block.text), 13, p.ink, Typeface.NORMAL);
                view.setLineSpacing(Ui.dp(context, 2), 1.2f);
                LinearLayout.LayoutParams params = Ui.matchWrap();
                params.topMargin = Ui.dp(context, 8);
                content.addView(view, params);
                break;
            }
            case "quote": {
                TextView view = Ui.text(context, safe(block.text), 12.5f, p.muted, Typeface.ITALIC);
                view.setLineSpacing(Ui.dp(context, 2), 1.2f);
                view.setPadding(Ui.dp(context, 12), Ui.dp(context, 9), Ui.dp(context, 12), Ui.dp(context, 9));
                view.setBackground(Ui.background(p.surface2, 10, p.accent, 2f, context));
                LinearLayout.LayoutParams params = Ui.matchWrap();
                params.topMargin = Ui.dp(context, 10);
                content.addView(view, params);
                break;
            }
            case "divider": {
                View view = new View(context);
                view.setBackgroundColor(p.line);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(context, 0.8f)));
                params.topMargin = Ui.dp(context, 14);
                params.bottomMargin = Ui.dp(context, 4);
                content.addView(view, params);
                break;
            }
            case "checklist": {
                if (block.title != null && !block.title.isEmpty()) {
                    TextView title = Ui.text(context, block.title, 12, p.ink, Typeface.BOLD);
                    LinearLayout.LayoutParams params = Ui.matchWrap();
                    params.topMargin = Ui.dp(context, 12);
                    content.addView(title, params);
                }
                List<String> items = block.items;
                if (items != null) {
                    for (String item : items) {
                        TextView row = Ui.text(context, "○  " + safe(item), 12, p.muted, Typeface.NORMAL);
                        row.setLineSpacing(Ui.dp(context, 1), 1.1f);
                        LinearLayout.LayoutParams params = Ui.matchWrap();
                        params.topMargin = Ui.dp(context, 5);
                        params.leftMargin = Ui.dp(context, 4);
                        content.addView(row, params);
                    }
                }
                break;
            }
            case "link": {
                String label = block.label == null || block.label.isEmpty() ? block.url : block.label;
                TextView view = Ui.text(context, "↗  " + safe(label), 12, p.accent, Typeface.BOLD);
                view.setBackground(Ui.ripple(Ui.background(p.accentUltra, 10, context), Theme.alpha(p.ink, 20)));
                view.setPadding(Ui.dp(context, 12), Ui.dp(context, 9), Ui.dp(context, 12), Ui.dp(context, 9));
                view.setOnClickListener(v -> openLink(block.url));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = Ui.dp(context, 10);
                content.addView(view, params);
                break;
            }
            case "addTask": {
                buildAddTask(content, block);
                break;
            }
            default:
                break;
        }
    }

    private void buildAddTask(LinearLayout content, Models.PanelBlock block) {
        String title = block.taskTitle != null && !block.taskTitle.isEmpty()
                ? block.taskTitle
                : (block.title != null && !block.title.isEmpty() ? block.title : "插件计划");
        String mode = block.taskMode;
        if (!"duration".equals(mode) && !"variation".equals(mode)) mode = "submit";
        int target = block.target == null ? 0 : block.target;
        String unit = block.unit;
        if ("duration".equals(mode)) {
            int minutes = target <= 0 ? 25 : target;
            if (minutes < 1 || minutes > 1440) minutes = 25;
            target = minutes * 60;
            unit = "分钟";
        } else if ("variation".equals(mode)) {
            if (target < 1 || target > 100000) target = 10;
            if (unit == null || unit.trim().isEmpty()) unit = "次";
        } else {
            target = 0;
            unit = "次";
        }
        final String finalMode = mode;
        final int finalTarget = target;
        final String finalUnit = unit;

        LinearLayout box = Ui.row(context);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(Ui.background(p.surface2, 14, p.line, 1f, context));
        box.setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10));
        LinearLayout texts = Ui.column(context);
        texts.addView(Ui.title(context, title, 13, p.ink));
        String detail = "submit".equals(mode) ? "提交型计划"
                : "duration".equals(mode) ? "时长 · " + (finalTarget / 60) + " 分钟"
                : "变化 · 目标 " + finalTarget + " " + finalUnit;
        texts.addView(Ui.text(context, detail, 11, p.muted, Typeface.NORMAL));
        box.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView add = Ui.text(context, "添加到主页", 11, p.accent, Typeface.BOLD);
        add.setBackground(Ui.ripple(Ui.background(p.accentSoft, 8, context), Theme.alpha(p.ink, 20)));
        add.setPadding(Ui.dp(context, 10), Ui.dp(context, 7), Ui.dp(context, 10), Ui.dp(context, 7));
        add.setOnClickListener(v -> {
            GoalApp.instance().store().addTask(title,
                    "由插件「" + plugin.manifest.name + "」添加。", finalMode, finalTarget, finalUnit, null);
            Toast.makeText(context, "已添加到主页", Toast.LENGTH_SHORT).show();
            dismiss();
        });
        box.addView(add);
        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.topMargin = Ui.dp(context, 12);
        content.addView(box, params);
    }

    private void openLink(String url) {
        String error = manager.openExternalLink(plugin, url);
        if (error != null) {
            new ConfirmDialog(context, "已阻止打开", error, "知道了", false, confirmed -> {}).showDialog();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public void showDialog() {
        showAnimated();
    }
}
