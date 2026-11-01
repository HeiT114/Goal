package com.goal.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Create or edit a plan, with the three completion methods. */
public class TaskEditorDialog extends BaseCardDialog {
    public interface Listener {
        void onSaved(String title, String note, String mode, int target, String unit, String dueDate);
    }

    private final Models.Task editing;
    private final Listener listener;
    private final EditText titleInput;
    private final EditText noteInput;
    private final EditText dueInput;
    private final EditText durationInput;
    private final EditText variationTargetInput;
    private final EditText unitInput;
    private final TextView errorText;
    private final LinearLayout durationGroup;
    private final LinearLayout variationGroup;
    private final TextView modeHint;
    private final TextView[] modeButtons = new TextView[3];
    private final String[] modes = {"submit", "duration", "variation"};
    private String mode;

    public TaskEditorDialog(Context context, Models.Task task, Listener listener) {
        super(context);
        this.editing = task;
        this.listener = listener;
        this.mode = task == null ? "submit" : task.mode;
        header(task == null ? "新建计划" : "编辑计划", "一次只做一件事。");

        sectionLabel("计划名称");
        titleInput = input("例如：读完 10 页", 1);
        titleInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        if (task != null) titleInput.setText(task.title);
        body.addView(titleInput, Ui.matchWrap());

        sectionLabel("备注（可选）");
        noteInput = input("一句话，为什么做它。", 3);
        noteInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        if (task != null) noteInput.setText(task.note);
        body.addView(noteInput, Ui.matchWrap());

        sectionLabel("完成方式");
        LinearLayout modeRow = Ui.row(context);
        String[] labels = {"提交", "时长", "变化"};
        for (int i = 0; i < 3; i++) {
            final String value = modes[i];
            TextView chip = new TextView(context);
            chip.setText(labels[i]);
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            chip.setOnClickListener(v -> {
                mode = value;
                renderMode();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(context, 42), 1f);
            if (i > 0) params.leftMargin = Ui.dp(context, 8);
            modeRow.addView(chip, params);
            modeButtons[i] = chip;
        }
        body.addView(modeRow);

        modeHint = Ui.text(context, "", 12, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = Ui.matchWrap();
        hintParams.topMargin = Ui.dp(context, 8);
        body.addView(modeHint, hintParams);

        durationGroup = Ui.column(context);
        LinearLayout durationRow = Ui.row(context);
        durationInput = input("25", 1);
        durationInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        durationInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        if (task != null && "duration".equals(task.mode))
            durationInput.setText(String.valueOf(Math.max(1, Math.round(task.target / 60f))));
        durationRow.addView(durationInput, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView unit = Ui.text(context, "分钟", 13, p.muted, Typeface.NORMAL);
        LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        unitParams.leftMargin = Ui.dp(context, 10);
        durationRow.addView(unit, unitParams);
        durationGroup.addView(durationRow);
        LinearLayout quick = Ui.row(context);
        String[] quickValues = {"15", "25", "45", "60"};
        for (int i = 0; i < quickValues.length; i++) {
            TextView chip = quickChip(quickValues[i]);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(context, 36), 1f);
            if (i > 0) params.leftMargin = Ui.dp(context, 6);
            quick.addView(chip, params);
        }
        LinearLayout.LayoutParams quickParams = Ui.matchWrap();
        quickParams.topMargin = Ui.dp(context, 8);
        durationGroup.addView(quick, quickParams);
        LinearLayout.LayoutParams durationParams = Ui.matchWrap();
        durationParams.topMargin = Ui.dp(context, 10);
        body.addView(durationGroup, durationParams);

        variationGroup = Ui.column(context);
        LinearLayout variationRow = Ui.row(context);
        variationTargetInput = input("10", 1);
        variationTargetInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        variationTargetInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        if (task != null && "variation".equals(task.mode))
            variationTargetInput.setText(String.valueOf(task.target));
        variationRow.addView(variationTargetInput, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        unitInput = input("页", 1);
        unitInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        if (task != null && task.unit != null && !"次".equals(task.unit)) unitInput.setText(task.unit);
        LinearLayout.LayoutParams variationUnitParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        variationUnitParams.leftMargin = Ui.dp(context, 8);
        variationRow.addView(unitInput, variationUnitParams);
        variationGroup.addView(variationRow);
        LinearLayout.LayoutParams variationParams = Ui.matchWrap();
        variationParams.topMargin = Ui.dp(context, 10);
        body.addView(variationGroup, variationParams);

        sectionLabel("截止日期（可选）");
        dueInput = input("例如 2026-11-05，留空不限", 1);
        if (task != null && task.dueDate != null) dueInput.setText(task.dueDate);
        body.addView(dueInput, Ui.matchWrap());

        errorText = Ui.text(context, "", 12, p.danger, Typeface.NORMAL);
        LinearLayout.LayoutParams errorParams = Ui.matchWrap();
        errorParams.topMargin = Ui.dp(context, 8);
        errorText.setVisibility(View.GONE);
        body.addView(errorText, errorParams);

        LinearLayout footer = footer();
        TextView cancel = button("取消", false, v -> dismiss());
        TextView save = button(task == null ? "创建计划" : "保存", true, v -> submit());
        ((LinearLayout.LayoutParams) cancel.getLayoutParams()).rightMargin = Ui.dp(context, 10);
        footer.addView(cancel);
        footer.addView(save);

        renderMode();
    }

    private TextView quickChip(String value) {
        TextView chip = new TextView(context);
        chip.setText(value + " 分钟");
        chip.setTextSize(11);
        chip.setGravity(Gravity.CENTER);
        chip.setTextColor(p.muted);
        chip.setBackground(Ui.ripple(Ui.background(p.surface2, 10, context), Theme.alpha(p.ink, 16)));
        chip.setOnClickListener(v -> durationInput.setText(value));
        Ui.pressable(chip);
        return chip;
    }

    private void renderMode() {
        String[] labels = {"提交", "时长", "变化"};
        for (int i = 0; i < 3; i++) {
            boolean selected = modes[i].equals(mode);
            modeButtons[i].setText(labels[i]);
            modeButtons[i].setTextColor(selected ? p.accent : p.muted);
            modeButtons[i].setBackground(selected
                    ? Ui.background(p.accentUltra, 11, p.accent, 1.2f, context)
                    : Ui.background(p.surface2, 11, context));
        }
        if ("duration".equals(mode)) {
            modeHint.setText("达到设定时长后自动完成。");
            durationGroup.setVisibility(View.VISIBLE);
            variationGroup.setVisibility(View.GONE);
            if (durationInput.getText().toString().isEmpty()) durationInput.setText("25");
        } else if ("variation".equals(mode)) {
            modeHint.setText("达到目标数值后自动完成。");
            durationGroup.setVisibility(View.GONE);
            variationGroup.setVisibility(View.VISIBLE);
            if (variationTargetInput.getText().toString().isEmpty()) variationTargetInput.setText("10");
        } else {
            modeHint.setText("提交文件、视频、音频或图片后完成。");
            durationGroup.setVisibility(View.GONE);
            variationGroup.setVisibility(View.GONE);
        }
    }

    private void submit() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            showError("给这份计划起一个名字。");
            titleInput.requestFocus();
            return;
        }
        int target = 0;
        String unit = "次";
        if ("duration".equals(mode)) {
            int minutes;
            try {
                minutes = Integer.parseInt(durationInput.getText().toString().trim());
            } catch (NumberFormatException error) {
                showError("时长需要填写 1–1440 分钟。");
                return;
            }
            if (minutes < 1 || minutes > 1440) {
                showError("时长需要填写 1–1440 分钟。");
                return;
            }
            target = minutes * 60;
            unit = "分钟";
        } else if ("variation".equals(mode)) {
            try {
                target = Integer.parseInt(variationTargetInput.getText().toString().trim());
            } catch (NumberFormatException error) {
                showError("变化目标需要填写 1–100000 的整数。");
                return;
            }
            if (target < 1 || target > 100000) {
                showError("变化目标需要填写 1–100000 的整数。");
                return;
            }
            String value = unitInput.getText().toString().trim();
            unit = value.isEmpty() ? "次" : value;
        }
        String due;
        try {
            due = normalizeDue(dueInput.getText().toString());
        } catch (IllegalArgumentException error) {
            showError(error.getMessage());
            return;
        }
        hideKeyboard();
        listener.onSaved(title, noteInput.getText().toString().trim(), mode, target, unit, due);
        dismiss();
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private static String normalizeDue(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return null;
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).toString();
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDate date = LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy/M/d"));
            return date.toString();
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDate date = LocalDate.parse(value, DateTimeFormatter.ofPattern("M-d"));
            LocalDate today = LocalDate.now();
            int year = today.getYear();
            if (date.getMonthValue() < today.getMonthValue() - 1) year++;
            return LocalDate.of(year, date.getMonthValue(), date.getDayOfMonth()).toString();
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException("截止日期格式应为 2026-11-05、2026/11/05 或 11-05。");
    }

    public void showDialog() {
        showAnimated();
    }
}
