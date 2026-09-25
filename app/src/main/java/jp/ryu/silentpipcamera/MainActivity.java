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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mediapipe.framework.image.BitmapExtractor;
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator;
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGeneratorResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int PICK_MODEL = 7001;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private EditText prompt;
    private EditText negativePrompt;
    private EditText steps;
    private EditText seed;
    private CheckBox filterSwitch;
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
        if (modelDir != null) initAsync();
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
        importModel.setText("モデルZIPを読み込む");
        importModel.setOnClickListener(v -> pickModel());
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
                "モデル本体はAPKに含みません。変換済みMediaPipe Image GeneratorモデルをZIPで読み込んで端末内に展開します。",
                12, false);
        footer.setAlpha(.65f);
        root.addView(footer, full());
        return scroll;
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
                ImageGeneratorResult result = generator.generate(p, finalSteps, seedValue);
                if (result == null || result.generatedImage() == null) {
                    throw new IllegalStateException("生成結果が空です");
                }
                Bitmap bitmap = BitmapExtractor.extract(result.generatedImage());
                lastBitmap = bitmap;
                runOnUiThread(() -> {
                    image.setImageBitmap(bitmap);
                    status.setText("生成完了");
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
