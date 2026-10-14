package ru.vorontsov.reader;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MainActivity extends Activity {
    static final String HOST = "reader.local";
    static final String BUNDLED_NAME = "Нереальная реальность";

    WebView web;
    FrameLayout root;
    SharedPreferences sp;
    volatile Book book;
    volatile int gen = 0;
    boolean volKeys = false;
    int crashes = 0;

    static class Book {
        String format, key, name;
        ZipFile zip;
        Map<String, String> lower = new HashMap<>();
        File fb2File;
        byte[] fb2Bytes;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (android.os.Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                if (web == null) { finish(); return; }
                web.evaluateJavascript("window.handleBack?handleBack():false", v -> { if (!"true".equals(v)) finish(); });
            });
        sp = getSharedPreferences("mod_reader", MODE_PRIVATE);
        requestHighRefresh();
        root = new FrameLayout(this);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            v.setPadding(ins.getSystemWindowInsetLeft(), ins.getSystemWindowInsetTop(),
                    ins.getSystemWindowInsetRight(), ins.getSystemWindowInsetBottom());
            return ins.consumeSystemWindowInsets();
        });
        setupWeb();
        applySettings();
        if (!handleIntent(getIntent())) openLast();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    void setupWeb() {
        if (BuildDebug.ON) WebView.setWebContentsDebuggingEnabled(true);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        if (Build.VERSION.SDK_INT >= 35) {
            try { web.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH); } catch (Throwable ignored) {}
        }
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setOffscreenPreRaster(true);
        web.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return serve(req.getUrl());
            }
            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                android.util.Log.w("Chitalka", "renderer gone, crash=" + detail.didCrash());
                root.removeView(view);
                view.destroy();
                setupWeb();
                applySettings();
                if (++crashes > 3) { Toast.makeText(MainActivity.this, "Движок WebView падает — обновите Android System WebView", Toast.LENGTH_LONG).show(); return true; }
                if (book != null) web.loadUrl("https://" + HOST + "/app/index.html");
                return true;
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                openExternal(u.toString());
                return true;
            }
        });
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.i("Chitalka", m.message() + " @" + m.sourceId() + ":" + m.lineNumber());
                return true;
            }
        });
    }

    boolean handleIntent(Intent it) {
        if (it == null) return false;
        if (BuildDebug.ON && it.getStringExtra("debugUrl") != null) { web.loadUrl(it.getStringExtra("debugUrl")); return true; }
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(it.getAction())) u = it.getData();
        else if (Intent.ACTION_SEND.equals(it.getAction())) u = it.getParcelableExtra(Intent.EXTRA_STREAM);
        if (u == null) return false;
        it.setAction(Intent.ACTION_MAIN);
        it.setData(null);
        openUri(u);
        return true;
    }

    void requestHighRefresh() {
        try {
            Display d = getWindowManager().getDefaultDisplay();
            Display.Mode cur = d.getMode(), best = cur;
            for (Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()
                        && m.getRefreshRate() > best.getRefreshRate()) best = m;
            }
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.preferredDisplayModeId = best.getModeId();
            lp.preferredRefreshRate = best.getRefreshRate();
            getWindow().setAttributes(lp);
        } catch (Throwable ignored) {}
    }

    // ---------- settings / theme ----------
    JSONObject settings() {
        try { return new JSONObject(sp.getString("settings", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    String defaultTheme() {
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES ? "dark" : "light";
    }

    void applySettings() {
        JSONObject o = settings();
        String theme = o.optString("theme", defaultTheme());
        int zoom = o.optInt("zoom", 100);
        volKeys = o.optBoolean("vol", false);
        web.getSettings().setTextZoom(zoom);
        int bg; boolean light;
        switch (theme) {
            case "dark": bg = 0xFF121212; light = false; break;
            case "sepia": bg = 0xFFF4ECD8; light = true; break;
            default: bg = 0xFFFFFFFF; light = true;
        }
        root.setBackgroundColor(bg);
        web.setBackgroundColor(bg);
        Window w = getWindow();
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int f = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(light ? f : 0, f);
            }
        } else {
            int f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            View dv = w.getDecorView();
            int cur = dv.getSystemUiVisibility();
            dv.setSystemUiVisibility(light ? (cur | f) : (cur & ~f));
        }
    }

    // ---------- books ----------
    File booksDir() { File d = new File(getFilesDir(), "mod_reader_books"); d.mkdirs(); return d; }

    void openLast() {
        String cur = sp.getString("cur", "bundled");
        if (!"bundled".equals(cur) && new File(booksDir(), cur).exists()) openKey(cur);
        else openBundled();
    }

    void openBundled() {
        new Thread(() -> {
            try {
                File f = new File(getFilesDir(), "mod_reader_bundled.epub");
                long ver = getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
                if (!f.exists() || sp.getLong("bundledVer", 0) != ver) {
                    try (InputStream in = getAssets().open("reader/kniga.epub")) { copy(in, f); }
                    sp.edit().putLong("bundledVer", ver).apply();
                }
                setBook(f, "bundled", BUNDLED_NAME);
            } catch (Exception e) { fail(e); }
        }).start();
    }

    void openKey(String key) {
        new Thread(() -> {
            try {
                String name = key;
                JSONArray r = recent();
                for (int i = 0; i < r.length(); i++) if (key.equals(r.getJSONObject(i).optString("key"))) name = r.getJSONObject(i).optString("name", key);
                setBook(new File(booksDir(), key), key, name);
            } catch (Exception e) { fail(e); }
        }).start();
    }

    void openUri(Uri uri) {
        new Thread(() -> {
            try {
                String name = displayName(uri);
                File tmp = new File(getCacheDir(), "incoming.tmp");
                try (InputStream in = getContentResolver().openInputStream(uri)) { copy(in, tmp); }
                String key = sha1(tmp);
                File dst = new File(booksDir(), key);
                if (!dst.exists() && !tmp.renameTo(dst)) { try (InputStream in = new FileInputStream(tmp)) { copy(in, dst); } }
                tmp.delete();
                addRecent(key, name);
                setBook(dst, key, name);
            } catch (Exception e) { fail(e); }
        }).start();
    }

    synchronized void setBook(File f, String key, String name) throws Exception {
        Book b = new Book();
        b.key = key; b.name = name;
        byte[] head = new byte[4];
        try (InputStream in = new FileInputStream(f)) { in.read(head); }
        if (head[0] == 'P' && head[1] == 'K') {
            b.zip = new ZipFile(f);
            Enumeration<? extends ZipEntry> en = b.zip.entries();
            String fb2 = null;
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                b.lower.put(n.toLowerCase(Locale.ROOT), n);
                if (n.toLowerCase(Locale.ROOT).endsWith(".fb2")) fb2 = n;
            }
            if (b.zip.getEntry("META-INF/container.xml") != null || b.lower.containsKey("meta-inf/container.xml")) {
                b.format = "epub";
            } else if (fb2 != null) {
                b.format = "fb2";
                try (InputStream in = b.zip.getInputStream(b.zip.getEntry(fb2))) { b.fb2Bytes = readAll(in); }
                b.zip.close(); b.zip = null;
            } else throw new Exception("Это не EPUB и не FB2");
        } else {
            b.format = "fb2";
            b.fb2File = f;
        }
        Book old = book;
        book = b;
        gen++;
        if (old != null && old.zip != null) try { old.zip.close(); } catch (Exception ignored) {}
        sp.edit().putString("cur", key).apply();
        runOnUiThread(() -> web.loadUrl("https://" + HOST + "/app/index.html"));
    }

    void fail(Exception e) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Не удалось открыть книгу: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (book == null) openBundled();
        });
    }

    JSONArray recent() {
        try { return new JSONArray(sp.getString("recent", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    synchronized void addRecent(String key, String name) throws Exception {
        JSONArray r = recent(), out = new JSONArray();
        JSONObject me = new JSONObject().put("key", key).put("name", name);
        for (int i = 0; i < r.length(); i++) {
            JSONObject o = r.getJSONObject(i);
            if (key.equals(o.optString("key"))) { me = o; me.put("name", name); }
        }
        out.put(me);
        for (int i = 0; i < r.length(); i++) {
            JSONObject o = r.getJSONObject(i);
            if (key.equals(o.optString("key"))) continue;
            if (out.length() >= 10) { new File(booksDir(), o.optString("key")).delete(); continue; }
            out.put(o);
        }
        sp.edit().putString("recent", out.toString()).apply();
    }

    String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) { String n = c.getString(0); if (n != null) return n; }
        } catch (Exception ignored) {}
        String p = u.getLastPathSegment();
        return p == null ? "Книга" : p;
    }

    // ---------- serving ----------
    WebResourceResponse serve(Uri u) {
        if (!HOST.equals(u.getHost())) return null;
        String path = u.getPath();
        if (BuildDebug.ON) android.util.Log.i("Chitalka", "serve " + path);
        if (path == null) return null;
        try {
            if (path.startsWith("/app/")) {
                String a = path.substring(5);
                return ok(mime(a), getAssets().open("reader/app/" + a));
            }
            if (path.startsWith("/book/")) {
                String rest = path.substring(6);
                int slash = rest.indexOf('/');
                rest = slash >= 0 ? rest.substring(slash + 1) : rest; // strip generation
                Book b = book;
                if (b == null) return notFound();
                if (b.zip != null) {
                    ZipEntry e = b.zip.getEntry(rest);
                    if (e == null) { String real = b.lower.get(rest.toLowerCase(Locale.ROOT)); if (real != null) e = b.zip.getEntry(real); }
                    if (e == null) return notFound();
                    return ok(mime(rest), b.zip.getInputStream(e));
                }
                if (rest.equals("book.fb2")) {
                    InputStream in = b.fb2Bytes != null ? new ByteArrayInputStream(b.fb2Bytes) : new FileInputStream(b.fb2File);
                    return ok("application/octet-stream", in);
                }
            }
        } catch (Exception e) { return notFound(); }
        return notFound();
    }

    WebResourceResponse ok(String mime, InputStream in) {
        Map<String, String> h = new HashMap<>();
        h.put("Cache-Control", "no-store");
        h.put("Access-Control-Allow-Origin", "*");
        return new WebResourceResponse(mime, mime.startsWith("text/") ? "utf-8" : null, 200, "OK", h, in);
    }

    WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
    }

    static String mime(String p) {
        String n = p.toLowerCase(Locale.ROOT);
        int q = n.lastIndexOf('.');
        String ext = q >= 0 ? n.substring(q + 1) : "";
        switch (ext) {
            case "html": case "htm": return "text/html";
            case "xhtml": case "xml": case "opf": case "ncx": return "application/xml";
            case "css": return "text/css";
            case "js": return "application/javascript";
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "svg": return "image/svg+xml";
            case "ttf": return "font/ttf";
            case "otf": return "font/otf";
            case "woff": return "font/woff";
            case "woff2": return "font/woff2";
            default: return "application/octet-stream";
        }
    }

    // ---------- utils ----------
    static void copy(InputStream in, File f) throws Exception {
        try (OutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536]; int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    static String sha1(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        byte[] d = md.digest();
        for (int i = 0; i < 10; i++) sb.append(String.format("%02x", d[i]));
        return sb.toString();
    }

    void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "Нет приложения для ссылки", Toast.LENGTH_SHORT).show(); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 1 && res == RESULT_OK && data != null && data.getData() != null) openUri(data.getData());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.evaluateJavascript("window.saveNow&&saveNow()", null);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.handleBack?handleBack():false", v -> {
            if (!"true".equals(v)) MainActivity.super.onBackPressed();
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (volKeys && (k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_UP)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN)
                web.evaluateJavascript("window.page&&page(" + (k == KeyEvent.KEYCODE_VOLUME_DOWN ? 1 : -1) + ")", null);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (!settings().has("theme")) applySettings();
    }

    // ---------- JS bridge ----------
    class Bridge {
        @JavascriptInterface public String getMeta() {
            Book b = book;
            try {
                JSONObject o = new JSONObject();
                if (b != null) o.put("format", b.format).put("key", b.key).put("name", b.name);
                o.put("gen", gen).put("bundled", b != null && "bundled".equals(b.key));
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }
        @JavascriptInterface public String getSettings() {
            JSONObject o = settings();
            try { if (!o.has("theme")) o.put("autoTheme", defaultTheme()); } catch (Exception ignored) {}
            return o.toString();
        }
        @JavascriptInterface public void setSettings(String json) {
            sp.edit().putString("settings", json).apply();
            runOnUiThread(MainActivity.this::applySettings);
        }
        @JavascriptInterface public String getPos(String key) { return sp.getString("pos_" + key, ""); }
        @JavascriptInterface public void savePos(String key, String json) { sp.edit().putString("pos_" + key, json).apply(); }
        @JavascriptInterface public String getRecent() { return recent().toString(); }
        @JavascriptInterface public void setBookInfo(String key, String title, String author) {
            if ("bundled".equals(key)) return;
            synchronized (MainActivity.this) {
                try {
                    JSONArray r = recent();
                    for (int i = 0; i < r.length(); i++) {
                        JSONObject o = r.getJSONObject(i);
                        if (key.equals(o.optString("key"))) o.put("title", title).put("author", author);
                    }
                    sp.edit().putString("recent", r.toString()).apply();
                } catch (Exception ignored) {}
            }
        }
        @JavascriptInterface public void openFile() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(i, 1); }
                catch (Exception e) { Toast.makeText(MainActivity.this, "Нет файлового менеджера", Toast.LENGTH_SHORT).show(); }
            });
        }
        @JavascriptInterface public void openBundled() { MainActivity.this.openBundled(); }
        @JavascriptInterface public void openRecent(String key) {
            if (new File(booksDir(), key).exists()) MainActivity.this.openKey(key);
        }
        @JavascriptInterface public void openExternal(String url) { runOnUiThread(() -> MainActivity.this.openExternal(url)); }
        @JavascriptInterface public void toast(String m) { runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show()); }
        @JavascriptInterface public void exit() { runOnUiThread(MainActivity.this::finish); }
    }
}
