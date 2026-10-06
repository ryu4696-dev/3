package jp.co.ichika.salesledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QuoteWorkspaceActivity extends Activity {
    private final int GREEN = Color.rgb(15, 91, 70);
    private final int GREEN_DARK = Color.rgb(10, 74, 56);
    private final int TEXT = Color.rgb(20, 28, 43);
    private final int SUB = Color.rgb(94, 104, 120);
    private final int BG = Color.rgb(248, 247, 243);
    private final int BORDER = Color.rgb(215, 220, 226);

    private EditText lengthEdit, widthEdit, depthEdit, processEdit;
    private TextView materialButton, unitText, detailText, editBanner;
    private LinearLayout detailCard, fluteRow, saveArea;

    private String selectedFlute = "AF";
    private MaterialInfo selectedMaterial;
    private final List<MaterialInfo> materials = new ArrayList<>();

    private QuoteCalc lastCalc;
    private String editingId;
    private String editingFolderId;
    private String editingName;
    private boolean launchAsNew;

    private static final class MaterialInfo {
        final String name;
        final Object raw;
        final double abc;
        final double wf;
        MaterialInfo(String name, Object raw, double abc, double wf) {
            this.name = name; this.raw = raw; this.abc = abc; this.wf = wf;
        }
    }

    private static final class QuoteCalc {
        int flowTotal;
        double widthTotal;
        int paperWidth;
        int up;
        double totalArea;
        double areaPerPiece;
        int unitPrice;
        double materialRate;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.rgb(247, 243, 234));
        w.setNavigationBarColor(Color.WHITE);

        loadMaterials();
        if (materials.isEmpty()) {
            Toast.makeText(this, "簡易見積の材質データを読み込めませんでした", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        selectedMaterial = materials.get(0);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        root.addView(appBar());
        editBanner = label("", 11.5f, GREEN_DARK, true);
        editBanner.setVisibility(View.GONE);
        editBanner.setPadding(dp(10), dp(8), dp(10), dp(8));
        editBanner.setBackground(round(Color.rgb(234, 246, 239), Color.rgb(190, 219, 205), 10));
        root.addView(editBanner, lp(-1, -2, 10, 0));

        root.addView(section("フルート"), lp(-1, -2, 18, 6));
        fluteRow = new LinearLayout(this);
        fluteRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(fluteRow);
        renderFlutes();

        root.addView(section("寸法（mm）"), lp(-1, -2, 16, 6));
        LinearLayout dims = new LinearLayout(this);
        dims.setOrientation(LinearLayout.HORIZONTAL);
        lengthEdit = numEdit("長", false);
        widthEdit = numEdit("巾", false);
        depthEdit = numEdit("深", false);
        dims.addView(lengthEdit, weight(1, 0));
        dims.addView(widthEdit, weight(1, 8));
        dims.addView(depthEdit, weight(1, 8));
        root.addView(dims);

        root.addView(section("材質"), lp(-1, -2, 16, 6));
        materialButton = label(displayMaterial(selectedMaterial.name) + "   ▼", 16.5f, TEXT, false);
        materialButton.setGravity(Gravity.CENTER_VERTICAL);
        materialButton.setPadding(dp(15), 0, dp(15), 0);
        materialButton.setBackground(fieldBg());
        materialButton.setOnClickListener(v -> showMaterialDialog());
        root.addView(materialButton, new LinearLayout.LayoutParams(-1, dp(58)));

        root.addView(section("加工賃（円 / ㎡）"), lp(-1, -2, 16, 6));
        processEdit = numEdit("加工賃", true);
        processEdit.setText("10");
        root.addView(processEdit, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout priceCard = card();
        priceCard.addView(label("見積単価", 14f, SUB, true));
        unitText = label("— 円", 35f, GREEN_DARK, true);
        unitText.setPadding(0, dp(4), 0, 0);
        priceCard.addView(unitText);
        root.addView(priceCard, lp(-1, -2, 20, 0));

        detailCard = card();
        detailCard.setVisibility(View.GONE);
        detailCard.addView(label("計算詳細", 14f, SUB, true));
        detailText = label("", 13f, TEXT, false);
        detailText.setLineSpacing(dp(2), 1.12f);
        detailText.setPadding(0, dp(9), 0, 0);
        detailCard.addView(detailText);
        root.addView(detailCard, lp(-1, -2, 12, 0));

        TextView calc = action("計算する", true);
        calc.setOnClickListener(v -> calculateAndRender(true));
        root.addView(calc, lp(-1, dp(54), 14, 0));

        TextView show = action("金額を見せる", false);
        show.setOnClickListener(v -> {
            if (calculateAndRender(true)) showResult();
        });
        root.addView(show, lp(-1, dp(54), 8, 0));

        saveArea = new LinearLayout(this);
        saveArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(saveArea, lp(-1, -2, 12, 0));
        renderSaveArea();

        TextWatcher dirty = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { invalidateCalc(); }
            public void afterTextChanged(Editable e) {}
        };
        lengthEdit.addTextChangedListener(dirty);
        widthEdit.addTextChangedListener(dirty);
        depthEdit.addTextChangedListener(dirty);
        processEdit.addTextChangedListener(dirty);

        loadEditingRecord();
    }

    private LinearLayout appBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = label("‹ 戻る", 16f, GREEN_DARK, true);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(80), dp(48)));

        TextView title = label("簡易見積", 25f, TEXT, true);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView history = label("保存一覧", 13f, GREEN_DARK, true);
        history.setGravity(Gravity.CENTER);
        history.setPadding(dp(10), 0, dp(10), 0);
        history.setBackground(round(Color.rgb(234,246,239), Color.rgb(192,220,207), 10));
        history.setOnClickListener(v -> startActivity(new Intent(this, QuoteHistoryActivity.class)));
        row.addView(history, new LinearLayout.LayoutParams(dp(82), dp(38)));
        return row;
    }

    private void renderFlutes() {
        fluteRow.removeAllViews();
        String[] labels = {"AF", "BF", "CF", "WF"};
        for (int i = 0; i < labels.length; i++) {
            String f = labels[i];
            boolean selected = f.equals(selectedFlute);
            TextView v = label(f, 17f, selected ? Color.WHITE : GREEN_DARK, true);
            v.setGravity(Gravity.CENTER);
            v.setBackground(round(selected ? GREEN_DARK : Color.WHITE, selected ? GREEN_DARK : BORDER, 13));
            v.setOnClickListener(x -> {
                selectedFlute = f;
                renderFlutes();
                invalidateCalc();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(54), 1f);
            if (i > 0) p.leftMargin = dp(7);
            fluteRow.addView(v, p);
        }
    }

    private void renderSaveArea() {
        saveArea.removeAllViews();
        boolean editing = editingId != null && !editingId.isEmpty() && !launchAsNew;
        if (editing) {
            TextView overwrite = action("上書き保存", true);
            overwrite.setOnClickListener(v -> saveExisting());
            saveArea.addView(overwrite, new LinearLayout.LayoutParams(-1, dp(52)));

            TextView asNew = action("新規で保存", false);
            asNew.setOnClickListener(v -> chooseFolderAndSave(true));
            saveArea.addView(asNew, lp(-1, dp(52), 8, 0));
        } else {
            TextView save = action("保存", true);
            save.setOnClickListener(v -> chooseFolderAndSave(true));
            saveArea.addView(save, new LinearLayout.LayoutParams(-1, dp(52)));
        }

        TextView history = action("保存一覧を開く", false);
        history.setOnClickListener(v -> startActivity(new Intent(this, QuoteHistoryActivity.class)));
        saveArea.addView(history, lp(-1, dp(52), 8, 0));
    }

    private void loadEditingRecord() {
        String id = getIntent().getStringExtra("quote_id");
        launchAsNew = getIntent().getBooleanExtra("save_as_new", false);
        if (id == null || id.isEmpty()) return;

        QuoteHistoryStore.Quote q = QuoteHistoryStore.getQuote(this, id);
        if (q == null) return;

        editingId = launchAsNew ? null : q.id;
        editingFolderId = q.folderId;
        editingName = launchAsNew ? q.name + " コピー" : q.name;

        selectedFlute = q.flute == null || q.flute.isEmpty() ? "AF" : q.flute;
        MaterialInfo m = findMaterial(q.material);
        if (m != null) selectedMaterial = m;

        lengthEdit.setText(String.valueOf(q.length));
        widthEdit.setText(String.valueOf(q.width));
        depthEdit.setText(String.valueOf(q.depth));
        processEdit.setText(trimNumber(q.processRate));
        materialButton.setText(displayMaterial(selectedMaterial.name) + "   ▼");
        renderFlutes();

        editBanner.setVisibility(View.VISIBLE);
        editBanner.setText(launchAsNew
                ? "複製して編集中　元の保存データは変更しません"
                : "保存データを編集中　「新規で保存」なら元データは残ります");
        renderSaveArea();
        calculateAndRender(false);
    }

    private boolean calculateAndRender(boolean toastErrors) {
        try {
            int l = parseInt(lengthEdit);
            int w = parseInt(widthEdit);
            int d = parseInt(depthEdit);
            double process = parseDouble(processEdit);
            if (l <= 0 || w <= 0 || d <= 0) throw new IllegalArgumentException("寸法を入力してください");
            if (process < 0) throw new IllegalArgumentException("加工賃を確認してください");

            Class<?> fluteClass = Class.forName("jp.co.kobayashi.cardboardquote.Flute");
            Object flute = Enum.valueOf((Class) fluteClass, selectedFlute);
            Class<?> materialClass = Class.forName("jp.co.kobayashi.cardboardquote.Material");
            Class<?> inputClass = Class.forName("jp.co.kobayashi.cardboardquote.QuoteInput");
            Constructor<?> ctor = inputClass.getConstructor(fluteClass, int.class, int.class, int.class, materialClass, double.class);
            Object input = ctor.newInstance(flute, l, w, d, selectedMaterial.raw, process);

            Class<?> calcClass = Class.forName("jp.co.kobayashi.cardboardquote.Calculator");
            Object calcInstance = calcClass.getField("INSTANCE").get(null);
            Method calculate = calcClass.getMethod("calculate", inputClass);
            Object result = calculate.invoke(calcInstance, input);

            QuoteCalc qc = new QuoteCalc();
            qc.flowTotal = ((Number) result.getClass().getMethod("getFlowTotal").invoke(result)).intValue();
            qc.widthTotal = ((Number) result.getClass().getMethod("getWidthTotal").invoke(result)).doubleValue();
            qc.paperWidth = ((Number) result.getClass().getMethod("getPaperWidth").invoke(result)).intValue();
            qc.up = ((Number) result.getClass().getMethod("getUp").invoke(result)).intValue();
            qc.totalArea = ((Number) result.getClass().getMethod("getTotalArea").invoke(result)).doubleValue();
            qc.areaPerPiece = ((Number) result.getClass().getMethod("getAreaPerPiece").invoke(result)).doubleValue();
            qc.unitPrice = ((Number) result.getClass().getMethod("getUnitPrice").invoke(result)).intValue();
            qc.materialRate = "WF".equals(selectedFlute) ? selectedMaterial.wf : selectedMaterial.abc;
            lastCalc = qc;

            unitText.setText(nf(qc.unitPrice) + " 円");
            detailText.setText(detailString(qc, process, l, w, d));
            detailCard.setVisibility(View.VISIBLE);
            return true;
        } catch (Exception ex) {
            lastCalc = null;
            unitText.setText("— 円");
            detailCard.setVisibility(View.GONE);
            if (toastErrors) {
                String msg = ex.getCause() != null && ex.getCause().getMessage() != null
                        ? ex.getCause().getMessage()
                        : ex.getMessage();
                Toast.makeText(this, msg == null ? "計算できませんでした" : msg, Toast.LENGTH_LONG).show();
            }
            return false;
        }
    }

    private String detailString(QuoteCalc r, double process, int l, int w, int d) {
        int glue = "WF".equals(selectedFlute) ? 35 : 32;
        int panelAdjust = "CF".equals(selectedFlute) ? -2 : -3;
        int flapAdjust = "AF".equals(selectedFlute) ? 4 : ("BF".equals(selectedFlute) ? 2 : ("CF".equals(selectedFlute) ? 3 : 6));
        int panel4 = w + panelAdjust;
        double flap = (w + flapAdjust) / 2.0;
        return "展開寸法\n"
                + "糊代        " + glue + " mm\n"
                + "一面        " + l + " mm\n"
                + "二面        " + w + " mm\n"
                + "三面        " + l + " mm\n"
                + "四面        " + panel4 + " mm\n"
                + "落ち          7 mm\n"
                + "流れ合計    " + r.flowTotal + " mm\n\n"
                + "上フラップ  " + trimNumber(flap) + " mm\n"
                + "深          " + d + " mm\n"
                + "下フラップ  " + trimNumber(flap) + " mm\n"
                + "巾合計      " + trimNumber(r.widthTotal) + " mm\n\n"
                + "製造計算\n"
                + "採用紙巾    " + r.paperWidth + " mm\n"
                + "丁取り        " + r.up + " 丁\n"
                + "全体面積    " + fmt3(r.totalArea) + " ㎡\n"
                + "1個面積     " + fmt3(r.areaPerPiece) + " ㎡\n\n"
                + "単価計算\n"
                + "材質単価    " + trimNumber(r.materialRate) + " 円/㎡\n"
                + "加工賃      " + trimNumber(process) + " 円/㎡\n"
                + "合計単価    " + trimNumber(r.materialRate + process) + " 円/㎡";
    }

    private void showResult() {
        if (lastCalc == null) return;
        Intent i = new Intent();
        i.setClassName(getPackageName(), "jp.co.kobayashi.cardboardquote.QuoteActivity");
        i.putExtra("flute", selectedFlute);
        i.putExtra("l", parseInt(lengthEdit));
        i.putExtra("w", parseInt(widthEdit));
        i.putExtra("d", parseInt(depthEdit));
        i.putExtra("material", selectedMaterial.name);
        i.putExtra("unit", lastCalc.unitPrice);
        startActivity(i);
    }

    private void saveExisting() {
        if (editingId == null || editingId.isEmpty()) {
            chooseFolderAndSave(true);
            return;
        }
        if (!calculateAndRender(true)) return;
        QuoteHistoryStore.Quote q = buildQuote();
        q.id = editingId;
        q.folderId = editingFolderId;
        q.name = editingName == null || editingName.trim().isEmpty() ? defaultName() : editingName.trim();
        QuoteHistoryStore.Quote saved = QuoteHistoryStore.save(this, q, false);
        editingId = saved.id;
        Toast.makeText(this, "上書き保存しました", Toast.LENGTH_SHORT).show();
    }

    private void chooseFolderAndSave(boolean asNew) {
        if (!calculateAndRender(true)) return;
        List<QuoteHistoryStore.Folder> folders = QuoteHistoryStore.folders(this);
        if (folders.isEmpty()) {
            promptNewFolder(folder -> promptNameAndSave(folder, asNew));
            return;
        }
        ArrayList<String> labels = new ArrayList<>();
        for (QuoteHistoryStore.Folder f : folders) labels.add("📁 " + f.name);
        labels.add("＋ 新しいフォルダ");
        new AlertDialog.Builder(this)
                .setTitle("保存先フォルダ")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which == folders.size()) promptNewFolder(folder -> promptNameAndSave(folder, asNew));
                    else promptNameAndSave(folders.get(which), asNew);
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private interface FolderCallback { void onFolder(QuoteHistoryStore.Folder folder); }

    private void promptNewFolder(FolderCallback cb) {
        EditText input = new EditText(this);
        input.setHint("例：○○株式会社");
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(fieldBg());
        LinearLayout wrap = dialogWrap(input);
        new AlertDialog.Builder(this)
                .setTitle("フォルダを作成")
                .setView(wrap)
                .setPositiveButton("作成", (d,w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "新しいフォルダ";
                    cb.onFolder(QuoteHistoryStore.createFolder(this, name));
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void promptNameAndSave(QuoteHistoryStore.Folder folder, boolean asNew) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText((editingName != null && !editingName.isEmpty()) ? editingName : defaultName());
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(fieldBg());
        LinearLayout wrap = dialogWrap(input);
        new AlertDialog.Builder(this)
                .setTitle("保存名")
                .setView(wrap)
                .setPositiveButton("保存", (d,w) -> {
                    QuoteHistoryStore.Quote q = buildQuote();
                    q.id = asNew ? null : editingId;
                    q.folderId = folder.id;
                    q.name = input.getText().toString().trim();
                    if (q.name.isEmpty()) q.name = defaultName();
                    QuoteHistoryStore.Quote saved = QuoteHistoryStore.save(this, q, asNew);
                    editingId = saved.id;
                    editingFolderId = saved.folderId;
                    editingName = saved.name;
                    launchAsNew = false;
                    editBanner.setVisibility(View.VISIBLE);
                    editBanner.setText("保存データを編集中　「新規で保存」なら元データは残ります");
                    renderSaveArea();
                    Toast.makeText(this, "「" + folder.name + "」に保存しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private QuoteHistoryStore.Quote buildQuote() {
        QuoteHistoryStore.Quote q = new QuoteHistoryStore.Quote();
        q.flute = selectedFlute;
        q.length = parseInt(lengthEdit);
        q.width = parseInt(widthEdit);
        q.depth = parseInt(depthEdit);
        q.material = selectedMaterial.name;
        q.processRate = parseDouble(processEdit);
        q.unitPrice = lastCalc == null ? 0 : lastCalc.unitPrice;
        return q;
    }

    private String defaultName() {
        return displayMaterial(selectedMaterial.name) + " " + parseInt(lengthEdit) + "×" + parseInt(widthEdit) + "×" + parseInt(depthEdit);
    }

    private void showMaterialDialog() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText search = new EditText(this);
        search.setHint("材質を検索");
        search.setSingleLine(true);
        search.setPadding(dp(14),0,dp(14),0);
        search.setBackground(fieldBg());
        ListView list = new ListView(this);
        ArrayList<MaterialInfo> visible = new ArrayList<>(materials);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names(visible));
        list.setAdapter(adapter);
        shell.addView(search, new LinearLayout.LayoutParams(-1, dp(54)));
        shell.addView(list, new LinearLayout.LayoutParams(-1, dp(420)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("材質を選択")
                .setView(shell)
                .setNegativeButton("閉じる", null)
                .create();

        list.setOnItemClickListener((p,v,pos,id) -> {
            selectedMaterial = visible.get(pos);
            materialButton.setText(displayMaterial(selectedMaterial.name) + "   ▼");
            invalidateCalc();
            dialog.dismiss();
        });

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void afterTextChanged(Editable e) {}
            public void onTextChanged(CharSequence s,int st,int b,int c) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                visible.clear();
                for (MaterialInfo m : materials) {
                    if (q.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(q) || displayMaterial(m.name).toLowerCase(Locale.ROOT).contains(q)) {
                        visible.add(m);
                    }
                }
                adapter.clear();
                adapter.addAll(names(visible));
                adapter.notifyDataSetChanged();
            }
        });
        dialog.show();
    }

    private ArrayList<String> names(List<MaterialInfo> ms) {
        ArrayList<String> out = new ArrayList<>();
        for (MaterialInfo m : ms) out.add(displayMaterial(m.name));
        return out;
    }

    private void loadMaterials() {
        try {
            Class<?> mm = Class.forName("jp.co.kobayashi.cardboardquote.MaterialMaster");
            Object inst = mm.getField("INSTANCE").get(null);
            Object rawList = mm.getMethod("getMaterials").invoke(inst);
            if (!(rawList instanceof Iterable)) return;
            for (Object raw : (Iterable<?>) rawList) {
                Class<?> c = raw.getClass();
                String name = String.valueOf(c.getMethod("getName").invoke(raw));
                double abc = ((Number)c.getMethod("getAbc").invoke(raw)).doubleValue();
                double wf = ((Number)c.getMethod("getWf").invoke(raw)).doubleValue();
                materials.add(new MaterialInfo(name, raw, abc, wf));
            }
        } catch (Exception ignored) {}
    }

    private MaterialInfo findMaterial(String name) {
        for (MaterialInfo m : materials) if (m.name.equals(name)) return m;
        return null;
    }

    private void invalidateCalc() {
        lastCalc = null;
        if (unitText != null) unitText.setText("— 円");
        if (detailCard != null) detailCard.setVisibility(View.GONE);
    }

    private EditText numEdit(String hint, boolean decimal) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16f);
        e.setSingleLine(true);
        e.setGravity(Gravity.CENTER);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(145,150,158));
        e.setInputType(decimal
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_NUMBER);
        e.setBackground(fieldBg());
        return e;
    }

    private GradientDrawable fieldBg() { return round(Color.WHITE, BORDER, 12); }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(17),dp(15),dp(17),dp(15));
        v.setBackground(round(Color.WHITE, Color.rgb(225,228,232), 15));
        return v;
    }

    private TextView action(String t, boolean primary) {
        TextView v = label(t, 15f, primary ? Color.WHITE : GREEN_DARK, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(round(primary ? GREEN_DARK : Color.WHITE, primary ? GREEN_DARK : Color.rgb(174,204,191), 12));
        return v;
    }

    private TextView section(String t) { return label(t, 13f, SUB, true); }

    private TextView label(String t, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return v;
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h);
        p.topMargin = dp(top); p.bottomMargin = dp(bottom);
        return p;
    }

    private LinearLayout.LayoutParams weight(float weight, int left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(58), weight);
        p.leftMargin = dp(left);
        return p;
    }

    private LinearLayout dialogWrap(View child) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20),dp(8),dp(20),0);
        wrap.addView(child, new LinearLayout.LayoutParams(-1,dp(54)));
        return wrap;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private int parseInt(EditText e) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception x) { return 0; }
    }
    private double parseDouble(EditText e) {
        try { return Double.parseDouble(e.getText().toString().trim()); } catch (Exception x) { return 0.0; }
    }
    private String nf(Number n) { return NumberFormat.getNumberInstance(Locale.JAPAN).format(n); }
    private String fmt3(double v) { return String.format(Locale.JAPAN, "%.3f", v); }
    private String trimNumber(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.0001) return String.valueOf((int)Math.rint(v));
        return String.format(Locale.JAPAN, "%.1f", v);
    }
    private String displayMaterial(String n) { return n == null ? "" : n.replace('X','x'); }
}
