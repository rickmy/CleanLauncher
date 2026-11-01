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
     * On this MediaTek unit the NWD headlight bit never arrives, so automatic stays dark
     * all day (Quito sun washes out the old blue gradient). The NWD path still follows
     * the ILL wire and the clock.
     */
    static boolean isDark(Context c) {
        String m = themeMode(c);
        if (THEME_DARK.equals(m)) return true;
        if (THEME_LIGHT.equals(m) && Profile.isNwd(c)) return false;
        if (!Profile.isNwd(c)) return true;
        int ill = Settings.System.getInt(c.getContentResolver(), "mcu_no_source_device_state", 0);
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return (ill & 2) != 0 || h >= 19 || h < 6;
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
        if (isDark(c)) {
            // Black all day. The halo sits lower at night; it is not the blue daytime wash.
            boolean night = nightHours();
            GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF0C0C0E, 0xFF070708, 0xFF000000});
            GradientDrawable glow = new GradientDrawable();
            glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            glow.setGradientRadius(dp(c, night ? 240 : 420));
            glow.setGradientCenter(0.5f, 1.05f);
            glow.setColors(new int[]{night ? 0x10141820 : 0x24303842, 0x00000000});
            return new LayerDrawable(new Drawable[]{base, glow});
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
        boolean dark = isDark(c);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                dark ? new int[]{0xFF17171E, 0xFF101015} : new int[]{0x4D5B6CFF, 0x333B3FB8});
        bg.setCornerRadius(dp(c, 18));
        bg.setStroke(dp(c, 1), dark ? 0x14FFFFFF : 0x337F9CFF);
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
        b.setBackground(circle(filled ? Color.WHITE : 0x26FFFFFF));
        b.setColorFilter(filled ? 0xFF2A1F6E : Color.WHITE);
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

    static void openEqualizer(Context c) {
        if (Profile.isNwd(c)) {
            c.startActivity(new Intent(c, EqActivity.class));
            return;
        }
        Intent eq = new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        if (!safeStart(c, eq) && !launchByKeyword(c, "ecualiz", "equaliz", "audio", "sonido", "dsp")) {
            Toast.makeText(c, "No se encontró un ecualizador", Toast.LENGTH_SHORT).show();
        }
    }

    static boolean notificationAccess(Context c) {
        String listeners = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return listeners != null && listeners.contains(c.getPackageName());
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
