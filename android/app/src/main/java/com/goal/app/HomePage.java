package com.goal.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Home: statistics, filters and plan cards. */
public class HomePage {
    private final MainActivity activity;
    private final Store store;
    private final Theme.Palette p;
    private final FrameLayout root;
    private final TextView activeValue;
    private final TextView doneValue;
    private final TextView percentValue;
    private final TextView countText;
    private final TextView[] filterViews = new TextView[3];
    private final String[] filters = {"all", "active", "done"};
    private final String[] filterLabels = {"全部", "进行中", "已完成"};
    private final LinearLayout list;
    private final List<TaskCard> cards = new ArrayList<>();
    private String filter = "all";

    public HomePage(MainActivity activity) {
        this.activity = activity;
        this.store = activity.store;
        this.p = Theme.get();
        Context context = activity;
        root = new FrameLayout(context);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = Ui.column(context);
        content.setPadding(Ui.dp(context, 20), Ui.dp(context, 22), Ui.dp(context, 20), Ui.dp(context, 108));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout overlineRow = Ui.row(context);
        overlineRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView overline = Ui.label(context, "HOME · 主页", 11, p.accent);
        overline.setLetterSpacing(0.14f);
        overlineRow.addView(overline, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView date = Ui.text(context, dateLabel(), 10.5f, p.muted, Typeface.NORMAL);
        overlineRow.addView(date);
        content.addView(overlineRow);

        TextView greeting = Ui.text(context, greeting(), 30, p.ink, Typeface.BOLD);
        greeting.setLineSpacing(0, 1.05f);
        LinearLayout.LayoutParams greetingParams = Ui.matchWrap();
        greetingParams.topMargin = Ui.dp(context, 12);
        content.addView(greeting, greetingParams);

        TextView subtitle = Ui.text(context, "把目标，做小一点。", 13, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = Ui.matchWrap();
        subtitleParams.topMargin = Ui.dp(context, 6);
        content.addView(subtitle, subtitleParams);

        LinearLayout stats = Ui.row(context);
        LinearLayout.LayoutParams statsParams = Ui.matchWrap();
        statsParams.topMargin = Ui.dp(context, 22);
        content.addView(stats, statsParams);
        activeValue = statCard(stats, "进行中", 0);
        doneValue = statCard(stats, "已完成", 1);
        percentValue = statCard(stats, "完成率", 2);

        LinearLayout filterRow = Ui.row(context);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams filterParams = Ui.matchWrap();
        filterParams.topMargin = Ui.dp(context, 20);
        content.addView(filterRow, filterParams);

        LinearLayout chips = Ui.row(context);
        for (int i = 0; i < filters.length; i++) {
            final String value = filters[i];
            TextView chip = new TextView(context);
            chip.setText(filterLabels[i]);
            chip.setTextSize(12);
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            chip.setOnClickListener(v -> {
                filter = value;
                refresh();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 32));
            if (i > 0) params.leftMargin = Ui.dp(context, 8);
            chips.addView(chip, params);
            filterViews[i] = chip;
        }
        filterRow.addView(chips, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        countText = Ui.text(context, "", 11, p.muted, Typeface.NORMAL);
        filterRow.addView(countText);

        list = Ui.column(context);
        LinearLayout.LayoutParams listParams = Ui.matchWrap();
        listParams.topMargin = Ui.dp(context, 14);
        content.addView(list, listParams);

        FrameLayout fab = new FrameLayout(context);
        fab.setBackground(Ui.ripple(Ui.background(p.accent, 28, context), Theme.alpha(0xFFFFFFFF, 40)));
        fab.setElevation(Ui.dp(context, 9));
        fab.setContentDescription("添加计划");
        GlyphView plus = new GlyphView(context, GlyphView.PLUS);
        plus.color(p.onAccent).size(22);
        FrameLayout.LayoutParams plusParams = new FrameLayout.LayoutParams(
                Ui.dp(context, 24), Ui.dp(context, 24), Gravity.CENTER);
        fab.addView(plus, plusParams);
        fab.setOnClickListener(v -> activity.showTaskEditor(null));
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(
                Ui.dp(context, 56), Ui.dp(context, 56), Gravity.BOTTOM | Gravity.END);
        fabParams.rightMargin = Ui.dp(context, 20);
        fabParams.bottomMargin = Ui.dp(context, 20);
        root.addView(fab, fabParams);

        refresh();
    }

    public View root() {
        return root;
    }

    private TextView statCard(LinearLayout row, String label, int index) {
        Context context = activity;
        LinearLayout card = Ui.column(context);
        card.setBackground(Ui.background(p.surface, 16, p.line, 1f, context));
        card.setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(context, 82), 1f);
        if (index > 0) params.leftMargin = Ui.dp(context, 8);
        row.addView(card, params);

        TextView labelView = Ui.text(context, label, 10.5f, p.muted, Typeface.NORMAL);
        card.addView(labelView);
        TextView value = Ui.text(context, "0", 23, p.ink, Typeface.BOLD);
        LinearLayout.LayoutParams valueParams = Ui.matchWrap();
        valueParams.topMargin = Ui.dp(context, 4);
        card.addView(value, valueParams);
        return value;
    }

    public void refresh() {
        activeValue.setText(String.valueOf(store.activeCount()));
        doneValue.setText(String.valueOf(store.doneCount()));
        percentValue.setText(store.percent() + "%");
        for (int i = 0; i < filterViews.length; i++) {
            boolean selected = filters[i].equals(filter);
            TextView chip = filterViews[i];
            chip.setTextColor(selected ? p.bg : p.muted);
            chip.setBackground(selected
                    ? Ui.ripple(Ui.background(p.ink, 16, activity), Theme.alpha(0xFFFFFFFF, 30))
                    : Ui.ripple(Ui.background(p.surface2, 16, activity), Theme.alpha(p.ink, 20)));
            chip.setPadding(Ui.dp(activity, 14), 0, Ui.dp(activity, 14), 0);
        }

        List<Models.Task> tasks = store.tasks();
        List<Models.Task> visible = new ArrayList<>();
        for (Models.Task task : tasks) {
            if ("active".equals(filter) && task.done) continue;
            if ("done".equals(filter) && !task.done) continue;
            visible.add(task);
        }
        countText.setText("共 " + visible.size() + " 条计划");
        list.removeAllViews();
        cards.clear();
        if (visible.isEmpty()) {
            list.addView(emptyState(tasks.isEmpty()));
        } else {
            for (Models.Task task : visible) {
                TaskCard card = new TaskCard(activity, task, callbacks);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = Ui.dp(activity, 10);
                list.addView(card, params);
                cards.add(card);
            }
        }
    }

    public void tick() {
        for (TaskCard card : cards) card.tick();
    }

    private View emptyState(boolean noTasksAtAll) {
        Context context = activity;
        LinearLayout box = Ui.column(context);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackground(Ui.background(p.surface, 18, p.line, 1f, context));
        box.setPadding(Ui.dp(context, 24), Ui.dp(context, 30), Ui.dp(context, 24), Ui.dp(context, 30));
        GlyphView icon = new GlyphView(context, GlyphView.GRID);
        icon.color(p.muted).size(28);
        box.addView(icon);
        String title = noTasksAtAll ? "还没有计划" : "这个筛选下没有计划";
        TextView headline = Ui.title(context, title, 16, p.ink);
        LinearLayout.LayoutParams headlineParams = Ui.matchWrap();
        headlineParams.topMargin = Ui.dp(context, 12);
        box.addView(headline, headlineParams);
        String detail = noTasksAtAll
                ? "从一个小步骤开始。"
                : "换个筛选，或新建计划。";
        TextView text = Ui.text(context, detail, 12, p.muted, Typeface.NORMAL);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = Ui.matchWrap();
        textParams.topMargin = Ui.dp(context, 6);
        box.addView(text, textParams);
        if (noTasksAtAll) {
            TextView add = Ui.text(context, "添加第一份计划", 12, p.accent, Typeface.BOLD);
            add.setBackground(Ui.ripple(Ui.background(p.accentSoft, 10, context), Theme.alpha(p.ink, 20)));
            add.setPadding(Ui.dp(context, 14), Ui.dp(context, 9), Ui.dp(context, 14), Ui.dp(context, 9));
            add.setOnClickListener(v -> activity.showTaskEditor(null));
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            addParams.topMargin = Ui.dp(context, 16);
            box.addView(add, addParams);
        }
        return box;
    }

    private final TaskCard.Callbacks callbacks = new TaskCard.Callbacks() {
        @Override
        public void onEdit(Models.Task task) {
            activity.showTaskEditor(task);
        }

        @Override
        public void onDelete(Models.Task task) {
            new ConfirmDialog(activity, "删除这份计划？",
                    "「" + task.title + "」将从本机删除，无法撤销。", "删除", true,
                    confirmed -> {
                        if (confirmed) store.deleteTask(task);
                    }).showDialog();
        }

        @Override
        public void onToggleDone(Models.Task task) {
            if (task.done) {
                store.reopen(task);
                return;
            }
            if ("submit".equals(task.mode) && !task.hasProofs()) {
                activity.pickProofs(task);
                return;
            }
            new ConfirmDialog(activity, "标记为已完成？",
                    "「" + task.title + "」会被直接标记为完成，之后可以恢复。",
                    "标记完成", false, confirmed -> {
                if (confirmed) store.complete(task, "一键完成");
            }).showDialog();
        }

        @Override
        public void onComplete(Models.Task task, String via) {
            store.complete(task, via);
        }

        @Override
        public void onSubmit(Models.Task task) {
            activity.pickProofs(task);
        }

        @Override
        public void onViewProofs(Models.Task task) {
            activity.showProofs(task);
        }

        @Override
        public void onStartPause(Models.Task task) {
            if (task.isRunning()) store.pauseTimer(task);
            else store.startTimer(task);
        }

        @Override
        public void onIncrement(Models.Task task, int delta) {
            store.increment(task, delta);
        }
    };

    private static String dateLabel() {
        LocalDate date = LocalDate.now();
        String[] weekdays = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        return (date.getMonthValue()) + "月" + date.getDayOfMonth() + "日 · "
                + weekdays[date.getDayOfWeek().getValue() - 1];
    }

    private static String greeting() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 5) return "夜深了，朋友";
        if (hour < 11) return "早上好，朋友";
        if (hour < 14) return "中午好，朋友";
        if (hour < 18) return "下午好，朋友";
        return "晚上好，朋友";
    }
}
