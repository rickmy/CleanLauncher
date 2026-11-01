package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.view.View;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * One still view of a place, drawn from OpenStreetMap tiles. No route, no trail,
 * and nothing is written to disk.
 */
final class PlaceMap extends View {

    interface Listener {
        void onTiles(boolean any);
    }

    static final int ZOOM = 15;
    private static final int TILE = 256;
    private static final String AGENT = "CleanLauncher/6.3 (com.simple.carlauncher; personal head unit)";

    private final Handler handler = new Handler();
    private final Map<String, Bitmap> tiles = new HashMap<>();
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double lat, lon;
    private int generation;
    private String loaded;
    private Listener listener;

    PlaceMap(Context c) {
        super(c);
        dot.setColor(0xFFFFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(c, 3));
        ring.setColor(0xFF101012);
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** @return true when the visible tiles changed and a new fetch started. */
    boolean setCenter(double latitude, double longitude) {
        lat = Math.max(-85, Math.min(85, latitude));
        lon = longitude;
        invalidate();
        if (getWidth() > 0 && getHeight() > 0) return loadVisible();
        return false;
    }

    void stop() {
        generation++;
        handler.removeCallbacksAndMessages(null);
        for (Bitmap b : tiles.values()) {
            if (b != null && !b.isRecycled()) b.recycle();
        }
        tiles.clear();
        loaded = null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w > 0 && h > 0) loadVisible();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        double left = tileX(lon) * TILE - w / 2.0;
        double top = tileY(lat) * TILE - h / 2.0;
        int n = 1 << ZOOM;
        int x0 = (int) Math.floor(left / TILE);
        int y0 = (int) Math.floor(top / TILE);
        int x1 = (int) Math.floor((left + w) / TILE);
        int y1 = (int) Math.floor((top + h) / TILE);
        for (int x = x0; x <= x1; x++) {
            int wrapped = Math.floorMod(x, n);
            for (int y = y0; y <= y1; y++) {
                if (y < 0 || y >= n) continue;
                Bitmap b = tiles.get(wrapped + "/" + y);
                if (b == null || b.isRecycled()) continue;
                canvas.drawBitmap(b, (float) (x * TILE - left), (float) (y * TILE - top), null);
            }
        }
        float r = Ui.dp(getContext(), 8);
        canvas.drawCircle(w / 2f, h / 2f, r + ring.getStrokeWidth(), ring);
        canvas.drawCircle(w / 2f, h / 2f, r, dot);
    }

    private boolean loadVisible() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return false;
        double left = tileX(lon) * TILE - w / 2.0;
        double top = tileY(lat) * TILE - h / 2.0;
        int n = 1 << ZOOM;
        int x0 = (int) Math.floor(left / TILE);
        int y0 = (int) Math.floor(top / TILE);
        int x1 = (int) Math.floor((left + w) / TILE);
        int y1 = (int) Math.floor((top + h) / TILE);
        String sig = x0 + ":" + y0 + ":" + x1 + ":" + y1;
        if (sig.equals(loaded)) {
            invalidate();
            return false;
        }
        loaded = sig;
        int gen = ++generation;
        new Thread(() -> {
            boolean any = false;
            for (int x = x0; x <= x1; x++) {
                int wrapped = Math.floorMod(x, n);
                for (int y = y0; y <= y1; y++) {
                    if (gen != generation) return;
                    if (y < 0 || y >= n) continue;
                    String key = wrapped + "/" + y;
                    if (tiles.containsKey(key)) {
                        any = true;
                        continue;
                    }
                    Bitmap b = fetch(wrapped, y);
                    if (b == null) continue;
                    any = true;
                    boolean got = any;
                    handler.post(() -> {
                        if (gen != generation) {
                            b.recycle();
                            return;
                        }
                        tiles.put(key, b);
                        invalidate();
                        if (listener != null) listener.onTiles(got);
                    });
                }
            }
            boolean got = any;
            handler.post(() -> {
                if (gen == generation && listener != null) listener.onTiles(got);
            });
        }, "place-map").start();
        return true;
    }

    private static Bitmap fetch(int x, int y) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://tile.openstreetmap.org/" + ZOOM + "/" + x + "/" + y + ".png");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", AGENT);
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static double tileX(double longitude) {
        return (longitude + 180.0) / 360.0 * (1 << ZOOM);
    }

    private static double tileY(double latitude) {
        double rad = Math.toRadians(latitude);
        return (1.0 - Math.log(Math.tan(rad) + 1.0 / Math.cos(rad)) / Math.PI) / 2.0 * (1 << ZOOM);
    }
}
