from pathlib import Path
import re

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

# ---------- Repository helpers: sales-analysis membership is the source of truth ----------
p = root / "DataRepository.kt"
s = p.read_text()

anchor = '''    fun listProducts(customerNumber: String, includeDeleted: Boolean): List<ProductSummary> {
'''
helper = '''    fun listSalesProductNumbers(customerNumber: String): Set<String> {
        val out = linkedSetOf<String>()
        readableDatabase.rawQuery(
            "SELECT product_no FROM sales_products WHERE customer_no=? ORDER BY product_no COLLATE NOCASE",
            arrayOf(customerNumber)
        ).use { c ->
            while (c.moveToNext()) out += c.getString(0)
        }
        return out
    }

'''
if "fun listSalesProductNumbers(" not in s:
    if anchor not in s:
        raise SystemExit("listProducts anchor missing")
    s = s.replace(anchor, helper + anchor, 1)
p.write_text(s)

# ---------- Main UI ----------
p = root / "MainActivity.kt"
s = p.read_text()

if "private var ledgerSalesOnly" not in s:
    s = s.replace(
        '    private var ledgerSearchQuery = ""',
        '    private var ledgerSearchQuery = ""\n    private var ledgerSalesOnly = true',
        1
    )

old = '''        val customerButton = actionButton(
            selectedLedgerCustomer?.let { if (it.name.isBlank()) it.number else it.name } ?: "取引先を選択",
            false
        ).apply {
'''
new = '''        val ledgerCustomerChoices = if (ledgerSalesOnly) repository.listSalesCustomers() else repository.listCustomers()
        val validLedgerCustomers = ledgerCustomerChoices.map { it.number }.toSet()
        if (selectedLedgerCustomer?.number !in validLedgerCustomers) selectedLedgerCustomer = null

        val customerButton = actionButton(
            selectedLedgerCustomer?.let { if (it.name.isBlank()) it.number else it.name } ?: "取引先を選択",
            false
        ).apply {
'''
if old not in s:
    raise SystemExit("ledger customer button anchor missing")
s = s.replace(old, new, 1)

s = s.replace(
    'showCustomerPicker("取引先を選択", repository.listCustomers(), this) { customer ->',
    'showCustomerPicker("取引先を選択", ledgerCustomerChoices, this) { customer ->',
    1
)

anchor = '''        controls.addView(chooserRow, fullWidth(dp(44)))

        val search = EditText(this).apply {
'''
scope = '''        controls.addView(chooserRow, fullWidth(dp(44)))

        val scopeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = rounded(Color.rgb(238, 240, 247), dp(10).toFloat(), Color.TRANSPARENT)
        }
        fun scopeChip(title: String, selected: Boolean, onClick: () -> Unit): TextView =
            TextView(this).apply {
                text = title
                gravity = Gravity.CENTER
                textSize = 11.5f
                setTextColor(if (selected) Color.rgb(15,91,70) else Color.rgb(89,97,116))
                typeface = Typeface.create(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
                background = rounded(
                    if (selected) Color.rgb(236,247,242) else Color.TRANSPARENT,
                    dp(9).toFloat(),
                    Color.TRANSPARENT
                )
                setOnClickListener { onClick() }
            }
        scopeRow.addView(
            scopeChip("売上分析あり", ledgerSalesOnly) {
                if (!ledgerSalesOnly) {
                    ledgerSalesOnly = true
                    showLedger()
                }
            },
            LinearLayout.LayoutParams(0, dp(38), 1f)
        )
        scopeRow.addView(
            scopeChip("全商品", !ledgerSalesOnly) {
                if (ledgerSalesOnly) {
                    ledgerSalesOnly = false
                    showLedger()
                }
            },
            LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(4) }
        )
        controls.addView(scopeRow, fullWidth(dp(38)).apply { topMargin = dp(7) })

        val search = EditText(this).apply {
'''
if anchor not in s:
    raise SystemExit("ledger scope insertion anchor missing")
s = s.replace(anchor, scope, 1)

old_reload = '''        val all = repository.listProducts(customer.number, includeDeleted)
        val q = ledgerSearchQuery.trim().lowercase(Locale.JAPAN)
        val products = if (q.isBlank()) all else all.filter {
            it.productNumber.lowercase(Locale.JAPAN).contains(q) ||
            it.productName.lowercase(Locale.JAPAN).contains(q) ||
            it.material.lowercase(Locale.JAPAN).contains(q)
        }
        ledgerTableView?.setProducts(products)
        ledgerCountView?.text = if (q.isBlank()) "${products.size}商品" else "${products.size}商品（検索中）"
'''
new_reload = '''        val all = repository.listProducts(customer.number, includeDeleted)
        val scoped = if (ledgerSalesOnly) {
            val salesNumbers = repository.listSalesProductNumbers(customer.number)
            all.filter { product ->
                val no = product.productNumber
                val base = no.replace(Regex("-\\\\d+$"), "")
                no in salesNumbers || base in salesNumbers
            }
        } else all
        val q = ledgerSearchQuery.trim().lowercase(Locale.JAPAN)
        val products = if (q.isBlank()) scoped else scoped.filter {
            it.productNumber.lowercase(Locale.JAPAN).contains(q) ||
            it.productName.lowercase(Locale.JAPAN).contains(q) ||
            it.material.lowercase(Locale.JAPAN).contains(q)
        }
        ledgerTableView?.setProducts(products)
        val scopeLabel = if (ledgerSalesOnly) "売上分析あり" else "全商品"
        ledgerCountView?.text = if (q.isBlank()) "${products.size}商品・$scopeLabel" else "${products.size}商品（検索中）・$scopeLabel"
'''
if old_reload not in s:
    raise SystemExit("reloadProducts scope block missing")
s = s.replace(old_reload, new_reload, 1)

s = s.replace(
    '''        val targetCustomers = repository.listTargetCustomers()
        val choices = listOf(Customer("全社", "")) + targetCustomers
''',
    '''        val salesCustomers = repository.listSalesCustomers()
        val choices = listOf(Customer("全社", "")) + salesCustomers
''',
    1
)

s = s.replace(
    'if (selectedSalesCustomer == null) "取引先を選択してください" else "販売目標対象のみ"',
    'if (selectedSalesCustomer == null) "取引先を選択してください" else "売上分析CSV対象"',
    1
)

old_all_filter = '''            val targetNumbers = repository.listTargetCustomers().map { it.number }.toSet()
            val sections = repository.listSalesAll(start.toString(), end.toString())
                .filter { it.customer.number in targetNumbers }
                .mapNotNull { section ->
'''
new_all_filter = '''            val sections = repository.listSalesAll(start.toString(), end.toString())
                .mapNotNull { section ->
'''
if old_all_filter not in s:
    raise SystemExit("all-sales target filter block missing")
s = s.replace(old_all_filter, new_all_filter, 1)
p.write_text(s)

# ---------- Sales target: customer population and totals follow Sales Analysis CSV ----------
p = root / "SalesDashboardActivity.kt"
s = p.read_text()

old_population = '''        val targetCompanies = mutableListOf<Pair<String, String>>()
        val targetWhere = StringBuilder("customer_no<>'全体' AND metric=? AND month IN (${periodMonths.joinToString { "?" }})")
        val targetArgs = mutableListOf(if (selectedMetric == "金額") "金額" else "平米").apply { addAll(periodMonths) }
        if (selectedCategory != "合計") {
            targetWhere.append(" AND category=?")
            targetArgs.add(selectedCategory)
        }
        db.rawQuery(
            "SELECT customer_no,MAX(customer_name) FROM sd_target WHERE $targetWhere GROUP BY customer_no HAVING SUM(value)>0 ORDER BY MAX(customer_name)",
            targetArgs.toTypedArray()
        ).use {
            while (it.moveToNext()) {
                val no = it.getString(0)
                targetCompanies += no to it.getString(1).orEmpty().ifBlank { no }
            }
        }

        val allTarget = targetCompanies.sumOf { (no, _) ->
            monthTotal("sd_target", no, periodMonths, selectedMetric, selectedCategory)
        }
        val allActual = targetCompanies.sumOf { (no, _) ->
            monthTotal("sd_actual", no, periodMonths, selectedMetric, selectedCategory)
        }
'''
new_population = '''        val salesCompanies = mutableListOf<Pair<String, String>>()
        db.rawQuery(
            """
            SELECT s.customer_no,
                   COALESCE(NULLIF(MAX(p.customer_name), ''), NULLIF(MAX(s.customer_name), ''), s.customer_no)
            FROM sales_customers s
            LEFT JOIN products p ON p.customer_no = s.customer_no
            GROUP BY s.customer_no
            ORDER BY COALESCE(NULLIF(MAX(p.customer_name), ''), NULLIF(MAX(s.customer_name), ''), s.customer_no) COLLATE NOCASE
            """.trimIndent(),
            null
        ).use {
            while (it.moveToNext()) {
                val no = it.getString(0)
                salesCompanies += no to it.getString(1).orEmpty().ifBlank { no }
            }
        }

        val allTarget = salesCompanies.sumOf { (no, _) ->
            monthTotal("sd_target", no, periodMonths, selectedMetric, selectedCategory)
        }
        val allActual = salesCompanies.sumOf { (no, _) ->
            monthTotal("sd_actual", no, periodMonths, selectedMetric, selectedCategory)
        }
'''
if old_population not in s:
    raise SystemExit("target-company population block missing")
s = s.replace(old_population, new_population, 1)

s = s.replace(
    '''        val companies = mutableListOf<Pair<String, String>>("全体" to "全社").apply {
            addAll(targetCompanies)
        }
''',
    '''        val companies = mutableListOf<Pair<String, String>>("全体" to "全社").apply {
            addAll(salesCompanies)
        }
''',
    1
)

s = s.replace(
    '''                        if (no == "全体") targetCompanies.sumOf { (customerNo, _) ->
                            oneValue("sd_target",customerNo,month,selectedMetric,selectedCategory)
                        } else oneValue("sd_target",no,month,selectedMetric,selectedCategory)
''',
    '''                        if (no == "全体") salesCompanies.sumOf { (customerNo, _) ->
                            oneValue("sd_target",customerNo,month,selectedMetric,selectedCategory)
                        } else oneValue("sd_target",no,month,selectedMetric,selectedCategory)
''',
    1
)
s = s.replace(
    '''                        if (no == "全体") targetCompanies.sumOf { (customerNo, _) ->
                            oneValue("sd_actual",customerNo,month,selectedMetric,selectedCategory)
                        } else oneValue("sd_actual",no,month,selectedMetric,selectedCategory)
''',
    '''                        if (no == "全体") salesCompanies.sumOf { (customerNo, _) ->
                            oneValue("sd_actual",customerNo,month,selectedMetric,selectedCategory)
                        } else oneValue("sd_actual",no,month,selectedMetric,selectedCategory)
''',
    1
)

p.write_text(s)

# ---------- Version ----------
build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, c1 = re.subn(r"versionCode\s+\d+", "versionCode 35", b, count=1)
b, c2 = re.subn(r"versionName\s+'[^']+'", "versionName '1.8.0'", b, count=1)
if c1 != 1 or c2 != 1:
    raise SystemExit("version declarations not found")
build.write_text(b)
