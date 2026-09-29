package com.scsonic.qwenimage21.demo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class NoctOverlayDownloader {
    public static final String VERSION = "noct-q-mnn-v1";
    private static final String BASE =
            "https://github.com/ryu4696-dev/3/releases/download/" + VERSION + "/";
    private static final String READY = ".noct-q-mnn-v1.ready";

    public interface Listener {
        void onProgress(String file, long done, long total);
    }

    private volatile boolean cancelled;

    public void cancel() { cancelled = true; }

    public static boolean isInstalled(File modelDir) {
        return new File(modelDir, READY).isFile();
    }

    public void download(File modelDir, Listener listener) throws Exception {
        cancelled = false;
        modelDir.mkdirs();
        JSONObject manifest = new JSONObject(readText(BASE + "manifest.json"));
        if (!VERSION.equals(manifest.optString("version"))) {
            throw new IOException("unexpected model manifest version");
        }
        JSONObject files = manifest.getJSONObject("files");

        long total = 0;
        Iterator<String> it = files.keys();
        while (it.hasNext()) {
            String name = it.next();
            JSONObject f = files.getJSONObject(name);
            if ("dit.mnn.weight".equals(name)) {
                JSONArray parts = f.getJSONArray("parts");
                for (int i = 0; i < parts.length(); i++) total += parts.getJSONObject(i).getLong("size");
            } else {
                total += f.getLong("size");
            }
        }

        long[] done = {0};
        List<String> simple = new ArrayList<>();
        it = files.keys();
        while (it.hasNext()) {
            String name = it.next();
            if (!"dit.mnn.weight".equals(name)) simple.add(name);
        }
        for (String name : simple) {
            JSONObject f = files.getJSONObject(name);
            downloadFinalAsset(modelDir, name, f.getLong("size"), f.getString("sha256"), done, total, listener);
        }

        JSONObject dit = files.getJSONObject("dit.mnn.weight");
        File finalDit = new File(modelDir, "dit.mnn.weight");
        long ditSize = dit.getLong("size");
        String ditSha = dit.getString("sha256");
        if (verified(finalDit, ditSize, ditSha)) {
            done[0] += ditSize;
            report(listener, "dit.mnn.weight", done[0], total);
        } else {
            File partDir = new File(modelDir, ".noct_parts");
            partDir.mkdirs();
            JSONArray parts = dit.getJSONArray("parts");
            List<File> localParts = new ArrayList<>();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject p = parts.getJSONObject(i);
                String name = p.getString("name");
                File dst = new File(partDir, name);
                downloadRaw(name, dst, p.getLong("size"), p.getString("sha256"), done, total, listener);
                localParts.add(dst);
            }

            File combined = new File(finalDit.getPath() + ".noct.part");
            if (combined.exists()) combined.delete();
            byte[] buf = new byte[8 << 20];
            try (FileOutputStream out = new FileOutputStream(combined)) {
                for (File p : localParts) {
                    try (FileInputStream in = new FileInputStream(p)) {
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                }
            }
            if (combined.length() != ditSize || !sha256(combined).equalsIgnoreCase(ditSha)) {
                combined.delete();
                throw new IOException("dit.mnn.weight checksum mismatch");
            }
            if (finalDit.exists() && !finalDit.delete()) throw new IOException("cannot replace dit.mnn.weight");
            if (!combined.renameTo(finalDit)) throw new IOException("cannot install dit.mnn.weight");
            writeMarker(finalDit, ditSha);
            for (File p : localParts) p.delete();
            partDir.delete();
            report(listener, "dit.mnn.weight ready", done[0], total);
        }

        try (FileOutputStream out = new FileOutputStream(new File(modelDir, READY))) {
            out.write(VERSION.getBytes(StandardCharsets.US_ASCII));
        }
    }

    private void downloadFinalAsset(File modelDir, String name, long size, String sha, long[] done, long total,
                                    Listener listener) throws Exception {
        File dst = new File(modelDir, name);
        if (verified(dst, size, sha)) {
            done[0] += size;
            report(listener, name, done[0], total);
            return;
        }
        File tmp = new File(dst.getPath() + ".noct.download");
        downloadRaw(name, tmp, size, sha, done, total, listener);
        if (dst.exists() && !dst.delete()) throw new IOException("cannot replace " + name);
        dst.getParentFile().mkdirs();
        if (!tmp.renameTo(dst)) throw new IOException("cannot install " + name);
        writeMarker(dst, sha);
    }

    private void downloadRaw(String asset, File dst, long size, String sha, long[] done, long total,
                             Listener listener) throws Exception {
        dst.getParentFile().mkdirs();
        if (dst.isFile() && dst.length() == size && sha256(dst).equalsIgnoreCase(sha)) {
            done[0] += size;
            report(listener, asset, done[0], total);
            return;
        }
        if (dst.length() > size) dst.delete();
        long have = dst.isFile() ? dst.length() : 0;
        HttpURLConnection c = open(BASE + asset);
        if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
        int code = c.getResponseCode();
        boolean append = code == 206 && have > 0;
        if (code != 200 && code != 206) throw new IOException("HTTP " + code + " for " + asset);
        if (!append) have = 0;
        long baseDone = done[0];
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(dst, append)) {
            byte[] buf = new byte[1 << 20];
            long got = have, last = have;
            int n;
            while ((n = in.read(buf)) > 0) {
                if (cancelled) throw new IOException("cancelled");
                out.write(buf, 0, n);
                got += n;
                if (got - last >= (8L << 20)) {
                    last = got;
                    report(listener, asset, baseDone + got, total);
                }
            }
        } finally {
            c.disconnect();
        }
        if (dst.length() != size) throw new IOException("incomplete download: " + asset);
        if (!sha256(dst).equalsIgnoreCase(sha)) {
            dst.delete();
            throw new IOException("checksum mismatch: " + asset);
        }
        done[0] += size;
        report(listener, asset, done[0], total);
    }

    private static boolean verified(File f, long size, String sha) {
        if (!f.isFile() || f.length() != size) return false;
        File marker = new File(f.getPath() + ".noct.sha256");
        if (!marker.isFile()) return false;
        try {
            String got = new String(readAll(marker), StandardCharsets.US_ASCII).trim();
            return got.equalsIgnoreCase(sha);
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeMarker(File f, String sha) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f.getPath() + ".noct.sha256")) {
            out.write(sha.getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static void report(Listener l, String file, long done, long total) {
        if (l != null) l.onProgress(file, done, total);
    }

    private static String readText(String url) throws Exception {
        HttpURLConnection c = open(url);
        int code = c.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " for manifest");
        try (InputStream in = c.getInputStream()) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setConnectTimeout(30000);
        c.setReadTimeout(120000);
        return c;
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8 << 20];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder s = new StringBuilder();
        for (byte b : md.digest()) s.append(String.format("%02x", b));
        return s.toString();
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) { return readAll(in); }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
