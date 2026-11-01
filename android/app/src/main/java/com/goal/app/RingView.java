package com.goal.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Circular progress ring with a check mark when the plan is complete. */
public class RingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int progress = 0;
    private boolean done = false;
    private int accent = 0xFFD9653B;
    private int muted = 0xFFE8DCCC;

    public RingView(Context context) {
        super(context);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(Ui.dp(context, 3));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(Ui.dp(context, 3));
        arc.setStrokeCap(Paint.Cap.ROUND);
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeWidth(Ui.dp(context, 2.4f));
        mark.setStrokeCap(Paint.Cap.ROUND);
        mark.setStrokeJoin(Paint.Join.ROUND);
    }

    public void set(int progress, boolean done, int accent, int muted) {
        this.progress = Math.max(0, Math.min(100, progress));
        this.done = done;
        this.accent = accent;
        this.muted = muted;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Ui.dp(getContext(), 30);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float inset = Ui.dp(getContext(), 3);
        RectF box = new RectF(inset, inset, size - inset, size - inset);
        track.setColor(done ? accent : muted);
        track.setAlpha(done ? 255 : 255);
        canvas.drawCircle(size / 2f, size / 2f, (size - inset * 2) / 2f, track);
        if (!done && progress > 0) {
            arc.setColor(accent);
            canvas.drawArc(box, -90, 360f * progress / 100f, false, arc);
        }
        if (done) {
            track.setColor(accent);
            track.setStyle(Paint.Style.FILL);
            canvas.drawCircle(size / 2f, size / 2f, (size - inset * 2) / 2f, track);
            mark.setColor(0xFFFFF9F2);
            float center = size / 2f;
            canvas.drawLine(center - size * 0.18f, center, center - size * 0.04f, center + size * 0.14f, mark);
            canvas.drawLine(center - size * 0.04f, center + size * 0.14f, center + size * 0.2f, center - size * 0.13f, mark);
        }
    }
}
