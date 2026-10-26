package com.appfactory.tidytv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** The few simple icons the home screen needs, drawn as paths so the APK carries no images. */
final class Glyph extends View {
    static final int WIFI = 0, ETHERNET = 1, OFFLINE = 2, GEAR = 3, HDMI = 4, PLUS = 5;

    private int kind;
    private int level = 4;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    Glyph(Context c, int kind) {
        super(c);
        this.kind = kind;
    }

    void set(int kind, int level) {
        this.kind = kind;
        this.level = level;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        if (s <= 0) return;
        c.save();
        c.translate((getWidth() - s) / 2f, (getHeight() - s) / 2f);
        float stroke = Math.max(2f, s * 0.07f);
        p.setColor(0xFFFFFFFF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (kind) {
            case WIFI:
            case OFFLINE:
                wifi(c, s, kind == OFFLINE ? -1 : level);
                break;
            case ETHERNET:
                ethernet(c, s);
                break;
            case GEAR:
                gear(c, s);
                break;
            case HDMI:
                hdmi(c, s);
                break;
            default:
                plus(c, s);
        }
        c.restore();
    }

    private void wifi(Canvas c, float s, int lvl) {
        float cx = s * 0.5f, cy = s * 0.82f;
        int active = (lvl * 3 + 3) / 4;
        for (int i = 1; i <= 3; i++) {
            float rad = s * 0.22f * i;
            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            p.setAlpha(i <= active ? 255 : 90);
            c.drawArc(r, 225, 90, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(lvl < 0 ? 90 : 255);
        c.drawCircle(cx, cy, s * 0.06f, p);
        if (lvl < 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setAlpha(255);
            c.drawLine(s * 0.15f, s * 0.12f, s * 0.85f, s * 0.92f, p);
        }
        p.setAlpha(255);
    }

    private void ethernet(Canvas c, float s) {
        path.reset();
        path.moveTo(s * 0.18f, s * 0.22f);
        path.lineTo(s * 0.82f, s * 0.22f);
        path.lineTo(s * 0.82f, s * 0.62f);
        path.lineTo(s * 0.68f, s * 0.62f);
        path.lineTo(s * 0.68f, s * 0.78f);
        path.lineTo(s * 0.32f, s * 0.78f);
        path.lineTo(s * 0.32f, s * 0.62f);
        path.lineTo(s * 0.18f, s * 0.62f);
        path.close();
        c.drawPath(path, p);
        for (int i = 0; i < 4; i++) {
            float x = s * (0.34f + i * 0.107f);
            c.drawLine(x, s * 0.22f, x, s * 0.42f, p);
        }
    }

    private void gear(Canvas c, float s) {
        float cx = s / 2f, cy = s / 2f;
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45f, cx, cy);
            r.set(cx - s * 0.08f, cy - s * 0.47f, cx + s * 0.08f, cy - s * 0.28f);
            c.drawRoundRect(r, s * 0.03f, s * 0.03f, p);
            c.restore();
        }
        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addCircle(cx, cy, s * 0.34f, Path.Direction.CW);
        path.addCircle(cx, cy, s * 0.14f, Path.Direction.CW);
        c.drawPath(path, p);
    }

    private void hdmi(Canvas c, float s) {
        path.reset();
        path.moveTo(s * 0.1f, s * 0.3f);
        path.lineTo(s * 0.9f, s * 0.3f);
        path.lineTo(s * 0.9f, s * 0.54f);
        path.lineTo(s * 0.77f, s * 0.72f);
        path.lineTo(s * 0.23f, s * 0.72f);
        path.lineTo(s * 0.1f, s * 0.54f);
        path.close();
        c.drawPath(path, p);
        c.drawLine(s * 0.24f, s * 0.43f, s * 0.76f, s * 0.43f, p);
    }

    private void plus(Canvas c, float s) {
        r.set(s * 0.14f, s * 0.14f, s * 0.86f, s * 0.86f);
        p.setAlpha(170);
        c.drawRoundRect(r, s * 0.18f, s * 0.18f, p);
        p.setAlpha(255);
        c.drawLine(s * 0.5f, s * 0.32f, s * 0.5f, s * 0.68f, p);
        c.drawLine(s * 0.32f, s * 0.5f, s * 0.68f, s * 0.5f, p);
    }
}
