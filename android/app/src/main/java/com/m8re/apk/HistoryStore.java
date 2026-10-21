package com.m8re.apk;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class HistoryStore {

    private static final String PREF = "m8re-history";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_FAVS = "favs";
    private static final int MAX_HISTORY = 60;

    static final class Entry {
        final String url;
        final String title;
        final long at;

        Entry(String url, String title, long at) {
            this.url = url;
            this.title = title;
            this.at = at;
        }

        String display() {
            String t = (title == null || title.trim().isEmpty()) ? url : title.trim();
            if (t.length() > 60) t = t.substring(0, 60) + "…";
            return t;
        }
    }

    private final SharedPreferences prefs;

    HistoryStore(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    void addHistory(String url, String title) {
        if (url == null || url.isEmpty()) return;
        List<Entry> list = read(KEY_HISTORY);
        list.removeIf(e -> e.url.equals(url));
        list.add(0, new Entry(url, title, System.currentTimeMillis()));
        while (list.size() > MAX_HISTORY) list.remove(list.size() - 1);
        write(KEY_HISTORY, list);
    }

    void addFavourite(String url, String title) {
        if (url == null || url.isEmpty()) return;
        List<Entry> list = read(KEY_FAVS);
        for (Entry e : list) if (e.url.equals(url)) return;
        list.add(0, new Entry(url, title, System.currentTimeMillis()));
        write(KEY_FAVS, list);
    }

    void removeFavourite(String url) {
        List<Entry> list = read(KEY_FAVS);
        list.removeIf(e -> e.url.equals(url));
        write(KEY_FAVS, list);
    }

    boolean isFavourite(String url) {
        if (url == null) return false;
        for (Entry e : read(KEY_FAVS)) if (e.url.equals(url)) return true;
        return false;
    }

    List<Entry> history() { return read(KEY_HISTORY); }
    List<Entry> favourites() { return read(KEY_FAVS); }

    void clearHistory() { write(KEY_HISTORY, new ArrayList<>()); }

    private List<Entry> read(String key) {
        List<Entry> out = new ArrayList<>();
        String raw = prefs.getString(key, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(o.optString("url"), o.optString("title"), o.optLong("at")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void write(String key, List<Entry> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : list) {
                JSONObject o = new JSONObject();
                o.put("url", e.url);
                o.put("title", e.title == null ? "" : e.title);
                o.put("at", e.at);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs.edit().putString(key, arr.toString()).apply();
    }
}
