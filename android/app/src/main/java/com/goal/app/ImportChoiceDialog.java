package com.goal.app;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Lets the user overwrite the whole backup or merge tasks only. */
public class ImportChoiceDialog extends BaseCardDialog {
    public interface Listener {
        void onChoice(String choice);
    }

    public ImportChoiceDialog(Context context, Models.AppData imported, String fileName, Listener listener) {
        super(context);
        header("导入备份", fileName);
        Models.User user = imported.user == null ? new Models.User() : imported.user;
        int count = imported.tasks == null ? 0 : imported.tasks.size();

        LinearLayout summary = Ui.column(context);
        summary.setBackground(Ui.background(p.surface2, 16, context));
        summary.setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 14));
        summary.addView(Ui.title(context, user.name + " · " + count + " 条计划", 15, p.ink));
        summary.addView(Ui.text(context, "主题：" + Theme.preset(imported.config.theme).name
                + (imported.config.dark ? " · 深色" : " · 浅色"), 12, p.muted, Typeface.NORMAL));
        LinearLayout.LayoutParams summaryParams = Ui.matchWrap();
        summaryParams.topMargin = Ui.dp(context, 16);
        body.addView(summary, summaryParams);

        TextView overwrite = Ui.text(context,
                "覆盖：用备份中的资料、配置和全部计划替换当前数据。", 12.5f, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams op = Ui.matchWrap();
        op.topMargin = Ui.dp(context, 16);
        body.addView(overwrite, op);
        TextView merge = Ui.text(context,
                "合并：保留当前资料与配置，只新增备份中尚不存在的计划。", 12.5f, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams mp = Ui.matchWrap();
        mp.topMargin = Ui.dp(context, 8);
        body.addView(merge, mp);

        LinearLayout footer = footer();
        TextView cancel = button("取消", false, v -> {
            listener.onChoice("cancel");
            dismiss();
        });
        TextView mergeButton = button("合并", false, v -> {
            listener.onChoice("merge");
            dismiss();
        });
        TextView overwriteButton = button("覆盖", true, v -> {
            listener.onChoice("overwrite");
            dismiss();
        });
        ((LinearLayout.LayoutParams) cancel.getLayoutParams()).weight = 0.7f;
        leftMargin(mergeButton, 10);
        leftMargin(overwriteButton, 10);
        footer.addView(cancel);
        footer.addView(mergeButton);
        footer.addView(overwriteButton);
    }

    private void leftMargin(TextView view, int dp) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.leftMargin = Ui.dp(context, dp);
    }

    public void showDialog() {
        showAnimated();
    }
}
