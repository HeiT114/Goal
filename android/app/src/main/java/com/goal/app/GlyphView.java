package com.goal.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/** Small line icons drawn by hand so the app needs no icon dependency. */
public class GlyphView extends View {
    public static final int HOME = 0;
    public static final int GRID = 1;
    public static final int SLIDERS = 2;
    public static final int PLUS = 3;
    public static final int CHECK = 4;
    public static final int MORE = 5;
    public static final int X = 6;
    public static final int CHEVRON = 7;
    public static final int CLOCK = 8;
    public static final int TRASH = 9;
    public static final int EDIT = 10;
    public static final int ARROW_LEFT = 11;
    public static final int DOWNLOAD = 12;
    public static final int LOCK = 13;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int type = HOME;
    private int color = 0xFF2B2520;

    public GlyphView(Context context) {
        super(context);
        init();
    }

    public GlyphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GlyphView(Context context, int type) {
        super(context);
        init();
        this.type = type;
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Ui.dp(getContext(), 1.9f));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public GlyphView type(int value) {
        this.type = value;
        invalidate();
        return this;
    }

    public GlyphView color(int value) {
        this.color = value;
        invalidate();
        return this;
    }

    public GlyphView size(int sizeDp) {
        int px = Ui.dp(getContext(), sizeDp);
        setLayoutParams(new android.widget.LinearLayout.LayoutParams(px, px));
        return this;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int fallback = Ui.dp(getContext(), 24);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (width <= 0) width = fallback;
        if (height <= 0) height = fallback;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float scale = Math.min(getWidth(), getHeight()) / 24f;
        canvas.save();
        canvas.scale(scale, scale);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.9f);
        path.reset();

        switch (type) {
            case GRID:
                drawGrid(canvas);
                break;
            case SLIDERS:
                drawSliders(canvas);
                break;
            case PLUS:
                canvas.drawLine(12, 4, 12, 20, paint);
                canvas.drawLine(4, 12, 20, 12, paint);
                break;
            case CHECK:
                path.moveTo(4.5f, 12.5f);
                path.lineTo(9.5f, 17.5f);
                path.lineTo(19.5f, 6.5f);
                canvas.drawPath(path, paint);
                break;
            case MORE:
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(5, 12, 1.7f, paint);
                canvas.drawCircle(12, 12, 1.7f, paint);
                canvas.drawCircle(19, 12, 1.7f, paint);
                break;
            case X:
                canvas.drawLine(6, 6, 18, 18, paint);
                canvas.drawLine(18, 6, 6, 18, paint);
                break;
            case CHEVRON:
                path.moveTo(9, 4.5f);
                path.lineTo(16.5f, 12);
                path.lineTo(9, 19.5f);
                canvas.drawPath(path, paint);
                break;
            case CLOCK:
                canvas.drawCircle(12, 12, 8.2f, paint);
                canvas.drawLine(12, 7.2f, 12, 12.3f, paint);
                canvas.drawLine(12, 12.3f, 15.6f, 14.3f, paint);
                break;
            case TRASH:
                canvas.drawLine(4, 6.5f, 20, 6.5f, paint);
                canvas.drawLine(9, 6.5f, 9, 4, paint);
                canvas.drawLine(9, 4, 15, 4, paint);
                canvas.drawLine(15, 4, 15, 6.5f, paint);
                path.moveTo(6.2f, 6.5f);
                path.lineTo(7.2f, 20);
                path.lineTo(16.8f, 20);
                path.lineTo(17.8f, 6.5f);
                canvas.drawPath(path, paint);
                canvas.drawLine(10.4f, 10, 10.4f, 16.5f, paint);
                canvas.drawLine(13.6f, 10, 13.6f, 16.5f, paint);
                break;
            case EDIT:
                path.moveTo(4, 20);
                path.lineTo(4.9f, 15.4f);
                path.lineTo(15.8f, 4.5f);
                path.lineTo(19.5f, 8.2f);
                path.lineTo(8.6f, 19.1f);
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawLine(13.5f, 6.8f, 17.2f, 10.5f, paint);
                break;
            case ARROW_LEFT:
                path.moveTo(15, 4.5f);
                path.lineTo(7.5f, 12);
                path.lineTo(15, 19.5f);
                canvas.drawPath(path, paint);
                break;
            case DOWNLOAD:
                canvas.drawLine(12, 4, 12, 15.5f, paint);
                path.moveTo(7, 10.8f);
                path.lineTo(12, 15.8f);
                path.lineTo(17, 10.8f);
                canvas.drawPath(path, paint);
                canvas.drawLine(4.5f, 20, 19.5f, 20, paint);
                break;
            case LOCK:
                canvas.drawRoundRect(5, 10.5f, 19, 20, 2.4f, 2.4f, paint);
                path.moveTo(8, 10.5f);
                path.lineTo(8, 7.8f);
                path.quadTo(8, 4, 12, 4);
                path.quadTo(16, 4, 16, 7.8f);
                path.lineTo(16, 10.5f);
                canvas.drawPath(path, paint);
                break;
            case HOME:
            default:
                path.moveTo(3.5f, 10.5f);
                path.lineTo(12, 3.5f);
                path.lineTo(20.5f, 10.5f);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(5.5f, 9.2f);
                path.lineTo(5.5f, 20);
                path.lineTo(18.5f, 20);
                path.lineTo(18.5f, 9.2f);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(9.6f, 20);
                path.lineTo(9.6f, 14.2f);
                path.lineTo(14.4f, 14.2f);
                path.lineTo(14.4f, 20);
                canvas.drawPath(path, paint);
                break;
        }
        canvas.restore();
    }

    private void drawGrid(Canvas canvas) {
        float radius = 2.2f;
        canvas.drawRoundRect(3.5f, 3.5f, 10.5f, 10.5f, radius, radius, paint);
        canvas.drawRoundRect(13.5f, 3.5f, 20.5f, 10.5f, radius, radius, paint);
        canvas.drawRoundRect(3.5f, 13.5f, 10.5f, 20.5f, radius, radius, paint);
        canvas.drawRoundRect(13.5f, 13.5f, 20.5f, 20.5f, radius, radius, paint);
    }

    private void drawSliders(Canvas canvas) {
        canvas.drawLine(3, 7, 21, 7, paint);
        canvas.drawLine(3, 12, 21, 12, paint);
        canvas.drawLine(3, 17, 21, 17, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(8, 7, 2.2f, paint);
        canvas.drawCircle(16, 12, 2.2f, paint);
        canvas.drawCircle(10, 17, 2.2f, paint);
    }
}
