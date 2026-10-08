from pathlib import Path
import re

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

# ---------- MainActivity: Sales Analysis XLSX export only ----------
p = root / "MainActivity.kt"
s = p.read_text()

old = '''        val root = rootLayout()
        root.addView(appBar("売上分析") { showHome() }, fullWidth(dp(64)))
'''
new = '''        val root = rootLayout()
        val salesBar = appBar("売上分析") { showHome() }.apply {
            addView(text("Excel", 12.5f, Color.rgb(15, 91, 70), Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
                background = rounded(Color.rgb(236, 247, 242), dp(10).toFloat(), Color.rgb(199, 224, 212))
                setOnClickListener { exportSalesExcel() }
            }, LinearLayout.LayoutParams(dp(68), dp(38)))
        }
        root.addView(salesBar, fullWidth(dp(64)))
'''
if old not in s:
    raise SystemExit("sales app bar anchor missing")
s = s.replace(old, new, 1)

anchor = '''    private fun ensureSalesDateRange() {
'''
export_fn = r'''    private fun exportSalesExcel() {
        val customer = selectedSalesCustomer ?: run {
            Toast.makeText(this, "取引先を選択してください", Toast.LENGTH_SHORT).show()
            return
        }
        val start = salesStartDate ?: return
        val end = salesEndDate ?: return
        val months = currentMonthKeys()
        val q = salesSearchQuery.trim().lowercase(Locale.JAPAN)

        val sections: List<SalesCompanyMonthly> = if (customer.number == "全社") {
            repository.listSalesAll(start.toString(), end.toString())
                .mapNotNull { section ->
                    if (q.isBlank()) return@mapNotNull section
                    val companyMatch =
                        section.customer.number.lowercase(Locale.JAPAN).contains(q) ||
                        section.customer.name.lowercase(Locale.JAPAN).contains(q)
                    if (companyMatch) return@mapNotNull section
                    val products = section.products.filter {
                        it.productNumber.lowercase(Locale.JAPAN).contains(q) ||
                        it.productName.lowercase(Locale.JAPAN).contains(q)
                    }
                    if (products.isEmpty()) null else SalesCompanyMonthly(section.customer, products)
                }
        } else {
            val all = repository.listSalesMonthly(customer.number, start.toString(), end.toString())
            val products = if (q.isBlank()) all else all.filter {
                it.productNumber.lowercase(Locale.JAPAN).contains(q) ||
                it.productName.lowercase(Locale.JAPAN).contains(q)
            }
            listOf(SalesCompanyMonthly(customer, products))
        }

        if (sections.all { it.products.isEmpty() }) {
            Toast.makeText(this, "出力する売上データがありません", Toast.LENGTH_SHORT).show()
            return
        }

        fun totalOf(product: SalesProductMonthly): SalesMetrics =
            product.monthly.values.fold(SalesMetrics()) { acc, v ->
                SalesMetrics(acc.amount + v.amount, acc.quantity + v.quantity, acc.sqm + v.sqm)
            }
        fun companyTotal(section: SalesCompanyMonthly): SalesMetrics =
            section.products.fold(SalesMetrics()) { acc, p ->
                val v = totalOf(p)
                SalesMetrics(acc.amount + v.amount, acc.quantity + v.quantity, acc.sqm + v.sqm)
            }
        val grand = sections.fold(SalesMetrics()) { acc, section ->
            val v = companyTotal(section)
            SalesMetrics(acc.amount + v.amount, acc.quantity + v.quantity, acc.sqm + v.sqm)
        }

        val listSheet = ExcelExport.Sheet("一覧").apply {
            width(1, 14.0); width(2, 28.0); width(3, 18.0); width(4, 34.0)
            width(5, 16.0); width(6, 14.0); width(7, 14.0)
            row(ExcelExport.text("売上分析", ExcelExport.TITLE))
            merge(1, 1, 1, 7)
            row(ExcelExport.text("取引先", ExcelExport.SECTION), ExcelExport.text(customer.label))
            row(ExcelExport.text("期間", ExcelExport.SECTION), ExcelExport.text("${start.format(uiDate)} ～ ${end.format(uiDate)}"))
            row(ExcelExport.text("検索", ExcelExport.SECTION), ExcelExport.text(if (salesSearchQuery.isBlank()) "なし" else salesSearchQuery))
            blank()
            row(
                ExcelExport.text("期間合計", ExcelExport.SECTION),
                ExcelExport.text(""),
                ExcelExport.text(""),
                ExcelExport.text(""),
                ExcelExport.num(grand.amount, ExcelExport.MONEY),
                ExcelExport.num(grand.sqm, ExcelExport.SQM),
                ExcelExport.num(grand.quantity, ExcelExport.QTY)
            )
            row(
                ExcelExport.text("得意先番号", ExcelExport.HEADER),
                ExcelExport.text("得意先名", ExcelExport.HEADER),
                ExcelExport.text("品番", ExcelExport.HEADER),
                ExcelExport.text("商品名", ExcelExport.HEADER),
                ExcelExport.text("金額", ExcelExport.HEADER),
                ExcelExport.text("平米", ExcelExport.HEADER),
                ExcelExport.text("数量", ExcelExport.HEADER)
            )
        }

        sections.forEach { section ->
            val company = companyTotal(section)
            listSheet.row(
                ExcelExport.text(section.customer.number, ExcelExport.SECTION),
                ExcelExport.text(section.customer.name.ifBlank { section.customer.number }, ExcelExport.SECTION),
                ExcelExport.text("得意先計", ExcelExport.SECTION),
                ExcelExport.text("", ExcelExport.SECTION),
                ExcelExport.num(company.amount, ExcelExport.MONEY),
                ExcelExport.num(company.sqm, ExcelExport.SQM),
                ExcelExport.num(company.quantity, ExcelExport.QTY)
            )
            section.products.forEach { product ->
                val total = totalOf(product)
                listSheet.row(
                    ExcelExport.text(section.customer.number),
                    ExcelExport.text(section.customer.name.ifBlank { section.customer.number }),
                    ExcelExport.text(product.productNumber),
                    ExcelExport.text(product.productName.ifBlank { product.productNumber }),
                    ExcelExport.num(total.amount, ExcelExport.MONEY),
                    ExcelExport.num(total.sqm, ExcelExport.SQM),
                    ExcelExport.num(total.quantity, ExcelExport.QTY)
                )
            }
        }

        val monthSheet = ExcelExport.Sheet("月別").apply {
            width(1, 14.0); width(2, 28.0); width(3, 18.0); width(4, 34.0)
            width(5, 10.0); width(6, 16.0); width(7, 14.0); width(8, 14.0)
            row(ExcelExport.text("売上分析・月別", ExcelExport.TITLE))
            merge(1, 1, 1, 8)
            row(
                ExcelExport.text("得意先番号", ExcelExport.HEADER),
                ExcelExport.text("得意先名", ExcelExport.HEADER),
                ExcelExport.text("品番", ExcelExport.HEADER),
                ExcelExport.text("商品名", ExcelExport.HEADER),
                ExcelExport.text("月", ExcelExport.HEADER),
                ExcelExport.text("金額", ExcelExport.HEADER),
                ExcelExport.text("平米", ExcelExport.HEADER),
                ExcelExport.text("数量", ExcelExport.HEADER)
            )
        }

        sections.forEach { section ->
            section.products.forEach { product ->
                months.forEach { month ->
                    val v = product.monthly[month] ?: return@forEach
                    monthSheet.row(
                        ExcelExport.text(section.customer.number),
                        ExcelExport.text(section.customer.name.ifBlank { section.customer.number }),
                        ExcelExport.text(product.productNumber),
                        ExcelExport.text(product.productName.ifBlank { product.productNumber }),
                        ExcelExport.text("${month.substring(5).toInt()}月"),
                        ExcelExport.num(v.amount, ExcelExport.MONEY),
                        ExcelExport.num(v.sqm, ExcelExport.SQM),
                        ExcelExport.num(v.quantity, ExcelExport.QTY)
                    )
                }
            }
        }

        try {
            val who = if (customer.number == "全社") "全社" else customer.name.ifBlank { customer.number }
            val file = "売上分析_${who}_${start}_${end}.xlsx"
            val path = ExcelExport.save(this, file, listOf(listSheet, monthSheet))
            Toast.makeText(this, "Excelを保存しました\n$path", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Excel出力に失敗しました: ${e.message ?: "不明なエラー"}", Toast.LENGTH_LONG).show()
        }
    }

'''
if anchor not in s:
    raise SystemExit("sales export insertion anchor missing")
s = s.replace(anchor, export_fn + anchor, 1)
p.write_text(s)

# ---------- SalesDashboardActivity: Sales Target XLSX export only ----------
p = root / "SalesDashboardActivity.kt"
s = p.read_text()

old = '''        top.addView(label(if (ordersOnly) "受注情報" else "販売目標", 21f, Color.rgb(15, 23, 42), true), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top, LinearLayout.LayoutParams(-1, dp(64)))
'''
new = '''        top.addView(label(if (ordersOnly) "受注情報" else "販売目標", 21f, Color.rgb(15, 23, 42), true), LinearLayout.LayoutParams(0, -2, 1f))
        if (!ordersOnly) {
            top.addView(label("Excel", 12.5f, Color.rgb(15, 91, 70), true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.rgb(236, 247, 242))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.rgb(199, 224, 212))
                }
                setOnClickListener { exportPerformanceExcel() }
            }, LinearLayout.LayoutParams(dp(68), dp(38)))
        }
        root.addView(top, LinearLayout.LayoutParams(-1, dp(64)))
'''
if old not in s:
    raise SystemExit("target export button anchor missing")
s = s.replace(old, new, 1)

anchor = '''    private fun addSortControls(body: LinearLayout) {
'''
export_target = r'''    private fun exportPerformanceExcel() {
        val periodMonths = activePeriodMonths()
        val salesCompanies = mutableListOf<Pair<String, String>>()
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

        val companies = mutableListOf<Pair<String, String>>("全体" to "全社").apply { addAll(salesCompanies) }
        val ordered = companies.take(1) + companies.drop(1).sortedWith { left, right ->
            fun score(company: Pair<String,String>): Double {
                val no = company.first
                val metric = if (sortMetric == "達成率") selectedMetric else sortMetric
                val actual = monthTotal("sd_actual", no, periodMonths, metric, selectedCategory)
                if (sortMetric != "達成率") return actual
                val target = monthTotal("sd_target", no, periodMonths, metric, selectedCategory)
                return if (target > 0.0) actual / target else Double.NaN
            }
            val a = score(left); val b = score(right)
            when {
                a.isNaN() && b.isNaN() -> left.second.compareTo(right.second)
                a.isNaN() -> 1
                b.isNaN() -> -1
                else -> (if (sortGoodFirst) b.compareTo(a) else a.compareTo(b)).takeIf { it != 0 }
                    ?: left.second.compareTo(right.second)
            }
        }

        val q = customerSearchQuery.trim()
        val visible = ordered.filter { (no, name) ->
            q.isBlank() || "$name $no".contains(q, ignoreCase = true)
        }

        val unit = if (selectedMetric == "金額") "千円" else "千㎡"
        fun exportValue(v: Double): Double = v / 1000.0

        val listSheet = ExcelExport.Sheet("一覧").apply {
            width(1, 14.0); width(2, 30.0); width(3, 15.0); width(4, 15.0); width(5, 13.0)
            row(ExcelExport.text("販売目標", ExcelExport.TITLE))
            merge(1, 1, 1, 5)
            row(ExcelExport.text("分類", ExcelExport.SECTION), ExcelExport.text(selectedCategory))
            row(ExcelExport.text("表示", ExcelExport.SECTION), ExcelExport.text(selectedMetric))
            row(ExcelExport.text("期間", ExcelExport.SECTION), ExcelExport.text(periodLabel()))
            row(ExcelExport.text("並び順", ExcelExport.SECTION), ExcelExport.text("$sortMetric・${if (sortGoodFirst) "良い順" else "悪い順"}"))
            row(ExcelExport.text("検索", ExcelExport.SECTION), ExcelExport.text(if (q.isBlank()) "なし" else customerSearchQuery))
            blank()
            row(
                ExcelExport.text("全社", ExcelExport.SECTION),
                ExcelExport.text(""),
                ExcelExport.num(exportValue(allTarget), ExcelExport.TARGET),
                ExcelExport.num(exportValue(allActual), ExcelExport.ACTUAL),
                if (allTarget > 0) ExcelExport.num(allActual / allTarget, ExcelExport.rateStyle(allActual, allTarget))
                else ExcelExport.text("—", ExcelExport.MUTED)
            )
            row(
                ExcelExport.text("得意先番号", ExcelExport.HEADER),
                ExcelExport.text("得意先名", ExcelExport.HEADER),
                ExcelExport.text("目標（$unit）", ExcelExport.HEADER),
                ExcelExport.text("実績（$unit）", ExcelExport.HEADER),
                ExcelExport.text("達成率", ExcelExport.HEADER)
            )
        }

        visible.forEach { (no, name) ->
            val target = if (no == "全体") allTarget else monthTotal("sd_target", no, periodMonths, selectedMetric, selectedCategory)
            val actual = if (no == "全体") allActual else monthTotal("sd_actual", no, periodMonths, selectedMetric, selectedCategory)
            listSheet.row(
                ExcelExport.text(if (no == "全体") "" else no),
                ExcelExport.text(if (no == "全体") "全社" else name),
                if (target > 0) ExcelExport.num(exportValue(target), ExcelExport.TARGET) else ExcelExport.text("—", ExcelExport.MUTED),
                ExcelExport.num(exportValue(actual), ExcelExport.ACTUAL),
                if (target > 0) ExcelExport.num(actual / target, ExcelExport.rateStyle(actual, target))
                else ExcelExport.text("—", ExcelExport.MUTED)
            )
        }

        val monthSheet = ExcelExport.Sheet("月別").apply {
            width(1, 14.0); width(2, 30.0); width(3, 10.0); width(4, 15.0); width(5, 15.0); width(6, 13.0)
            row(ExcelExport.text("販売目標・月別", ExcelExport.TITLE))
            merge(1, 1, 1, 6)
            row(
                ExcelExport.text("得意先番号", ExcelExport.HEADER),
                ExcelExport.text("得意先名", ExcelExport.HEADER),
                ExcelExport.text("月", ExcelExport.HEADER),
                ExcelExport.text("目標（$unit）", ExcelExport.HEADER),
                ExcelExport.text("実績（$unit）", ExcelExport.HEADER),
                ExcelExport.text("達成率", ExcelExport.HEADER)
            )
        }

        visible.forEach { (no, name) ->
            periodMonths.forEach { month ->
                val target = if (no == "全体") {
                    salesCompanies.sumOf { (customerNo, _) ->
                        oneValue("sd_target", customerNo, month, selectedMetric, selectedCategory)
                    }
                } else oneValue("sd_target", no, month, selectedMetric, selectedCategory)
                val actual = if (no == "全体") {
                    salesCompanies.sumOf { (customerNo, _) ->
                        oneValue("sd_actual", customerNo, month, selectedMetric, selectedCategory)
                    }
                } else oneValue("sd_actual", no, month, selectedMetric, selectedCategory)

                monthSheet.row(
                    ExcelExport.text(if (no == "全体") "" else no),
                    ExcelExport.text(if (no == "全体") "全社" else name),
                    ExcelExport.text("${month.substring(5).toInt()}月"),
                    if (target > 0) ExcelExport.num(exportValue(target), ExcelExport.TARGET) else ExcelExport.text("—", ExcelExport.MUTED),
                    ExcelExport.num(exportValue(actual), ExcelExport.ACTUAL),
                    if (target > 0) ExcelExport.num(actual / target, ExcelExport.rateStyle(actual, target))
                    else ExcelExport.text("—", ExcelExport.MUTED)
                )
            }
        }

        try {
            val file = "販売目標_${selectedCategory}_${selectedMetric}_${periodLabel().replace("（","").replace("）","")}.xlsx"
            val path = ExcelExport.save(this, file, listOf(listSheet, monthSheet))
            Toast.makeText(this, "Excelを保存しました\n$path", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Excel出力に失敗しました: ${e.message ?: "不明なエラー"}", Toast.LENGTH_LONG).show()
        }
    }

'''
if anchor not in s:
    raise SystemExit("target export insertion anchor missing")
s = s.replace(anchor, export_target + anchor, 1)
p.write_text(s)

# Version only.
build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, c1 = re.subn(r"versionCode\s+\d+", "versionCode 42", b, count=1)
b, c2 = re.subn(r"versionName\s+'[^']+'", "versionName '1.9.6'", b, count=1)
if c1 != 1 or c2 != 1:
    raise SystemExit("version declarations not found")
build.write_text(b)
