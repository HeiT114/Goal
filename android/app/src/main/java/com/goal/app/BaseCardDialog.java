package com.goal.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Centred card dialog with the shared visual language. */
public class BaseCardDialog extends Dialog {
    protected final Context context;
    protected final LinearLayout card;
    protected final LinearLayout body;
    protected final Theme.Palette p;

    public BaseCardDialog(Context context) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        this.context = context;
        this.p = Theme.get();
        FrameLayout root = new FrameLayout(context);
        root.setPadding(Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 18));
        card = Ui.column(context);
        card.setBackground(Ui.background(p.surface, 24, context));
        card.setPadding(Ui.dp(context, 22), Ui.dp(context, 20), Ui.dp(context, 22), Ui.dp(context, 20));
        card.setElevation(Ui.dp(context, 18));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        root.addView(card, cardParams);
        setContentView(root);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.48f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        }
        setCanceledOnTouchOutside(true);
        body = Ui.column(context);
        card.addView(body, Ui.matchWrap());
    }

    protected void header(String title, String subtitle) {
        LinearLayout row = Ui.row(context);
        LinearLayout texts = Ui.column(context);
        TextView heading = Ui.title(context, title, 21, p.ink);
        texts.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = Ui.text(context, subtitle, 12.5f, p.muted, Typeface.NORMAL);
            LinearLayout.LayoutParams params = Ui.matchWrap();
            params.topMargin = Ui.dp(context, 5);
            texts.addView(sub, params);
        }
        row.addView(texts, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        GlyphView close = new GlyphView(context).type(GlyphView.X).color(p.muted).size(22);
        close.setPadding(Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8));
        close.setOnClickListener(v -> dismiss());
        row.addView(close);
        body.addView(row);
    }

    protected void sectionLabel(String text) {
        TextView label = Ui.text(context, text, 11, p.muted, Typeface.BOLD);
        label.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.topMargin = Ui.dp(context, 18);
        params.bottomMargin = Ui.dp(context, 8);
        body.addView(label, params);
    }

    protected EditText input(String hint, int lines) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setHintTextColor(Theme.blend(p.muted, p.surface, 0.1f));
        input.setTextColor(p.ink);
        input.setTextSize(15);
        input.setBackground(Ui.background(p.surface2, 14, context));
        input.setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12));
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | (lines > 1 ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setMinLines(lines);
        input.setMaxLines(lines > 1 ? 5 : 1);
        input.setGravity(lines > 1 ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL);
        if (lines > 1) input.setSingleLine(false);
        return input;
    }

    protected TextView button(String label, boolean primary, View.OnClickListener listener) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(14);
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0);
        if (primary) {
            view.setTextColor(p.onAccent);
            view.setBackground(Ui.ripple(Ui.background(p.accent, 13, context), Theme.alpha(Color.WHITE, 40)));
        } else {
            view.setTextColor(p.ink);
            view.setBackground(Ui.ripple(
                    Ui.background(Theme.alpha(p.surface2, 160), 13, p.line, 0.8f, context),
                    Theme.alpha(p.ink, 20)));
        }
        view.setLayoutParams(new LinearLayout.LayoutParams(0, Ui.dp(context, 46), 1f));
        view.setOnClickListener(listener);
        Ui.pressable(view);
        return view;
    }

    protected LinearLayout footer() {
        LinearLayout row = Ui.row(context);
        LinearLayout.LayoutParams params = Ui.matchWrap();
        params.topMargin = Ui.dp(context, 20);
        body.addView(row, params);
        return row;
    }

    protected ScrollView scroll(View content, int maxHeightDp) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        int height = Ui.dp(context, maxHeightDp);
        int screen = context.getResources().getDisplayMetrics().heightPixels;
        height = Math.min(height, (int) (screen * 0.66f));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        return scroll;
    }

    protected void showAnimated() {
        show();
        Window window = getWindow();
        if (window != null) {
            int width = context.getResources().getDisplayMetrics().widthPixels - Ui.dp(context, 36);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        card.setAlpha(0f);
        card.setScaleX(0.96f);
        card.setScaleY(0.96f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190).start();
    }

    protected void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }
}
