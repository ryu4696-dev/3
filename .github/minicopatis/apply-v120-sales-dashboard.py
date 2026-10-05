from pathlib import Path
import re

base = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")
main = base / "MainActivity.kt"
s = main.read_text()

if "private var homeDataTab = false" not in s:
    s = s.replace("    private var currentScreen = Screen.HOME", "    private var currentScreen = Screen.HOME\n    private var homeDataTab = false", 1)
if "if (requestCode == REQ_HOME_IMPORT)" not in s:
    s = s.replace(
        "        if (resultCode != RESULT_OK) return\n        val uri = data?.data ?: return",
        "        if (requestCode == REQ_HOME_IMPORT) { if (resultCode == RESULT_OK) showHome(); return }\n        if (resultCode != RESULT_OK) return\n        val uri = data?.data ?: return",
        1,
    )
s = s.replace(
    "        if (currentScreen != Screen.HOME) showHome() else super.onBackPressed()",
    "        if (currentScreen != Screen.HOME) showHome() else if (homeDataTab) { homeDataTab = false; showHome() } else super.onBackPressed()",
    1,
)
if "import android.widget.FrameLayout" not in s:
    s = s.replace("import android.widget.EditText", "import android.widget.EditText\nimport android.widget.FrameLayout", 1)

home_method = r'''    private fun showHome() {
        currentScreen = Screen.HOME
        ledgerTableView = null
        ledgerCountView = null
        salesTableView = null
        salesCountView = null
        salesStartButton = null
        salesEndButton = null

        val root = rootLayout()
        root.setPadding(dp(14), dp(4), dp(14), dp(8))
        root.addView(text("MiniCoPaTis", 21f, Color.rgb(15, 23, 42), Typeface.BOLD).apply {
            setPadding(dp(2), dp(2), 0, dp(7))
        })

        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val homeTabButton = actionButton("ホーム", !homeDataTab)
        val dataTabButton = actionButton("データ読込", homeDataTab)
        tabRow.addView(homeTabButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(5) })
        tabRow.addView(dataTabButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(5) })
        root.addView(tabRow, fullWidth(dp(42)).apply { bottomMargin = dp(10) })

        val homePage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dataScroll = ScrollView(this).apply { isFillViewport = true }
        val dataPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), 0, dp(2), dp(8)) }
        dataScroll.addView(dataPage)
        homePage.visibility = if (homeDataTab) View.GONE else View.VISIBLE
        dataScroll.visibility = if (homeDataTab) View.VISIBLE else View.GONE
        homeTabButton.setOnClickListener { if (homeDataTab) { homeDataTab = false; showHome() } }
        dataTabButton.setOnClickListener { if (!homeDataTab) { homeDataTab = true; showHome() } }

        val quote = card().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        quote.addView(text("簡易見積", 16f, Color.rgb(15, 23, 42), Typeface.BOLD))
        quote.addView(actionButton("簡易見積を開く", true).apply {
            setOnClickListener { openQuickQuote() }
        }, fullWidth(dp(46)).apply { topMargin = dp(7) })
        homePage.addView(quote, fullWidthWrap().apply { bottomMargin = dp(10) })

        val ledgerStats = repository.stats()
        val salesStats = repository.salesStats()
        val ledgerSummary = if (ledgerStats.productCount > 0) "${ledgerStats.customerCount}取引先・${ledgerStats.productCount}商品" else "CSV未読込"
        val salesSummary = if (salesStats.productCount > 0) "${salesStats.customerCount}取引先・${salesStats.productCount}商品" else "CSV未読込"
        fun tile(title: String, detail: String, action: String, onClick: () -> Unit): LinearLayout {
            val box = card().apply { setPadding(dp(12), dp(11), dp(12), dp(11)) }
            box.addView(text(title, 15f, Color.rgb(15, 23, 42), Typeface.BOLD))
            box.addView(text(detail, 11f, Color.rgb(100, 116, 139), Typeface.NORMAL).apply {
                setPadding(0, dp(3), 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            box.addView(actionButton(action, true).apply { textSize = 12f; setOnClickListener { onClick() } }, fullWidth(dp(40)).apply { topMargin = dp(7) })
            return box
        }
        fun tileRow(left: View, right: View) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(5) })
            row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(5) })
            homePage.addView(row, fullWidthWrap().apply { bottomMargin = dp(10) })
        }
        tileRow(
            tile("商品台帳", ledgerSummary, "台帳を開く") { showLedger() },
            tile("売上分析", salesSummary, "分析を開く") { showSales() }
        )
        tileRow(
            tile("販売目標", "得意先別・月別の実績", "目標を開く") {
                startActivity(Intent(this, SalesDashboardActivity::class.java))
            },
            tile("受注情報", "納期・得意先別の明細", "受注を開く") {
                startActivity(Intent(this, SalesDashboardActivity::class.java).putExtra("orders_only", true))
            }
        )

        dataPage.addView(text("データを読み込む", 18f, Color.rgb(15, 23, 42), Typeface.BOLD))
        fun importButton(title: String, note: String, click: () -> Unit) {
            val box = card().apply { setPadding(dp(13), dp(10), dp(13), dp(10)) }
            box.addView(text(title, 14f, Color.rgb(15, 23, 42), Typeface.BOLD))
            box.addView(actionButton("読み込む", false).apply { setOnClickListener { click() } }, fullWidth(dp(42)))
            dataPage.addView(box, fullWidthWrap().apply { bottomMargin = dp(8) })
        }
        importButton("商品台帳CSV", "商品・取引先情報を更新", { chooseCsv(REQ_LEDGER_CSV) })
        importButton("売上分析CSV", "販売目標の金額・平米実績にも共通反映", { chooseCsv(REQ_SALES_CSV) })
        importButton("販売目標表（Excel）", "目標値を更新", {
            startActivityForResult(Intent(this, SalesDashboardActivity::class.java)
                .putExtra("import_only", true).putExtra("import_type", "target"), REQ_HOME_IMPORT)
        })
        importButton("受注明細表（Excel）", "受注情報を更新", {
            startActivityForResult(Intent(this, SalesDashboardActivity::class.java)
                .putExtra("import_only", true).putExtra("import_type", "orders"), REQ_HOME_IMPORT)
        })

        val host = FrameLayout(this)
        host.addView(homePage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        host.addView(dataScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(host, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setRootContent(root)
    }
'''
s, count = re.subn(r"    private fun showHome\(\) \{.*?\n    private fun showLedger\(\) \{", home_method + "\n    private fun showLedger() {", s, count=1, flags=re.S)
if count != 1:
    raise SystemExit("could not replace showHome")
if "private fun openQuickQuote()" not in s:
    quote_method = '''    private fun openQuickQuote() {
        try {
            startActivity(Intent().setClassName(packageName, "jp.co.kobayashi.cardboardquote.MainActivity"))
        } catch (e: Exception) {
            Toast.makeText(this, "簡易見積を開けませんでした", Toast.LENGTH_LONG).show()
        }
    }

'''
    s = s.replace("    private fun showLedger() {", quote_method + "    private fun showLedger() {", 1)
if "private const val REQ_HOME_IMPORT" not in s:
    s = s.replace("        private const val REQ_SALES_CSV = 4002", "        private const val REQ_SALES_CSV = 4002\n        private const val REQ_HOME_IMPORT = 4003", 1)
main.write_text(s)

# Extend the same Sales Analysis CSV parse result with category/month totals for the target screen.
models = base / "Models.kt"
m = models.read_text()
if "data class TargetSalesActual(" not in m:
    m = m.replace("data class SalesImportedData(\n", '''data class TargetSalesActual(
    val customerNumber: String,
    val customerName: String,
    val category: String,
    val month: String,
    val amount: Double,
    val sqm: Double
)

data class SalesImportedData(
''', 1)
    m = m.replace("    val skippedRows: Int\n)", "    val skippedRows: Int,\n    val targetActuals: List<TargetSalesActual> = emptyList()\n)", 1)
models.write_text(m)

imp = base / "SalesCsvImporter.kt"
i = imp.read_text()
if "val categoryIdx = index[\"商品区分名\"]" not in i:
    i = i.replace('''        val amountIdx = required("売上金額")
        val maxRequiredIndex''', '''        val amountIdx = required("売上金額")
        val categoryIdx = index["商品区分名"] ?: index["商品区分"] ?: -1
        val maxRequiredIndex''', 1)
    i = i.replace('''        val daily = LinkedHashMap<String, DailyAccumulator>()
        val customerChoices''', '''        val daily = LinkedHashMap<String, DailyAccumulator>()
        val targetMonthly = LinkedHashMap<String, TargetMonthlyAccumulator>()
        val customerChoices''', 1)
    i = i.replace('''            accumulator.sqm += number(sqmIdx)

            val customerName''', '''            accumulator.sqm += number(sqmIdx)

            val categoryName = if (categoryIdx >= 0 && categoryIdx < row.size) normalizeCategory(cell(categoryIdx)) else "その他"
            val targetKey = "$customerNo\\u001F$categoryName\\u001F${date.toString().substring(0, 7)}"
            val targetAccumulator = targetMonthly.getOrPut(targetKey) {
                TargetMonthlyAccumulator(customerNo, categoryName, date.toString().substring(0, 7))
            }
            targetAccumulator.amount += number(amountIdx)
            targetAccumulator.sqm += number(sqmIdx)

            val customerName''', 1)
    i = i.replace('''            skippedRows = skippedRows
        )''', '''            skippedRows = skippedRows,
            targetActuals = targetMonthly.values.map { value ->
                TargetSalesActual(value.customerNumber, customerChoices[value.customerNumber]?.name.orEmpty(), value.category, value.month, value.amount, value.sqm)
            }
        )''', 1)
    i = i.replace('''    private data class DailyAccumulator(''', '''    private fun normalizeCategory(raw: String): String = when {
        raw.contains("段ボール") || raw.contains("ダンボール") || raw.contains("箱") -> "段ボール"
        raw.contains("商品") -> "商品"
        raw.contains("版") || raw.contains("型") -> "版代型代"
        raw.contains("運賃") || raw.contains("送料") -> "運賃"
        else -> "その他"
    }

    private data class TargetMonthlyAccumulator(
        val customerNumber: String,
        val category: String,
        val month: String,
        var amount: Double = 0.0,
        var sqm: Double = 0.0
    )

    private data class DailyAccumulator(''', 1)
imp.write_text(i)

repo = base / "DataRepository.kt"
r = repo.read_text()
needle = '''            for (record in data.dailyRecords) {
                db.insertOrThrow("sales_daily", null, ContentValues().apply {
                    put("customer_no", record.customerNumber)
                    put("product_no", record.productNumber)
                    put("sale_date", record.saleDate)
                    put("amount", record.amount)
                    put("quantity", record.quantity)
                    put("sqm", record.sqm)
                })
            }
            db.setTransactionSuccessful()'''
if needle in r:
    replacement = '''            for (record in data.dailyRecords) {
                db.insertOrThrow("sales_daily", null, ContentValues().apply {
                    put("customer_no", record.customerNumber)
                    put("product_no", record.productNumber)
                    put("sale_date", record.saleDate)
                    put("amount", record.amount)
                    put("quantity", record.quantity)
                    put("sqm", record.sqm)
                })
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS sd_actual(customer_no TEXT, customer_name TEXT, category TEXT, month TEXT, amount REAL, sqm REAL, PRIMARY KEY(customer_no,category,month))")
            db.delete("sd_actual", null, null)
            val targetRows = LinkedHashMap<Triple<String, String, String>, DoubleArray>()
            for (actual in data.targetActuals) {
                val key = Triple(actual.customerNumber, actual.category, actual.month)
                val values = targetRows.getOrPut(key) { doubleArrayOf(0.0, 0.0) }
                values[0] += actual.amount
                values[1] += actual.sqm
            }
            val customerNames = data.customers.associate { it.number to it.name }
            val companyRows = LinkedHashMap<Triple<String, String, String>, DoubleArray>()
            targetRows.forEach { (key, values) ->
                val totalKey = Triple("全体", key.second, key.third)
                val total = companyRows.getOrPut(totalKey) { doubleArrayOf(0.0, 0.0) }
                total[0] += values[0]
                total[1] += values[1]
            }
            targetRows.putAll(companyRows)
            targetRows.forEach { (key, values) ->
                db.insertOrThrow("sd_actual", null, ContentValues().apply {
                    put("customer_no", key.first)
                    put("customer_name", if (key.first == "全体") "全体" else customerNames[key.first].orEmpty())
                    put("category", key.second)
                    put("month", key.third)
                    put("amount", values[0])
                    put("sqm", values[1])
                })
            }
            db.setTransactionSuccessful()'''
    r = r.replace(needle, replacement, 1)
    repo.write_text(r)
elif "CREATE TABLE IF NOT EXISTS sd_actual" not in r:
    raise SystemExit("replaceSales insertion point missing")

# Bump version and copy the dashboard source into the generated Android project.
build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, code_count = re.subn(r"versionCode\s+\d+", "versionCode 29", b, count=1)
b, name_count = re.subn(r"versionName\s+'[^']+'", "versionName '1.3.9'", b, count=1)
if code_count != 1 or name_count != 1:
    raise SystemExit("app version declarations not found")
build.write_text(b)

activity = Path(".github/minicopatis/SalesDashboardActivity.kt")
if not activity.exists():
    raise SystemExit("SalesDashboardActivity.kt is missing")
activity_source = activity.read_text().replace("        val imports = card()\n", "", 1)
activity_source = activity_source.replace(
    'runOnUiThread { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); if(intent.getBooleanExtra("import_only",false)) finish() else render() }',
    'runOnUiThread { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); if(intent.getBooleanExtra("import_only",false)) { setResult(RESULT_OK); finish() } else render() }',
    1,
)
activity.write_text(activity_source)
(base / "SalesDashboardActivity.kt").write_text(activity_source)

manifest = Path("ledgerapp/app/src/main/AndroidManifest.xml")
manifest_text = manifest.read_text()
if 'android:name=".SalesDashboardActivity"' not in manifest_text:
    match = re.search(r'<activity\s+android:name="\.MainActivity"', manifest_text)
    if not match:
        raise SystemExit("MainActivity manifest entry not found")
    manifest_text = manifest_text[:match.start()] + '        <activity android:name=".SalesDashboardActivity" android:screenOrientation="unspecified" android:exported="false" />\n' + manifest_text[match.start():]
for activity_name in (".MainActivity", ".SalesDashboardActivity"):
    pattern = r'<activity(?P<attrs>[^>]*android:name="' + re.escape(activity_name) + r'"[^>]*)>'
    def add_theme(match):
        attrs = match.group("attrs")
        if "android:theme=" in attrs:
            return match.group(0)
        self_closing = attrs.rstrip().endswith("/")
        if self_closing:
            attrs = attrs.rstrip()[:-1].rstrip()
        attrs += ' android:theme="@style/QuoteCompatTheme"'
        if self_closing:
            attrs += " /"
        return "<activity" + attrs + ">"
    manifest_text = re.sub(pattern, add_theme, manifest_text, count=1, flags=re.S)
manifest.write_text(manifest_text)
