package com.appfactory.tidytv.home;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Which apps sit on the bar and in what order. Pure Java so it can be unit tested without Android. */
public final class DockOrder {
    private DockOrder() {}

    /** Shown first, in this order, when nothing has been configured. */
    public static final String[] PREFERRED = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.google.android.youtube.tvkids",
            "com.amazon.amazonvideo.livingroom",
            "in.startv.hotstar",
            "com.disney.disneyplus",
            "com.spotify.tv.android",
            "org.xbmc.kodi",
            "com.plexapp.android",
    };
    public static final int MIN_PREFERRED = 3;
    public static final int FILL_TO = 8;

    /** Reads {"dock_apps": ["a","b"]}; anything malformed gives an empty list. Duplicates are dropped. */
    public static List<String> parseConfig(String json) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (json == null) return new ArrayList<>();
        int key = json.indexOf("\"dock_apps\"");
        if (key < 0) return new ArrayList<>();
        int i = json.indexOf('[', key);
        if (i < 0) return new ArrayList<>();
        int n = json.length();
        for (i++; i < n; i++) {
            char c = json.charAt(i);
            if (c == ']') break;
            if (c != '"') continue;
            StringBuilder sb = new StringBuilder();
            for (i++; i < n && (c = json.charAt(i)) != '"'; i++) {
                if (c == '\\' && i + 1 < n) {
                    char e = json.charAt(++i);
                    if (e == 'u' && i + 4 < n) {
                        try {
                            sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException ex) {
                            sb.append(e);
                        }
                    } else if (e == 'n' || e == 't' || e == 'r') {
                        sb.append(' ');
                    } else {
                        sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            String s = sb.toString().trim();
            if (!s.isEmpty()) out.add(s);
        }
        return new ArrayList<>(out);
    }

    /** theme_revision file contents as a number; missing or unreadable is 0. */
    public static long parseRevision(String text) {
        if (text == null) return 0;
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The wanted packages that are installed, in order, without duplicates. */
    public static List<String> resolve(List<String> wanted, Collection<String> installed) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (wanted != null) for (String p : wanted) if (p != null && installed.contains(p)) out.add(p);
        return new ArrayList<>(out);
    }

    /**
     * Installed preferred apps first; if fewer than {@link #MIN_PREFERRED} are installed, fill up to
     * {@link #FILL_TO} from {@code others} (already sorted, e.g. alphabetically by label).
     */
    public static List<String> defaultSelection(Collection<String> installed, List<String> others) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String p : PREFERRED) if (installed.contains(p)) out.add(p);
        if (out.size() < MIN_PREFERRED && others != null) {
            for (String p : others) {
                if (out.size() >= FILL_TO) break;
                if (installed.contains(p)) out.add(p);
            }
        }
        return new ArrayList<>(out);
    }

    /** Whether config.json should replace the saved order: only if there is one, and either no edit yet or a new revision. */
    public static boolean shouldApplyConfig(boolean hasConfig, boolean userEdited, long appliedRevision, long currentRevision) {
        return hasConfig && (!userEdited || appliedRevision != currentRevision);
    }

    /** Moves the item at index by delta (clamped to the list); returns its new index. */
    public static int move(List<String> list, int index, int delta) {
        if (index < 0 || index >= list.size()) return index;
        int to = Math.max(0, Math.min(list.size() - 1, index + delta));
        if (to == index) return index;
        list.add(to, list.remove(index));
        return to;
    }

    public static boolean remove(List<String> list, String pkg) {
        return list.remove(pkg);
    }

    /** Appends pkg unless it is already there. */
    public static boolean add(List<String> list, String pkg) {
        if (pkg == null || pkg.isEmpty() || list.contains(pkg)) return false;
        list.add(pkg);
        return true;
    }

    public static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(s);
        }
        return sb.toString();
    }

    public static List<String> split(String joined) {
        List<String> out = new ArrayList<>();
        if (joined == null) return out;
        for (String s : joined.split("\n")) if (!s.isEmpty() && !out.contains(s)) out.add(s);
        return out;
    }
}
