package com.m8re.apk;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.webkit.WebView;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class GamepadView extends View {

    static final String TAG = "M8RE-pad";

    static final int K_ESC = 27;
    static final int K_PGDN = 34, K_END = 35, K_HOME = 36;
    static final int K_LEFT = 37, K_UP = 38, K_RIGHT = 39, K_DOWN = 40;
    static final int K_A = 65, K_D = 68, K_S = 83, K_W = 87;
    static final int K_NUMPAD0 = 96;

    private final WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static class Btn {
        String label; int code; RectF rect = new RectF();
        boolean pressed; boolean round; float textScale = 1f;
        Runnable repeat;
        Btn(String l, int c, boolean round) { label = l; code = c; this.round = round; }
    }

    private final List<Btn> all = new ArrayList<>();
    private final List<Btn> buttons = new ArrayList<>();
    private final java.util.Set<Integer> enabled = new java.util.HashSet<>();

    private final java.util.Map<Integer, float[]> user = new java.util.HashMap<>();
    private boolean hasUser = false;

    private boolean editMode = false;
    private Btn sel = null;
    private float lastX, lastY, pinch0, pinchW0, pinchH0;
    private float minSize, maxSize;

    interface EditListener { void onPadSelection(int code, String label, float sizePx); }
    private EditListener listener;
    void setEditListener(EditListener l) { listener = l; }

    boolean isEditMode() { return editMode; }
    float getMinSize() { return minSize; }
    float getMaxSize() { return maxSize; }
    int getSelectedCode() { return sel == null ? -1 : sel.code; }
    float getSelectedSizePx() { return sel == null ? 0 : sel.rect.width(); }

    private float padRadius;
    private float btnRadius;
    private float numRadius;
    private boolean visible = true;

    public GamepadView(Context ctx, WebView web) {
        super(ctx);
        this.web = web;
        setFocusable(false);
        setClickable(false);

        float d = ctx.getResources().getDisplayMetrics().density;
        padRadius = 46 * d;
        btnRadius = 30 * d;
        numRadius = 23 * d;

        all.add(new Btn("\u2190", K_LEFT, true));
        all.add(new Btn("\u2191", K_UP, true));
        all.add(new Btn("\u2192", K_RIGHT, true));
        all.add(new Btn("\u2193", K_DOWN, true));

        all.add(new Btn("W", K_W, true));
        all.add(new Btn("A", K_A, true));
        all.add(new Btn("S", K_S, true));
        all.add(new Btn("D", K_D, true));

        all.add(new Btn("Esc", K_ESC, false));
        all.add(new Btn("Home", K_HOME, false));

        for (int i = 0; i <= 9; i++) {
            all.add(new Btn(String.valueOf(i), K_NUMPAD0 + i, true));
        }
        all.add(new Btn("PgDn", K_PGDN, false));
        all.add(new Btn("End", K_END, false));

        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2 * d);
        selStroke.setStyle(Paint.Style.STROKE);
        selStroke.setStrokeWidth(3 * d);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(17 * d);
        text.setFakeBoldText(true);
        small.setColor(Color.WHITE);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(10 * d);
        small.setFakeBoldText(true);

        minSize = 16 * d;
        maxSize = 200 * d;

        enabled.addAll(loadSaved());
        loadSavedLayout();
        leftHanded = prefs().getBoolean("leftHanded", false);
        setAlpha(prefs().getFloat("padAlpha", 1f));
        rebuild();
    }

    static final int[] MOVE_CODES = { K_UP, K_DOWN, K_LEFT, K_RIGHT, K_W, K_A, K_S, K_D };
    static final int[] OTHER_CODES = { K_ESC, K_HOME, K_PGDN, K_END };
    static final int[] ALL_CODES = buildAllCodes();

    private static int[] buildAllCodes() {
        int[] a = new int[8 + 4 + 10];
        System.arraycopy(MOVE_CODES, 0, a, 0, 8);
        System.arraycopy(OTHER_CODES, 0, a, 8, 4);
        for (int i = 0; i < 10; i++) a[12 + i] = K_NUMPAD0 + i;
        return a;
    }

    java.util.Set<Integer> getEnabledCodes() {
        return new java.util.HashSet<>(enabled);
    }

    void setEnabledCodes(java.util.Collection<Integer> codes) {
        enabled.clear();
        if (codes != null) enabled.addAll(codes);
        if (enabled.isEmpty()) enabled.add(K_W);
        rebuild();
        hideAndRelease();
        saveSaved();
        if (getWidth() > 0) {
            layoutButtons(getWidth(), getHeight());
            invalidate();
        }
    }

    private static final int[] PRESET_SIMPLE = { K_LEFT, K_RIGHT, K_UP, K_DOWN, K_A, K_S, K_W };
    private static final int[] PRESET_FULL = buildAllCodes();

    void applyPresetSimple() {
        java.util.List<Integer> l = new java.util.ArrayList<>();
        for (int c : PRESET_SIMPLE) l.add(c);
        setEnabledCodes(l);
    }

    void applyPresetFull() {
        java.util.List<Integer> l = new java.util.ArrayList<>();
        for (int c : PRESET_FULL) l.add(c);
        setEnabledCodes(l);
    }

    void setAlphaLevel(float a) {
        setAlpha(Math.max(0.25f, Math.min(1f, a)));
        prefs().edit().putFloat("padAlpha", getAlpha()).apply();
    }

    float getAlphaLevel() {
        return getAlpha();
    }

    void setLeftHanded(boolean left) {
        leftHanded = left;
        prefs().edit().putBoolean("leftHanded", left).apply();
        if (getWidth() > 0) {
            layoutButtons(getWidth(), getHeight());
            invalidate();
        }
    }

    boolean isLeftHanded() { return leftHanded; }

    private boolean leftHanded = false;

    private void rebuild() {
        buttons.clear();
        for (Btn b : all) if (enabled.contains(b.code)) buttons.add(b);
    }

    private void hideAndRelease() {
        for (Btn b : all) if (b.pressed) release(b);
    }

    private java.util.Set<Integer> loadSaved() {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        String raw = prefs().getString("keys", null);
        if (raw == null) {
            for (int c : ALL_CODES) out.add(c);
            return out;
        }
        for (String part : raw.split(",")) {
            try { if (part.trim().length() > 0) out.add(Integer.parseInt(part.trim())); } catch (Exception ignored) { }
        }
        return out;
    }

    private void saveSaved() {
        StringBuilder sb = new StringBuilder();
        for (int c : ALL_CODES) {
            if (enabled.contains(c)) sb.append(sb.length() == 0 ? "" : ",").append(c);
        }
        prefs().edit().putString("keys", sb.toString()).apply();
    }

    private android.content.SharedPreferences prefs() {
        return getContext().getSharedPreferences("m8re-pad", Context.MODE_PRIVATE);
    }

    void setEditMode(boolean on) {
        editMode = on;
        sel = null;
        for (Btn b : all) if (b.pressed) release(b);
        if (!on) saveSavedLayout();
        notifySel();
        invalidate();
    }

    void setSelectedSizePx(float wpx) {
        if (sel == null) return;
        float w0 = sel.rect.width(), h0 = sel.rect.height();
        if (w0 <= 1 || h0 <= 1) return;
        float w1 = clamp(wpx, minSize, maxSize);
        float h1 = h0 * (w1 / w0);
        float cx = sel.rect.centerX(), cy = sel.rect.centerY();
        sel.rect.set(cx - w1 / 2f, cy - h1 / 2f, cx + w1 / 2f, cy + h1 / 2f);
        clampInside(sel);
        remember(sel);
        notifySel();
        invalidate();
    }

    void resetLayout() {
        user.clear();
        hasUser = false;
        if (getWidth() > 0) layoutButtons(getWidth(), getHeight());
        saveSavedLayout();
        notifySel();
        invalidate();
    }

    private boolean onEditTouch(MotionEvent ev) {
        final int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                sel = topAt(ev.getX(), ev.getY());
                lastX = ev.getX();
                lastY = ev.getY();
                pinch0 = 0;
                notifySel();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (sel != null && ev.getPointerCount() >= 2) {
                    pinch0 = spread(ev);
                    pinchW0 = sel.rect.width();
                    pinchH0 = sel.rect.height();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (sel == null) return true;
                if (pinch0 > 1 && ev.getPointerCount() >= 2 && pinchW0 > 1) {
                    float sc = spread(ev) / pinch0;
                    float w1 = clamp(pinchW0 * sc, minSize, maxSize);
                    float h1 = pinchH0 * (w1 / pinchW0);
                    float cx = sel.rect.centerX(), cy = sel.rect.centerY();
                    sel.rect.set(cx - w1 / 2f, cy - h1 / 2f, cx + w1 / 2f, cy + h1 / 2f);
                } else {
                    sel.rect.offset(ev.getX() - lastX, ev.getY() - lastY);
                    lastX = ev.getX();
                    lastY = ev.getY();
                }
                clampInside(sel);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
                pinch0 = 0;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (sel != null) {
                    clampInside(sel);
                    remember(sel);
                }
                pinch0 = 0;
                notifySel();
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private static float spread(MotionEvent ev) {
        float dx = ev.getX(0) - ev.getX(1);
        float dy = ev.getY(0) - ev.getY(1);
        return (float) Math.hypot(dx, dy);
    }

    private void clampInside(Btn b) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (b.rect.left < 0) b.rect.offset(-b.rect.left, 0);
        if (b.rect.top < 0) b.rect.offset(0, -b.rect.top);
        if (b.rect.right > w) b.rect.offset(w - b.rect.right, 0);
        if (b.rect.bottom > h) b.rect.offset(0, h - b.rect.bottom);
    }

    private Btn topAt(float x, float y) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Btn b = buttons.get(i);
            if (!b.rect.isEmpty() && b.rect.contains(x, y)) return b;
        }
        return null;
    }

    private void notifySel() {
        if (listener != null) listener.onPadSelection(getSelectedCode(),
                sel == null ? null : sel.label, getSelectedSizePx());
    }

    private void remember(Btn b) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        user.put(b.code, new float[]{ b.rect.left / w, b.rect.top / h,
                b.rect.right / w, b.rect.bottom / h });
        hasUser = true;
        saveSavedLayout();
    }

    private void applyUserOverrides(int w, int h) {
        if (!hasUser || user.isEmpty()) return;
        for (java.util.Map.Entry<Integer, float[]> e : user.entrySet()) {
            Btn b = find(e.getKey());
            if (b == null) continue;
            float[] f = e.getValue();
            float l = f[0] * w, t = f[1] * h, r = f[2] * w, bo = f[3] * h;
            if (r - l < minSize || bo - t < minSize * 0.5f) continue;
            b.rect.set(l, t, r, bo);
        }
    }

    private void loadSavedLayout() {
        user.clear();
        String raw = prefs().getString("layout", null);
        if (raw != null) {
            for (String part : raw.split(";")) {
                String[] f = part.split(":");
                if (f.length != 5) continue;
                try {
                    user.put(Integer.parseInt(f[0]), new float[]{
                            Float.parseFloat(f[1]), Float.parseFloat(f[2]),
                            Float.parseFloat(f[3]), Float.parseFloat(f[4]) });
                } catch (Exception ignored) { }
            }
        }
        hasUser = !user.isEmpty();
    }

    private void saveSavedLayout() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<Integer, float[]> e : user.entrySet()) {
            float[] f = e.getValue();
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append(':').append(f[0]).append(':').append(f[1])
              .append(':').append(f[2]).append(':').append(f[3]);
        }
        prefs().edit().putString("layout", sb.toString()).apply();
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public void setPadVisible(boolean v) {
        visible = v;
        setVisibility(v ? VISIBLE : GONE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutButtons(w, h);
    }

    private Btn find(int code) {
        for (Btn b : buttons) if (b.code == code) return b;
        return null;
    }

    private void place(int code, float l, float t, float r, float b) {
        Btn btn = find(code);
        if (btn != null) btn.rect.set(l, t, r, b);
    }

    private void layoutButtons(int w, int h) {
        for (Btn b : all) b.rect.setEmpty();
        float d = getResources().getDisplayMetrics().density;
        float margin = 28 * d;
        float gap = 12 * d;

        float cx = leftHanded ? (w - margin - padRadius) : (padRadius + margin);
        float cy = h - padRadius - margin;
        float r = padRadius;
        place(K_UP, cx - r * 0.42f, cy - r, cx + r * 0.42f, cy - r * 0.15f);
        place(K_DOWN, cx - r * 0.42f, cy + r * 0.15f, cx + r * 0.42f, cy + r);
        place(K_LEFT, cx - r, cy - r * 0.42f, cx - r * 0.15f, cy + r * 0.42f);
        place(K_RIGHT, cx + r * 0.15f, cy - r * 0.42f, cx + r, cy + r * 0.42f);

        float wCx = leftHanded
                ? (margin + btnRadius * 2 + gap)
                : (w - margin - btnRadius * 2 - gap);
        float wCy = h - margin - btnRadius * 2 - gap;
        float wr = btnRadius;
        place(K_W, wCx - wr, wCy - wr * 2 - gap, wCx + wr, wCy - gap);
        place(K_S, wCx - wr, wCy + gap, wCx + wr, wCy + wr * 2 + gap);
        place(K_A, wCx - wr * 2 - gap, wCy - wr, wCx - gap, wCy + wr);
        place(K_D, wCx + gap, wCy - wr, wCx + wr * 2 + gap, wCy + wr);

        float cell = numRadius * 2 + 10 * d;
        float gridW = cell * 3, gridH = cell * 4;
        float gx = (w - gridW) / 2f;
        float gy = h - margin - gridH;
        int[] rows = { 7, 8, 9, 4, 5, 6, 1, 2, 3 };
        for (int i = 0; i < rows.length; i++) {
            int row = i / 3, col = i % 3;
            float bx = gx + col * cell + cell / 2f;
            float by = gy + row * cell + cell / 2f;
            Btn b = find(K_NUMPAD0 + rows[i]);
            if (b != null) b.rect.set(bx - numRadius, by - numRadius, bx + numRadius, by + numRadius);
        }

        for (int col = 0; col < 3; col++) {
            float bx = gx + col * cell + cell / 2f;
            float by = gy + 3 * cell + cell / 2f;
            Btn b = col == 0 ? find(K_PGDN) : (col == 1 ? find(K_NUMPAD0) : find(K_END));
            float half = numRadius;
            if (b != null) b.rect.set(bx - half, by - half, bx + half, by + half);
        }

        float pillH = 30 * d, pillW = 52 * d;
        place(K_ESC, margin, 18 * d, margin + pillW, 18 * d + pillH);
        place(K_HOME, margin + pillW + gap, 18 * d, margin + pillW * 2 + gap, 18 * d + pillH);

        applyUserOverrides(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!visible) return;
        for (Btn b : buttons) {
            if (b.rect.isEmpty()) continue;
            boolean round = b.round;
            if (round) {
                fill.setColor(b.pressed ? 0xCCE11D2A : 0x55FFFFFF);
                c.drawCircle(b.rect.centerX(), b.rect.centerY(), b.rect.width() / 2f, fill);
                stroke.setColor(0x88FFFFFF);
                c.drawCircle(b.rect.centerX(), b.rect.centerY(), b.rect.width() / 2f, stroke);
                text.setTextSize((b.label.length() > 1 ? 13 : 17) * getResources().getDisplayMetrics().density);
                c.drawText(b.label, b.rect.centerX(),
                        b.rect.centerY() + text.getTextSize() / 3f, text);
            } else {
                fill.setColor(b.pressed ? 0xCCE11D2A : 0x66222222);
                c.drawRoundRect(b.rect, 8, 8, fill);
                stroke.setColor(0x88FFFFFF);
                c.drawRoundRect(b.rect, 8, 8, stroke);
                c.drawText(b.label, b.rect.centerX(),
                        b.rect.centerY() + small.getTextSize() / 3f, small);
            }
        }

        if (editMode) {
            if (sel != null) {
                selStroke.setColor(0xFF4FC3F7);
                c.drawRoundRect(sel.rect, 10, 10, selStroke);
            }
            float d = getResources().getDisplayMetrics().density;
            Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
            hint.setTextSize(12 * d);
            hint.setTextAlign(Paint.Align.CENTER);
            String msg = sel == null ? "点一个键选中:拖动=移动,双指捏合=缩放"
                    : ("已选中 " + sel.label + " · 拖动=移动 · 双指=缩放 · 顶栏滑条=大小");
            float tw = hint.measureText(msg);
            RectF box = new RectF(getWidth() / 2f - tw / 2f - 12 * d, 8 * d,
                    getWidth() / 2f + tw / 2f + 12 * d,
                    8 * d + hint.getTextSize() * 1.9f);
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(0x88000000);
            c.drawRoundRect(box, 8 * d, 8 * d, bg);
            hint.setColor(Color.WHITE);
            c.drawText(msg, getWidth() / 2f, box.centerY() + hint.getTextSize() / 3f, hint);
        }
    }

    private boolean active = false;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!visible) return false;
        if (editMode) return onEditTouch(ev);
        final int action = ev.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {

            active = hitAny(ev.getX(), ev.getY());
            if (!active) return false;
        } else if (!active) {
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_MOVE:
                syncPressed(ev, -1);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                syncPressed(ev, ev.getActionIndex());
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                active = false;
                break;
            default:
                break;
        }
        invalidate();
        return active;
    }

    private boolean hitAny(float x, float y) {
        for (Btn b : buttons) {
            if (!b.rect.isEmpty() && b.rect.contains(x, y)) return true;
        }
        return false;
    }

    private void syncPressed(MotionEvent ev, int skipIndex) {
        for (Btn b : buttons) {
            if (b.rect.isEmpty()) continue;
            boolean under = false;
            for (int i = 0; i < ev.getPointerCount(); i++) {
                if (i == skipIndex) continue;
                if (b.rect.contains(ev.getX(i), ev.getY(i))) { under = true; break; }
            }
            if (under && !b.pressed) press(b);
            else if (!under && b.pressed) release(b);
        }
    }

    private void releaseAll() {
        for (Btn b : buttons) if (b.pressed) release(b);
    }

    private void press(Btn b) {
        b.pressed = true;
        if (b.code >= 0) {
            dispatchKey(b.code, true, b.label, false);
            startRepeat(b);
        }
    }

    private void release(Btn b) {
        b.pressed = false;
        stopRepeat(b);
        if (b.code >= 0) dispatchKey(b.code, false, b.label, false);
    }

    private void startRepeat(final Btn b) {
        stopRepeat(b);
        b.repeat = new Runnable() {
            @Override
            public void run() {
                if (!b.pressed) return;
                dispatchKey(b.code, true, b.label, true);
                ui.postDelayed(this, 110);
            }
        };
        ui.postDelayed(b.repeat, 400);
    }

    private void stopRepeat(Btn b) {
        if (b.repeat != null) {
            ui.removeCallbacks(b.repeat);
            b.repeat = null;
        }
    }

    private void echo(String label, boolean down) {
        try {
            ((MainActivity) getContext()).setStatus(
                    "按键 " + label + (down ? " ↓" : " ↑") + " (keyCode=" + codeOf(label) + ")");
        } catch (Throwable ignore) { }
    }

    private int codeOf(String label) {
        for (Btn b : buttons) if (b.label.equals(label)) return b.code;
        return -1;
    }

    private void dispatchKey(final int code, final boolean down, final String label, boolean quiet) {
        if (!quiet) echo(label, down);
        final String type = down ? "keydown" : "keyup";
        final String js =
            "(function(){try{" +
            "  if (typeof window.__moKey === 'function') { window.__moKey('" + type + "'," + code
                    + (quiet ? ",true" : "") + "); return; }" +
            "  var ev = new KeyboardEvent('" + type + "', {" +
            "    bubbles:true, cancelable:true, composed:true," +
            "    key:" + jsKeyName(code) + ", code:" + jsCodeName(code) + "," +
            "    keyCode:" + code + ", which:" + code + ", view:window" +
            "  });" +
            "  function def(o,k,v){try{Object.defineProperty(o,k,{get:function(){return v;}," +
            "    configurable:true});}catch(x){}}" +
            "  def(ev,'keyCode'," + code + "); def(ev,'which'," + code + "); def(ev,'charCode',0);" +
            "  document.dispatchEvent(ev);" +
            "}catch(e){try{M8NativeLog.log('keydispatch ' + e);}catch(y){}}})();";
        ui.post(() -> {
            if (web != null) web.evaluateJavascript(js, value -> { });
        });
    }

    private static String jsKeyName(int code) {
        switch (code) {
            case K_LEFT: return "'ArrowLeft'";
            case K_UP: return "'ArrowUp'";
            case K_RIGHT: return "'ArrowRight'";
            case K_DOWN: return "'ArrowDown'";
            case K_A: return "'a'";
            case K_D: return "'d'";
            case K_S: return "'s'";
            case K_W: return "'w'";
            case K_ESC: return "'Escape'";
            case K_PGDN: return "'PageDown'";
            case K_END: return "'End'";
            case K_HOME: return "'Home'";
            default: return "String.fromCharCode(" + code + ")";
        }
    }

    private static String jsCodeName(int code) {
        switch (code) {
            case K_LEFT: return "'ArrowLeft'";
            case K_UP: return "'ArrowUp'";
            case K_RIGHT: return "'ArrowRight'";
            case K_DOWN: return "'ArrowDown'";
            case K_A: return "'KeyA'";
            case K_D: return "'KeyD'";
            case K_S: return "'KeyS'";
            case K_W: return "'KeyW'";
            case K_ESC: return "'Escape'";
            case K_PGDN: return "'PageDown'";
            case K_END: return "'End'";
            case K_HOME: return "'Home'";
            default: return "''";
        }
    }
}
