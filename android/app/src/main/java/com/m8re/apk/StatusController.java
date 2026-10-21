package com.m8re.apk;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

final class StatusController {

    enum Level {
        IDLE(R.color.status_idle),
        INFO(R.color.status_loading),
        BUSY(R.color.status_loading),
        OK(R.color.status_ok),
        WARN(R.color.status_warn),
        ERROR(R.color.status_error);

        final int colorRes;
        Level(int colorRes) { this.colorRes = colorRes; }
    }

    private final Activity act;
    private final TextView text;
    private final TextView hint;
    private String lastMain = "";
    private String lastHint = "";

    StatusController(Activity act) {
        this.act = act;

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.holo_status_bg);
        int padH = dp(8), padV = dp(4);
        box.setPadding(padH, padV, padH, padV);

        text = new TextView(act);
        text.setTextColor(act.getResources().getColor(R.color.status_text));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        box.addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        hint = new TextView(act);
        hint.setTextColor(0xffd0d0d0);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hint.setVisibility(View.GONE);
        hint.setPadding(0, dp(2), 0, 0);
        box.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        this.view = box;
        set(Level.IDLE, act.getString(R.string.st_init), null);
    }

    private final LinearLayout view;

    LinearLayout view() { return view; }

    void set(Level level, String main, String actionHint) {
        act.runOnUiThread(() -> {
            lastMain = main == null ? "" : main;
            lastHint = actionHint == null ? "" : actionHint;
            text.setText(lastMain);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(act.getResources().getColor(level.colorRes));
            view.setBackground(bg);
            if (lastHint.isEmpty()) {
                hint.setVisibility(View.GONE);
            } else {
                hint.setVisibility(View.VISIBLE);
                hint.setText("建议：" + lastHint);
            }
        });
    }

    void info(String main) { set(Level.INFO, main, null); }
    void busy(String main) { set(Level.BUSY, main, null); }
    void ok(String main) { set(Level.OK, main, null); }

    void warn(String main, String actionHint) { set(Level.WARN, main, actionHint); }
    void error(String main, String actionHint) { set(Level.ERROR, main, actionHint); }

    void pageLoading() { busy(act.getString(R.string.st_page_loading)); }
    void injecting() { busy(act.getString(R.string.st_injecting)); }
    void injectedWaiting() {
        warn(act.getString(R.string.st_injected_wait), act.getString(R.string.act_retry));
    }
    void engineReady() { ok(act.getString(R.string.st_engine_ok)); }
    void notVideo() {
        warn(act.getString(R.string.st_not_video), act.getString(R.string.act_open_video));
    }
    void notLoggedIn() {
        warn(act.getString(R.string.st_no_login), act.getString(R.string.act_login));
    }
    void netError() {
        error(act.getString(R.string.st_net_error), act.getString(R.string.act_check_net));
    }
    void noDanmakuLayer(String detail) {
        warn(act.getString(R.string.st_no_danmaku), act.getString(R.string.act_retry)
                + " · " + detail);
    }

    String lastMain() { return lastMain; }

    private int dp(int v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }
}
