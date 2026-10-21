package com.m8re.apk;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends android.app.Activity {

    static final String TAG = "M8RE";
    static final String HOME_URL = "https://www.bilibili.com/";

    private WebView web;
    private ProgressBar progress;
    private TextView statusBar;
    private EditText urlBar;
    private LinearLayout topBar;
    private FrameLayout root;
    private FrameLayout webHolder;
    private GamepadView gamepad;
    private android.widget.Button padToggleBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        LinearLayout vertical = new LinearLayout(this);
        vertical.setOrientation(LinearLayout.VERTICAL);

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(dp(6), dp(6), dp(6), dp(6));
        topBar.setBackgroundColor(0xFF1A1A1A);

        urlBar = new EditText(this);
        urlBar.setSingleLine(true);
        urlBar.setHint("bilibili URL / BV id");
        urlBar.setTextColor(0xFFEEEEEE);
        urlBar.setHintTextColor(0xFF888888);
        urlBar.setTextSize(13);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        topBar.addView(urlBar, ulp);

        android.widget.Button goBtn = new android.widget.Button(this);
        goBtn.setText("Go");
        goBtn.setOnClickListener(v -> navigate(resolveInput(urlBar.getText().toString())));
        topBar.addView(goBtn);

        android.widget.Button homeBtn = new android.widget.Button(this);
        homeBtn.setText("B站");
        homeBtn.setOnClickListener(v -> web.loadUrl(HOME_URL));
        topBar.addView(homeBtn);

        android.widget.Button loginBtn = new android.widget.Button(this);
        loginBtn.setText("登录");
        loginBtn.setOnClickListener(v ->
                web.loadUrl("https://passport.bilibili.com/login"));
        topBar.addView(loginBtn);

        padToggleBtn = new android.widget.Button(this);
        padToggleBtn.setText("手柄");
        padToggleBtn.setOnClickListener(v -> toggleGamepad());
        padToggleBtn.setOnLongClickListener(v -> { togglePadEdit(); return true; });
        topBar.addView(padToggleBtn);

        mouseToggleBtn = new android.widget.Button(this);
        mouseToggleBtn.setText("鼠标");
        mouseToggleBtn.setOnClickListener(v -> toggleMouse());
        topBar.addView(mouseToggleBtn);

        vertical.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildPadEditBar(vertical);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        vertical.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        webHolder = new FrameLayout(this);
        web = new WebView(this);
        webHolder.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        vertical.addView(webHolder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        statusBar = new TextView(this);
        statusBar.setTextColor(0xFF2A2A2A);
        statusBar.setTextSize(11);
        statusBar.setPadding(dp(6), dp(2), dp(6), dp(2));
        statusBar.setBackgroundColor(0xFFDDDDDD);
        statusBar.setText("M8RE 初始化中...");
        vertical.addView(statusBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(vertical, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        configureWebView();

        gamepad = new GamepadView(this, web);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webHolder.addView(gamepad, glp);
        gamepad.setPadVisible(true);
        gamepad.setEditListener((code, label, sizePx) -> {
            if (sizeBar == null || code == -1) return;
            float min = gamepad.getMinSize(), max = gamepad.getMaxSize();
            int pr = Math.round(200f * (sizePx - min) / (max - min));
            sizeBar.setProgress(Math.max(0, Math.min(200, pr)));
        });

        String startUrl = HOME_URL;
        Intent it = getIntent();
        if (it != null) {
            String action = it.getAction();
            if (Intent.ACTION_VIEW.equals(action) && it.getData() != null) {
                startUrl = it.getData().toString();
            } else if (Intent.ACTION_SEND.equals(action)) {
                String txt = it.getStringExtra(Intent.EXTRA_TEXT);
                if (txt != null) startUrl = extractUrl(txt);
            }
        }
        navigate(startUrl);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        s.setUserAgentString(desktopUserAgent());

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(web, true);
        }

        web.setWebViewClient(new M8WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        web.setBackgroundColor(0xFF000000);

        web.addJavascriptInterface(new M8NetBridge(), "M8Native");
        web.addJavascriptInterface(new M8LogBridge(), "M8NativeLog");
    }

    private String resolveInput(String raw) {
        if (raw == null) return HOME_URL;
        String t = raw.trim();
        if (t.isEmpty()) return HOME_URL;
        if (t.matches("(?i)^BV[0-9A-Za-z]+$")) return "https://www.bilibili.com/video/" + t;
        if (t.matches("(?i)^av\\d+$")) return "https://www.bilibili.com/video/" + t;
        if (!t.startsWith("http")) return "https://" + t;
        return t;
    }

    private static String extractUrl(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("https?://[^\\s]+").matcher(s);
        return m.find() ? m.group() : s;
    }

    private static String desktopUserAgent() {

        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    }

    private void navigate(String url) {
        if (url == null || url.isEmpty()) url = HOME_URL;
        urlBar.setText(url);
        web.loadUrl(url);
    }

    private boolean padVisible = true;
    private boolean mouseOn = true;
    private android.widget.Button mouseToggleBtn;
    private long lastStatusAt = 0;
    private String lastInjected = "";

    void pushMouseFlag() {
        if (web == null) return;
        web.evaluateJavascript("window.__moMouseTakeover=" + (mouseOn ? "true" : "false") + ";",
                value -> { });
    }

    String engineName() {
        return "WebView";
    }

    private void toggleMouse() {
        mouseOn = !mouseOn;
        pushMouseFlag();
        if (mouseToggleBtn != null) mouseToggleBtn.setText(mouseOn ? "鼠标" : "鼠标(关)");
        setStatus(mouseOn
                ? "点击层已开:弹幕游戏区域内触摸会转成鼠标事件(视频区域点一下不再暂停)"
                : "点击层已关:触摸照常传给网页");
    }

    private LinearLayout padEditBar;
    private android.widget.SeekBar sizeBar;
    private android.widget.TextView sizeHint;

    private void buildPadEditBar(LinearLayout vertical) {
        padEditBar = new LinearLayout(this);
        padEditBar.setOrientation(LinearLayout.HORIZONTAL);
        padEditBar.setPadding(dp(6), 0, dp(6), 0);
        padEditBar.setVisibility(View.GONE);

        sizeHint = new android.widget.TextView(this);
        sizeHint.setText("大小:");
        sizeHint.setTextSize(12);
        padEditBar.addView(sizeHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        sizeBar = new android.widget.SeekBar(this);
        sizeBar.setMax(200);
        sizeBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seek, int pr, boolean byUser) {
                if (!byUser || gamepad == null) return;
                float min = gamepad.getMinSize(), max = gamepad.getMaxSize();
                gamepad.setSelectedSizePx(min + (max - min) * pr / 200f);
            }

            @Override public void onStartTrackingTouch(android.widget.SeekBar seek) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar seek) { }
        });
        padEditBar.addView(sizeBar, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        padEditBar.addView(editBtn("按键", v -> showPadEditor()));
        padEditBar.addView(editBtn("默认", v -> {
            if (gamepad != null) gamepad.resetLayout();
            setStatus("键位布局已恢复默认");
        }));
        padEditBar.addView(editBtn("完成", v -> setPadEditMode(false)));

        vertical.addView(padEditBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private android.widget.Button editBtn(String label,
                                          android.view.View.OnClickListener l) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setOnClickListener(l);
        return b;
    }

    private void togglePadEdit() {
        setPadEditMode(gamepad == null || !gamepad.isEditMode());
    }

    private void setPadEditMode(boolean on) {
        if (gamepad == null) return;
        if (on && !padVisible) {
            padVisible = true;
            gamepad.setPadVisible(true);
            padToggleBtn.setText("手柄");
        }
        if (padEditBar != null) padEditBar.setVisibility(on ? View.VISIBLE : View.GONE);
        gamepad.setEditMode(on);
        setStatus(on
                ? "布局编辑:点一个键选中 → 拖动=移动 · 双指=缩放 · 上方滑条=大小"
                : "布局已保存");
    }

    private void showPadEditor() {
        if (gamepad == null) return;
        final java.util.Set<Integer> cur = gamepad.getEnabledCodes();

        final java.util.List<android.widget.CheckBox> boxes = new java.util.ArrayList<>();
        final java.util.List<Integer> codes = new java.util.ArrayList<>();

        android.widget.LinearLayout col = new android.widget.LinearLayout(this);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(4), dp(14), dp(10));

        padSection(col, "方向键");
        padCheckRow(col, boxes, codes, cur,
                new int[]{GamepadView.K_UP, GamepadView.K_DOWN, GamepadView.K_LEFT, GamepadView.K_RIGHT},
                new String[]{"\u2191", "\u2193", "\u2190", "\u2192"});

        padSection(col, "移动键 W A S D");
        padCheckRow(col, boxes, codes, cur,
                new int[]{GamepadView.K_W, GamepadView.K_A, GamepadView.K_S, GamepadView.K_D},
                new String[]{"W", "A", "S", "D"});

        padSection(col, "其他键");
        padCheckRow(col, boxes, codes, cur, new int[]{0}, new String[]{"小键盘 0-9"});
        padCheckRow(col, boxes, codes, cur,
                new int[]{GamepadView.K_ESC, GamepadView.K_HOME, GamepadView.K_PGDN, GamepadView.K_END},
                new String[]{"Esc", "Home", "PgDn", "End"});

        android.widget.LinearLayout preset = new android.widget.LinearLayout(this);
        preset.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.Button onlyMove = new android.widget.Button(this);
        onlyMove.setText("只留移动键");
        onlyMove.setOnClickListener(v -> applyPadPreset(boxes, codes, true));
        preset.addView(onlyMove, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Button allKeys = new android.widget.Button(this);
        allKeys.setText("全部显示");
        allKeys.setOnClickListener(v -> applyPadPreset(boxes, codes, false));
        preset.addView(allKeys, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(preset);

        android.widget.TextView hint = new android.widget.TextView(this);
        hint.setText("勾掉的键会从手柄上删掉。引擎只认:方向键 / W A S D / 小键盘 0-9 / Esc / Home / PgDn / End。");
        hint.setTextSize(11);
        hint.setPadding(0, dp(8), 0, 0);
        col.addView(hint);

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.addView(col);

        new android.app.AlertDialog.Builder(this)
                .setTitle("编辑手柄键位")
                .setView(sc)
                .setPositiveButton("保存", (d, w) -> {
                    java.util.Set<Integer> out = new java.util.HashSet<>();
                    for (int i = 0; i < boxes.size(); i++) {
                        if (!boxes.get(i).isChecked()) continue;
                        int c = codes.get(i);
                        if (c == 0) {
                            for (int k = 0; k < 10; k++) out.add(GamepadView.K_NUMPAD0 + k);
                        } else {
                            out.add(c);
                        }
                    }
                    gamepad.setEnabledCodes(out);
                    setStatus("键位已保存:盘上 " + out.size() + " 个键(长按「手柄」可再改)");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void padSection(android.widget.LinearLayout col, String title) {
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(title);
        tv.setTextSize(13);
        tv.setPadding(0, dp(10), 0, dp(2));
        col.addView(tv);
    }

    private void padCheckRow(android.widget.LinearLayout col,
                             java.util.List<android.widget.CheckBox> boxes,
                             java.util.List<Integer> codes,
                             java.util.Set<Integer> cur,
                             int[] rowCodes, String[] labels) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        for (int i = 0; i < rowCodes.length; i++) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(labels[i]);
            cb.setTextSize(13);
            cb.setChecked(rowCodes[i] == 0 ? cur.contains(GamepadView.K_NUMPAD0)
                    : cur.contains(rowCodes[i]));
            row.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            boxes.add(cb);
            codes.add(rowCodes[i]);
        }
        col.addView(row);
    }

    private void applyPadPreset(java.util.List<android.widget.CheckBox> boxes,
                                java.util.List<Integer> codes, boolean onlyMove) {
        for (int i = 0; i < boxes.size(); i++) {
            int c = codes.get(i);
            boolean move = c == GamepadView.K_UP || c == GamepadView.K_DOWN
                    || c == GamepadView.K_LEFT || c == GamepadView.K_RIGHT
                    || c == GamepadView.K_W || c == GamepadView.K_A
                    || c == GamepadView.K_S || c == GamepadView.K_D;
            boxes.get(i).setChecked(onlyMove ? move : true);
        }
    }

    private void toggleGamepad() {
        padVisible = !padVisible;
        if (gamepad != null) gamepad.setPadVisible(padVisible);
        padToggleBtn.setText(padVisible ? "手柄" : "手柄(关)");
        setStatus(padVisible ? "虚拟手柄已开启(长按「手柄」编辑键位)" : "虚拟手柄已隐藏");
    }

    private class M8WebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String host = u.getHost() == null ? "" : u.getHost();

            if (host.endsWith("bilibili.com") || host.endsWith("hdslb.com")
                    || host.endsWith("bilivideo.com") || host.endsWith("biliapi.net")
                    || host.endsWith("bilibili.co")) {
                return false;
            }
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            return null;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            EngineInjector.newDocument();
            lastInjected = "";
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progress.setVisibility(View.GONE);
            if (url != null && url.contains("bilibili.com/video/")) {
                if (url.equals(lastInjected)) return;
                lastInjected = url;
                EngineInjector.inject(MainActivity.this, web);
            } else {
                statusBar.setText("已加载: " + url);
            }
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            super.doUpdateVisitedHistory(view, url, isReload);

            if (url != null && url.contains("bilibili.com/video/") && !url.equals(lastInjected)) {
                lastInjected = url;
                EngineInjector.newDocument();
                EngineInjector.inject(MainActivity.this, web);
            }
        }
    }

    private static String guessMime(String url) {
        String u = url.toLowerCase();
        if (u.contains("seg.so") || u.contains("dm/web/view") || u.contains(".bin")) return "application/octet-stream";
        if (u.contains("/x/") || u.contains("json")) return "application/json";
        return "application/octet-stream";
    }

    public class M8NetBridge {
        @JavascriptInterface
        public void get(final String url, final String reqId) {

            String cookie = null;
            try {
                cookie = CookieManager.getInstance().getCookie(
                        "https://" + Uri.parse(url).getHost() + "/");
            } catch (Throwable ignore) { }
            final String fCookie = cookie;
            new Thread(() -> {
                byte[] body = null;
                String err = null;
                try {
                    body = M8Http.fetch(url, null, fCookie);
                } catch (Throwable t) {
                    err = String.valueOf(t);
                }
                String b64 = body == null ? null : android.util.Base64.encodeToString(
                        body, android.util.Base64.NO_WRAP);
                final String js = "window.__M8NativeDeliver(" + jsStr(reqId) + ","
                        + (b64 == null ? "null" : jsStr(b64)) + ","
                        + jsStr(err) + ");";
                runOnUiThread(() -> web.evaluateJavascript(js, value -> { }));
            }).start();
        }

        @JavascriptInterface
        public String getUserAgent() {
            return desktopUserAgent();
        }
    }

    public class M8LogBridge {
        @JavascriptInterface
        public void log(String msg) {
            if (msg == null) return;
            Log.i(TAG + "-js", msg);
            final long now = System.currentTimeMillis();
            if (now - lastStatusAt < 250) return;
            lastStatusAt = now;
            String one = msg.replace("\n", " ");
            if (one.length() > 90) one = one.substring(0, 90) + "…";
            setStatus("[js] " + one);
        }
    }

    static String jsStr(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append("\"").toString();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())
                && intent.getData() != null) {
            navigate(intent.getData().toString());
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    void setStatus(final String txt) {
        runOnUiThread(() -> statusBar.setText(txt));
    }
}
