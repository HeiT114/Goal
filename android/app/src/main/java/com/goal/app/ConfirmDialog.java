package com.goal.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Two-action confirmation used before irreversible or meaningful actions. */
public class ConfirmDialog extends BaseCardDialog {
    public interface Listener {
        void onResult(boolean confirmed);
    }

    private boolean answered = false;

    public ConfirmDialog(Context context, String title, String bodyText, String primaryText,
                         boolean danger, Listener listener) {
        super(context);
        header(title, null);
        TextView bodyView = Ui.text(context, bodyText, 13.5f, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams bodyParams = Ui.matchWrap();
        bodyParams.topMargin = Ui.dp(context, 14);
        body.addView(bodyView, bodyParams);

        LinearLayout footer = footer();
        TextView cancel = button("取消", false, v -> {
            answered = true;
            listener.onResult(false);
            dismiss();
        });
        TextView confirm = button(primaryText, true, v -> {
            answered = true;
            listener.onResult(true);
            dismiss();
        });
        if (danger) {
            confirm.setBackground(Ui.ripple(Ui.background(p.danger, 13, context), Theme.alpha(0xFFFFFFFF, 40)));
        }
        LinearLayout.LayoutParams left = (LinearLayout.LayoutParams) cancel.getLayoutParams();
        left.rightMargin = Ui.dp(context, 10);
        footer.addView(cancel);
        footer.addView(confirm);
        setOnDismissListener(dialog -> {
            if (!answered) listener.onResult(false);
        });
        setCanceledOnTouchOutside(true);
    }

    public void showDialog() {
        showAnimated();
    }
}
