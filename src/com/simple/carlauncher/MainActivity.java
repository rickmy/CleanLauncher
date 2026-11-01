package com.simple.carlauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * Home for the Almera's MediaTek unit: one light clock, the Android session that is
 * playing, and a named band. NWD screens stay behind {@link Profile#isNwd}.
 */
public class MainActivity extends Activity implements NowPlaying.Listener {

    private final Handler handler = new Handler();
    private final RadioClient radio = new RadioClient();
    private NowPlaying nowPlaying;

    private boolean createdDark;
    private boolean createdNight;
    private boolean radioBound;
    private ImageView art, playBtn;
    private TextView songSource, songTitle, songArtist, radioLive, dimPill;
    private LinearLayout band;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.background(this));
        Ui.padBelowSystemBar(root);

        root.addView(buildHeader());
        root.addView(buildHero());
        root.addView(buildBand(), new LinearLayout.LayoutParams(-1, dp(108)));
        setContentView(root);
        root.requestApplyInsets();

        nowPlaying = NowPlaying.get(this);
        createdDark = Ui.isDark(this);
        createdNight = Ui.nightHours();
        if (!Profile.isSetupDone(this)) {
            startActivity(new Intent(this, SetupActivity.class));
        }
        if (Profile.isNwd(this)) {
            radio.bind(this);
            radioBound = true;
            startService(new Intent(this, VolumeOverlay.class));
        }
        startService(new Intent(this, GreeterService.class));
        startService(new Intent(this, Odometer.class));
    }

    // ---------- Header: one clock, then the pills clear of the top-right nav ----------

    private View buildHeader() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(72);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.START);
        col.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d 'de' MMMM");
        date.setFormat12Hour("EEEE d 'de' MMMM");
        date.setTextSize(18);
        date.setTextColor(0xB3FFFFFF);
        date.setGravity(Gravity.START);
        date.setPadding(0, dp(2), 0, 0);
        col.addView(date);

        // Nav (recents, home, back) sits in the status bar at the top right.
        // These pills stay on the next row, full width, so they are not under that cluster.
        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setGravity(Gravity.CENTER_VERTICAL);
        dimPill = pill(dimLabel(), this::toggleDim);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(64), 1f);
        left.rightMargin = dp(12);
        pills.addView(dimPill, left);
        pills.addView(pill("Ajustes", () -> startActivity(new Intent(this, SetupActivity.class))),
                new LinearLayout.LayoutParams(0, dp(64), 1f));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(64));
        plp.topMargin = dp(10);
        col.addView(pills, plp);
        return col;
    }

    private TextView pill(String label, Runnable action) {
        TextView b = Ui.text(this, label, 20, Color.WHITE);
        b.setMinHeight(dp(64));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(24), dp(12), dp(24), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(dp(32));
        bg.setStroke(dp(1), 0x66FFFFFF);
        b.setBackground(bg);
        Ui.pressable(b, 0.96f);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private String dimLabel() {
        return Ui.dimmed(this) ? "Brillo" : "Atenuar";
    }

    private void toggleDim() {
        if (!Settings.System.canWrite(this)) {
            toast("Permite modificar los ajustes del sistema para atenuar");
            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName()));
            Ui.safeStart(this, i);
            return;
        }
        try {
            if (!Ui.dimmed(this)) {
                int mode = Settings.System.getInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                int level = Settings.System.getInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 128);
                Ui.rememberBrightness(this, mode, level);
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 12);
                applyWindowBrightness(12f / 255f);
                Ui.setDimmed(this, true);
            } else {
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Ui.savedBrightnessMode(this));
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                        Ui.savedBrightness(this));
                applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
                Ui.setDimmed(this, false);
            }
        } catch (Exception e) {
            toast("No se pudo cambiar el brillo");
        }
        dimPill.setText(dimLabel());
    }

    private void applyWindowBrightness(float value) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = value;
        getWindow().setAttributes(lp);
    }

    // ---------- Now playing (MediaSession) ----------

    private View buildHero() {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF16161A);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), 0x33FFFFFF);
        card.setBackground(bg);
        card.setClipToOutline(true);

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(art, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF0101012, 0xE0101012, 0xC0101012}));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(dp(28), dp(16), dp(24), dp(16));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);

        songSource = Ui.text(this, "MÚSICA", 16, 0xCCFFFFFF);
        songSource.setGravity(Gravity.START);
        songSource.setLetterSpacing(0.08f);
        texts.addView(songSource);

        songTitle = Ui.text(this, "Nada sonando", 44, Color.WHITE);
        songTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        songTitle.setGravity(Gravity.START);
        songTitle.setSingleLine(false);
        songTitle.setMaxLines(2);
        songTitle.setPadding(0, dp(6), 0, 0);
        texts.addView(songTitle);

        songArtist = Ui.text(this, "", 22, 0xB3FFFFFF);
        songArtist.setGravity(Gravity.START);
        songArtist.setSingleLine(false);
        songArtist.setMaxLines(2);
        songArtist.setPadding(0, dp(4), 0, 0);
        texts.addView(songArtist);
        body.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 72, false, this::skipBack),
                new LinearLayout.LayoutParams(dp(72), dp(72)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 84, true, this::togglePlay);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(84), dp(84));
        plp.setMargins(dp(18), 0, dp(18), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 72, false, this::skipForward),
                new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.leftMargin = dp(20);
        body.addView(controls, clp);
        card.addView(body, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER_VERTICAL));

        Ui.pressable(card, 0.96f);
        card.setOnClickListener(v -> nowPlaying.open(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        return card;
    }

    private void togglePlay() {
        if (!Profile.isNwd(this) && !nowPlaying.hasSession()) {
            toast("Nada en reproducción");
            return;
        }
        nowPlaying.playPause();
    }

    private void skipBack() {
        if (!Profile.isNwd(this) && !nowPlaying.hasSession()) {
            toast("Nada en reproducción");
            return;
        }
        nowPlaying.skip(false);
    }

    private void skipForward() {
        if (!Profile.isNwd(this) && !nowPlaying.hasSession()) {
            toast("Nada en reproducción");
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
        songSource.setText(s == null ? "MÚSICA" : nowPlaying.label(s).toUpperCase());
        songTitle.setText(empty ? "Nada sonando" : t.title);
        if (empty) {
            songArtist.setText(Ui.notificationAccess(this)
                    ? "Toca para abrir la música"
                    : "En Ajustes, permite ver la música");
        } else {
            songArtist.setText(t.artist.isEmpty() ? " " : t.artist);
        }
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);

        if (t != null && t.art != null) {
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.clearColorFilter();
            art.setAlpha(1f);
            art.setImageBitmap(t.art);
        } else {
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageDrawable(null);
            art.setAlpha(0f);
        }
    }

    // ---------- Band ----------

    private View buildBand() {
        band = new LinearLayout(this);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setGravity(Gravity.CENTER_VERTICAL);
        return band;
    }

    private LinearLayout.LayoutParams tileSlot() {
        return new LinearLayout.LayoutParams(0, -1, 1f);
    }

    private void fillBand() {
        band.removeAllViews();
        radioLive = null;
        addNamed(R.drawable.ic_nav, "Mapas", () -> startActivity(new Intent(this, MapActivity.class)));
        View radioTile = addNamed(R.drawable.ic_radio, "Radio", () -> Ui.openRadio(this));
        radioLive = new TextView(this);
        radioLive.setTextColor(0xB3FFFFFF);
        radioLive.setTextSize(13);
        radioLive.setGravity(Gravity.CENTER);
        radioLive.setVisibility(View.GONE);
        if (radioTile instanceof LinearLayout) ((LinearLayout) radioTile).addView(radioLive);
        addNamed(R.drawable.ic_phone, "Teléfono", () -> Ui.openPhone(this));
        addNamed(R.drawable.ic_bluetooth, "Bluetooth", () -> Ui.openBluetooth(this));
        addNamed(R.drawable.ic_music, "Música", () -> nowPlaying.open(this));
        addNamed(R.drawable.ic_play, "YT Music", () -> Ui.openYouTubeMusic(this));
        for (String flat : Profile.favorites(this)) addFavorite(flat);
        addNamed(R.drawable.ic_apps, "Apps", () -> startActivity(new Intent(this, AppsActivity.class)));
    }

    private View addNamed(int icon, String name, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setMinimumHeight(dp(84));
        tile.setPadding(dp(4), dp(8), dp(4), dp(8));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Color.WHITE);
        tile.addView(iv, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView label = Ui.text(this, name, 16, Color.WHITE);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(6), 0, 0);
        tile.addView(label);
        Ui.pressable(tile, 0.96f);
        tile.setOnClickListener(v -> action.run());
        band.addView(tile, tileSlot());
        return tile;
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
        tile.setMinimumHeight(dp(84));
        tile.setPadding(dp(4), dp(8), dp(4), dp(8));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(ri.loadIcon(pm));
        tile.addView(iv, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView label = Ui.text(this, ri.loadLabel(pm), 16, Color.WHITE);
        label.setPadding(0, dp(6), 0, 0);
        tile.addView(label);
        Ui.pressable(tile, 0.96f);
        String pkg = ri.activityInfo.packageName;
        String cls = ri.activityInfo.name;
        tile.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(pkg, cls);
            Ui.safeStart(this, i);
        });
        band.addView(tile, tileSlot());
    }

    private void updateRadio() {
        if (radioLive == null || !Profile.isNwd(this)) return;
        RadioClient.Station st = radio.current();
        if (st == null) {
            radioLive.setText("");
            radioLive.setVisibility(View.GONE);
            return;
        }
        String label = st.frequencyLabel();
        if (label == null || label.isEmpty()) {
            radioLive.setText("");
            radioLive.setVisibility(View.GONE);
            return;
        }
        radioLive.setText(label);
        radioLive.setVisibility(View.VISIBLE);
    }

    private final Runnable nightWatch = new Runnable() {
        public void run() {
            if (Ui.nightHours() != createdNight || Ui.isDark(MainActivity.this) != createdDark) {
                recreate();
                return;
            }
            handler.postDelayed(this, 60_000);
        }
    };

    private final Runnable radioPoll = new Runnable() {
        public void run() {
            updateRadio();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.isDark(this) != createdDark || Ui.nightHours() != createdNight) {
            recreate();
            return;
        }
        fillBand();
        dimPill.setText(dimLabel());
        if (Ui.dimmed(this)) applyWindowBrightness(12f / 255f);
        if (Profile.isNwd(this)) handler.post(radioPoll);
        handler.postDelayed(nightWatch, 60_000);
        nowPlaying.addListener(this);
        onNowPlayingChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(radioPoll);
        handler.removeCallbacks(nightWatch);
        nowPlaying.removeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (radioBound) radio.unbind(this);
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
