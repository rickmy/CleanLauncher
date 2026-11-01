package com.simple.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-user settings so the launcher can be shared: driver name, own car photo and which
 * reminders to speak. Also detects whether the head unit is an NWD one (factory radio,
 * Bluetooth and sound services available) so NWD-only features can be hidden elsewhere.
 */
final class Profile {

    private Profile() { }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("profile", Context.MODE_PRIVATE);
    }

    static String name(Context c) {
        return prefs(c).getString("name", "").trim();
    }

    static boolean voiceEnabled(Context c) {
        return prefs(c).getBoolean("voice", true);
    }

    static boolean picoPlacaEnabled(Context c) {
        return prefs(c).getBoolean("picoplaca", false);
    }

    static boolean isSetupDone(Context c) {
        return prefs(c).getBoolean("setup_done", false);
    }

    /** "Hola, Ana" or just "Hola". */
    static String hello(Context c) {
        String n = name(c);
        return n.isEmpty() ? "Hola" : "Hola, " + n;
    }

    // ---------- Car picture ----------

    static File carPhotoFile(Context c) {
        return new File(c.getFilesDir(), "car.png");
    }

    /** The user's own car photo, if they picked one. */
    static Bitmap carPhoto(Context c) {
        File f = carPhotoFile(c);
        return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    /**
     * Photo of this Almera. The Chevrolet pictures shipped with the NWD build are not used.
     * Lights-on art does not exist for this panel (0).
     */
    static int bundledCar(Context c, boolean lights) {
        if (lights) return 0;
        return c.getResources().getIdentifier("almera", "drawable", c.getPackageName());
    }

    // ---------- Band favorites (component names, in the order the driver added them) ----------

    static List<String> favorites(Context c) {
        String raw = prefs(c).getString("band_favorites", "");
        List<String> out = new ArrayList<>();
        if (raw.isEmpty()) return out;
        for (String s : raw.split("\n")) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    static boolean isFavorite(Context c, String component) {
        return favorites(c).contains(component);
    }

    static void toggleFavorite(Context c, String component) {
        List<String> cur = favorites(c);
        if (!cur.remove(component)) cur.add(component);
        StringBuilder sb = new StringBuilder();
        for (String s : cur) sb.append(s).append('\n');
        prefs(c).edit().putString("band_favorites", sb.toString()).apply();
    }

    // ---------- Device ----------

    private static Boolean nwd;

    /** NWD head unit: factory radio, Bluetooth module and sound services are installed. */
    static boolean isNwd(Context c) {
        if (nwd == null) {
            PackageManager pm = c.getPackageManager();
            nwd = installed(pm, "com.nwd.radio.service") && installed(pm, "com.bt.bc03")
                    && installed(pm, "com.nwd.setting.service");
        }
        return nwd;
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
