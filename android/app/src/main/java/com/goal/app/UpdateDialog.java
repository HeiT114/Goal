package com.goal.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shows the update notes and downloads the APK through the configured prefix. */
public class UpdateDialog extends BaseCardDialog {
    private final MainActivity activity;
    private final UpdateManager.Result result;
    private final String prefix;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final ProgressBar progress;
    private final TextView status;
    private TextView action;
    private File downloaded;

    public UpdateDialog(MainActivity activity, UpdateManager.Result result, String prefix) {
        super(activity);
        this.activity = activity;
        this.result = result;
        this.prefix = prefix;

        LinearLayout stripe = new LinearLayout(activity);
        stripe.setBackground(Ui.background(p.accent, 2, activity));
        body.addView(stripe, new LinearLayout.LayoutParams(Ui.dp(activity, 38), Ui.dp(activity, 4)));

        header("更新 goal", "发现新版本 v" + result.latestVersion + "，下载后由你确认安装。");

        LinearLayout versions = Ui.row(activity);
        versions.setGravity(Gravity.CENTER_VERTICAL);
        versions.addView(versionBadge("当前 v" + UpdateManager.currentVersion(), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        versions.addView(Ui.text(activity, "→", 15, p.muted, Typeface.NORMAL));
        LinearLayout.LayoutParams latestParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        latestParams.leftMargin = Ui.dp(activity, 8);
        versions.addView(versionBadge("最新 v" + result.latestVersion, true), latestParams);
        LinearLayout.LayoutParams versionsParams = Ui.matchWrap();
        versionsParams.topMargin = Ui.dp(activity, 16);
        body.addView(versions, versionsParams);

        sectionLabel("更新说明");
        LinearLayout notesBox = Ui.column(activity);
        notesBox.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10), Ui.dp(activity, 12), Ui.dp(activity, 10));
        notesBox.setBackground(Ui.background(p.surface2, 12, activity));
        String notes = result.notes == null || result.notes.trim().isEmpty()
                ? "这个版本没有附带更新说明。" : result.notes.trim();
        TextView notesText = Ui.text(activity, notes, 12, p.ink, Typeface.NORMAL);
        notesText.setLineSpacing(Ui.dp(activity, 3), 1.15f);
        notesBox.addView(notesText);
        body.addView(scroll(notesBox, 170));

        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(p.accent));
        progress.setBackgroundTintList(ColorStateList.valueOf(p.line));
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 18));
        progressParams.topMargin = Ui.dp(activity, 14);
        body.addView(progress, progressParams);

        status = Ui.text(activity, "", 10.5f, p.muted, Typeface.NORMAL);
        status.setLineSpacing(Ui.dp(activity, 2), 1.15f);
        LinearLayout.LayoutParams statusParams = Ui.matchWrap();
        statusParams.topMargin = Ui.dp(activity, 7);
        body.addView(status, statusParams);

        LinearLayout footer = footer();
        TextView later = button("稍后", false, v -> dismiss());
        footer.addView(later);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, Ui.dp(activity, 46), 1f);
        actionParams.leftMargin = Ui.dp(activity, 10);
        action = button("下载更新", true, v -> onAction());
        footer.addView(action, actionParams);

        if (result.downloadUrl == null || result.downloadUrl.trim().isEmpty()) {
            action.setEnabled(false);
            action.setAlpha(0.45f);
            status.setText("这份更新清单没有提供下载地址，请前往项目主页手动下载。");
        } else {
            status.setText("下载走设置中的更新前缀，只在你点击后开始。");
        }
        setOnDismissListener(dialog -> cancelled.set(true));
    }

    public void showDialog() {
        showAnimated();
    }

    private TextView versionBadge(String text, boolean highlight) {
        TextView view = Ui.text(activity, text, 11.5f, highlight ? p.accent : p.ink, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 9), Ui.dp(activity, 10), Ui.dp(activity, 9));
        view.setBackground(highlight
                ? Ui.background(p.accentUltra, 10, Theme.alpha(p.accent, 70), 0.8f, activity)
                : Ui.background(p.surface2, 10, p.line, 0.8f, activity));
        return view;
    }

    private void onAction() {
        if (downloaded != null) {
            installDownloaded();
            return;
        }
        if (result.downloadUrl == null || result.downloadUrl.trim().isEmpty()) return;
        download();
    }

    private void installDownloaded() {
        if (downloaded == null || !downloaded.isFile() || downloaded.length() == 0) {
            status.setText("安装包不存在，请重新下载。");
            downloaded = null;
            action.setText("下载更新");
            return;
        }
        activity.installApk(downloaded);
    }

    private void download() {
        final File target = new File(activity.getCacheDir(), "goal-" + result.latestVersion + ".apk");
        if (target.exists() && !target.delete()) {
            status.setText("无法清理旧的安装包。");
            return;
        }
        cancelled.set(false);
        action.setEnabled(false);
        action.setAlpha(0.55f);
        action.setText("下载中…");
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        status.setText("正在下载更新…");

        new Thread(() -> {
            try {
                UpdateManager.download(prefix, result.downloadUrl, target, fraction -> {
                    int value = (int) Math.round(fraction * 100);
                    activity.runOnUiThread(() -> {
                        progress.setProgress(value);
                        status.setText(String.format(Locale.CHINA, "正在下载 %d%%", value));
                    });
                }, cancelled);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || cancelled.get()) return;
                    progress.setProgress(100);
                    progress.setVisibility(View.GONE);
                    downloaded = target;
                    action.setText("安装更新");
                    action.setEnabled(true);
                    action.setAlpha(1f);
                    status.setText("下载完成，点击“安装更新”继续。文件位于应用缓存目录。");
                    Toast.makeText(activity, "更新已下载", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    progress.setVisibility(View.GONE);
                    action.setText("重新下载");
                    action.setEnabled(true);
                    action.setAlpha(1f);
                    String message = error.getMessage();
                    status.setText(message == null || message.isEmpty() ? "下载失败，请稍后重试。" : message);
                    if (!cancelled.get() && target.exists()) target.delete();
                });
            }
        }, "goal-download").start();
    }
}
