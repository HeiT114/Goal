package com.goal.app;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** A single plan card with its completion controls. */
public class TaskCard extends LinearLayout {
    public interface Callbacks {
        void onEdit(Models.Task task);
        void onDelete(Models.Task task);
        void onToggleDone(Models.Task task);
        void onComplete(Models.Task task, String via);
        void onSubmit(Models.Task task);
        void onViewProofs(Models.Task task);
        void onStartPause(Models.Task task);
        void onIncrement(Models.Task task, int delta);
    }

    private final Models.Task task;
    private final Callbacks callbacks;
    private final Theme.Palette p;
    private TextView progressText;
    private BarView bar;
    private TextView startLabel;
    private TextView variationValue;
    private RingView ring;

    public TaskCard(Context context, Models.Task task, Callbacks callbacks) {
        super(context);
        this.task = task;
        this.callbacks = callbacks;
        this.p = Theme.get();
        build();
    }

    public Models.Task task() {
        return task;
    }

    private void build() {
        setOrientation(HORIZONTAL);
        setBackground(Ui.background(p.surface, 18, p.line, 1f, getContext()));
        setPadding(Ui.dp(getContext(), 15), Ui.dp(getContext(), 16), Ui.dp(getContext(), 14), Ui.dp(getContext(), 14));
        setAlpha(task.done ? 0.66f : 1f);

        ring = new RingView(getContext());
        ring.set(task.percent(), task.done, p.accent, p.line);
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(Ui.dp(getContext(), 28), Ui.dp(getContext(), 28));
        ringParams.topMargin = Ui.dp(getContext(), 2);
        addView(ring, ringParams);
        ring.setOnClickListener(v -> callbacks.onToggleDone(task));

        LinearLayout column = Ui.column(getContext());
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.leftMargin = Ui.dp(getContext(), 12);
        addView(column, columnParams);

        LinearLayout titleRow = Ui.row(getContext());
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(getContext(), task.title, 16, p.ink, Typeface.BOLD);
        title.setMaxLines(2);
        title.setLineSpacing(0, 1.15f);
        if (task.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        titleRow.addView(title, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tag = Ui.text(getContext(), task.modeLabel(), 10, p.accent, Typeface.BOLD);
        tag.setBackground(Ui.background(p.accentUltra, 6, getContext()));
        tag.setPadding(Ui.dp(getContext(), 6), Ui.dp(getContext(), 2), Ui.dp(getContext(), 6), Ui.dp(getContext(), 2));
        LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagParams.leftMargin = Ui.dp(getContext(), 6);
        titleRow.addView(tag, tagParams);

        column.addView(titleRow);

        String note = task.note == null || task.note.trim().isEmpty()
                ? defaultNote() : task.note;
        TextView noteView = Ui.text(getContext(), note, 12.5f, p.muted, Typeface.NORMAL);
        noteView.setMaxLines(3);
        noteView.setLineSpacing(Ui.dp(getContext(), 1), 1.2f);
        LinearLayout.LayoutParams noteParams = Ui.matchWrap();
        noteParams.topMargin = Ui.dp(getContext(), 4);
        column.addView(noteView, noteParams);

        String due = dueLabel();
        if (due != null) {
            TextView dueView = Ui.text(getContext(), due, 11.5f, dueColor(), Typeface.NORMAL);
            LinearLayout.LayoutParams dueParams = Ui.matchWrap();
            dueParams.topMargin = Ui.dp(getContext(), 6);
            column.addView(dueView, dueParams);
        }

        if (task.done) {
            buildDone(column);
        } else if ("duration".equals(task.mode)) {
            buildDuration(column);
        } else if ("variation".equals(task.mode)) {
            buildVariation(column);
        } else {
            buildSubmit(column);
        }
        enableLongPress(this);
    }

    private void enableLongPress(View view) {
        if (!view.isClickable()) {
            view.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                showMenu(v);
                return true;
            });
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) enableLongPress(group.getChildAt(i));
        }
    }

    private String defaultNote() {
        if ("duration".equals(task.mode)) return "25 分钟，从一件事开始。";
        if ("variation".equals(task.mode)) return "数值达到目标后自动完成。";
        return "做到后提交材料，才算完成。";
    }

    private String dueLabel() {
        if (task.dueDate == null || task.dueDate.isEmpty()) return null;
        if (task.done) return "已完成";
        try {
            LocalDate due = LocalDate.parse(task.dueDate);
            LocalDate today = LocalDate.now();
            if (due.isBefore(today)) return "已逾期 · " + task.dueDate;
            if (due.isEqual(today)) return "今天到期";
            if (due.isEqual(today.plusDays(1))) return "明天到期";
            return "截止 " + task.dueDate;
        } catch (DateTimeParseException error) {
            return task.dueDate;
        }
    }

    private int dueColor() {
        if (task.dueDate == null || task.done) return p.muted;
        try {
            LocalDate due = LocalDate.parse(task.dueDate);
            return due.isBefore(LocalDate.now()) ? p.danger : p.muted;
        } catch (DateTimeParseException error) {
            return p.muted;
        }
    }

    private void buildSubmit(LinearLayout column) {
        LinearLayout actions = Ui.row(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = Ui.matchWrap();
        actionsParams.topMargin = Ui.dp(getContext(), 10);

        TextView proof = Ui.text(getContext(), task.hasProofs()
                ? "材料 · " + task.proofs.size() + " 份 · 点击查看"
                : "需要文件、视频、音频或图片", 11, task.hasProofs() ? p.accent : p.muted, Typeface.NORMAL);
        proof.setPadding(0, Ui.dp(getContext(), 2), 0, Ui.dp(getContext(), 2));
        proof.setOnClickListener(v -> callbacks.onViewProofs(task));
        LinearLayout.LayoutParams proofParams = Ui.matchWrap();
        proofParams.topMargin = Ui.dp(getContext(), 8);
        column.addView(proof, proofParams);
        column.addView(actions, actionsParams);

        TextView submit = actionButton("提交完成", true);
        submit.setOnClickListener(v -> {
            if (task.hasProofs()) callbacks.onComplete(task, "提交");
            else callbacks.onSubmit(task);
        });
        actions.addView(submit);

        if (task.hasProofs()) {
            TextView addMore = Ui.text(getContext(), "添加材料", 12, p.muted, Typeface.NORMAL);
            addMore.setPadding(Ui.dp(getContext(), 12), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8));
            addMore.setOnClickListener(v -> callbacks.onSubmit(task));
            Ui.pressable(addMore);
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            addParams.leftMargin = Ui.dp(getContext(), 4);
            actions.addView(addMore, addParams);
        }

        TextView quick = Ui.text(getContext(), "一键完成", 12, p.muted, Typeface.NORMAL);
        quick.setPadding(Ui.dp(getContext(), 12), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8));
        quick.setOnClickListener(v -> callbacks.onComplete(task, "一键完成"));
        Ui.pressable(quick);
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        quickParams.leftMargin = Ui.dp(getContext(), 4);
        actions.addView(quick, quickParams);
    }

    private void buildDuration(LinearLayout column) {
        LinearLayout labels = Ui.row(getContext());
        labels.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelsParams = Ui.matchWrap();
        labelsParams.topMargin = Ui.dp(getContext(), 10);
        column.addView(labels, labelsParams);

        progressText = Ui.text(getContext(), progressLabelText(), 11.5f, p.muted, Typeface.NORMAL);
        labels.addView(progressText, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView target = Ui.text(getContext(), "目标 " + DateTimes.clock(task.target), 11.5f, p.muted, Typeface.NORMAL);
        labels.addView(target);

        bar = new BarView(getContext());
        bar.set(task.percent(), p.accent, p.line);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 5));
        barParams.topMargin = Ui.dp(getContext(), 8);
        column.addView(bar, barParams);

        LinearLayout actions = Ui.row(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = Ui.matchWrap();
        actionsParams.topMargin = Ui.dp(getContext(), 10);
        column.addView(actions, actionsParams);

        TextView start = actionButton(task.isRunning() ? "暂停" : "开始", true);
        startLabel = start;
        start.setOnClickListener(v -> callbacks.onStartPause(task));
        actions.addView(start);

        TextView quick = Ui.text(getContext(), "一键完成", 12, p.muted, Typeface.NORMAL);
        quick.setPadding(Ui.dp(getContext(), 12), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8));
        quick.setOnClickListener(v -> callbacks.onComplete(task, "一键完成"));
        Ui.pressable(quick);
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        quickParams.leftMargin = Ui.dp(getContext(), 4);
        actions.addView(quick, quickParams);
    }

    private String progressLabelText() {
        int progress = Math.min(task.effectiveProgress(), task.target);
        return DateTimes.clock(progress) + " / " + DateTimes.clock(task.target);
    }

    private void buildVariation(LinearLayout column) {
        LinearLayout labels = Ui.row(getContext());
        labels.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelsParams = Ui.matchWrap();
        labelsParams.topMargin = Ui.dp(getContext(), 10);
        column.addView(labels, labelsParams);

        progressText = Ui.text(getContext(), task.effectiveProgress() + " / " + task.target + " " + task.unit, 11.5f, p.muted, Typeface.NORMAL);
        labels.addView(progressText, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        bar = new BarView(getContext());
        bar.set(task.percent(), p.accent, p.line);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 5));
        barParams.topMargin = Ui.dp(getContext(), 8);
        column.addView(bar, barParams);

        LinearLayout actions = Ui.row(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = Ui.matchWrap();
        actionsParams.topMargin = Ui.dp(getContext(), 8);
        column.addView(actions, actionsParams);

        TextView minus = stepButton("−");
        minus.setOnClickListener(v -> callbacks.onIncrement(task, -1));
        actions.addView(minus);
        variationValue = Ui.text(getContext(), String.valueOf(task.effectiveProgress()), 13, p.ink, Typeface.BOLD);
        variationValue.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(Ui.dp(getContext(), 40), Ui.dp(getContext(), 32));
        actions.addView(variationValue, valueParams);
        TextView plus = stepButton("+");
        plus.setOnClickListener(v -> callbacks.onIncrement(task, 1));
        actions.addView(plus);

        TextView quick = Ui.text(getContext(), "一键完成", 12, p.muted, Typeface.NORMAL);
        quick.setPadding(Ui.dp(getContext(), 12), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8));
        quick.setOnClickListener(v -> callbacks.onComplete(task, "一键完成"));
        Ui.pressable(quick);
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        quickParams.leftMargin = Ui.dp(getContext(), 4);
        actions.addView(quick, quickParams);
    }

    private void buildDone(LinearLayout column) {
        LinearLayout actions = Ui.row(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = Ui.matchWrap();
        actionsParams.topMargin = Ui.dp(getContext(), 10);
        column.addView(actions, actionsParams);
        TextView done = Ui.text(getContext(), "已完成", 12, p.accent, Typeface.BOLD);
        done.setBackground(Ui.background(p.accentUltra, 8, getContext()));
        done.setPadding(Ui.dp(getContext(), 10), Ui.dp(getContext(), 5), Ui.dp(getContext(), 10), Ui.dp(getContext(), 5));
        actions.addView(done);
        TextView reopen = Ui.text(getContext(), "恢复", 12, p.muted, Typeface.NORMAL);
        reopen.setPadding(Ui.dp(getContext(), 12), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8));
        reopen.setOnClickListener(v -> callbacks.onToggleDone(task));
        Ui.pressable(reopen);
        LinearLayout.LayoutParams reopenParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        reopenParams.leftMargin = Ui.dp(getContext(), 4);
        actions.addView(reopen, reopenParams);
    }

    private TextView actionButton(String label, boolean primary) {
        TextView button = Ui.text(getContext(), label, 12, primary ? p.accent : p.muted, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(Ui.dp(getContext(), 12), Ui.dp(getContext(), 7), Ui.dp(getContext(), 12), Ui.dp(getContext(), 7));
        button.setBackground(Ui.ripple(Ui.background(primary ? p.accentSoft : p.surface2, 9, getContext()), Theme.alpha(p.ink, 20)));
        Ui.pressable(button);
        return button;
    }

    private TextView stepButton(String label) {
        TextView button = Ui.text(getContext(), label, 16, p.muted, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.ripple(Ui.background(p.surface2, 9, getContext()), Theme.alpha(p.ink, 20)));
        button.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(getContext(), 32), Ui.dp(getContext(), 32)));
        Ui.pressable(button);
        return button;
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        menu.getMenu().add("编辑计划");
        if (!task.done) menu.getMenu().add("标记为完成");
        else menu.getMenu().add("恢复为进行中");
        menu.getMenu().add("删除计划");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("编辑计划".equals(title)) callbacks.onEdit(task);
            else if ("删除计划".equals(title)) callbacks.onDelete(task);
            else callbacks.onToggleDone(task);
            return true;
        });
        menu.show();
    }

    /** Called once per second by the host page while a timer is running. */
    public void tick() {
        if (task.done) return;
        if ("duration".equals(task.mode) && progressText != null) {
            progressText.setText(progressLabelText());
            if (bar != null) bar.set(task.percent(), p.accent, p.line);
            if (startLabel != null) startLabel.setText(task.isRunning() ? "暂停" : "开始");
        }
        if ("variation".equals(task.mode) && progressText != null) {
            progressText.setText(task.effectiveProgress() + " / " + task.target + " " + task.unit);
            if (bar != null) bar.set(task.percent(), p.accent, p.line);
            if (variationValue != null) variationValue.setText(String.valueOf(task.effectiveProgress()));
        }
    }
}
