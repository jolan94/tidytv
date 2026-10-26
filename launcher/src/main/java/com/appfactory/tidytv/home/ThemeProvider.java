package com.appfactory.tidytv.home;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Lets the adb shell write the three theme files where only this app can reach on Android 14:
 * content write --uri content://com.appfactory.tidytv.home.theme/config.json &lt; file
 */
public class ThemeProvider extends ContentProvider {

    static boolean allowed(String name) {
        return "config.json".equals(name) || "wallpaper.jpg".equals(name) || "theme_revision".equals(name);
    }

    static File dir(android.content.Context c) {
        File d = c.getExternalFilesDir(null);
        if (d != null && !d.isDirectory()) d.mkdirs();
        return d;
    }

    private File file(Uri uri) throws FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 1) throw new FileNotFoundException(String.valueOf(uri));
        String name = seg.get(0);
        File dir = getContext() == null ? null : dir(getContext());
        if (dir == null || !allowed(name)) throw new FileNotFoundException(String.valueOf(uri));
        return new File(dir, name);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(uri);
        switch (mode) {
            case "r":
                return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            case "w":
            case "wt":
                return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY
                        | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
            default:
                throw new FileNotFoundException("mode " + mode);
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        try {
            return file(uri).delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
