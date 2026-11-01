package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Thin GPS speed scale. Marks are 0, 60, 100 and 160 km/h. The number is not capped. */
final class SpeedScale extends View {

    private int kmh;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);

    SpeedScale(Context c) {
        super(c);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        label.setTextAlign(Paint.Align.CENTER);
    }

    void setKmh(int v) {
        kmh = Math.max(0, v);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean light = Ui.lightSurface(getContext());
        float d = getResources().getDisplayMetrics().density;
        track.setStrokeWidth(3f * d);
        track.setColor(light ? 0x33000000 : 0x66FFFFFF);
        tick.setStrokeWidth(2f * d);
        tick.setColor(light ? 0xFF121418 : 0xFFFFFFFF);
        thumb.setColor(0xFF2F6BFF);
        label.setColor(light ? 0xFF2A3038 : 0xB3FFFFFF);
        label.setTextSize(12f * d);

        float left = 10f * d;
        float right = getWidth() - 10f * d;
        if (right <= left) return;
        float y = getHeight() * 0.36f;
        canvas.drawLine(left, y, right, y, track);

        int[] marks = {0, 60, 100, 160};
        for (int mark : marks) {
            float x = left + (right - left) * (mark / 160f);
            canvas.drawLine(x, y - 5f * d, x, y + 5f * d, tick);
            canvas.drawText(String.valueOf(mark), x, y + 18f * d, label);
        }
        float x = left + (right - left) * (Math.min(kmh, 160) / 160f);
        canvas.drawCircle(x, y, 6f * d, thumb);
    }
}
