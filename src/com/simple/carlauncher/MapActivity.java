package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * Map in the same frame as the home. Google Maps and Waze, when installed,
 * open as themselves. This screen does not route or keep a trip.
 */
public class MapActivity extends Activity implements LocationListener {

    static final double QUITO_LAT = -0.1807;
    static final double QUITO_LON = -78.4678;
    private static final long FRESH_MS = 30 * 60 * 1000;

    private PlaceMap map;
    private TextView note;
    private LinearLayout band;
    private LocationManager locations;
    private boolean listening;
    private boolean hasFix;
    private boolean tilesOk;
    private boolean tilesDone;
    private double lat = QUITO_LAT;
    private double lon = QUITO_LON;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locations = (LocationManager) getSystemService(LOCATION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.background(this));
        Ui.padBelowSystemBar(root);

        root.addView(buildHeader());
        root.addView(buildMap(), mapLayout());
        root.addView(buildBand(), new LinearLayout.LayoutParams(-1, dp(108)));
        setContentView(root);
        root.requestApplyInsets();
        showPlace(false);
    }

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

        boolean maps = installed("com.google.android.apps.maps");
        boolean waze = installed("com.waze");
        if (maps || waze) {
            LinearLayout pills = new LinearLayout(this);
            pills.setOrientation(LinearLayout.HORIZONTAL);
            pills.setGravity(Gravity.CENTER_VERTICAL);
            if (maps) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(64), 1f);
                if (waze) lp.rightMargin = dp(12);
                pills.addView(pill("Google Maps", () -> openNav("com.google.android.apps.maps", true)), lp);
            }
            if (waze) {
                pills.addView(pill("Waze", () -> openNav("com.waze", false)),
                        new LinearLayout.LayoutParams(0, dp(64), 1f));
            }
            if (maps ^ waze) {
                pills.addView(new View(this), new LinearLayout.LayoutParams(0, dp(64), 1f));
            }
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(64));
            plp.topMargin = dp(10);
            col.addView(pills, plp);
        }
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

    private View buildMap() {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF101012);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), 0x33FFFFFF);
        card.setBackground(bg);
        card.setClipToOutline(true);

        map = new PlaceMap(this);
        map.setListener(any -> {
            tilesDone = true;
            tilesOk = any;
            refreshNote();
        });
        card.addView(map, new FrameLayout.LayoutParams(-1, -1));

        note = Ui.text(this, "", 18, Color.WHITE);
        note.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        note.setSingleLine(false);
        note.setMaxLines(3);
        note.setPadding(dp(20), dp(14), dp(20), dp(14));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0xCC101012);
        chip.setCornerRadius(dp(14));
        note.setBackground(chip);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        nlp.setMargins(dp(16), 0, dp(16), dp(16));
        card.addView(note, nlp);
        return card;
    }

    private LinearLayout.LayoutParams mapLayout() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = dp(12);
        lp.bottomMargin = dp(10);
        return lp;
    }

    private void showPlace(boolean fromFix) {
        hasFix = fromFix;
        if (!fromFix) {
            lat = QUITO_LAT;
            lon = QUITO_LON;
        }
        if (map.setCenter(lat, lon)) {
            tilesDone = false;
            tilesOk = false;
        }
        refreshNote();
    }

    private void refreshNote() {
        if (note == null) return;
        StringBuilder sb = new StringBuilder();
        if (!online()) sb.append("Sin conexión. El mapa no carga.");
        else if (tilesDone && !tilesOk) sb.append("No se pudo cargar el mapa.");
        else if (!tilesDone && online()) sb.append("Cargando el mapa…");
        if (!hasFix) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("Sin ubicación del equipo. Mostrando Quito.");
        }
        if (sb.length() == 0) {
            note.setVisibility(View.GONE);
        } else {
            note.setText(sb.toString());
            note.setVisibility(View.VISIBLE);
        }
    }

    private boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (SecurityException e) {
            return true;
        }
    }

    private void readLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showPlace(false);
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        Location best = newer(last(LocationManager.GPS_PROVIDER), last(LocationManager.NETWORK_PROVIDER));
        boolean fresh = best != null && System.currentTimeMillis() - best.getTime() < FRESH_MS;
        if (fresh) {
            lat = best.getLatitude();
            lon = best.getLongitude();
            showPlace(true);
        } else {
            showPlace(false);
        }
        if (!listening) {
            try {
                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 40, this);
                listening = true;
            } catch (Exception ignored) { }
            try {
                locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 40, this);
                listening = true;
            } catch (Exception ignored) { }
        }
    }

    private Location last(String provider) {
        try {
            return locations.getLastKnownLocation(provider);
        } catch (Exception e) {
            return null;
        }
    }

    private static Location newer(Location a, Location b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.getTime() >= b.getTime() ? a : b;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;
        lat = location.getLatitude();
        lon = location.getLongitude();
        showPlace(true);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == 1 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            readLocation();
        }
    }

    private void openNav(String pkg, boolean maps) {
        String ll = lat + "," + lon;
        Uri uri = maps
                ? Uri.parse("geo:" + ll + "?q=" + ll + "&z=15")
                : Uri.parse("https://waze.com/ul?ll=" + ll + "&zoom=15");
        Intent i = new Intent(Intent.ACTION_VIEW, uri).setPackage(pkg);
        if (!Ui.safeStart(this, i)) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) Ui.safeStart(this, launch);
        }
    }

    private boolean installed(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private View buildBand() {
        band = new LinearLayout(this);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setGravity(Gravity.CENTER_VERTICAL);
        return band;
    }

    private void fillBand() {
        band.removeAllViews();
        addNamed(R.drawable.ic_nav, "Mapas", () -> { });
        addNamed(R.drawable.ic_radio, "Radio", () -> Ui.openRadio(this));
        addNamed(R.drawable.ic_phone, "Teléfono", () -> Ui.openPhone(this));
        addNamed(R.drawable.ic_bluetooth, "Bluetooth", () -> Ui.openBluetooth(this));
        addNamed(R.drawable.ic_music, "Música", () -> NowPlaying.get(this).open(this));
        addNamed(R.drawable.ic_play, "YT Music", () -> Ui.openYouTubeMusic(this));
        for (String flat : Profile.favorites(this)) addFavorite(flat);
        addNamed(R.drawable.ic_apps, "Apps", () -> startActivity(new Intent(this, AppsActivity.class)));
    }

    private void addNamed(int icon, String name, Runnable action) {
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
        band.addView(tile, new LinearLayout.LayoutParams(0, -1, 1f));
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
        tile.setOnClickListener(v -> Ui.safeStart(this, new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).setClassName(pkg, cls)));
        band.addView(tile, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    @Override
    protected void onResume() {
        super.onResume();
        fillBand();
        readLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (listening) {
            try {
                locations.removeUpdates(this);
            } catch (Exception ignored) { }
            listening = false;
        }
    }

    @Override
    protected void onDestroy() {
        if (map != null) map.stop();
        super.onDestroy();
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
