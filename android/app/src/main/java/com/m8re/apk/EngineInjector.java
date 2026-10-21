package com.m8re.apk;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the M8RE engine JS (shipped verbatim from the upstream repo) out of
 * assets/m8re/ and injects it into the loaded bilibili video page.
 *
 * Injection order: host shim -> mouse layer -> engine files (manifest.json order).
 *
 * Two things this class is careful about, both learned the hard way:
 *
 *  1. evaluateJavascript() is always called with a real ValueCallback. Some
 *     WebView implementations silently drop the script when the callback is
 *     null, which looks exactly like "the engine never starts".
 *  2. The bundle is injected in line-aligned chunks with a callback chain
 *     (plus a timed fallback) instead of one ~330 KB call, so a single huge
 *     eval can neither be truncated nor block the UI thread.
 */
final class EngineInjector {

    // Same order as manifest.json content_scripts.js
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

    private static final int CHUNK_GAP_MS = 80;  // fallback pacing
    private static final int PROBE_TRIES = 12;
    private static final int PROBE_GAP_MS = 2500;

    /** Bumped on every new document so a stale chunk chain aborts itself. */
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

    /**
     * 每个 evaluateJavascript 调用的内容,按"完整文件"为单位拼装。
     *
     * 这里曾经按字符数在换行处切块 —— 结果是每块都切在函数中间,单块 eval 直接
     * 语法错误被整块丢弃,引擎等于一行都没加载("一直检测不到弹幕"就是这个原因)。
     * 引擎的每个 .js 都是一个完整 IIFE,所以只要不切断文件,单块一定是合法 JS。
     */
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

        // 小文件合并成块(仍然不切断任何文件),大文件独占一块
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
        if (gen != GEN) return;                       // a new page took over
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
        act.pushMouseFlag();                          // restore the click-layer switch
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
