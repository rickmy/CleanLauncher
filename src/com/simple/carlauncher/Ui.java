package com.simple.carlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/** Shared look (neon blue/purple) and small helpers for all screens. */
final class Ui {

    // MCU source ids for ACTION_REQUEST_CHANGE_SOURCE: the "Source" column of
    // /nwdappconfig/app/ApplicationList.xml (not the AppID).
    static final int SOURCE_MUSIC = -128;
    static final int SOURCE_RADIO = 4;
    static final int SOURCE_BT_MUSIC = -125;

    private Ui() { }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ---------- Theme: light (blue/purple neon) or dark (black) ----------

    static final String THEME_AUTO = "auto", THEME_LIGHT = "light", THEME_DARK = "dark";

    static String themeMode(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("theme", THEME_AUTO);
    }

    static void setThemeMode(Context c, String mode) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString("theme", mode).apply();
    }

    /**
     * This Almera has no headlight CAN bit. Day is light from 06:00 through 18:59
     * and night is dark from 19:00 through 05:59, from the device clock alone.
     * An NWD unit still follows its ILL wire and the clock.
     */
    static boolean isDark(Context c) {
        if (!Profile.isNwd(c)) return nightHours();
        String m = themeMode(c);
        if (THEME_DARK.equals(m)) return true;
        if (THEME_LIGHT.equals(m)) return false;
        int ill = Settings.System.getInt(c.getContentResolver(), "mcu_no_source_device_state", 0);
        return (ill & 2) != 0 || nightHours();
    }

    /** Near-white surface and dark type. False on the old NWD blue daytime screen. */
    static boolean lightSurface(Context c) {
        return !isDark(c) && !Profile.isNwd(c);
    }

    static int ink(Context c) {
        return lightSurface(c) ? 0xFF121418 : Color.WHITE;
    }

    static int muted(Context c) {
        return lightSurface(c) ? 0xFF2A3038 : 0xB3FFFFFF;
    }

    static boolean nightHours() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h >= 19 || h < 6;
    }

    /** Picks the light or dark variant of a gradient. */
    static int[] pick(Context c, int[] light, int[] dark) {
        return isDark(c) ? dark : light;
    }

    static Drawable background(Context c) {
        if (lightSurface(c)) {
            GradientDrawable day = new GradientDrawable();
            day.setColor(0xFFF4F6F8);
            return day;
        }
        if (isDark(c)) {
            return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF0C1424, 0xFF070B14, 0xFF05080E});
        }
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1B2C8C, 0xFF20206E, 0xFF371764});
        GradientDrawable glowLeft = new GradientDrawable();
        glowLeft.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowLeft.setGradientRadius(dp(c, 520));
        glowLeft.setGradientCenter(0.15f, 0.1f);
        glowLeft.setColors(new int[]{0x553FA9FF, 0x003FA9FF});
        GradientDrawable glowRight = new GradientDrawable();
        glowRight.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowRight.setGradientRadius(dp(c, 560));
        glowRight.setGradientCenter(0.95f, 0.95f);
        glowRight.setColors(new int[]{0x66B04CFF, 0x00B04CFF});
        return new LayerDrawable(new Drawable[]{base, glowLeft, glowRight});
    }

    static GradientDrawable card(Context c) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 18));
        if (lightSurface(c)) {
            bg.setColor(0xFFFFFFFF);
            bg.setStroke(dp(c, 1), 0x33000000);
        } else if (isDark(c)) {
            bg.setColor(0xFF141C2E);
            bg.setStroke(dp(c, 1), 0x332C4066);
        } else {
            bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            bg.setColors(new int[]{0x4D5B6CFF, 0x333B3FB8});
            bg.setStroke(dp(c, 1), 0x337F9CFF);
        }
        return bg;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    /** Round icon button. filled = white disc with dark icon (primary action). */
    static ImageView iconButton(Context c, int icon, int sizeDp, boolean filled, Runnable action) {
        ImageView b = new ImageView(c);
        b.setImageResource(icon);
        int pad = Math.round(sizeDp * 0.24f);
        b.setPadding(dp(c, pad), dp(c, pad), dp(c, pad), dp(c, pad));
        boolean light = lightSurface(c);
        b.setBackground(circle(filled ? 0xFF2F6BFF : (light ? 0x14000000 : 0x26FFFFFF)));
        b.setColorFilter(filled || !light ? Color.WHITE : 0xFF121418);
        pressable(b, 0.88f);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    /** Short press: about 0.96 scale, 120 ms. Readable even if the unit animates slowly. */
    static void pressable(View v, float scale) {
        float s = Math.max(scale, 0.96f);
        v.setClickable(true);
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(s).scaleY(s).alpha(0.92f).setDuration(120).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
            }
            return false;
        });
    }

    /**
     * Keeps content under the system back / home / recents bar.
     * The RS8577 reports 1075×600 on a 1280×720 panel. Do not apply a corrective
     * scale until a touch in each of the four corners has been checked on the unit.
     */
    static void padBelowSystemBar(View root) {
        Context c = root.getContext();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            if (top < dp(c, 48)) top = dp(c, 48);
            v.setPadding(
                    insets.getSystemWindowInsetLeft() + dp(c, 16),
                    top + dp(c, 8),
                    insets.getSystemWindowInsetRight() + dp(c, 16),
                    insets.getSystemWindowInsetBottom() + dp(c, 8));
            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
    }

    static boolean safeStart(Context c, Intent i) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Opens the first other launcher activity whose label or package contains a keyword. */
    static boolean launchByKeyword(Context c, String... keywords) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        String self = c.getPackageName();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo == null || self.equals(ri.activityInfo.packageName)) continue;
            String label = (ri.loadLabel(pm) + " " + ri.activityInfo.packageName)
                    .toLowerCase(java.util.Locale.ROOT);
            for (String k : keywords) {
                if (label.contains(k)) {
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                    return safeStart(c, i);
                }
            }
        }
        return false;
    }

    static void openMaps(Context c) {
        Intent maps = c.getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
        if (maps == null || !safeStart(c, maps)) {
            safeStart(c, new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")));
        }
    }

    /** This unit's radio app. NWD keeps its own tuner screen. */
    static void openRadio(Context c) {
        if (Profile.isNwd(c)) {
            c.startActivity(new Intent(c, RadioActivity.class));
            return;
        }
        if (!launchByKeyword(c, "radio", "fm", "tuner", "dab")) {
            Toast.makeText(c, "No se encontró la app de radio de este equipo", Toast.LENGTH_SHORT).show();
        }
    }

    /** This unit's Bluetooth app. Does not open the NWD phone screen. */
    static void openBluetooth(Context c) {
        if (Profile.isNwd(c)) {
            c.startActivity(new Intent(c, BtActivity.class));
            return;
        }
        if (!launchByKeyword(c, "bluetooth", "bt music", "btmusic")) {
            safeStart(c, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        }
    }

    /**
     * ZLink (CarPlay / Android Auto), if this unit has it.
     * Tries the usual package names, then any package whose name contains "zlink".
     */
    static String findZlink(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String pkg : new String[]{"com.zjinnova.zlink", "com.zjinnova.zlink5"}) {
            if (packageInstalled(pm, pkg)) return pkg;
        }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
            if (ri.activityInfo.packageName.toLowerCase(java.util.Locale.ROOT).contains("zlink")) {
                return ri.activityInfo.packageName;
            }
        }
        try {
            for (android.content.pm.ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if (ai.packageName != null
                        && ai.packageName.toLowerCase(java.util.Locale.ROOT).contains("zlink")) {
                    return ai.packageName;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    static void openZlink(Context c, String pkg) {
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null || !safeStart(c, launch)) {
            Toast.makeText(c, "No se pudo abrir ZLink", Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean packageInstalled(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** YouTube Music. A missing package is a short notice, not a crash. */
    static void openYouTubeMusic(Context c) {
        Intent launch = c.getPackageManager()
                .getLaunchIntentForPackage("com.google.android.apps.youtube.music");
        if (launch == null || !safeStart(c, launch)) {
            Toast.makeText(c, "YT Music no está instalado", Toast.LENGTH_SHORT).show();
        }
    }

    /** Dialer of this unit. On NWD the factory phone screen is the same place as Bluetooth. */
    static void openPhone(Context c) {
        if (Profile.isNwd(c)) {
            c.startActivity(new Intent(c, BtActivity.class));
            return;
        }
        if (!safeStart(c, new Intent(Intent.ACTION_DIAL))) {
            launchByKeyword(c, "teléfono", "telefono", "phone", "dialer");
        }
    }

    /**
     * NWD keeps {@link EqActivity}, which binds {@code com.nwd.setting.service}.
     * This unit does not. Look for its own equalizer, then the system panel.
     * Never binds the NWD sound service from here.
     */
    static void openEqualizer(Context c) {
        if (Profile.isNwd(c)) {
            c.startActivity(new Intent(c, EqActivity.class));
            return;
        }
        Intent unit = unitEqualizer(c);
        if (unit != null && safeStart(c, unit)) return;
        Intent panel = new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        if (!safeStart(c, panel)) {
            Toast.makeText(c, "Este radio no presta el ecualizador a otras apps.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Launcher activity whose package or label looks like this unit's EQ.
     * Skips this launcher. "eq" only counts as its own word, not inside "request".
     */
    private static Intent unitEqualizer(Context c) {
        PackageManager pm = c.getPackageManager();
        String self = c.getPackageName();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Intent bestIntent = null;
        int best = Integer.MAX_VALUE;
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (self.equals(pkg) || pkg.startsWith("com.nwd.")) continue;
            int rank = eqRank(pkg, String.valueOf(ri.loadLabel(pm)));
            if (rank < 0 || rank >= best) continue;
            best = rank;
            bestIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(pkg, ri.activityInfo.name);
        }
        try {
            for (android.content.pm.ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if (ai.packageName == null || self.equals(ai.packageName) || ai.packageName.startsWith("com.nwd.")) {
                    continue;
                }
                int rank = eqRank(ai.packageName, "");
                if (rank < 0 || rank >= best) continue;
                Intent launch = pm.getLaunchIntentForPackage(ai.packageName);
                if (launch == null) continue;
                best = rank;
                bestIntent = launch;
            }
        } catch (Exception ignored) { }
        return bestIntent;
    }

    private static int eqRank(String pkg, String label) {
        String blob = (pkg + " " + label).toLowerCase(java.util.Locale.ROOT);
        if (blob.contains("equalizer") || blob.contains("ecualiz")) return 0;
        if (blob.contains("dsp")) return 1;
        if (blob.contains("sound settings") || blob.contains("soundsettings")
                || blob.contains("sound_settings") || blob.contains("sound-settings")
                || blob.contains("ajustes de sonido")) return 2;
        if (eqToken(pkg) || eqToken(label)) return 3;
        return -1;
    }

    private static boolean eqToken(String s) {
        String n = s.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < n.length() - 1; i++) {
            if (n.charAt(i) != 'e' || n.charAt(i + 1) != 'q') continue;
            boolean before = i == 0 || !Character.isLetter(n.charAt(i - 1));
            boolean after = i + 2 >= n.length() || !Character.isLetter(n.charAt(i + 2));
            if (before && after) return true;
        }
        return false;
    }

    static boolean notificationAccess(Context c) {
        String listeners = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return listeners != null && listeners.contains(c.getPackageName());
    }

    static void applyDim(Context c) {
        if (!(c instanceof android.app.Activity)) return;
        android.view.Window w = ((android.app.Activity) c).getWindow();
        android.view.WindowManager.LayoutParams lp = w.getAttributes();
        lp.screenBrightness = dimmed(c) ? 12f / 255f
                : android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        w.setAttributes(lp);
    }

    /** Screen brightness, not the panel's headlight wire. Returns false if permission is missing. */
    static boolean toggleDim(Context c) {
        if (!Settings.System.canWrite(c)) {
            safeStart(c, new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + c.getPackageName())));
            return false;
        }
        try {
            if (!dimmed(c)) {
                int mode = Settings.System.getInt(c.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                int level = Settings.System.getInt(c.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 128);
                rememberBrightness(c, mode, level);
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 12);
                setDimmed(c, true);
            } else {
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        savedBrightnessMode(c));
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                        savedBrightness(c));
                setDimmed(c, false);
            }
            applyDim(c);
            return true;
        } catch (Exception e) {
            Toast.makeText(c, "No se pudo cambiar el brillo", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    static boolean dimmed(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("dimmed", false);
    }

    static void setDimmed(Context c, boolean on) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("dimmed", on).apply();
    }

    static void rememberBrightness(Context c, int mode, int level) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit()
                .putInt("brightness_mode", mode)
                .putInt("brightness", level)
                .apply();
    }

    static int savedBrightness(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("brightness", 128);
    }

    static int savedBrightnessMode(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("brightness_mode",
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
    }

    static void launch(Context c, String component) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(ComponentName.unflattenFromString(component));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            c.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(c, "No disponible", Toast.LENGTH_SHORT).show();
        }
    }

    /** When the user last picked the radio; the BT auto-switch leaves it alone for a while. */
    static long userChoseRadioAt;

    /** True while the FM radio is the active audio source. */
    static boolean radioIsSource(Context c) {
        return android.provider.Settings.System.getInt(c.getContentResolver(), "mcu_current_source", 0)
                == SOURCE_RADIO;
    }

    /**
     * User picked the radio: route it, power the FM chip up and unmute it, and pause the
     * phone so it does not keep streaming (and trigger the Bluetooth auto-switch).
     */
    static void selectRadio(Context c, RadioClient radio) {
        userChoseRadioAt = android.os.SystemClock.elapsedRealtime();
        NowPlaying np = NowPlaying.get(c);
        if (np.track(NowPlaying.Source.BT).playing) np.btClient.playControl(BtClient.PAUSE);
        requestSource(c, SOURCE_RADIO);
        new android.os.Handler().postDelayed(() -> radio.wakeTuner(c), 800);
    }

    /**
     * Makes Bluetooth music the source. Source id -125 is shared by the BT phone (AppID 10) and
     * BT music (AppID 15); a plain source request picks the phone entry, which mutes the
     * amplifier. Announcing the BT music app (KernelUtils.appStart: id 15, op 1, event 0) is what
     * the factory apps do and routes the music properly.
     */
    static void selectBluetooth(Context c) {
        Intent i = new Intent("com.nwd.action.ACTION_APP_IN_OUT");
        i.putExtra("extra_app_id", 15);
        i.putExtra("extra_app_operation", 1);
        i.putExtra("extra_app_event", 0);
        c.sendBroadcast(i);
        // Picking the radio mutes Bluetooth audio (A2DP mute = amplifier mute flag 32) and only
        // the factory BT screen lifts it. Lift it after the source change has gone through.
        new android.os.Handler().postDelayed(
                () -> c.sendBroadcast(new Intent("com.bt.ACTION_A2DP_UNMUTE")), 500);
    }

    /** Asks the NWD kernel to route this audio source to the speakers. */
    static void requestSource(Context c, int sourceId) {
        Intent i = new Intent("com.nwd.action.ACTION_REQUEST_CHANGE_SOURCE");
        i.putExtra("extra_source_id", (byte) sourceId);
        c.sendBroadcast(i);
    }
}
