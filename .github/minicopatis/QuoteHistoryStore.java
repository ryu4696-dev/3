package jp.co.ichika.salesledger;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class QuoteHistoryStore {
    private static final String PREFS = "quick_quote_history_v1";
    private static final String KEY_FOLDERS = "folders";
    private static final String KEY_QUOTES = "quotes";

    private QuoteHistoryStore() {}

    public static final class Folder {
        public String id;
        public String name;
        public long createdAt;
        public Folder(String id, String name, long createdAt) {
            this.id = id; this.name = name; this.createdAt = createdAt;
        }
    }

    public static final class Quote {
        public String id;
        public String folderId;
        public String name;
        public String flute;
        public int length;
        public int width;
        public int depth;
        public String material;
        public double processRate;
        public int unitPrice;
        public long createdAt;
        public long updatedAt;

        public Quote copy() {
            Quote q = new Quote();
            q.id = id; q.folderId = folderId; q.name = name; q.flute = flute;
            q.length = length; q.width = width; q.depth = depth; q.material = material;
            q.processRate = processRate; q.unitPrice = unitPrice;
            q.createdAt = createdAt; q.updatedAt = updatedAt;
            return q;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<Folder> folders(Context c) {
        ArrayList<Folder> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY_FOLDERS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Folder(o.optString("id"), o.optString("name"), o.optLong("createdAt")));
            }
        } catch (Exception ignored) {}
        out.sort(Comparator.comparingLong(f -> f.createdAt));
        return out;
    }

    public static Folder createFolder(Context c, String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) clean = "新しいフォルダ";
        Folder f = new Folder(UUID.randomUUID().toString(), clean, System.currentTimeMillis());
        List<Folder> all = folders(c);
        all.add(f);
        saveFolders(c, all);
        return f;
    }

    public static void renameFolder(Context c, String id, String name) {
        List<Folder> all = folders(c);
        for (Folder f : all) if (f.id.equals(id)) f.name = name == null ? f.name : name.trim();
        saveFolders(c, all);
    }

    public static void deleteFolder(Context c, String id) {
        List<Folder> all = folders(c);
        all.removeIf(f -> f.id.equals(id));
        saveFolders(c, all);
        List<Quote> qs = allQuotes(c);
        qs.removeIf(q -> id.equals(q.folderId));
        saveQuotes(c, qs);
    }

    public static int countInFolder(Context c, String folderId) {
        int n = 0;
        for (Quote q : allQuotes(c)) if (folderId.equals(q.folderId)) n++;
        return n;
    }

    public static List<Quote> quotesInFolder(Context c, String folderId) {
        ArrayList<Quote> out = new ArrayList<>();
        for (Quote q : allQuotes(c)) if (folderId.equals(q.folderId)) out.add(q);
        out.sort((a,b) -> Long.compare(b.updatedAt, a.updatedAt));
        return out;
    }

    public static Quote getQuote(Context c, String id) {
        if (id == null) return null;
        for (Quote q : allQuotes(c)) if (id.equals(q.id)) return q;
        return null;
    }

    public static Quote save(Context c, Quote q, boolean asNew) {
        List<Quote> all = allQuotes(c);
        long now = System.currentTimeMillis();
        Quote saved = q.copy();
        if (asNew || saved.id == null || saved.id.isEmpty()) {
            saved.id = UUID.randomUUID().toString();
            saved.createdAt = now;
        } else {
            Quote old = getQuote(c, saved.id);
            if (old != null && old.createdAt > 0) saved.createdAt = old.createdAt;
            all.removeIf(x -> saved.id.equals(x.id));
        }
        saved.updatedAt = now;
        all.add(saved);
        saveQuotes(c, all);
        return saved;
    }

    public static void renameQuote(Context c, String id, String name) {
        List<Quote> all = allQuotes(c);
        for (Quote q : all) {
            if (id.equals(q.id)) {
                q.name = name == null ? q.name : name.trim();
                q.updatedAt = System.currentTimeMillis();
            }
        }
        saveQuotes(c, all);
    }

    public static void moveQuote(Context c, String id, String folderId) {
        List<Quote> all = allQuotes(c);
        for (Quote q : all) {
            if (id.equals(q.id)) {
                q.folderId = folderId;
                q.updatedAt = System.currentTimeMillis();
            }
        }
        saveQuotes(c, all);
    }

    public static void deleteQuote(Context c, String id) {
        List<Quote> all = allQuotes(c);
        all.removeIf(q -> id.equals(q.id));
        saveQuotes(c, all);
    }

    private static List<Quote> allQuotes(Context c) {
        ArrayList<Quote> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY_QUOTES, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Quote q = new Quote();
                q.id = o.optString("id");
                q.folderId = o.optString("folderId");
                q.name = o.optString("name");
                q.flute = o.optString("flute", "AF");
                q.length = o.optInt("length");
                q.width = o.optInt("width");
                q.depth = o.optInt("depth");
                q.material = o.optString("material");
                q.processRate = o.optDouble("processRate", 10.0);
                q.unitPrice = o.optInt("unitPrice");
                q.createdAt = o.optLong("createdAt");
                q.updatedAt = o.optLong("updatedAt");
                out.add(q);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void saveFolders(Context c, List<Folder> list) {
        JSONArray a = new JSONArray();
        try {
            for (Folder f : list) {
                JSONObject o = new JSONObject();
                o.put("id", f.id);
                o.put("name", f.name);
                o.put("createdAt", f.createdAt);
                a.put(o);
            }
        } catch (Exception ignored) {}
        prefs(c).edit().putString(KEY_FOLDERS, a.toString()).apply();
    }

    private static void saveQuotes(Context c, List<Quote> list) {
        JSONArray a = new JSONArray();
        try {
            for (Quote q : list) {
                JSONObject o = new JSONObject();
                o.put("id", q.id);
                o.put("folderId", q.folderId);
                o.put("name", q.name);
                o.put("flute", q.flute);
                o.put("length", q.length);
                o.put("width", q.width);
                o.put("depth", q.depth);
                o.put("material", q.material);
                o.put("processRate", q.processRate);
                o.put("unitPrice", q.unitPrice);
                o.put("createdAt", q.createdAt);
                o.put("updatedAt", q.updatedAt);
                a.put(o);
            }
        } catch (Exception ignored) {}
        prefs(c).edit().putString(KEY_QUOTES, a.toString()).apply();
    }
}
