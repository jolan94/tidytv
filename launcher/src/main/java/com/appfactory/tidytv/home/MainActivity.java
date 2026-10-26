package com.appfactory.tidytv.home;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** TidyTV Home: wallpaper, clock, and one bar of apps and HDMI inputs. Plain Views, no libraries. */
public class MainActivity extends Activity {
    private static final int BLUE = 0xFF4C8DFF;
    private static final int APP = 0, HDMI = 1, ADD = 2;

    private float density;
    private PackageManager pm;
    private SharedPreferences prefs;

    private FrameLayout root, main;
    private ImageView wallpaper;
    private TextView clock, date;
    private Glyph net;
    private HorizontalScrollView scroll;
    private LinearLayout row;
    private View overlay;

    private final Map<String, ResolveInfo> apps = new LinkedHashMap<>();
    private final List<String> leanback = new ArrayList<>();
    private List<String> displayed = new ArrayList<>();
    private long revision;
    private long wallpaperStamp = Long.MIN_VALUE;

    private String movingPkg;
    private String focusKey;
    private boolean downOnTile, longFired;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        pm = getPackageManager();
        prefs = getSharedPreferences("dock", MODE_PRIVATE);
        buildUi();
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        register(timeReceiver, time);
        IntentFilter nf = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        nf.addAction(WifiManager.RSSI_CHANGED_ACTION);
        nf.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        register(netReceiver, nf);
        IntentFilter pf = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        register(pkgReceiver, pf);
        updateClock();
        updateNetwork();
        loadWallpaper();
        reload();
        // The window attaches after onResume and hands focus to the first view; take it back once it has.
        root.post(new Runnable() {
            @Override public void run() {
                if (overlay == null && movingPkg == null) restoreFocus();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(timeReceiver);
        unregisterReceiver(netReceiver);
        unregisterReceiver(pkgReceiver);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The Home key: back to a calm default state.
        movingPkg = null;
        if (overlay != null) closeOverlay(false);
        focusKey = null;
        reload();
        scroll.scrollTo(0, 0);
    }

    private void register(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(r, f);
    }

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };
    private final BroadcastReceiver netReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateNetwork(); }
    };
    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (movingPkg != null) dropMove();
            reload();
        }
    };

    // ---------------------------------------------------------------- UI

    private int dp(float v) { return Math.round(v * density); }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        main = new FrameLayout(this);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        main.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0x66000000}));
        main.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        // Clock and date, bottom-left, just above the bar.
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        clock = text(64, Typeface.create("sans-serif-thin", Typeface.NORMAL), 0xFFFFFFFF);
        clock.setIncludeFontPadding(false);
        date = text(20, Typeface.create("sans-serif", Typeface.NORMAL), 0xFFFFFFFF);
        info.addView(clock);
        info.addView(date);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        ip.leftMargin = dp(37);
        ip.bottomMargin = dp(25 + 76 + 14);
        main.addView(info, ip);

        // Network and settings, top-right.
        LinearLayout top = new LinearLayout(this);
        net = icon(Glyph.WIFI, "Network", new View.OnClickListener() {
            @Override public void onClick(View v) { openNetwork(); }
        });
        Glyph gear = icon(Glyph.GEAR, "Settings", new View.OnClickListener() {
            @Override public void onClick(View v) { open(new Intent(Settings.ACTION_SETTINGS)); }
        });
        top.setClipChildren(false);
        top.addView(net);
        top.addView(gear);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        tp.topMargin = dp(10);
        tp.rightMargin = dp(18);
        main.addView(top, tp);

        // The bar.
        LinearLayout bar = new LinearLayout(this);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(0xCC15181F);
        barBg.setCornerRadius(dp(16));
        bar.setBackground(barBg);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(8), 0, dp(8), 0);
        scroll.setFillViewport(true);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        scroll.addView(row, new FrameLayout.LayoutParams(-2, -1));
        bar.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, dp(76), Gravity.BOTTOM);
        bp.leftMargin = dp(18);
        bp.rightMargin = dp(18);
        bp.bottomMargin = dp(25);
        main.addView(bar, bp);
    }

    private TextView text(float sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
        return t;
    }

    private Glyph icon(int kind, String description, View.OnClickListener click) {
        Glyph g = new Glyph(this, kind);
        g.setContentDescription(description);
        g.setPadding(dp(14), dp(14), dp(14), dp(14));
        g.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        g.setFocusable(true);
        g.setOnClickListener(click);
        ringOnFocus(g, 1.12f, 24);
        return g;
    }

    private GradientDrawable ring(float radiusDp, int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(radiusDp));
        g.setColor(fill);
        g.setStroke(dp(3), BLUE);
        return g;
    }

    private void ringOnFocus(View v, final float scale, final float radiusDp) {
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean has) {
                view.setBackground(has ? ring(radiusDp, 0x334C8DFF) : null);
                float s = has ? scale : 1f;
                view.animate().scaleX(s).scaleY(s).setDuration(120).start();
                if (has && view instanceof Tile) {
                    focusKey = ((Tile) view).key();
                    centerInScroll(view);
                }
            }
        });
    }

    private void centerInScroll(View v) {
        if (v.getParent() != row) return;
        int target = v.getLeft() - (scroll.getWidth() - v.getWidth()) / 2;
        scroll.smoothScrollTo(Math.max(0, target), 0);
    }

    // ---------------------------------------------------------------- tiles

    private final class Tile extends LinearLayout {
        final int type;
        String pkg, inputId;
        ComponentName component;
        String label = "";

        Tile(int type) {
            super(MainActivity.this);
            this.type = type;
        }

        String key() { return type == APP ? pkg : type == HDMI ? "in:" + inputId : "add"; }

        void lift(boolean on) {
            setBackground(on ? ring(12, 0x554C8DFF) : (hasFocus() ? ring(12, 0x334C8DFF) : null));
            float s = on ? 1.15f : (hasFocus() ? 1.08f : 1f);
            animate().scaleX(s).scaleY(s).setDuration(120).start();
        }
    }

    private Tile makeTile(int type, View iconView, String label, int width) {
        Tile t = new Tile(type);
        t.label = label;
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setContentDescription(label);
        LinearLayout.LayoutParams ilp = iconView instanceof Glyph
                ? new LinearLayout.LayoutParams(dp(40), dp(38)) : new LinearLayout.LayoutParams(dp(72), dp(38));
        t.addView(iconView, ilp);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(15);
        tv.setTextColor(0xFFFFFFFF);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(6), dp(3), dp(6), 0);
        t.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, dp(66));
        lp.leftMargin = dp(2);
        lp.rightMargin = dp(2);
        t.setLayoutParams(lp);
        ringOnFocus(t, 1.08f, 12);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { activate((Tile) v); }
        });
        return t;
    }

    private void activate(Tile t) {
        if (movingPkg != null) return;
        if (t.type == ADD) {
            openPicker();
        } else if (t.type == HDMI) {
            Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(t.inputId));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            open(i);
        } else {
            Intent i = new Intent(Intent.ACTION_MAIN).setComponent(t.component);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            open(i);
        }
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show();
        }
    }

    private void openNetwork() {
        try {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception e) {
            open(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ---------------------------------------------------------------- data

    private File dir() { return ThemeProvider.dir(this); }

    private String readFile(String name) {
        try {
            File d = dir();
            if (d == null) return null;
            File f = new File(d, name);
            if (!f.isFile() || f.length() > 256 * 1024) return null;
            FileInputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[(int) f.length()];
                int n = 0, r;
                while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
                return new String(buf, 0, n, "UTF-8");
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void loadApps() {
        apps.clear();
        leanback.clear();
        String self = getPackageName();
        Intent lb = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(lb, 0)) {
            String p = ri.activityInfo.packageName;
            if (p.equals(self) || apps.containsKey(p)) continue;
            apps.put(p, ri);
            leanback.add(p);
        }
        Intent ln = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(ln, 0)) {
            String p = ri.activityInfo.packageName;
            if (p.equals(self) || apps.containsKey(p)) continue;
            apps.put(p, ri);
        }
    }

    private List<String> sortedByLabel(java.util.Collection<String> pkgs) {
        final Map<String, String> labels = new LinkedHashMap<>();
        for (String p : pkgs) labels.put(p, String.valueOf(apps.get(p).loadLabel(pm)).toLowerCase(Locale.ROOT));
        List<String> out = new ArrayList<>(pkgs);
        Collections.sort(out, new java.util.Comparator<String>() {
            @Override public int compare(String a, String b) { return labels.get(a).compareTo(labels.get(b)); }
        });
        return out;
    }

    private List<String> defaultApps() {
        List<String> def = DockOrder.defaultSelection(apps.keySet(), Collections.<String>emptyList());
        if (def.size() < DockOrder.MIN_PREFERRED) {
            // Phones and emulators have few leanback apps, so fall back to any launchable app.
            List<String> others = sortedByLabel(leanback.size() < DockOrder.MIN_PREFERRED ? apps.keySet() : leanback);
            def = DockOrder.defaultSelection(apps.keySet(), others);
        }
        return def;
    }

    private void reload() {
        loadApps();
        List<String> cfg = DockOrder.parseConfig(readFile("config.json"));
        revision = DockOrder.parseRevision(readFile("theme_revision"));
        boolean edited = prefs.getBoolean("edited", false);
        long applied = prefs.getLong("applied_rev", -1);
        if (DockOrder.shouldApplyConfig(!cfg.isEmpty(), edited, applied, revision)) {
            edited = false;
            prefs.edit().putBoolean("edited", false).putLong("applied_rev", revision)
                    .putString("order", DockOrder.join(cfg)).apply();
        }
        List<String> shown = DockOrder.resolve(
                edited ? DockOrder.split(prefs.getString("order", "")) : cfg, apps.keySet());
        if (shown.isEmpty() && !edited) shown = defaultApps();
        displayed = new ArrayList<>(shown);
        loadWallpaper();
        buildBar();
    }

    private void saveEdit() {
        prefs.edit().putBoolean("edited", true).putLong("applied_rev", revision)
                .putString("order", DockOrder.join(displayed)).apply();
    }

    private List<String[]> hdmiInputs() {
        List<String[]> out = new ArrayList<>();
        try {
            TvInputManager tm = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tm == null) return out;
            for (TvInputInfo info : tm.getTvInputList()) {
                if (info.isPassthroughInput()) out.add(new String[]{info.getId(), String.valueOf(info.loadLabel(this))});
            }
        } catch (Throwable ignored) {
            out.clear();
        }
        return out;
    }

    private Drawable iconFor(ResolveInfo ri) {
        Drawable d = null;
        try {
            d = ri.activityInfo.loadBanner(pm);
        } catch (Throwable ignored) {
        }
        return d != null ? d : ri.loadIcon(pm);
    }

    private void buildBar() {
        row.removeAllViews();
        List<String[]> inputs = hdmiInputs();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int count = displayed.size() + 1 + inputs.size();
        int avail = Math.max(dm.widthPixels, dm.heightPixels) - dp(36 + 16 + (inputs.isEmpty() ? 0 : 14));
        int w = Math.max(dp(118), Math.min(dp(160), avail / Math.max(1, count)));

        for (final String p : displayed) {
            ResolveInfo ri = apps.get(p);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageDrawable(iconFor(ri));
            Tile t = makeTile(APP, iv, String.valueOf(ri.loadLabel(pm)), w);
            t.pkg = p;
            t.component = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { openMenu((Tile) v); return true; }
            });
            row.addView(t);
        }
        row.addView(makeTile(ADD, new Glyph(this, Glyph.PLUS), "Add app", w));

        if (!inputs.isEmpty()) {
            View div = new View(this);
            div.setBackgroundColor(0x44FFFFFF);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(1), dp(44));
            dl.leftMargin = dp(6);
            dl.rightMargin = dp(6);
            row.addView(div, dl);
            for (String[] in : inputs) {
                Tile t = makeTile(HDMI, new Glyph(this, Glyph.HDMI), in[1], w);
                t.inputId = in[0];
                row.addView(t);
            }
        }
        if (overlay == null) restoreFocus();
    }

    private void restoreFocus() {
        Tile target = null;
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Tile) {
                if (target == null) target = (Tile) v;
                if (((Tile) v).key().equals(focusKey)) { target = (Tile) v; break; }
            }
        }
        if (target != null) target.requestFocus();
    }

    // ---------------------------------------------------------------- clock, network, wallpaper

    private void updateClock() {
        Date now = new Date();
        String pattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm";
        clock.setText(new java.text.SimpleDateFormat(pattern, Locale.getDefault()).format(now));
        String dp = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setText(new java.text.SimpleDateFormat(dp, Locale.getDefault()).format(now));
    }

    @SuppressWarnings("deprecation")
    private void updateNetwork() {
        int kind = Glyph.OFFLINE, level = 0;
        String desc = "Offline";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false, other = false;
            boolean known = false;
            if (Build.VERSION.SDK_INT >= 23) {
                Network n = cm.getActiveNetwork();
                NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    known = true;
                    wifi = nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    ethernet = nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                    other = !wifi && !ethernet;
                }
            }
            if (!known && Build.VERSION.SDK_INT < 23) {
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni != null && ni.isConnected()) {
                    wifi = ni.getType() == ConnectivityManager.TYPE_WIFI;
                    ethernet = ni.getType() == ConnectivityManager.TYPE_ETHERNET;
                    other = !wifi && !ethernet;
                }
            }
            if (!known && Build.VERSION.SDK_INT >= 23) {
                // Without the INTERNET permission Android hides the active network, so ask Wi-Fi and the interfaces directly.
                try {
                    WifiInfo wi = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getConnectionInfo();
                    wifi = wi != null && (wi.getNetworkId() != -1 || wi.getSupplicantState() == android.net.wifi.SupplicantState.COMPLETED);
                } catch (Throwable ignored) {
                }
                if (!wifi) ethernet = ethernetUp();
            }
            if (ethernet) {
                kind = Glyph.ETHERNET;
                desc = "Ethernet";
            } else if (wifi || other) {
                kind = Glyph.WIFI;
                level = 4;
                desc = "Connected";
                if (wifi) {
                    try {
                        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        WifiInfo wi = wm.getConnectionInfo();
                        if (wi != null && wi.getRssi() > -127) level = WifiManager.calculateSignalLevel(wi.getRssi(), 5);
                    } catch (Throwable ignored) {
                    }
                    desc = "Wi-Fi, signal " + level + " of 4";
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("TidyHome", "network state", t);
        }
        net.set(kind, level);
        net.setContentDescription("Network: " + desc);
    }

    private static boolean ethernetUp() {
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> e = java.net.NetworkInterface.getNetworkInterfaces();
                 e != null && e.hasMoreElements(); ) {
                java.net.NetworkInterface ni = e.nextElement();
                if (ni.getName().startsWith("eth") && ni.isUp() && !ni.isLoopback() && ni.getInetAddresses().hasMoreElements()) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void loadWallpaper() {
        File d = dir();
        File f = d == null ? null : new File(d, "wallpaper.jpg");
        long stamp = f != null && f.isFile() ? f.lastModified() * 31 + f.length() : 0;
        if (stamp == wallpaperStamp) return;
        wallpaperStamp = stamp;
        Bitmap b = null;
        if (stamp != 0) b = decode(f, 0);
        if (b == null) {
            int id = getResources().getIdentifier("wallpaper", "raw", getPackageName());
            if (id != 0) b = decode(null, id);
        }
        if (b != null) {
            wallpaper.setBackground(null);
            wallpaper.setImageBitmap(b);
        } else {
            wallpaper.setImageDrawable(null);
            wallpaper.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF6E2D55, 0xFFFF6A3C, 0xFFFFB347, 0xFF3A2440}));
        }
    }

    private Bitmap decode(File file, int rawId) {
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = file != null ? new FileInputStream(file) : getResources().openRawResource(rawId);
            try { BitmapFactory.decodeStream(in, null, o); } finally { in.close(); }
            int s = 1;
            while (o.outWidth / (s * 2) >= sw && o.outHeight / (s * 2) >= sh) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            in = file != null ? new FileInputStream(file) : getResources().openRawResource(rawId);
            try { return BitmapFactory.decodeStream(in, null, o); } finally { in.close(); }
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- keys

    private Tile focusedTile() {
        View f = getCurrentFocus();
        return f instanceof Tile ? (Tile) f : null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode(), a = e.getAction();
        if (k == KeyEvent.KEYCODE_BACK) {
            if (a == KeyEvent.ACTION_UP) {
                if (overlay != null) closeOverlay(true);
                else if (movingPkg != null) dropMove();
            }
            return true; // Back does nothing on the home screen.
        }
        if (overlay != null) return super.dispatchKeyEvent(e);

        if (getCurrentFocus() == null && a == KeyEvent.ACTION_DOWN && k >= KeyEvent.KEYCODE_DPAD_UP && k <= KeyEvent.KEYCODE_DPAD_CENTER) {
            restoreFocus(); // leaving touch mode: start on the bar, not wherever the search lands
            return true;
        }
        if (movingPkg != null) {
            if (a == KeyEvent.ACTION_DOWN) {
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) moveBy(-1);
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) moveBy(1);
            } else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                dropMove();
            }
            return k >= KeyEvent.KEYCODE_DPAD_UP && k <= KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                    ? true : super.dispatchKeyEvent(e);
        }

        Tile t = focusedTile();
        if (a == KeyEvent.ACTION_DOWN && getCurrentFocus() != null && (k == KeyEvent.KEYCODE_DPAD_LEFT
                || k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN)) {
            // Stay inside the row at its ends, and inside the icons at theirs, instead of jumping across the screen.
            int dir = k == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : k == KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT
                    : k == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN;
            View next = getCurrentFocus().focusSearch(dir);
            boolean inRow = t != null;
            if (next == null || (inRow && dir != View.FOCUS_UP && next.getParent() != row)
                    || (!inRow && (next instanceof Tile ? dir != View.FOCUS_DOWN : next.getParent() != getCurrentFocus().getParent()))) {
                return true;
            }
        }
        if (t != null && k == KeyEvent.KEYCODE_MENU) {
            if (a == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && t.type == APP) openMenu(t);
            return true;
        }
        if (t != null && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
            if (a == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) {
                    downOnTile = true;
                    longFired = false;
                } else if (downOnTile && !longFired) {
                    longFired = true;
                    if (t.type == APP) openMenu(t);
                }
            } else if (a == KeyEvent.ACTION_UP) {
                boolean fire = downOnTile && !longFired;
                downOnTile = false;
                if (fire) activate(t);
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------------------------------------------------------------- move, menu, picker

    private void startMove(Tile t) {
        movingPkg = t.pkg;
        t.requestFocus();
        t.lift(true);
        Toast.makeText(this, "Left and right to move, OK to drop", Toast.LENGTH_SHORT).show();
    }

    private Tile tileFor(String pkg) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Tile && pkg.equals(((Tile) v).pkg)) return (Tile) v;
        }
        return null;
    }

    private void moveBy(int delta) {
        int from = displayed.indexOf(movingPkg);
        int to = DockOrder.move(displayed, from, delta);
        if (to == from) return;
        Tile t = tileFor(movingPkg);
        row.removeView(t);
        row.addView(t, to);
        t.requestFocus();
        final Tile moved = t;
        t.post(new Runnable() { @Override public void run() { centerInScroll(moved); } });
    }

    private void dropMove() {
        Tile t = movingPkg == null ? null : tileFor(movingPkg);
        movingPkg = null;
        if (t != null) t.lift(false);
        saveEdit();
    }

    private void showOverlay(View v) {
        if (overlay != null) root.removeView(overlay);
        overlay = v;
        root.addView(v, new FrameLayout.LayoutParams(-1, -1));
        main.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    private void closeOverlay(boolean restore) {
        if (overlay == null) return;
        root.removeView(overlay);
        overlay = null;
        main.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (restore) restoreFocus();
    }

    private TextView menuItem(String label, final Runnable action) {
        final TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(18);
        t.setTextColor(0xFFFFFFFF);
        t.setPadding(dp(18), dp(12), dp(18), dp(12));
        t.setFocusable(true);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                v.setBackground(has ? ring(10, 0x554C8DFF) : null);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(2);
        t.setLayoutParams(lp);
        return t;
    }

    private void openMenu(final Tile t) {
        FrameLayout scrimView = new FrameLayout(this);
        scrimView.setBackgroundColor(0xAA000000);
        scrimView.setClickable(true);
        scrimView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeOverlay(true); }
        });
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF01E2128);
        bg.setCornerRadius(dp(16));
        card.setBackground(bg);
        card.setClickable(true);
        TextView title = new TextView(this);
        title.setText(t.label);
        title.setTextSize(20);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(dp(18), 0, dp(18), dp(10));
        title.setSingleLine(true);
        card.addView(title);

        final String pkg = t.pkg;
        TextView first = menuItem("Move", new Runnable() {
            @Override public void run() { closeOverlay(true); startMove(t); }
        });
        card.addView(first);
        card.addView(menuItem("Remove from home", new Runnable() {
            @Override public void run() {
                int idx = displayed.indexOf(pkg);
                DockOrder.remove(displayed, pkg);
                saveEdit();
                closeOverlay(false);
                focusKey = idx < displayed.size() ? displayed.get(idx) : idx > 0 ? displayed.get(idx - 1) : "add";
                buildBar();
            }
        }));
        card.addView(menuItem("App info", new Runnable() {
            @Override public void run() {
                closeOverlay(true);
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
            }
        }));
        ResolveInfo ri = apps.get(pkg);
        if (ri != null && (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            card.addView(menuItem("Uninstall", new Runnable() {
                @Override public void run() {
                    closeOverlay(true);
                    open(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
                }
            }));
        }
        scrimView.addView(card, new FrameLayout.LayoutParams(dp(320), -2, Gravity.CENTER));
        showOverlay(scrimView);
        first.requestFocus();
    }

    private void openPicker() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(0xFC101216);
        page.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(28, Typeface.create("sans-serif-light", Typeface.NORMAL), 0xFFFFFFFF);
        title.setText("Add app");
        title.setPadding(0, 0, 0, dp(12));
        col.addView(title);

        List<String> candidates = new ArrayList<>();
        for (String p : apps.keySet()) if (!displayed.contains(p)) candidates.add(p);
        candidates = sortedByLabel(candidates);

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        View firstRow = null;
        for (final String p : candidates) {
            ResolveInfo ri = apps.get(p);
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(12), dp(6), dp(12), dp(6));
            r.setFocusable(true);
            String label = String.valueOf(ri.loadLabel(pm));
            r.setContentDescription(label);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(iconFor(ri));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            r.addView(iv, new LinearLayout.LayoutParams(dp(64), dp(36)));
            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextSize(20);
            tv.setTextColor(0xFFFFFFFF);
            tv.setSingleLine(true);
            tv.setPadding(dp(16), 0, 0, 0);
            r.addView(tv);
            r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean has) {
                    v.setBackground(has ? ring(12, 0x554C8DFF) : null);
                }
            });
            r.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    DockOrder.add(displayed, p);
                    saveEdit();
                    closeOverlay(false);
                    focusKey = p;
                    buildBar();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(3);
            list.addView(r, lp);
            if (firstRow == null) firstRow = r;
        }
        if (candidates.isEmpty()) {
            TextView none = text(18, Typeface.DEFAULT, 0xCCFFFFFF);
            none.setText("Every app is already on the home screen");
            none.setFocusable(true);
            none.setPadding(dp(12), dp(12), dp(12), dp(12));
            list.addView(none);
            firstRow = none;
        }
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(520), -1, Gravity.CENTER_HORIZONTAL);
        cp.topMargin = dp(36);
        cp.bottomMargin = dp(24);
        page.addView(col, cp);
        showOverlay(page);
        firstRow.requestFocus();
    }
}
