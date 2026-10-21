package com.m8re.apk;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class EngineInjector {

    static final String[] ENGINE_FILES = {
            "m8re/00_ns.js",
            "m8re/01_scanner.js",
            "m8re/02_expr.js",
            "m8re/03_codegen.js",
            "m8re/04_parser.js",
            "m8re/05_vm.js",
            "m8re/06_tween.js",
            "m8re/07_bytearray.js",
            "m8re/08_graphics.js",
            "m8re/09_elements.js",
            "m8re/10_motion.js",
            "m8re/11_hostapi.js",
            "m8re/12_protobuf.js",
            "m8re/13_fetcher.js",
            "m8re/14_main.js",
    };

    private static final int CHUNK_GAP_MS = 80;
    private static final int PROBE_TRIES = 12;
    private static final int PROBE_GAP_MS = 2500;

    private static volatile int GEN = 0;

    static void newDocument() {
        GEN++;
    }

    static void inject(final MainActivity act, final WebView web) {
        final int gen = ++GEN;
        new Thread(() -> {
            final List<String> chunks = buildChunks(act);
            if (chunks == null || chunks.isEmpty()) return;
            new Handler(Looper.getMainLooper()).post(() ->
                    act.setStatus("注入 M8RE(" + chunks.size() + " 块)..."));
            new Handler(Looper.getMainLooper()).post(() -> chunk(act, web, chunks, 0, gen));
        }, "m8re-inject").start();
    }

    private static List<String> buildChunks(MainActivity act) {
        List<String> units = new ArrayList<>();
        String shim = readAsset(act, "m8re/host_shim.js");
        if (shim == null) { act.setStatus("缺少 host_shim.js"); return null; }
        units.add("/* ==== host_shim ==== */\n" + shim + "\n");
        String mouse = readAsset(act, "m8re/_mouse.js");
        if (mouse != null) units.add("/* ==== _mouse ==== */\n" + mouse + "\n");
        for (String f : ENGINE_FILES) {
            String src = readAsset(act, f);
            if (src == null) { act.setStatus("缺少引擎文件: " + f); return null; }
            units.add("\n/* ==== " + f + " ==== */\n" + src + "\n");
        }

        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String u : units) {
            if (cur.length() > 0 && cur.length() + u.length() > 60000) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(u);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static void chunk(final MainActivity act, final WebView web,
                              final List<String> chunks, final int index, final int gen) {
        if (gen != GEN) return;
        if (index >= chunks.size()) { done(act, web, chunks.size(), gen); return; }
        final boolean[] advanced = { false };
        final Runnable next = () -> {
            if (advanced[0]) return;
            advanced[0] = true;
            chunk(act, web, chunks, index + 1, gen);
        };
        new Handler(Looper.getMainLooper()).postDelayed(next, CHUNK_GAP_MS + 400);
        try {
            web.evaluateJavascript(chunks.get(index), value -> next.run());
        } catch (Throwable t) {
            act.setStatus("注入第 " + index + " 块失败: " + t);
            next.run();
        }
    }

    private static void done(final MainActivity act, final WebView web, int n, final int gen) {
        if (gen != GEN) return;
        act.setStatus("脚本已注入(" + n + " 块),等待引擎挂载...");
        act.pushMouseFlag();
        probe(act, web, 0);
    }

    private static final String PROBE =
            "(function(){try{" +
            " var m = window.M8RE ? 1 : 0;" +
            " var ov = document.getElementById('m8re-overlay') ? 1 : 0;" +
            " var v = document.querySelector('video') ? 1 : 0;" +
            " var p = (document.querySelector('.bpx-player-container')||document.querySelector('#bilibili-player')) ? 1 : 0;" +
            " return 'M'+m+'O'+ov+'V'+v+'P'+p;" +
            "}catch(e){return 'E'+e;}})()";

    private static void probe(final MainActivity act, final WebView web, final int tryNo) {
        if (tryNo >= PROBE_TRIES) {
            act.setStatus("脚本已注入,但未检测到弹幕层(状态 " + lastState + ")");
            return;
        }
        try {
            web.evaluateJavascript(PROBE, value -> {
                String st = value == null ? "?" : value.replace("\"", "").trim();
                lastState = st;
                if (st.contains("O1")) {
                    act.setStatus("M8RE 引擎已启动 ✓");
                    return;
                }
                act.setStatus("引擎启动中 " + (tryNo + 1) + "/" + PROBE_TRIES + " [" + st + "]");
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> probe(act, web, tryNo + 1), PROBE_GAP_MS);
            });
        } catch (Throwable t) {
            act.setStatus("引擎探测失败: " + t);
        }
    }

    private static String lastState = "?";

    static String readAsset(MainActivity act, String path) {
        try (InputStream in = act.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }
}
