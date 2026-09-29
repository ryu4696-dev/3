package com.scsonic.qwenimage21.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.scsonic.qwenimage21.ModelDownloader;
import com.scsonic.qwenimage21.QwenImage21;
import com.scsonic.qwenimage21.QwenImage21Exception;

import java.io.File;

public class MainActivity extends Activity {
    private EditText prompt, steps, seed;
    private Spinner ratio, tier;
    private CheckBox gpu, teCpu;
    private Button download, generate;
    private ProgressBar progress;
    private TextView status, sizeInfo;
    private ImageView image;
    private File modelDir, crashMarker;
    private QwenImage21 model;
    private String modelKey;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        prompt = findViewById(R.id.prompt);
        steps = findViewById(R.id.steps);
        seed = findViewById(R.id.seed);
        ratio = findViewById(R.id.ratio);
        tier = findViewById(R.id.tier);
        gpu = findViewById(R.id.gpu);
        teCpu = findViewById(R.id.te_cpu);
        download = findViewById(R.id.download);
        generate = findViewById(R.id.generate);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.status);
        sizeInfo = findViewById(R.id.size_info);
        image = findViewById(R.id.image);
        image.setBackgroundColor(Color.rgb(0x20, 0x20, 0x20));

        modelDir = new File(getExternalFilesDir(null), "qwen_image21");
        crashMarker = new File(getFilesDir(), "generation_in_progress.txt");

        ratio.setAdapter(adapter(QwenImage21.Size.Ratio.values()));
        tier.setAdapter(adapter(QwenImage21.Size.Tier.values()));
        ratio.setSelection(0);
        tier.setSelection(1);
        ratio.setOnItemSelectedListener(new SimpleItemSelected(this::showSize));
        tier.setOnItemSelectedListener(new SimpleItemSelected(this::showSize));
        showSize();

        prompt.setText("An anime illustration of an adult woman in a softly lit room, natural pose, detailed eyes, clean line art, cinematic composition.");
        download.setOnClickListener(v -> startDownload());
        generate.setOnClickListener(v -> startGeneration());

        String crash = QwenImage21.readCrashMarker(crashMarker);
        if (crash != null) {
            new AlertDialog.Builder(this)
                    .setTitle("前回の生成が中断されました")
                    .setMessage(crash + "\n\nメモリ不足の可能性があります。Fast/Tinyを使うと軽くなります。")
                    .setPositiveButton("OK", null).show();
        }
        refresh();
    }

    @Override
    protected void onDestroy() {
        if (model != null) model.close();
        super.onDestroy();
    }

    private <T> ArrayAdapter<T> adapter(T[] values) {
        ArrayAdapter<T> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private QwenImage21.Size selectedSize() {
        return QwenImage21.Size.of((QwenImage21.Size.Ratio) ratio.getSelectedItem(),
                (QwenImage21.Size.Tier) tier.getSelectedItem());
    }

    private void showSize() {
        if (ratio == null || tier == null || ratio.getSelectedItem() == null || tier.getSelectedItem() == null) return;
        QwenImage21.Size s = selectedSize();
        sizeInfo.setText(String.format("出力 %d×%d / %d latent tokens", s.width, s.height, s.tokens()));
    }

    private boolean refresh() {
        boolean noct = NoctOverlayDownloader.isInstalled(modelDir);
        String missing = QwenImage21.missingStandardDitFiles(modelDir);
        boolean ready = noct && missing == null;
        if (ready) {
            status.setText("Noct-Q Anime V1: 準備完了\n保存先: " + modelDir
                    + "\n空きメモリ: " + QwenImage21.availableMemoryMB() + " MB");
        } else {
            status.setText("Noct-Qモデルは未導入です。\n「モデルを取得 / 修復」を押すと必要ファイルを自動取得します。"
                    + "\n初回の通信量は約15GBです。");
        }
        generate.setEnabled(ready);
        return ready;
    }

    private void setBusy(boolean busy) {
        download.setEnabled(!busy);
        generate.setEnabled(!busy && NoctOverlayDownloader.isInstalled(modelDir));
        ratio.setEnabled(!busy);
        tier.setEnabled(!busy);
        steps.setEnabled(!busy);
        seed.setEnabled(!busy);
        gpu.setEnabled(!busy);
        teCpu.setEnabled(!busy);
    }

    private void startDownload() {
        setBusy(true);
        progress.setProgress(0);
        if (model != null) {
            model.close();
            model = null;
            modelKey = null;
        }
        new Thread(() -> {
            try {
                runOnUiThread(() -> status.setText("Qwen共通モデルを取得中…"));
                new ModelDownloader().download(modelDir, true, false, false, (file, done, total) ->
                        runOnUiThread(() -> {
                            progress.setProgress((int) (100 * done / Math.max(1, total)));
                            status.setText(String.format("共通モデル: %s\n%.2f / %.2f GB",
                                    file.replaceFirst("^verifying ", ""), done / 1e9, total / 1e9));
                        }));

                runOnUiThread(() -> {
                    progress.setProgress(0);
                    status.setText("Noct-Q専用重みを取得中…");
                });
                new NoctOverlayDownloader().download(modelDir, (file, done, total) ->
                        runOnUiThread(() -> {
                            progress.setProgress((int) (100 * done / Math.max(1, total)));
                            status.setText(String.format("Noct-Q: %s\n%.2f / %.2f GB",
                                    file, done / 1e9, total / 1e9));
                        }));

                runOnUiThread(() -> {
                    progress.setProgress(100);
                    refresh();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("モデル取得に失敗: " + e.getMessage()
                            + "\n同じボタンを押せば途中から再開します。");
                    new AlertDialog.Builder(this)
                            .setTitle("モデル取得エラー")
                            .setMessage(String.valueOf(e.getMessage()))
                            .setPositiveButton("OK", null).show();
                });
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        }, "noct-model-download").start();
    }

    private void startGeneration() {
        if (!refresh()) return;
        final String text = prompt.getText().toString().trim();
        if (text.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("プロンプトが空です。").setPositiveButton("OK", null).show();
            return;
        }
        final int nSteps = Math.max(1, Math.min(100, parse(steps, 25)));
        final int nSeed = parse(seed, 42);
        final QwenImage21.Size sz = selectedSize();
        final QwenImage21.Options options = new QwenImage21.Options();
        options.useGpu = gpu.isChecked();
        options.textEncoderOnCpu = teCpu.isChecked();
        options.vaeOnCpu = true;
        options.keepModelsLoaded = false;
        options.turbo = false;
        options.crashMarkerFile = crashMarker;
        final String key = options.useGpu + "," + options.textEncoderOnCpu;
        final File out = new File(getExternalFilesDir(null),
                "outputs/noct_" + System.currentTimeMillis() + ".png");

        setBusy(true);
        progress.setProgress(0);
        status.setText("生成中… " + sz.width + "×" + sz.height + " / " + nSteps + " steps");
        final long start = SystemClock.elapsedRealtime();

        new Thread(() -> {
            Bitmap bmp = null;
            Throwable error = null;
            try {
                if (model == null || !key.equals(modelKey)) {
                    if (model != null) model.close();
                    model = new QwenImage21(modelDir, options);
                    modelKey = key;
                }
                bmp = model.generate(text, sz, nSteps, nSeed, out,
                        p -> runOnUiThread(() -> progress.setProgress(p)));
            } catch (Throwable e) {
                error = e;
                if (model != null) {
                    try { model.close(); } catch (Exception ignored) {}
                    model = null;
                    modelKey = null;
                }
            }

            final Bitmap result = bmp;
            final Throwable err = error;
            final double sec = (SystemClock.elapsedRealtime() - start) / 1000.0;
            runOnUiThread(() -> {
                setBusy(false);
                if (result != null) {
                    image.setImageBitmap(result);
                    progress.setProgress(100);
                    status.setText(String.format("完了 %.1f秒 / seed %d\n%s", sec, nSeed, out));
                } else {
                    String msg = err == null ? "unknown error" : String.valueOf(err.getMessage());
                    status.setText(String.format("生成失敗 %.1f秒\n%s", sec, msg));
                    new AlertDialog.Builder(this)
                            .setTitle(err instanceof QwenImage21Exception
                                    && ((QwenImage21Exception) err).isOutOfMemory() ? "メモリ不足" : "生成エラー")
                            .setMessage(msg + "\n\nFast/Tinyへの変更で改善する場合があります。")
                            .setPositiveButton("OK", null).show();
                }
            });
        }, "noct-generate").start();
    }

    private static int parse(EditText e, int def) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (Exception ex) { return def; }
    }

    private static final class SimpleItemSelected implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable r;
        SimpleItemSelected(Runnable r) { this.r = r; }
        @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { r.run(); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
    }
}
