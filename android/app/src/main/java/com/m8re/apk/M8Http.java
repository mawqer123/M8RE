package com.m8re.apk;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 面向 bilibili API 与弹幕 CDN 的极简 HTTP 客户端。
 *
 * 替代原扩展后台 service worker 的 fetch（它的作用就是绕过 CORS 并带上页面
 * 的 cookie）。在后台线程运行；cookie 由调用方通过 Cookie 头传入，
 * 取自 WebView 会话。
 */
final class M8Http {
    private static final String TAG = "M8RE-http";
    private static final int TIMEOUT_MS = 20000;
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private M8Http() {}

    static byte[] fetch(String url, Map<String, String> extraHeaders, String cookie) {
        HttpURLConnection conn = null;
        try {
            url = url.replaceFirst("^http://", "https://");
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            conn.setRequestProperty("Origin", "https://www.bilibili.com");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            if (cookie != null && !cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }
            if (extraHeaders != null) {
                for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                    String k = e.getKey();
                    if (k == null || e.getValue() == null) continue;
                    String lk = k.toLowerCase();
                    // 不要用浏览器传来的头覆盖上面已经设好的关键头
                    // （例如 "accept-encoding: br"，本端没有 brotli 解码器）。
                    if (lk.equals("accept-encoding") || lk.equals("host")
                            || lk.equals("connection") || lk.equals("content-length")) continue;
                    conn.setRequestProperty(k, e.getValue());
                }
            }

            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) {
                Log.w(TAG, "HTTP " + code + " no stream: " + url);
                return null;
            }
            String enc = conn.getContentEncoding();
            if (enc != null && enc.toLowerCase().contains("gzip")) {
                in = new GZIPInputStream(in);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            if (code < 200 || code >= 400) {
                Log.w(TAG, "HTTP " + code + " for " + url + " (" + out.size() + " bytes)");
                return null;
            }
            return out.toByteArray();
        } catch (Throwable t) {
            Log.w(TAG, "fetch failed " + url + " : " + t);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
