package com.goal.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Slim rounded progress track. */
public class BarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float percent = 0f;
    private int fill = 0xFFD9653B;
    private int track = 0xFFE7DED2;

    public BarView(Context context) {
        super(context);
    }

    public void set(float percent, int fill, int track) {
        this.percent = Math.max(0f, Math.min(1f, percent));
        this.fill = fill;
        this.track = track;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = getHeight() / 2f;
        rect.set(0, 0, getWidth(), getHeight());
        paint.setColor(track);
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (percent <= 0f) return;
        rect.set(0, 0, Math.max(getHeight(), getWidth() * percent), getHeight());
        paint.setColor(fill);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }
}
