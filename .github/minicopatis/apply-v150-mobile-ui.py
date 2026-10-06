from pathlib import Path
import re

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

p = root / "MainActivity.kt"
s = p.read_text()

if "private var ledgerSearchQuery" not in s:
    s = s.replace(
        "    private var includeDeleted = false",
        '    private var includeDeleted = false\n    private var ledgerSearchQuery = ""\n    private var salesSearchQuery = ""',
        1
    )

ledger_method = r'''    private fun showLedger() {
        currentScreen = Screen.LEDGER
        val root = rootLayout()
        root.addView(appBar("商品台帳") { showHome() }, fullWidth(dp(64)))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(Color.rgb(246, 248, 251))
        }

        val chooserRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val customerButton = actionButton(
            selectedLedgerCustomer?.let { if (it.name.isBlank()) it.number else it.name } ?: "取引先を選択",
            false
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener {
                showCustomerPicker("取引先を選択", repository.listCustomers(), this) { customer ->
                    selectedLedgerCustomer = customer
                    reloadProducts()
                }
            }
        }
        chooserRow.addView(customerButton, LinearLayout.LayoutParams(0, dp(44), 1f))

        val deletedCheck = CheckBox(this).apply {
            text = "削除品"
            textSize = 11.5f
            setTextColor(Color.rgb(71, 85, 105))
            isChecked = includeDeleted
            setPadding(dp(8), 0, 0, 0)
            setOnCheckedChangeListener { _, checked ->
                includeDeleted = checked
                reloadProducts()
            }
        }
        chooserRow.addView(deletedCheck, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
        controls.addView(chooserRow, fullWidth(dp(44)))

        val search = EditText(this).apply {
            hint = "品番・商品名・材質で検索"
            textSize = 13.5f
            isSingleLine = true
            setText(ledgerSearchQuery)
            setPadding(dp(13), 0, dp(13), 0)
            background = rounded(Color.WHITE, dp(11).toFloat(), Color.rgb(218, 224, 233))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                    ledgerSearchQuery = value?.toString().orEmpty()
                    reloadProducts()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        controls.addView(search, fullWidth(dp(44)).apply { topMargin = dp(8) })

        ledgerCountView = text("取引先を選択してください", 11.5f, Color.rgb(100, 116, 139), Typeface.NORMAL).apply {
            setPadding(dp(3), dp(7), 0, 0)
        }
        controls.addView(ledgerCountView)
        root.addView(controls, fullWidthWrap())

        ledgerTableView = LedgerTableView(this).apply {
            setOnRowClickListener { showProductDetail(it) }
        }
        root.addView(ledgerTableView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setRootContent(root)

        if (repository.stats().productCount == 0) {
            Toast.makeText(this, "先に商品台帳CSVを読み込んでください", Toast.LENGTH_LONG).show()
        } else if (selectedLedgerCustomer != null) {
            reloadProducts()
        }
    }
'''

sales_method = r'''    private fun showSales() {
        currentScreen = Screen.SALES
        ensureSalesDateRange()

        val root = rootLayout()
        root.addView(appBar("売上分析") { showHome() }, fullWidth(dp(64)))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(Color.rgb(246, 248, 251))
        }

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modeRow.addView(text("個別", 12.5f, Color.rgb(15, 91, 70), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(236,247,242), dp(10).toFloat(), Color.rgb(199,224,212))
        }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(5) })
        modeRow.addView(actionButton("全社", false).apply {
            textSize = 12.5f
            setOnClickListener { showAllSales() }
        }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(5) })
        controls.addView(modeRow, fullWidth(dp(38)))

        val customerButton = actionButton(
            selectedSalesCustomer?.let { if (it.name.isBlank()) it.number else it.name } ?: "取引先を選択",
            false
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener {
                showCustomerPicker("取引先を選択", repository.listSalesCustomers(), this) { customer ->
                    selectedSalesCustomer = customer
                    reloadSales()
                }
            }
        }
        controls.addView(customerButton, fullWidth(dp(44)).apply { topMargin = dp(8) })

        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "今月" to "this",
            "前月" to "prev",
            "前期" to "first",
            "後期" to "second",
            "年度" to "year"
        ).forEachIndexed { index, (label, key) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 10.5f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(55, 65, 81))
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                background = rounded(Color.WHITE, dp(9).toFloat(), Color.rgb(218,224,233))
                setOnClickListener { applySalesPreset(key) }
            }
            presetRow.addView(chip, LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                if (index > 0) leftMargin = dp(4)
            })
        }
        controls.addView(presetRow, fullWidth(dp(36)).apply { topMargin = dp(8) })

        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        salesStartButton = actionButton("", false).apply {
            textSize = 11.5f
            setOnClickListener { chooseSalesDate(true) }
        }
        salesEndButton = actionButton("", false).apply {
            textSize = 11.5f
            setOnClickListener { chooseSalesDate(false) }
        }
        dateRow.addView(salesStartButton, LinearLayout.LayoutParams(0, dp(40), 1f))
        dateRow.addView(text("〜", 15f, Color.rgb(100,116,139), Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(28), dp(40)))
        dateRow.addView(salesEndButton, LinearLayout.LayoutParams(0, dp(40), 1f))
        controls.addView(dateRow, fullWidth(dp(40)).apply { topMargin = dp(7) })
        updateSalesDateButtons()

        val search = EditText(this).apply {
            hint = "品番・商品名で検索"
            textSize = 13.5f
            isSingleLine = true
            setText(salesSearchQuery)
            setPadding(dp(13), 0, dp(13), 0)
            background = rounded(Color.WHITE, dp(11).toFloat(), Color.rgb(218,224,233))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                    salesSearchQuery = value?.toString().orEmpty()
                    reloadSales()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        controls.addView(search, fullWidth(dp(42)).apply { topMargin = dp(7) })

        salesCountView = text("取引先を選択してください", 11.5f, Color.rgb(100, 116, 139), Typeface.NORMAL).apply {
            setPadding(dp(3), dp(7), 0, 0)
        }
        controls.addView(salesCountView)
        root.addView(controls, fullWidthWrap())

        salesTableView = SalesTableView(this).apply {
            setOnMonthClickListener { product, month -> showSalesDayDetail(product, month) }
            setOnProductTotalClickListener { product -> showSalesPeriodDayDetail(product) }
            setOnMonthTotalClickListener { month -> showSalesMonthDayDetail(month) }
        }
        root.addView(salesTableView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setRootContent(root)

        if (repository.salesStats().productCount == 0) {
            Toast.makeText(this, "先に売上分析CSVを読み込んでください", Toast.LENGTH_LONG).show()
        } else if (selectedSalesCustomer != null) {
            reloadSales()
        } else {
            salesTableView?.setData(emptyList(), currentMonthKeys())
        }
    }
'''

pattern = r'    private fun showLedger\(\) \{.*?(?=    private fun showSales\(\) \{)'
s, n1 = re.subn(pattern, ledger_method + "\n", s, count=1, flags=re.S)
if n1 != 1:
    raise SystemExit("showLedger replacement failed")
pattern = r'    private fun showSales\(\) \{.*?(?=    private fun showAllSales\(\) \{)'
s, n2 = re.subn(pattern, sales_method + "\n", s, count=1, flags=re.S)
if n2 != 1:
    raise SystemExit("showSales replacement failed")

helper_anchor = '''    private fun reloadProducts() {
'''
preset_helper = r'''    private fun applySalesPreset(kind: String) {
        val today = LocalDate.now()
        val fiscalYear = if (today.monthValue >= 4) today.year else today.year - 1
        when (kind) {
            "this" -> {
                salesStartDate = today.withDayOfMonth(1)
                salesEndDate = today
            }
            "prev" -> {
                val month = YearMonth.from(today).minusMonths(1)
                salesStartDate = month.atDay(1)
                salesEndDate = month.atEndOfMonth()
            }
            "first" -> {
                salesStartDate = LocalDate.of(fiscalYear, 4, 1)
                salesEndDate = LocalDate.of(fiscalYear, 9, 30)
            }
            "second" -> {
                salesStartDate = LocalDate.of(fiscalYear, 10, 1)
                salesEndDate = LocalDate.of(fiscalYear + 1, 3, 31)
            }
            "year" -> {
                salesStartDate = LocalDate.of(fiscalYear, 4, 1)
                salesEndDate = LocalDate.of(fiscalYear + 1, 3, 31)
            }
        }
        saveSalesDateRange()
        updateSalesDateButtons()
        reloadSales()
    }

'''
if "private fun applySalesPreset(" not in s:
    if helper_anchor not in s:
        raise SystemExit("reloadProducts anchor missing")
    s = s.replace(helper_anchor, preset_helper + helper_anchor, 1)

old_reload_products = r'''    private fun reloadProducts() {
        val customer = selectedLedgerCustomer ?: run {
            ledgerTableView?.setProducts(emptyList())
            ledgerCountView?.text = "取引先を選択してください"
            return
        }
        val products = repository.listProducts(customer.number, includeDeleted)
        ledgerTableView?.setProducts(products)
        ledgerCountView?.text = "${products.size}商品"
    }
'''
new_reload_products = r'''    private fun reloadProducts() {
        val customer = selectedLedgerCustomer ?: run {
            ledgerTableView?.setProducts(emptyList())
            ledgerCountView?.text = "取引先を選択してください"
            return
        }
        val all = repository.listProducts(customer.number, includeDeleted)
        val q = ledgerSearchQuery.trim().lowercase(Locale.JAPAN)
        val products = if (q.isBlank()) all else all.filter {
            it.productNumber.lowercase(Locale.JAPAN).contains(q) ||
            it.productName.lowercase(Locale.JAPAN).contains(q) ||
            it.material.lowercase(Locale.JAPAN).contains(q)
        }
        ledgerTableView?.setProducts(products)
        ledgerCountView?.text = if (q.isBlank()) "${products.size}商品" else "${products.size}商品（検索中）"
    }
'''
if old_reload_products not in s:
    raise SystemExit("reloadProducts block missing")
s = s.replace(old_reload_products, new_reload_products, 1)

old_reload_sales = r'''    private fun reloadSales() {
        val months = currentMonthKeys()
        val customer = selectedSalesCustomer ?: run {
            salesTableView?.setData(emptyList(), months)
            salesCountView?.text = "取引先を選択してください"
            return
        }
        val start = salesStartDate ?: return
        val end = salesEndDate ?: return
        val products = repository.listSalesMonthly(customer.number, start.toString(), end.toString())
        salesTableView?.setData(products, months)
        salesCountView?.text = "${products.size}商品"
    }
'''
new_reload_sales = r'''    private fun reloadSales() {
        val months = currentMonthKeys()
        val customer = selectedSalesCustomer ?: run {
            salesTableView?.setData(emptyList(), months)
            salesCountView?.text = "取引先を選択してください"
            return
        }
        val start = salesStartDate ?: return
        val end = salesEndDate ?: return
        val all = repository.listSalesMonthly(customer.number, start.toString(), end.toString())
        val q = salesSearchQuery.trim().lowercase(Locale.JAPAN)
        val products = if (q.isBlank()) all else all.filter {
            it.productNumber.lowercase(Locale.JAPAN).contains(q) ||
            it.productName.lowercase(Locale.JAPAN).contains(q)
        }
        salesTableView?.setData(products, months)
        salesCountView?.text = if (q.isBlank()) "${products.size}商品" else "${products.size}商品（検索中）"
    }
'''
if old_reload_sales not in s:
    raise SystemExit("reloadSales block missing")
s = s.replace(old_reload_sales, new_reload_sales, 1)

p.write_text(s)

p = root / "SalesDashboardActivity.kt"
s = p.read_text()

old = r'''        val months = months()
        val periodMonths = activePeriodMonths()
        val allTarget = monthTotal("sd_target", "全体", periodMonths, selectedMetric, selectedCategory)
        val allActual = monthTotal("sd_actual", "全体", periodMonths, selectedMetric, selectedCategory)
'''
new = r'''        val months = months()
        val periodMonths = activePeriodMonths()

        val targetCompanies = mutableListOf<Pair<String, String>>()
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
if old not in s:
    raise SystemExit("target summary population anchor missing")
s = s.replace(old, new, 1)

old = r'''        val companies = mutableListOf<Pair<String, String>>("全体" to "全社")
        db.rawQuery("SELECT customer_no,MAX(customer_name) FROM (SELECT customer_no,customer_name FROM sd_target UNION ALL SELECT customer_no,customer_name FROM sd_actual) WHERE customer_no<>'全体' GROUP BY customer_no ORDER BY MAX(customer_name)", null).use {
            while (it.moveToNext()) companies += it.getString(0) to it.getString(1).ifBlank { it.getString(0) }
        }
        val known = companies.map { it.first }.toSet()
        db.rawQuery("SELECT customer_no FROM sd_actual WHERE customer_no<>'全体' GROUP BY customer_no ORDER BY customer_no", null).use { while (it.moveToNext()) if (it.getString(0) !in known) companies += it.getString(0) to it.getString(0) }
'''
new = r'''        val companies = mutableListOf<Pair<String, String>>("全体" to "全社").apply {
            addAll(targetCompanies)
        }
'''
if old not in s:
    raise SystemExit("target company population block missing")
s = s.replace(old, new, 1)

old = r'''        orderedCompanies.forEach { (no, name) ->
            val target = monthTotal("sd_target", no, periodMonths, selectedMetric, selectedCategory)
            val actual = monthTotal("sd_actual", no, periodMonths, selectedMetric, selectedCategory)
'''
new = r'''        orderedCompanies.forEach { (no, name) ->
            val target = if (no == "全体") allTarget else monthTotal("sd_target", no, periodMonths, selectedMetric, selectedCategory)
            val actual = if (no == "全体") allActual else monthTotal("sd_actual", no, periodMonths, selectedMetric, selectedCategory)
'''
if old not in s:
    raise SystemExit("company actual/target row anchor missing")
s = s.replace(old, new, 1)
p.write_text(s)

build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, c1 = re.subn(r"versionCode\s+\d+", "versionCode 32", b, count=1)
b, c2 = re.subn(r"versionName\s+'[^']+'", "versionName '1.5.0'", b, count=1)
if c1 != 1 or c2 != 1:
    raise SystemExit("version declarations not found")
build.write_text(b)
