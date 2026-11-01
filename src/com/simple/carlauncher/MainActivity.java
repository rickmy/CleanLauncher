package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * Home for the Almera: clock and car on the left, cards on the right.
 * Light from 06:00 through 18:59, dark from 19:00 through 05:59.
 */
public class MainActivity extends Activity implements NowPlaying.Listener {

    private final Handler handler = new Handler();
    private NowPlaying nowPlaying;

    private boolean createdDark;
    private boolean createdZlink;
    private ImageView playBtn;
    private TextView songTitle, songArtist, speedValue;
    private SpeedScale speedScale;
    private LinearLayout tiles;
    private LocationManager locations;
    private boolean listening;

    private final LocationListener speedFix = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            showSpeed(location);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.background(this));
        Ui.padBelowSystemBar(root);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.CENTER_VERTICAL);
        columns.addView(buildLeft(), new LinearLayout.LayoutParams(0, -1, 1.05f));
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -1, 1f);
        rightLp.leftMargin = dp(12);
        columns.addView(buildRight(), rightLp);
        root.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        root.requestApplyInsets();

        nowPlaying = NowPlaying.get(this);
        createdDark = Ui.isDark(this);
        createdZlink = Ui.findZlink(this) != null;
        if (!Profile.isSetupDone(this)) {
            startActivity(new Intent(this, SetupActivity.class));
        }
        if (Profile.isNwd(this)) {
            startService(new Intent(this, VolumeOverlay.class));
        }
        startService(new Intent(this, GreeterService.class));
        startService(new Intent(this, Odometer.class));
        fillTiles();
    }

    // ---------- Left: clock, date, Almera, GPS speed ----------

    private View buildLeft() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(60);
        time.setTextColor(Ui.ink(this));
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.START);
        col.addView(time);

        TextClock date = new TextClock(this) {
            @Override
            public void setText(CharSequence text, BufferType type) {
                if (text != null) {
                    String up = text.toString().toUpperCase(java.util.Locale.getDefault());
                    if (!up.contentEquals(text)) {
                        super.setText(up, type);
                        return;
                    }
                }
                super.setText(text, type);
            }
        };
        date.setFormat24Hour("EEEE · d MMM");
        date.setFormat12Hour("EEEE · d MMM");
        date.setTextSize(16);
        date.setTextColor(Ui.muted(this));
        date.setSingleLine(true);
        date.setGravity(Gravity.START);
        date.setPadding(0, dp(2), 0, 0);
        col.addView(date);

        ImageView car = new ImageView(this);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap own = Profile.carPhoto(this);
        if (own != null) {
            car.setImageBitmap(own);
        } else {
            int bundled = Profile.bundledCar(this, false);
            if (bundled != 0) car.setImageResource(bundled);
        }
        LinearLayout.LayoutParams carLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        carLp.topMargin = dp(4);
        carLp.bottomMargin = dp(4);
        col.addView(car, carLp);

        col.addView(buildSpeed());
        return col;
    }

    private View buildSpeed() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(dp(88));
        card.setPadding(dp(16), dp(10), dp(12), dp(10));
        card.setBackground(Ui.card(this));

        LinearLayout numbers = new LinearLayout(this);
        numbers.setOrientation(LinearLayout.VERTICAL);
        numbers.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.BOTTOM);
        speedValue = Ui.text(this, "0", 42, Ui.ink(this));
        speedValue.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        speedValue.setIncludeFontPadding(false);
        speedValue.setGravity(Gravity.START | Gravity.BOTTOM);
        line.addView(speedValue);
        TextView unit = Ui.text(this, "km/h", 14, Ui.muted(this));
        unit.setPadding(dp(6), 0, 0, dp(6));
        line.addView(unit);
        numbers.addView(line);

        TextView gps = Ui.text(this, "GPS", 13, Ui.muted(this));
        gps.setGravity(Gravity.START);
        gps.setPadding(0, dp(2), 0, 0);
        numbers.addView(gps);
        card.addView(numbers);

        speedScale = new SpeedScale(this);
        LinearLayout.LayoutParams scaleLp = new LinearLayout.LayoutParams(0, dp(64), 1f);
        scaleLp.leftMargin = dp(8);
        card.addView(speedScale, scaleLp);
        return card;
    }

    // ---------- Right: ZLink, tiles, all apps, now playing ----------

    private View buildRight() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        String zlink = Ui.findZlink(this);
        if (zlink != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(10);
            col.addView(zlinkCard(zlink), lp);
        }

        tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tilesLp = new LinearLayout.LayoutParams(-1, 0, 1.15f);
        tilesLp.bottomMargin = dp(10);
        col.addView(tiles, tilesLp);

        col.addView(allAppsRow(), appsRowLp());
        col.addView(buildNowPlaying(), nowPlayingLp());
        return col;
    }

    private LinearLayout.LayoutParams appsRowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(64));
        lp.bottomMargin = dp(10);
        return lp;
    }

    private LinearLayout.LayoutParams nowPlayingLp() {
        return new LinearLayout.LayoutParams(-1, dp(88));
    }

    private View zlinkCard(String pkg) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(dp(72));
        card.setPadding(dp(16), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF2A62F0, 0xFF1A3E9E});
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_smartphone);
        icon.setColorFilter(0xFFFFFFFF);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(0x33FFFFFF);
        icon.setBackground(disc);
        int pad = dp(10);
        icon.setPadding(pad, pad, pad, pad);
        card.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, dp(8), 0);
        TextView title = Ui.text(this, "ZLink", 20, 0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START);
        texts.addView(title);
        TextView sub = Ui.text(this, "CarPlay · Android Auto", 14, 0xE6FFFFFF);
        sub.setGravity(Gravity.START);
        texts.addView(sub);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_forward);
        chevron.setColorFilter(0xFFFFFFFF);
        card.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(28)));

        Ui.pressable(card, 0.96f);
        card.setOnClickListener(v -> Ui.openZlink(this, pkg));
        return card;
    }

    private void fillTiles() {
        tiles.removeAllViews();
        addTile(R.drawable.ic_radio, "Radio", () -> Ui.openRadio(this));
        addTile(R.drawable.ic_bluetooth, "Bluetooth", () -> Ui.openBluetooth(this));
        addTile(R.drawable.ic_nav, "Mapas", () -> startActivity(new Intent(this, MapActivity.class)));
        addTile(R.drawable.ic_play, "YT Music", () -> Ui.openYouTubeMusic(this));
        for (String flat : Profile.favorites(this)) addFavorite(flat);
    }

    private void addTile(int icon, String name, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setMinimumHeight(dp(96));
        tile.setPadding(dp(4), dp(10), dp(4), dp(8));
        tile.setBackground(Ui.card(this));

        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Ui.ink(this));
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(Ui.lightSurface(this) ? 0x14000000 : 0x26FFFFFF);
        iv.setBackground(disc);
        int pad = dp(10);
        iv.setPadding(pad, pad, pad, pad);
        tile.addView(iv, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView label = Ui.text(this, name, 14, Ui.ink(this));
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(8), 0, 0);
        tile.addView(label);

        Ui.pressable(tile, 0.96f);
        tile.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        if (tiles.getChildCount() > 0) lp.leftMargin = dp(8);
        tiles.addView(tile, lp);
    }

    private void addFavorite(String flat) {
        ComponentName cn = ComponentName.unflattenFromString(flat);
        if (cn == null) return;
        PackageManager pm = getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn);
        ResolveInfo ri = pm.resolveActivity(probe, 0);
        if (ri == null || ri.activityInfo == null) return;

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setMinimumHeight(dp(96));
        tile.setPadding(dp(4), dp(10), dp(4), dp(8));
        tile.setBackground(Ui.card(this));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(ri.loadIcon(pm));
        tile.addView(iv, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView label = Ui.text(this, ri.loadLabel(pm), 14, Ui.ink(this));
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(8), 0, 0);
        tile.addView(label);

        String pkg = ri.activityInfo.packageName;
        String cls = ri.activityInfo.name;
        Ui.pressable(tile, 0.96f);
        tile.setOnClickListener(v -> Ui.safeStart(this, new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).setClassName(pkg, cls)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.leftMargin = dp(8);
        tiles.addView(tile, lp);
    }

    private View allAppsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(18), dp(8), dp(14), dp(8));
        row.setBackground(Ui.card(this));

        TextView label = Ui.text(this, "Todas las apps", 18, Ui.ink(this));
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_forward);
        chevron.setColorFilter(Ui.ink(this));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(28)));

        Ui.pressable(row, 0.96f);
        row.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        return row;
    }

    private View buildNowPlaying() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(dp(88));
        card.setPadding(dp(14), dp(8), dp(10), dp(8));
        card.setBackground(Ui.card(this));

        ImageView note = new ImageView(this);
        note.setImageResource(R.drawable.ic_music);
        note.setColorFilter(Ui.ink(this));
        card.addView(note, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        texts.setPadding(dp(12), 0, dp(8), 0);
        songTitle = Ui.text(this, "Sin reproducción", 18, Ui.ink(this));
        songTitle.setGravity(Gravity.START);
        songTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(songTitle);
        songArtist = Ui.text(this, "Selecciona tu música", 14, Ui.muted(this));
        songArtist.setGravity(Gravity.START);
        texts.addView(songArtist);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        texts.setOnClickListener(v -> nowPlaying.open(this));
        texts.setClickable(true);

        card.addView(transport());
        return card;
    }

    private View transport() {
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 56, false, this::skipBack),
                new LinearLayout.LayoutParams(dp(56), dp(56)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 64, true, this::togglePlay);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(64), dp(64));
        plp.setMargins(dp(6), 0, dp(6), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 56, false, this::skipForward),
                new LinearLayout.LayoutParams(dp(56), dp(56)));
        return controls;
    }

    private void togglePlay() {
        if (!Profile.isNwd(this) && !nowPlaying.hasSession()) {
            toast("Sin reproducción");
            return;
        }
        nowPlaying.playPause();
    }

    private void skipBack() {
        if (!Profile.isNwd(this) && !nowPlaying.hasSession()) {
            toast("Sin reproducción");
            return;
        }
        nowPlaying.skip(false);
    }

    private void skipForward() {
        if (!Profile.isNwd(this) && !nowPlaying.hasSession()) {
            toast("Sin reproducción");
            return;
        }
        nowPlaying.skip(true);
    }

    @Override
    public void onNowPlayingChanged() {
        if (songTitle == null) return;
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();
        songTitle.setText(empty ? "Sin reproducción" : t.title);
        if (empty) {
            songArtist.setText(Ui.notificationAccess(this)
                    ? "Selecciona tu música"
                    : "En Ajustes, permite ver la música");
        } else {
            songArtist.setText(t.artist.isEmpty() ? " " : t.artist);
        }
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    // ---------- GPS speed. Not stored. 0 without a fix. ----------

    private void watchSpeed() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showSpeed(null);
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 7);
            return;
        }
        locations = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location best = newer(last(LocationManager.GPS_PROVIDER), last(LocationManager.NETWORK_PROVIDER));
        boolean fresh = best != null && System.currentTimeMillis() - best.getTime() < 2 * 60 * 1000L;
        showSpeed(fresh ? best : null);
        if (listening || locations == null) return;
        try {
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, speedFix);
            listening = true;
        } catch (Exception ignored) { }
        try {
            locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, speedFix);
            listening = true;
        } catch (Exception ignored) { }
    }

    private void showSpeed(Location location) {
        float mps = location != null && location.hasSpeed() ? location.getSpeed() : 0f;
        int kmh = mps > 0f ? Math.round(mps * 3.6f) : 0;
        if (speedValue != null) speedValue.setText(String.valueOf(kmh));
        if (speedScale != null) speedScale.setKmh(kmh);
    }

    private Location last(String provider) {
        try {
            return locations == null ? null : locations.getLastKnownLocation(provider);
        } catch (Exception e) {
            return null;
        }
    }

    private static Location newer(Location a, Location b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.getTime() >= b.getTime() ? a : b;
    }

    private void stopSpeed() {
        if (locations != null && listening) {
            try {
                locations.removeUpdates(speedFix);
            } catch (Exception ignored) { }
        }
        listening = false;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == 7 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            watchSpeed();
        }
    }

    private final Runnable nightWatch = new Runnable() {
        public void run() {
            if (Ui.isDark(MainActivity.this) != createdDark) {
                recreate();
                return;
            }
            handler.postDelayed(this, 60_000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.isDark(this) != createdDark || (Ui.findZlink(this) != null) != createdZlink) {
            recreate();
            return;
        }
        fillTiles();
        if (Ui.dimmed(this)) Ui.applyDim(this);
        watchSpeed();
        handler.postDelayed(nightWatch, 60_000);
        nowPlaying.addListener(this);
        onNowPlayingChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(nightWatch);
        stopSpeed();
        nowPlaying.removeListener(this);
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    static void pressable(View v) {
        Ui.pressable(v, 0.96f);
    }

    @Override
    public void onBackPressed() {
        // Home screen: nothing to go back to.
    }
}
