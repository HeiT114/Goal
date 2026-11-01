package com.goal.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Shows the complete package, network declarations and responsibility notice before install. */
public class PluginInstallDialog extends BaseCardDialog {
    public interface Listener {
        void onInstalled(Models.InstalledPlugin plugin);
    }

    private final PluginManager manager;
    private final PluginManager.Preview preview;
    private final Listener listener;
    private TextView errorText;
    private TextView installButton;

    public PluginInstallDialog(Context context, PluginManager manager, PluginManager.Preview preview,
                               Listener listener) {
        super(context);
        this.manager = manager;
        this.preview = preview;
        this.listener = listener;
        Models.PluginManifest manifest = preview.manifest;
        header("添加插件", manifest.name + " · v" + manifest.version);

        LinearLayout summary = Ui.row(context);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setBackground(Ui.background(p.surface2, 16, context));
        summary.setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12));
        TextView badge = Ui.title(context, manifest.name.substring(0, 1), 18, p.accent);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.background(p.accentUltra, 12, context));
        summary.addView(badge, new LinearLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)));
        LinearLayout texts = Ui.column(context);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = Ui.dp(context, 12);
        summary.addView(texts, textParams);
        texts.addView(Ui.title(context, manifest.name, 15, p.ink));
        texts.addView(Ui.text(context,
                "v" + manifest.version + " · " + manifest.author + " · " + manifest.id,
                11, p.muted, Typeface.NORMAL));
        LinearLayout.LayoutParams summaryParams = Ui.matchWrap();
        summaryParams.topMargin = Ui.dp(context, 14);
        body.addView(summary, summaryParams);

        sectionLabel("网络权限");
        TextView network = Ui.text(context,
                manifest.networkLabel(), 12.5f, p.ink, Typeface.NORMAL);
        network.setBackground(Ui.background(p.surface2, 12, context));
        network.setPadding(Ui.dp(context, 12), Ui.dp(context, 9), Ui.dp(context, 12), Ui.dp(context, 9));
        body.addView(network, Ui.matchWrap());
        TextView entry = Ui.text(context,
                "入口：" + manifest.entry + " · 声明式面板，不执行插件代码",
                10.5f, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams entryParams = Ui.matchWrap();
        entryParams.topMargin = Ui.dp(context, 6);
        body.addView(entry, entryParams);

        sectionLabel("说明文件");
        LinearLayout description = Ui.column(context);
        description.setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 8));
        description.setBackground(Ui.background(p.surface2, 12, context));
        String text = preview.description == null || preview.description.trim().isEmpty()
                ? "（没有说明内容）" : preview.description.trim();
        TextView desc = Ui.text(context, text, 11.5f, p.muted, Typeface.NORMAL);
        desc.setLineSpacing(Ui.dp(context, 2), 1.15f);
        description.addView(desc);
        ScrollView descScroll = new ScrollView(context);
        descScroll.setVerticalScrollBarEnabled(false);
        descScroll.addView(description);
        descScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 160)));
        body.addView(descScroll);

        LinearLayout notice = Ui.column(context);
        notice.setBackground(Ui.background(p.accentUltra, 14, p.accent, 0.7f, context));
        notice.setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10));
        TextView noticeText = Ui.text(context, PluginManager.NOTICE, 11, p.ink, Typeface.NORMAL);
        noticeText.setLineSpacing(Ui.dp(context, 2), 1.15f);
        notice.addView(noticeText);
        LinearLayout.LayoutParams noticeParams = Ui.matchWrap();
        noticeParams.topMargin = Ui.dp(context, 14);
        body.addView(notice, noticeParams);

        CheckBox agree = new CheckBox(context);
        agree.setText("我已阅读并理解上述责任声明");
        agree.setTextSize(12);
        agree.setTextColor(p.ink);
        agree.setButtonTintList(ColorStateList.valueOf(p.accent));
        agree.setPadding(0, Ui.dp(context, 6), 0, 0);
        LinearLayout.LayoutParams agreeParams = Ui.matchWrap();
        agreeParams.topMargin = Ui.dp(context, 6);
        body.addView(agree, agreeParams);

        errorText = Ui.text(context, "", 11.5f, p.danger, Typeface.NORMAL);
        errorText.setVisibility(View.GONE);
        LinearLayout.LayoutParams errorParams = Ui.matchWrap();
        errorParams.topMargin = Ui.dp(context, 6);
        body.addView(errorText, errorParams);

        LinearLayout footer = footer();
        TextView cancel = button("取消", false, v -> dismiss());
        installButton = button("安装（默认关闭）", true, v -> install());
        ((LinearLayout.LayoutParams) cancel.getLayoutParams()).rightMargin = Ui.dp(context, 10);
        footer.addView(cancel);
        footer.addView(installButton);
        installButton.setEnabled(false);
        installButton.setAlpha(0.5f);
        agree.setOnCheckedChangeListener((buttonView, checked) -> {
            installButton.setEnabled(checked);
            installButton.setAlpha(checked ? 1f : 0.5f);
        });
    }

    private void install() {
        try {
            Models.InstalledPlugin plugin = manager.install(preview);
            listener.onInstalled(plugin);
            Toast.makeText(context, "已安装「" + plugin.manifest.name + "」，默认关闭", Toast.LENGTH_SHORT).show();
            dismiss();
        } catch (Exception error) {
            errorText.setText(error.getMessage() == null ? "安装失败。" : error.getMessage());
            errorText.setVisibility(View.VISIBLE);
        }
    }

    public void showDialog() {
        showAnimated();
    }
}
