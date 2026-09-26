package jp.ryu.silentpipcamera;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mediapipe.framework.image.BitmapExtractor;
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator;
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGeneratorResult;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int PICK_MODEL = 7001;
    private static final String[] MODEL_URLS = new String[] {
            "https://github.com/ShiftHackZ/Local-Diffusion-Models-SDAI-MediaPipe/releases/download/patch-26082024/stable-diffusion-v1-5.zip",
            "https://sdai-models.moroz.cc/SDAI/MediaPipe/stable-diffusion-v1-5.zip"
    };
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private EditText prompt;
    private EditText negativePrompt;
    private EditText steps;
    private EditText seed;
    private CheckBox filterSwitch;
    private CheckBox promptAssistSwitch;
    private Spinner outputSize;
    private TextView status;
    private TextView modelInfo;
    private ImageView image;
    private Button generate;
    private Button save;

    private ImageGenerator generator;
    private Bitmap lastBitmap;
    private File modelDir;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        modelDir = findInstalledModel();
        setContentView(buildUi());
        updateModelInfo();
        generate.setEnabled(false);
        if (modelDir != null) {
            initAsync();
        } else {
            autoInstallModel();
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(36));
        scroll.addView(root);

        root.addView(label("Anatomy Diffusion", 26, true), full());
        TextView intro = label("ON-DEVICE / OFFLINE / DEBUG BUILD", 13, false);
        intro.setAlpha(.7f);
        root.addView(intro, full());
        space(root, 12);

        modelInfo = label("", 13, true);
        root.addView(modelInfo, full());

        Button importModel = new Button(this);
        importModel.setText("モデルを再セットアップ");
        importModel.setOnClickListener(v -> autoInstallModel());
        root.addView(importModel, full());

        prompt = new EditText(this);
        prompt.setHint("Prompt");
        prompt.setMinLines(4);
        prompt.setGravity(Gravity.TOP);
        root.addView(prompt, full());

        negativePrompt = new EditText(this);
        negativePrompt.setHint("Negative Prompt（現在のMediaPipe backendでは未使用）");
        negativePrompt.setMinLines(2);
        root.addView(negativePrompt, full());

        promptAssistSwitch = new CheckBox(this);
        promptAssistSwitch.setText("Prompt Assist（日本語の構図・人物・背景を補助）");
        promptAssistSwitch.setChecked(true);
        root.addView(promptAssistSwitch, full());

        TextView outputLabel = label("出力サイズ", 13, true);
        root.addView(outputLabel, full());
        outputSize = new Spinner(this);
        ArrayAdapter<String> outputAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{
                        "1920 x 1920（正方形）",
                        "1920 x 1080（横 / Full HD）",
                        "1080 x 1920（縦 / Full HD）"
                });
        outputAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        outputSize.setAdapter(outputAdapter);
        root.addView(outputSize, full());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        steps = new EditText(this);
        steps.setHint("Steps");
        steps.setText("20");
        steps.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        row.addView(steps, new LinearLayout.LayoutParams(0, dp(60), 1f));

        seed = new EditText(this);
        seed.setHint("Seed");
        seed.setText(String.valueOf((int)(System.currentTimeMillis() & 0x7fffffff)));
        seed.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        row.addView(seed, new LinearLayout.LayoutParams(0, dp(60), 1f));
        root.addView(row, full());

        Button reseed = new Button(this);
        reseed.setText("Seedを更新");
        reseed.setOnClickListener(v ->
                seed.setText(String.valueOf((int)(System.currentTimeMillis() & 0x7fffffff))));
        root.addView(reseed, full());

        filterSwitch = new CheckBox(this);
        filterSwitch.setText("Local Filter path（初期OFF / デバッグ用）");
        filterSwitch.setChecked(false);
        root.addView(filterSwitch, full());

        TextView filterNote = label(
                "v0.1では判定ルールを入れず、ON時に文字列 BLOCK_TEST のみ停止します。生成エンジンの不具合とフィルタ経路を切り分けるための試験用です。",
                12, false);
        filterNote.setAlpha(.68f);
        root.addView(filterNote, full());

        generate = new Button(this);
        generate.setText("GENERATE");
        generate.setOnClickListener(v -> generateImage());
        root.addView(generate, full());

        status = label("待機中", 13, false);
        root.addView(status, full());
        space(root, 10);

        image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setBackgroundColor(0xff111318);
        root.addView(image, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(420)));

        save = new Button(this);
        save.setText("画像を保存");
        save.setEnabled(false);
        save.setOnClickListener(v -> saveImage());
        root.addView(save, full());

        TextView footer = label(
                "初回起動時に画像生成モデル（約1.9GB）を自動取得します。取得後の生成処理は端末内で実行します。生成後は選択サイズへ高品質リサイズして保存します。",
                12, false);
        footer.setAlpha(.65f);
        root.addView(footer, full());
        return scroll;
    }


    private void autoInstallModel() {
        generate.setEnabled(false);
        save.setEnabled(false);
        status.setText("モデル準備中… 初回のみ約1.9GBダウンロードします");
        worker.execute(() -> {
            File zipFile = new File(getCacheDir(), "stable-diffusion-v1-5.zip");
            File root = new File(getFilesDir(), "image_generator_model");
            Throwable lastError = null;
            try {
                closeGenerator();
                if (root.exists()) deleteTree(root);
                if (!root.mkdirs() && !root.isDirectory()) {
                    throw new IllegalStateException("モデル保存フォルダを作成できません");
                }

                boolean downloaded = false;
                for (String source : MODEL_URLS) {
                    try {
                        downloadModel(source, zipFile);
                        downloaded = true;
                        break;
                    } catch (Throwable t) {
                        lastError = t;
                        if (zipFile.exists()) zipFile.delete();
                    }
                }
                if (!downloaded) {
                    throw new IllegalStateException(
                            "モデルの自動取得に失敗しました",
                            lastError);
                }

                runOnUiThread(() -> status.setText("モデル展開中…"));
                unzipModel(zipFile, root);
                zipFile.delete();

                modelDir = detectModelDir(root);
                runOnUiThread(() -> {
                    updateModelInfo();
                    status.setText("モデル初期化中…");
                });

                initGenerator();
                runOnUiThread(() -> {
                    status.setText("準備完了");
                    generate.setEnabled(true);
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    status.setText("モデル準備失敗");
                    generate.setEnabled(false);
                    error("モデル準備失敗", t);
                });
            } finally {
                if (zipFile.exists()) zipFile.delete();
            }
        });
    }

    private void downloadModel(String source, File target) throws Exception {
        HttpURLConnection connection = openFollowingRedirects(source);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        connection.connect();

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + code);
        }

        final long total = connection.getContentLengthLong();
        long done = 0L;
        int lastPercent = -1;
        byte[] buffer = new byte[1024 * 1024];

        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = new FileOutputStream(target)) {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) continue;
                out.write(buffer, 0, n);
                done += n;

                if (total > 0) {
                    int percent = (int) Math.min(100L, (done * 100L) / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        final int p = percent;
                        final long mb = done / (1024L * 1024L);
                        final long totalMb = total / (1024L * 1024L);
                        runOnUiThread(() ->
                                status.setText("モデル取得中… " + p + "%  (" +
                                        mb + " / " + totalMb + " MB)"));
                    }
                } else {
                    final long mb = done / (1024L * 1024L);
                    runOnUiThread(() ->
                            status.setText("モデル取得中… " + mb + " MB"));
                }
            }
        } finally {
            connection.disconnect();
        }

        if (!target.isFile() || target.length() < 100L * 1024L * 1024L) {
            throw new IllegalStateException("モデルファイルが不完全です");
        }
    }

    private HttpURLConnection openFollowingRedirects(String source) throws Exception {
        URL url = new URL(source);
        for (int i = 0; i < 8; i++) {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "AnatomyDiffusion/0.2.1");
            c.setConnectTimeout(30000);
            c.setReadTimeout(60000);

            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = c.getHeaderField("Location");
                c.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new IllegalStateException("リダイレクト先がありません");
                }
                url = new URL(url, location);
                continue;
            }
            return c;
        }
        throw new IllegalStateException("リダイレクト回数が多すぎます");
    }

    private void unzipModel(File zipFile, File root) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buf = new byte[1024 * 1024];
            String safeRoot = root.getCanonicalPath() + File.separator;

            while ((entry = zip.getNextEntry()) != null) {
                File out = new File(root, entry.getName());
                String canonical = out.getCanonicalPath();
                if (!canonical.startsWith(safeRoot)) {
                    throw new IllegalStateException("ZIP内パスが不正です");
                }

                if (entry.isDirectory()) {
                    if (!out.mkdirs() && !out.isDirectory()) {
                        throw new IllegalStateException("フォルダ作成失敗");
                    }
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                        throw new IllegalStateException("フォルダ作成失敗");
                    }
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n;
                        while ((n = zip.read(buf)) > 0) {
                            os.write(buf, 0, n);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private void pickModel() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        startActivityForResult(i, PICK_MODEL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MODEL && resultCode == RESULT_OK &&
                data != null && data.getData() != null) {
            importModel(data.getData());
        }
    }

    private void importModel(Uri uri) {
        generate.setEnabled(false);
        status.setText("モデル展開中…");
        worker.execute(() -> {
            try {
                closeGenerator();
                File root = new File(getFilesDir(), "image_generator_model");
                deleteTree(root);
                if (!root.mkdirs() && !root.isDirectory()) {
                    throw new IllegalStateException("保存フォルダを作成できません");
                }

                InputStream input = getContentResolver().openInputStream(uri);
                if (input == null) throw new IllegalStateException("ZIPを開けません");

                try (ZipInputStream zip = new ZipInputStream(input)) {
                    ZipEntry entry;
                    byte[] buf = new byte[1024 * 1024];
                    String safeRoot = root.getCanonicalPath() + File.separator;
                    while ((entry = zip.getNextEntry()) != null) {
                        File out = new File(root, entry.getName());
                        if (!out.getCanonicalPath().startsWith(safeRoot)) {
                            throw new IllegalStateException("ZIP内パスが不正です");
                        }
                        if (entry.isDirectory()) {
                            if (!out.mkdirs() && !out.isDirectory()) {
                                throw new IllegalStateException("フォルダ作成失敗");
                            }
                        } else {
                            File parent = out.getParentFile();
                            if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                                throw new IllegalStateException("フォルダ作成失敗");
                            }
                            try (OutputStream os = new FileOutputStream(out)) {
                                int n;
                                while ((n = zip.read(buf)) > 0) os.write(buf, 0, n);
                            }
                        }
                        zip.closeEntry();
                    }
                }

                modelDir = detectModelDir(root);
                runOnUiThread(() -> {
                    updateModelInfo();
                    status.setText("モデル初期化中…");
                });
                initGenerator();
                runOnUiThread(() -> {
                    status.setText("準備完了");
                    generate.setEnabled(true);
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    status.setText("モデル読込失敗");
                    generate.setEnabled(true);
                    error("モデル読込失敗", t);
                });
            }
        });
    }

    private File detectModelDir(File root) {
        File bins = new File(root, "bins");
        if (bins.isDirectory()) return bins;
        File[] list = root.listFiles();
        if (list != null && list.length == 1 && list[0].isDirectory()) {
            File nestedBins = new File(list[0], "bins");
            if (nestedBins.isDirectory()) return nestedBins;
            return list[0];
        }
        return root;
    }

    private File findInstalledModel() {
        File root = new File(getFilesDir(), "image_generator_model");
        return root.isDirectory() ? detectModelDir(root) : null;
    }

    private void initAsync() {
        generate.setEnabled(false);
        status.setText("モデル初期化中…");
        worker.execute(() -> {
            try {
                initGenerator();
                runOnUiThread(() -> {
                    status.setText("準備完了");
                    generate.setEnabled(true);
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    status.setText("初期化失敗");
                    generate.setEnabled(true);
                    error("初期化失敗", t);
                });
            }
        });
    }

    private void initGenerator() {
        if (modelDir == null || !modelDir.isDirectory()) {
            throw new IllegalStateException("モデル未読込");
        }
        closeGenerator();
        ImageGenerator.ImageGeneratorOptions options =
                ImageGenerator.ImageGeneratorOptions.builder()
                        .setImageGeneratorModelDirectory(modelDir.getAbsolutePath())
                        .build();
        generator = ImageGenerator.createFromOptions(this, options);
    }

    private void generateImage() {
        String p = prompt.getText().toString().trim();
        if (p.isEmpty()) {
            Toast.makeText(this, "Promptを入力してください", Toast.LENGTH_SHORT).show();
            return;
        }
        if (filterSwitch.isChecked() && p.contains("BLOCK_TEST")) {
            status.setText("Local Filter pathで停止");
            return;
        }

        final String effectivePrompt = promptAssistSwitch.isChecked()
                ? enhancePrompt(p)
                : p;

        int stepValue;
        int seedValue;
        try {
            stepValue = Integer.parseInt(steps.getText().toString().trim());
            seedValue = Integer.parseInt(seed.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Steps / Seedを確認してください", Toast.LENGTH_SHORT).show();
            return;
        }
        stepValue = Math.max(1, Math.min(stepValue, 50));
        final int finalSteps = stepValue;

        generate.setEnabled(false);
        save.setEnabled(false);
        status.setText("生成中… " + finalSteps + " steps");

        worker.execute(() -> {
            try {
                if (generator == null) initGenerator();
                ImageGeneratorResult result = generator.generate(effectivePrompt, finalSteps, seedValue);
                if (result == null || result.generatedImage() == null) {
                    throw new IllegalStateException("生成結果が空です");
                }
                Bitmap bitmap = BitmapExtractor.extract(result.generatedImage());
                Bitmap exported = exportBitmap(bitmap);
                if (exported != bitmap && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                lastBitmap = exported;
                runOnUiThread(() -> {
                    image.setImageBitmap(exported);
                    status.setText("生成完了 • " + exported.getWidth() + " x " + exported.getHeight());
                    generate.setEnabled(true);
                    save.setEnabled(true);
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    status.setText("生成失敗");
                    generate.setEnabled(true);
                    error("生成失敗", t);
                });
            }
        });
    }

    private String enhancePrompt(String input) {
        StringBuilder extra = new StringBuilder();

        if (input.contains("日本人")) extra.append(", Japanese");
        if (input.contains("女性") || input.contains("女の子") || input.contains("美女")) extra.append(", adult woman");
        if (input.contains("男性") || input.contains("男")) extra.append(", adult man");
        if (input.contains("黒髪")) extra.append(", black hair");
        if (input.contains("白髪")) extra.append(", white hair");
        if (input.contains("金髪")) extra.append(", blonde hair");
        if (input.contains("座") ) extra.append(", sitting pose");
        if (input.contains("立") ) extra.append(", standing pose");
        if (input.contains("横顔")) extra.append(", profile view");
        if (input.contains("正面")) extra.append(", front view");
        if (input.contains("斜め")) extra.append(", three-quarter view");
        if (input.contains("見上げ") || input.contains("下から")) extra.append(", low angle");
        if (input.contains("見下ろし") || input.contains("上から")) extra.append(", high angle");
        if (input.contains("笑")) extra.append(", gentle smile");
        if (input.contains("砂浜") || input.contains("ビーチ")) extra.append(", beach");
        if (input.contains("海")) extra.append(", ocean background");
        if (input.contains("夏")) extra.append(", summer");
        if (input.contains("夜")) extra.append(", night scene");
        if (input.contains("雨")) extra.append(", rainy atmosphere");
        if (input.contains("室内")) extra.append(", indoor scene");

        boolean illustration = input.contains("アニメ") ||
                input.contains("イラスト") ||
                input.toLowerCase().contains("anime");

        if (illustration) {
            extra.append(", high detail illustration, clean composition, coherent anatomy, detailed hands, balanced proportions");
        } else {
            extra.append(", highly detailed, coherent composition, natural anatomy, detailed hands, realistic proportions, natural lighting, sharp focus");
        }

        return input + extra;
    }

    private Bitmap exportBitmap(Bitmap src) {
        int targetW = 1920;
        int targetH = 1920;
        int selected = outputSize == null ? 0 : outputSize.getSelectedItemPosition();
        if (selected == 1) {
            targetW = 1920;
            targetH = 1080;
        } else if (selected == 2) {
            targetW = 1080;
            targetH = 1920;
        }

        float srcAspect = src.getWidth() / (float) src.getHeight();
        float targetAspect = targetW / (float) targetH;

        int cropW = src.getWidth();
        int cropH = src.getHeight();
        int x = 0;
        int y = 0;

        if (srcAspect > targetAspect) {
            cropW = Math.max(1, Math.round(src.getHeight() * targetAspect));
            x = Math.max(0, (src.getWidth() - cropW) / 2);
        } else if (srcAspect < targetAspect) {
            cropH = Math.max(1, Math.round(src.getWidth() / targetAspect));
            y = Math.max(0, (src.getHeight() - cropH) / 2);
        }

        Bitmap cropped = Bitmap.createBitmap(src, x, y, cropW, cropH);
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true);
        if (cropped != src && cropped != scaled && !cropped.isRecycled()) {
            cropped.recycle();
        }
        return scaled;
    }

    private void saveImage() {
        Bitmap bitmap = lastBitmap;
        if (bitmap == null) return;
        worker.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,
                        "anatomy_" + System.currentTimeMillis() + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/AnatomyDiffusion");
                Uri uri = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("保存先作成失敗");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw new IllegalStateException("PNG保存失敗");
                    }
                }
                runOnUiThread(() ->
                        Toast.makeText(this, "画像を保存しました", Toast.LENGTH_LONG).show());
            } catch (Throwable t) {
                runOnUiThread(() -> error("保存失敗", t));
            }
        });
    }

    private void updateModelInfo() {
        modelInfo.setText(modelDir == null ? "MODEL: 未読込" :
                "MODEL: " + modelDir.getAbsolutePath());
    }

    private void closeGenerator() {
        ImageGenerator g = generator;
        generator = null;
        if (g != null) {
            try { g.close(); } catch (Throwable ignored) {}
        }
    }

    private void error(String title, Throwable t) {
        String msg = t.getClass().getSimpleName() + ": " +
                (t.getMessage() == null ? "(no message)" : t.getMessage());
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] c = f.listFiles();
            if (c != null) for (File x : c) deleteTree(x);
        }
        f.delete();
    }

    private TextView label(String s, int size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setPadding(0, dp(4), 0, dp(4));
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void space(LinearLayout root, int amount) {
        View v = new View(this);
        root.addView(v, new LinearLayout.LayoutParams(1, dp(amount)));
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        closeGenerator();
        worker.shutdownNow();
        super.onDestroy();
    }
}
