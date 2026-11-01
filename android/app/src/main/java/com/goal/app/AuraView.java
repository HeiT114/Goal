package com.goal.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Very soft coloured glow used behind page headers. */
public class AuraView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int accent = 0xFFD9653B;

    public AuraView(Context context) {
        super(context);
    }

    public void setAccent(int value) {
        accent = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;
        int top = Theme.withAlpha(accent, 0.16f);
        paint.setShader(new RadialGradient(width * 0.88f, height * 0.04f, width * 0.9f,
                new int[]{top, Theme.withAlpha(accent, 0.06f), 0x00FFFFFF}, new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
    }
}
