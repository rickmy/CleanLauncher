package com.simple.carlauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
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
import android.widget.HorizontalScrollView;
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
    private ImageView art, playBtn, car;
    private TextView songSource, songTitle, songArtist, radioLive, dimPill, oilInfo;
    private LinearLayout band;
    private GradientDrawable oilBg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.background(this));
        Ui.padBelowSystemBar(root);

        root.addView(buildHeader());
        root.addView(buildHero());
        root.addView(buildBand(), new LinearLayout.LayoutParams(-1, dp(100)));
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

    // ---------- Header: one clock, the car, Ajustes ----------

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(8));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView hello = Ui.text(this, Profile.hello(this), 16, 0xB3FFFFFF);
        hello.setGravity(Gravity.START);
        texts.addView(hello);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setTextSize(64);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.START);
        texts.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d 'de' MMMM");
        date.setFormat12Hour("EEEE d 'de' MMMM");
        date.setTextSize(15);
        date.setTextColor(0xB3FFFFFF);
        date.setGravity(Gravity.START);
        texts.addView(date);

        oilInfo = Ui.text(this, "", 14, Color.WHITE);
        oilInfo.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        oilInfo.setMinHeight(dp(56));
        oilInfo.setPadding(0, dp(8), 0, 0);
        oilBg = new GradientDrawable();
        oilBg.setCornerRadius(dp(14));
        oilInfo.setBackground(oilBg);
        Ui.pressable(oilInfo, 0.96f);
        oilInfo.setOnClickListener(v -> startActivity(new Intent(this, OilActivity.class)));
        texts.addView(oilInfo);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        car = new ImageView(this);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        showCar();
        Ui.pressable(car, 0.96f);
        car.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(168), dp(88));
        clp.leftMargin = dp(12);
        row.addView(car, clp);

        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.VERTICAL);
        pills.setGravity(Gravity.END);
        dimPill = pill(dimLabel(), this::toggleDim);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.bottomMargin = dp(8);
        pills.addView(dimPill, dlp);
        pills.addView(pill("Ajustes", () -> startActivity(new Intent(this, SetupActivity.class))));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.leftMargin = dp(12);
        row.addView(pills, plp);
        return row;
    }

    private void showCar() {
        Bitmap own = Profile.carPhoto(this);
        int bundled = Profile.bundledCar(this, false);
        car.clearColorFilter();
        if (own != null) car.setImageBitmap(own);
        else if (bundled != 0) car.setImageResource(bundled);
        else {
            car.setImageResource(R.drawable.ic_car);
            car.setColorFilter(0x66FFFFFF);
        }
    }

    private TextView pill(String label, Runnable action) {
        TextView b = Ui.text(this, label, 16, Color.WHITE);
        b.setMinWidth(dp(96));
        b.setMinHeight(dp(56));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(dp(28));
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
        bg.setColor(0xFF141416);
        bg.setCornerRadius(dp(20));
        card.setBackground(bg);
        card.setClipToOutline(true);

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(art, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF0101012, 0xCC101012, 0x66101012}));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(dp(28), dp(20), dp(28), dp(20));

        songSource = Ui.text(this, "MÚSICA", 13, 0xCCFFFFFF);
        songSource.setGravity(Gravity.START);
        songSource.setLetterSpacing(0.08f);
        body.addView(songSource);

        songTitle = Ui.text(this, "Nada sonando", 32, Color.WHITE);
        songTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        songTitle.setGravity(Gravity.START);
        songTitle.setSingleLine(false);
        songTitle.setMaxLines(2);
        songTitle.setPadding(0, dp(8), 0, 0);
        body.addView(songTitle);

        songArtist = Ui.text(this, "", 18, 0xB3FFFFFF);
        songArtist.setGravity(Gravity.START);
        songArtist.setSingleLine(false);
        songArtist.setMaxLines(2);
        songArtist.setPadding(0, dp(4), 0, dp(18));
        body.addView(songArtist);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 64, false, this::skipBack),
                new LinearLayout.LayoutParams(dp(64), dp(64)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 76, true, this::togglePlay);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(76), dp(76));
        plp.setMargins(dp(16), 0, dp(16), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 64, false, this::skipForward),
                new LinearLayout.LayoutParams(dp(64), dp(64)));
        body.addView(controls);
        card.addView(body, new FrameLayout.LayoutParams(-1, -1));

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
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        band = new LinearLayout(this);
        band.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(band, new FrameLayout.LayoutParams(-2, -1));
        return scroll;
    }

    private void fillBand() {
        band.removeAllViews();
        radioLive = null;
        addNamed(R.drawable.ic_nav, "Mapas", () -> Ui.openMaps(this));
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
        for (String flat : Profile.favorites(this)) addFavorite(flat);
        addNamed(R.drawable.ic_apps, "Apps", () -> startActivity(new Intent(this, AppsActivity.class)));
    }

    private View addNamed(int icon, String name, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setMinimumWidth(dp(104));
        tile.setMinimumHeight(dp(84));
        tile.setPadding(dp(10), dp(8), dp(10), dp(8));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Color.WHITE);
        tile.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView label = Ui.text(this, name, 15, Color.WHITE);
        label.setPadding(0, dp(6), 0, 0);
        tile.addView(label);
        Ui.pressable(tile, 0.96f);
        tile.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        band.addView(tile, lp);
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
        tile.setMinimumWidth(dp(104));
        tile.setMinimumHeight(dp(84));
        tile.setPadding(dp(10), dp(8), dp(10), dp(8));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(ri.loadIcon(pm));
        tile.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView label = Ui.text(this, ri.loadLabel(pm), 15, Color.WHITE);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        band.addView(tile, lp);
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

    private void updateOil() {
        if (!Odometer.isConfigured(this)) {
            oilInfo.setText("Aceite");
            oilBg.setColor(0x00000000);
            return;
        }
        int left = Odometer.remainingKm(this);
        oilInfo.setText(left < 0 ? "Aceite: ya toca el cambio" : "Aceite: faltan " + OilActivity.fmt(left) + " km");
        oilBg.setColor(left < 0 ? 0xCCE53935 : left <= Odometer.WARN_KM ? 0xCCFF9800 : 0x26FFFFFF);
        oilInfo.setPadding(dp(12), dp(6), dp(12), dp(6));
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
        showCar();
        fillBand();
        updateOil();
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
