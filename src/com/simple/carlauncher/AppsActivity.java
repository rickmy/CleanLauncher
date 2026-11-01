package com.simple.carlauncher;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppsActivity extends Activity {

    private final List<ResolveInfo> apps = new ArrayList<>();
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.background(this));
        Ui.padBelowSystemBar(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(Ui.ink(this));
        back.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Ui.lightSurface(this) ? 0x14000000 : 0x1FFFFFFF);
        back.setBackground(circle);
        MainActivity.pressable(back);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Aplicaciones");
        title.setTextSize(26);
        title.setTextColor(Ui.ink(this));
        title.setPadding(dp(16), 0, 0, 0);
        titles.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Mantén pulsada una app para ponerla en el inicio");
        hint.setTextSize(13);
        hint.setTextColor(Ui.muted(this));
        hint.setPadding(dp(16), dp(2), 0, 0);
        titles.addView(hint);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        if (Profile.isNwd(this)) {
            TextView theme = new TextView(this);
            theme.setTextSize(16);
            theme.setTextColor(Color.WHITE);
            theme.setMinHeight(dp(56));
            theme.setGravity(Gravity.CENTER);
            theme.setPadding(dp(18), dp(10), dp(18), dp(10));
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(0x33FFFFFF);
            tbg.setCornerRadius(dp(22));
            theme.setBackground(tbg);
            theme.setText(themeLabel());
            MainActivity.pressable(theme);
            theme.setOnClickListener(v -> {
                String m = Ui.themeMode(this);
                Ui.setThemeMode(this, Ui.THEME_AUTO.equals(m) ? Ui.THEME_LIGHT
                        : Ui.THEME_LIGHT.equals(m) ? Ui.THEME_DARK : Ui.THEME_AUTO);
                recreate();
            });
            header.addView(theme);
        }
        TextView settings = new TextView(this);
        settings.setText("Ajustes");
        settings.setTextSize(16);
        settings.setTextColor(Ui.ink(this));
        settings.setMinHeight(dp(56));
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(dp(18), dp(10), dp(18), dp(10));
        settings.setBackground(Ui.card(this));
        MainActivity.pressable(settings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.leftMargin = dp(8);
        header.addView(settings, slp);
        root.addView(header);

        GridView grid = new GridView(this);
        int widthDp = getResources().getConfiguration().screenWidthDp;
        grid.setNumColumns(widthDp >= 600 ? 5 : 4);
        grid.setVerticalSpacing(dp(12));
        grid.setHorizontalSpacing(dp(8));
        grid.setPadding(0, dp(12), 0, dp(12));
        grid.setClipToPadding(false);
        grid.setDrawSelectorOnTop(true);
        StateListDrawable selector = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(0x40FFFFFF);
        pressed.setCornerRadius(dp(18));
        selector.addState(new int[]{android.R.attr.state_pressed}, pressed);
        selector.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        grid.setSelector(selector);
        adapter = new BaseAdapter() {
            public int getCount() { return apps.size(); }
            public Object getItem(int p) { return apps.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View convert, ViewGroup parent) {
                return buildItem(apps.get(p), convert);
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> launch(apps.get(pos)));
        grid.setOnItemLongClickListener((parent, view, pos, id) -> {
            showSheet(apps.get(pos));
            return true;
        });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        root.requestApplyInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        apps.clear();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (!ri.activityInfo.packageName.equals(getPackageName())) apps.add(ri);
        }
        Collator collator = Collator.getInstance();
        Collections.sort(apps, (a, b) -> collator.compare(
                a.loadLabel(pm).toString(), b.loadLabel(pm).toString()));
        adapter.notifyDataSetChanged();
    }

    private void launch(ResolveInfo ri) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(i);
    }

    private void showSheet(ResolveInfo ri) {
        PackageManager pm = getPackageManager();
        String component = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name).flattenToString();
        boolean fav = Profile.isFavorite(this, component);
        CharSequence name = ri.loadLabel(pm);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(16));
        box.setBackground(Ui.card(this));

        TextView heading = Ui.text(this, name, 22, Ui.ink(this));
        heading.setGravity(Gravity.START);
        heading.setPadding(0, 0, 0, dp(14));
        box.addView(heading);
        box.addView(sheetButton("Abrir", () -> {
            dialog.dismiss();
            launch(ri);
        }));
        box.addView(sheetButton(fav ? "Quitar del inicio" : "Poner en el inicio", () -> {
            Profile.toggleFavorite(this, component);
            dialog.dismiss();
        }));
        box.addView(sheetButton("Cerrar", dialog::dismiss));
        dialog.setContentView(box);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private TextView sheetButton(String label, Runnable action) {
        TextView b = Ui.text(this, label, 18, Ui.ink(this));
        b.setMinHeight(dp(56));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.lightSurface(this) ? 0xFFE6E8EC : 0x33FFFFFF);
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        Ui.pressable(b, 0.96f);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private View buildItem(ResolveInfo ri, View convert) {
        LinearLayout item;
        ImageView icon;
        TextView label;
        if (convert == null) {
            item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setMinimumHeight(dp(112));
            item.setPadding(dp(8), dp(14), dp(8), dp(14));
            icon = new ImageView(this);
            item.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
            label = new TextView(this);
            label.setTextSize(15);
            label.setTextColor(Ui.ink(this));
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(0, dp(8), 0, 0);
            item.addView(label, new LinearLayout.LayoutParams(-1, -2));
        } else {
            item = (LinearLayout) convert;
            icon = (ImageView) item.getChildAt(0);
            label = (TextView) item.getChildAt(1);
        }
        PackageManager pm = getPackageManager();
        icon.setImageDrawable(ri.loadIcon(pm));
        label.setText(ri.loadLabel(pm));
        return item;
    }

    private String themeLabel() {
        String m = Ui.themeMode(this);
        return Ui.THEME_DARK.equals(m) ? "Tema: Oscuro" : Ui.THEME_LIGHT.equals(m) ? "Tema: Claro" : "Tema: Automático";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
