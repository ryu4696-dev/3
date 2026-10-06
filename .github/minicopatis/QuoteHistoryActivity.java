package jp.co.ichika.salesledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuoteHistoryActivity extends Activity {
    private final int GREEN = Color.rgb(15, 91, 70);
    private final int GREEN_DARK = Color.rgb(10, 74, 56);
    private final int TEXT = Color.rgb(20, 28, 43);
    private final int SUB = Color.rgb(94, 104, 120);
    private final int BG = Color.rgb(248, 247, 243);
    private final int BORDER = Color.rgb(215, 220, 226);

    private LinearLayout content;
    private String currentFolderId;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(247,243,234));
        getWindow().setNavigationBarColor(Color.WHITE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18),dp(16),dp(18),dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1,-2));
        setContentView(scroll);

        currentFolderId = getIntent().getStringExtra("folder_id");
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void render() {
        content.removeAllViews();
        if (currentFolderId == null) renderFolders();
        else renderFolder(currentFolderId);
    }

    private void renderFolders() {
        content.addView(appBar("保存した見積", v -> finish()));

        TextView create = action("＋ フォルダを作成", true);
        create.setOnClickListener(v -> promptFolderCreate());
        content.addView(create, lp(-1,dp(48),12,10));

        List<QuoteHistoryStore.Folder> folders = QuoteHistoryStore.folders(this);
        if (folders.isEmpty()) {
            TextView empty = label("まだフォルダがありません\n見積を保存するとここに整理できます", 14f, SUB, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0,dp(60),0,dp(60));
            content.addView(empty);
            return;
        }

        for (QuoteHistoryStore.Folder folder : folders) {
            content.addView(folderCard(folder), lp(-1,-2,0,9));
        }
    }

    private LinearLayout folderCard(QuoteHistoryStore.Folder folder) {
        LinearLayout card = card(false);
        card.setOnClickListener(v -> {
            currentFolderId = folder.id;
            render();
        });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(label("📁  " + folder.name, 15f, TEXT, true));
        names.addView(label(QuoteHistoryStore.countInFolder(this, folder.id) + "件", 11.5f, SUB, false), lp(-1,-2,4,0));
        head.addView(names, new LinearLayout.LayoutParams(0,-2,1f));

        TextView menu = label("⋮", 24f, SUB, true);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(v -> showFolderMenu(folder));
        head.addView(menu, new LinearLayout.LayoutParams(dp(42),dp(42)));

        card.addView(head);
        return card;
    }

    private void renderFolder(String folderId) {
        QuoteHistoryStore.Folder folder = findFolder(folderId);
        if (folder == null) {
            currentFolderId = null;
            render();
            return;
        }

        content.addView(appBar(folder.name, v -> {
            currentFolderId = null;
            render();
        }));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView fresh = action("新規見積", true);
        fresh.setOnClickListener(v -> startActivity(new Intent(this, QuoteWorkspaceActivity.class)));
        actions.addView(fresh, new LinearLayout.LayoutParams(0,dp(46),1f));

        TextView rename = action("フォルダ名変更", false);
        rename.setOnClickListener(v -> promptFolderRename(folder));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0,dp(46),1f);
        rp.leftMargin = dp(7);
        actions.addView(rename, rp);
        content.addView(actions, lp(-1,dp(46),12,10));

        List<QuoteHistoryStore.Quote> quotes = QuoteHistoryStore.quotesInFolder(this, folderId);
        if (quotes.isEmpty()) {
            TextView empty = label("このフォルダにはまだ見積がありません",14f,SUB,false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0,dp(60),0,dp(60));
            content.addView(empty);
            return;
        }

        for (QuoteHistoryStore.Quote q : quotes) {
            content.addView(quoteCard(q), lp(-1,-2,0,9));
        }
    }

    private LinearLayout quoteCard(QuoteHistoryStore.Quote q) {
        LinearLayout card = card(false);
        card.setOnClickListener(v -> editQuote(q, false));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = label(q.name == null || q.name.isEmpty() ? "名称未設定" : q.name, 14.5f, TEXT, true);
        name.setMaxLines(2);
        head.addView(name, new LinearLayout.LayoutParams(0,-2,1f));

        TextView menu = label("⋮", 24f, SUB, true);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(v -> showQuoteMenu(q));
        head.addView(menu, new LinearLayout.LayoutParams(dp(42),dp(42)));
        card.addView(head);

        card.addView(label(displayMaterial(q.material) + "  " + q.flute, 11.5f, SUB, false), lp(-1,-2,4,0));
        card.addView(label(q.length + " × " + q.width + " × " + q.depth + " mm", 13f, Color.rgb(45,76,157), true), lp(-1,-2,6,0));

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);

        TextView price = mini(nf(q.unitPrice) + "円", Color.rgb(10,103,72), Color.rgb(225,245,235), Color.rgb(171,217,193));
        summary.addView(price, new LinearLayout.LayoutParams(0,dp(38),1f));

        TextView process = mini("加工 " + trimNumber(q.processRate) + "円/㎡", Color.rgb(71,85,105), Color.rgb(240,242,245), Color.rgb(209,215,223));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0,dp(38),1f);
        pp.leftMargin = dp(6);
        summary.addView(process, pp);
        card.addView(summary, lp(-1,dp(38),9,0));

        String date = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(new Date(q.updatedAt));
        card.addView(label("更新 " + date, 10.5f, Color.rgb(120,128,140), false), lp(-1,-2,8,0));
        return card;
    }

    private void showQuoteMenu(QuoteHistoryStore.Quote q) {
        String[] items = {"編集", "新規として編集", "名前変更", "移動", "削除"};
        new AlertDialog.Builder(this)
                .setTitle(q.name)
                .setItems(items, (d, which) -> {
                    if (which == 0) editQuote(q,false);
                    else if (which == 1) editQuote(q,true);
                    else if (which == 2) promptQuoteRename(q);
                    else if (which == 3) promptMove(q);
                    else confirmDeleteQuote(q);
                })
                .show();
    }

    private void editQuote(QuoteHistoryStore.Quote q, boolean asNew) {
        Intent i = new Intent(this, QuoteWorkspaceActivity.class);
        i.putExtra("quote_id", q.id);
        i.putExtra("save_as_new", asNew);
        startActivity(i);
    }

    private void promptMove(QuoteHistoryStore.Quote q) {
        List<QuoteHistoryStore.Folder> folders = QuoteHistoryStore.folders(this);
        String[] names = new String[folders.size()];
        for (int i=0;i<folders.size();i++) names[i] = folders.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("移動先")
                .setItems(names, (d, which) -> {
                    QuoteHistoryStore.moveQuote(this, q.id, folders.get(which).id);
                    Toast.makeText(this,"移動しました",Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("キャンセル",null)
                .show();
    }

    private void promptQuoteRename(QuoteHistoryStore.Quote q) {
        EditText input = dialogInput(q.name);
        new AlertDialog.Builder(this)
                .setTitle("見積名を変更")
                .setView(wrap(input))
                .setPositiveButton("変更",(d,w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) QuoteHistoryStore.renameQuote(this,q.id,name);
                    render();
                })
                .setNegativeButton("キャンセル",null)
                .show();
    }

    private void confirmDeleteQuote(QuoteHistoryStore.Quote q) {
        new AlertDialog.Builder(this)
                .setTitle("削除しますか？")
                .setMessage("「" + q.name + "」を削除します。")
                .setPositiveButton("削除",(d,w) -> {
                    QuoteHistoryStore.deleteQuote(this,q.id);
                    render();
                })
                .setNegativeButton("キャンセル",null)
                .show();
    }

    private void showFolderMenu(QuoteHistoryStore.Folder folder) {
        String[] items = {"名前変更", "削除"};
        new AlertDialog.Builder(this)
                .setTitle(folder.name)
                .setItems(items,(d,which) -> {
                    if (which == 0) promptFolderRename(folder);
                    else confirmDeleteFolder(folder);
                })
                .show();
    }

    private void promptFolderCreate() {
        EditText input = dialogInput("");
        input.setHint("例：○○株式会社");
        new AlertDialog.Builder(this)
                .setTitle("フォルダを作成")
                .setView(wrap(input))
                .setPositiveButton("作成",(d,w) -> {
                    String name = input.getText().toString().trim();
                    QuoteHistoryStore.createFolder(this, name);
                    render();
                })
                .setNegativeButton("キャンセル",null)
                .show();
    }

    private void promptFolderRename(QuoteHistoryStore.Folder folder) {
        EditText input = dialogInput(folder.name);
        new AlertDialog.Builder(this)
                .setTitle("フォルダ名を変更")
                .setView(wrap(input))
                .setPositiveButton("変更",(d,w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) QuoteHistoryStore.renameFolder(this,folder.id,name);
                    render();
                })
                .setNegativeButton("キャンセル",null)
                .show();
    }

    private void confirmDeleteFolder(QuoteHistoryStore.Folder folder) {
        int count = QuoteHistoryStore.countInFolder(this, folder.id);
        new AlertDialog.Builder(this)
                .setTitle("フォルダを削除しますか？")
                .setMessage("中の見積 " + count + "件も削除されます。")
                .setPositiveButton("削除",(d,w) -> {
                    QuoteHistoryStore.deleteFolder(this,folder.id);
                    if (folder.id.equals(currentFolderId)) currentFolderId = null;
                    render();
                })
                .setNegativeButton("キャンセル",null)
                .show();
    }

    private QuoteHistoryStore.Folder findFolder(String id) {
        for (QuoteHistoryStore.Folder f : QuoteHistoryStore.folders(this)) if (f.id.equals(id)) return f;
        return null;
    }

    private LinearLayout appBar(String title, View.OnClickListener backClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = label("‹ 戻る",16f,GREEN_DARK,true);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(backClick);
        row.addView(back,new LinearLayout.LayoutParams(dp(80),dp(48)));

        TextView t = label(title,24f,TEXT,true);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(t,new LinearLayout.LayoutParams(0,dp(48),1f));
        return row;
    }

    private LinearLayout card(boolean selected) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(14),dp(12),dp(14),dp(12));
        v.setBackground(round(
                selected ? Color.rgb(241,250,245) : Color.WHITE,
                selected ? GREEN : Color.rgb(199,208,219),
                14
        ));
        return v;
    }

    private TextView mini(String value, int color, int fill, int stroke) {
        TextView v = label(value,11.5f,color,true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(round(fill,stroke,11));
        return v;
    }

    private TextView action(String t, boolean primary) {
        TextView v = label(t,13f,primary ? Color.WHITE : GREEN_DARK,true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(round(primary ? GREEN_DARK : Color.WHITE, primary ? GREEN_DARK : Color.rgb(174,204,191),11));
        return v;
    }

    private TextView label(String t,float size,int color,boolean bold) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        return v;
    }

    private EditText dialogInput(String value) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(value == null ? "" : value);
        e.setSelectAllOnFocus(true);
        e.setPadding(dp(14),0,dp(14),0);
        e.setBackground(round(Color.WHITE,BORDER,11));
        return e;
    }

    private LinearLayout wrap(View child) {
        LinearLayout w = new LinearLayout(this);
        w.setPadding(dp(20),dp(8),dp(20),0);
        w.addView(child,new LinearLayout.LayoutParams(-1,dp(54)));
        return w;
    }

    private GradientDrawable round(int fill,int stroke,int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1),stroke);
        return g;
    }

    private LinearLayout.LayoutParams lp(int w,int h,int top,int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private String nf(Number n) { return NumberFormat.getNumberInstance(Locale.JAPAN).format(n); }
    private String trimNumber(double v) {
        if (Math.abs(v - Math.rint(v)) < .0001) return String.valueOf((int)Math.rint(v));
        return String.format(Locale.JAPAN,"%.1f",v);
    }
    private String displayMaterial(String n) { return n == null ? "" : n.replace('X','x'); }
}
