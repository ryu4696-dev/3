from pathlib import Path

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

p = root / "Models.kt"
s = p.read_text()
marker = '''data class SalesStats(
'''
model = '''data class SalesDayProductMetrics(
    val date: String,
    val productNumber: String,
    val productName: String,
    val amount: Double,
    val quantity: Double,
    val sqm: Double
)

'''
if model not in s:
    if marker not in s:
        raise SystemExit("Models SalesStats marker not found")
    s = s.replace(marker, model + marker, 1)
p.write_text(s)

p = root / "DataRepository.kt"
s = p.read_text()
marker = '''    fun getComponents(productKey: String): List<ComponentInfo> {
'''
methods = r'''    fun listSalesProductDays(
        customerNumber: String,
        productNumber: String,
        startDate: String,
        endDate: String
    ): List<SalesDayMetrics> {
        val out = mutableListOf<SalesDayMetrics>()
        readableDatabase.rawQuery(
            """
            SELECT sale_date, SUM(amount), SUM(quantity), SUM(sqm)
            FROM sales_daily
            WHERE customer_no = ? AND product_no = ?
              AND sale_date BETWEEN ? AND ?
            GROUP BY sale_date
            ORDER BY sale_date
            """.trimIndent(),
            arrayOf(customerNumber, productNumber, startDate, endDate)
        ).use { c ->
            while (c.moveToNext()) {
                out += SalesDayMetrics(
                    date = c.getString(0),
                    amount = c.getDouble(1),
                    quantity = c.getDouble(2),
                    sqm = c.getDouble(3)
                )
            }
        }
        return out
    }

    fun listSalesMonthDays(
        customerNumber: String,
        month: String,
        startDate: String,
        endDate: String
    ): List<SalesDayProductMetrics> {
        val out = mutableListOf<SalesDayProductMetrics>()
        readableDatabase.rawQuery(
            """
            SELECT d.sale_date,
                   d.product_no,
                   COALESCE(NULLIF(MAX(p.product_name), ''), NULLIF(MAX(sp.product_name), ''), d.product_no) AS product_name,
                   SUM(d.amount), SUM(d.quantity), SUM(d.sqm)
            FROM sales_daily d
            LEFT JOIN products p
                   ON p.customer_no = d.customer_no AND p.product_no = d.product_no
            LEFT JOIN sales_products sp
                   ON sp.customer_no = d.customer_no AND sp.product_no = d.product_no
            WHERE d.customer_no = ?
              AND d.sale_date BETWEEN ? AND ?
              AND substr(d.sale_date, 1, 7) = ?
            GROUP BY d.sale_date, d.product_no
            ORDER BY d.sale_date, d.product_no COLLATE NOCASE
            """.trimIndent(),
            arrayOf(customerNumber, startDate, endDate, month)
        ).use { c ->
            while (c.moveToNext()) {
                out += SalesDayProductMetrics(
                    date = c.getString(0),
                    productNumber = c.getString(1),
                    productName = c.getString(2) ?: c.getString(1),
                    amount = c.getDouble(3),
                    quantity = c.getDouble(4),
                    sqm = c.getDouble(5)
                )
            }
        }
        return out
    }

'''
if "fun listSalesProductDays(" not in s:
    if marker not in s:
        raise SystemExit("DataRepository getComponents marker not found")
    s = s.replace(marker, methods + marker, 1)
p.write_text(s)

p = root / "SalesTableView.kt"
s = p.read_text()

old = '''    private var monthClickListener: ((SalesProductMonthly, String) -> Unit)? = null
'''
new = '''    private var monthClickListener: ((SalesProductMonthly, String) -> Unit)? = null
    private var productTotalClickListener: ((SalesProductMonthly) -> Unit)? = null
    private var monthTotalClickListener: ((String) -> Unit)? = null
'''
if old not in s:
    raise SystemExit("SalesTableView listener field marker not found")
s = s.replace(old, new, 1)

old = '''        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (e.y <= headerHeight || e.x < fixedWidth) return true
            val rowIndex = ((e.y - headerHeight + offsetY) / rowHeight).toInt()
            val columnIndex = ((e.x - fixedWidth + offsetX) / monthWidth).toInt()
            val product = products.getOrNull(rowIndex) ?: return true
            val month = months.getOrNull(columnIndex) ?: return true
            if (product.monthly[month] != null) monthClickListener?.invoke(product, month)
            return true
        }
'''
new = '''        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (e.y <= headerHeight || e.x < fixedWidth) return true
            val rowIndex = ((e.y - headerHeight + offsetY) / rowHeight).toInt()
            val columnIndex = ((e.x - fixedWidth + offsetX) / monthWidth).toInt()

            when {
                rowIndex in products.indices && columnIndex in months.indices -> {
                    val product = products[rowIndex]
                    val month = months[columnIndex]
                    if (product.monthly[month] != null) monthClickListener?.invoke(product, month)
                }
                rowIndex in products.indices && columnIndex == months.size -> {
                    productTotalClickListener?.invoke(products[rowIndex])
                }
                rowIndex == products.size && columnIndex in months.indices -> {
                    monthTotalClickListener?.invoke(months[columnIndex])
                }
            }
            return true
        }
'''
if old not in s:
    raise SystemExit("SalesTableView tap handler not found")
s = s.replace(old, new, 1)

marker = '''    fun setOnMonthClickListener(listener: (SalesProductMonthly, String) -> Unit) {
        monthClickListener = listener
    }
'''
addition = '''    fun setOnMonthClickListener(listener: (SalesProductMonthly, String) -> Unit) {
        monthClickListener = listener
    }

    fun setOnProductTotalClickListener(listener: (SalesProductMonthly) -> Unit) {
        productTotalClickListener = listener
    }

    fun setOnMonthTotalClickListener(listener: (String) -> Unit) {
        monthTotalClickListener = listener
    }
'''
if marker not in s:
    raise SystemExit("SalesTableView listener method marker not found")
s = s.replace(marker, addition, 1)
p.write_text(s)

p = root / "MainActivity.kt"
s = p.read_text()

old = '''        salesTableView = SalesTableView(this).apply {
            setOnMonthClickListener { product, month -> showSalesDayDetail(product, month) }
        }
'''
new = '''        salesTableView = SalesTableView(this).apply {
            setOnMonthClickListener { product, month -> showSalesDayDetail(product, month) }
            setOnProductTotalClickListener { product -> showSalesPeriodDayDetail(product) }
            setOnMonthTotalClickListener { month -> showSalesMonthDayDetail(month) }
        }
'''
if old not in s:
    raise SystemExit("MainActivity SalesTableView listener block not found")
s = s.replace(old, new, 1)

marker = '''    private fun showProductDetail(product: ProductSummary) {
'''
methods = '''    private fun showSalesPeriodDayDetail(product: SalesProductMonthly) {
        val customer = selectedSalesCustomer ?: return
        val start = salesStartDate ?: return
        val end = salesEndDate ?: return
        val days = repository.listSalesProductDays(
            customer.number,
            product.productNumber,
            start.toString(),
            end.toString()
        )
        if (days.isEmpty()) return

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(22))
        }
        scroll.addView(body)
        body.addView(text(product.productNumber, 12f, Color.rgb(100, 116, 139), Typeface.NORMAL))
        body.addView(text(product.productName, 18f, Color.rgb(15, 23, 42), Typeface.BOLD).apply {
            setPadding(0, dp(3), 0, dp(3))
        })
        body.addView(text(
            "${start} ～ ${end}",
            12f,
            Color.rgb(100, 116, 139),
            Typeface.NORMAL
        ).apply { setPadding(0, 0, 0, dp(12)) })

        for ((index, day) in days.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(
                    if (index % 2 == 0) Color.rgb(248, 250, 252) else Color.WHITE,
                    dp(9).toFloat(),
                    Color.rgb(226, 232, 240)
                )
            }
            val date = parseIsoDate(day.date)
            val dateLabel = date?.format(DateTimeFormatter.ofPattern("M/d (E)", Locale.JAPAN)) ?: day.date
            row.addView(text(dateLabel, 14f, Color.rgb(15, 23, 42), Typeface.BOLD))
            row.addView(text(
                "金額  ¥${money.format(day.amount)}    数量  ${number.format(day.quantity)}\\n平米  ${number.format(day.sqm)}㎡",
                12.5f,
                Color.rgb(51, 65, 85),
                Typeface.NORMAL
            ).apply { setPadding(0, dp(5), 0, 0) })
            body.addView(row, fullWidthWrap().apply { bottomMargin = dp(7) })
        }

        AlertDialog.Builder(this)
            .setTitle("指定期間 日別")
            .setView(scroll)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun showSalesMonthDayDetail(month: String) {
        val customer = selectedSalesCustomer ?: return
        val start = salesStartDate ?: return
        val end = salesEndDate ?: return
        val days = repository.listSalesMonthDays(
            customer.number,
            month,
            start.toString(),
            end.toString()
        )
        if (days.isEmpty()) return

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(22))
        }
        scroll.addView(body)

        for ((index, day) in days.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(
                    if (index % 2 == 0) Color.rgb(248, 250, 252) else Color.WHITE,
                    dp(9).toFloat(),
                    Color.rgb(226, 232, 240)
                )
            }
            val date = parseIsoDate(day.date)
            val dateLabel = date?.format(DateTimeFormatter.ofPattern("M/d (E)", Locale.JAPAN)) ?: day.date
            row.addView(text(dateLabel, 14f, Color.rgb(15, 23, 42), Typeface.BOLD))
            row.addView(text(day.productNumber, 11.5f, Color.rgb(100, 116, 139), Typeface.NORMAL).apply {
                setPadding(0, dp(4), 0, 0)
            })
            row.addView(text(day.productName, 14f, Color.rgb(15, 23, 42), Typeface.BOLD).apply {
                setPadding(0, dp(2), 0, dp(2))
            })
            row.addView(text(
                "金額  ¥${money.format(day.amount)}    数量  ${number.format(day.quantity)}\\n平米  ${number.format(day.sqm)}㎡",
                12.5f,
                Color.rgb(51, 65, 85),
                Typeface.NORMAL
            ).apply { setPadding(0, dp(4), 0, 0) })
            body.addView(row, fullWidthWrap().apply { bottomMargin = dp(7) })
        }

        val ym = runCatching { YearMonth.parse(month) }.getOrNull()
        val title = if (ym != null) "${ym.year}年${ym.monthValue}月 日別・全商品" else "$month 日別・全商品"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("閉じる", null)
            .show()
    }

'''
if marker not in s:
    raise SystemExit("MainActivity showProductDetail marker not found")
s = s.replace(marker, methods + marker, 1)
p.write_text(s)

g = Path("ledgerapp/app/build.gradle")
gs = g.read_text()
gs = gs.replace("versionCode 16", "versionCode 17")
gs = gs.replace("versionName '0.6.0'", "versionName '0.6.1'")
g.write_text(gs)

print("MiniCoPaTis v0.6.1 sales total drill-down patch applied")
