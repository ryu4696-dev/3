from pathlib import Path

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

# 1) 商品台帳: 付属番号ごとに別商品として扱う
p = root / "CsvImporter.kt"
s = p.read_text()

old = r'''            val customerName = cell(customerNameIdx)
            val productName = cell(productNameIdx)
            val key = "$customerNo\u001F$productNo"
            val builder = builders.getOrPut(key) {
                ProductBuilder(
                    key = key,
                    customerNumber = customerNo,
                    customerName = customerName,
                    productNumber = productNo,
                    productName = productName
                )
            }
'''
new = r'''            val customerName = cell(customerNameIdx)
            val productName = cell(productNameIdx)
            val partNo = integer(partNoIdx)
            val displayProductNo = if (partNo > 0) "$productNo-$partNo" else productNo
            val displayProductName = if (partNo > 0) cell(partNameIdx).ifBlank { productName } else productName
            val key = "$customerNo\u001F$displayProductNo"
            val builder = builders.getOrPut(key) {
                ProductBuilder(
                    key = key,
                    customerNumber = customerNo,
                    customerName = customerName,
                    productNumber = displayProductNo,
                    productName = displayProductName
                )
            }
'''
if old not in s:
    raise SystemExit("CsvImporter product grouping block not found")
s = s.replace(old, new, 1)
s = s.replace("                partNumber = integer(partNoIdx),", "                partNumber = partNo,", 1)
s = s.replace("                ratio = ratio,", "                ratio = 1.0,", 1)
s = s.replace(
    "            builder.add(rawComponent, number(productPriceIdx))",
    "            builder.add(rawComponent, if (partNo > 0) 0.0 else number(productPriceIdx))",
    1
)
p.write_text(s)

# 2) 全社表示用モデル
p = root / "Models.kt"
s = p.read_text()
marker = '''data class SalesDayMetrics(
'''
model = '''data class SalesCompanyMonthly(
    val customer: Customer,
    val products: List<SalesProductMonthly>
)

'''
if marker not in s:
    raise SystemExit("Models insertion marker not found")
s = s.replace(marker, model + marker, 1)
p.write_text(s)

# 3) 全社売上を一括取得
p = root / "DataRepository.kt"
s = p.read_text()
marker = '''    fun listSalesDays(
'''
method = r'''    fun listSalesAll(startDate: String, endDate: String): List<SalesCompanyMonthly> {
        val sql = """
            SELECT d.customer_no,
                   MAX(d.customer_name) AS customer_name,
                   d.product_no,
                   COALESCE(NULLIF(MAX(p.product_name), ''), NULLIF(MAX(sp.product_name), ''), d.product_no) AS product_name,
                   substr(d.sale_date, 1, 7) AS sale_month,
                   SUM(d.amount), SUM(d.quantity), SUM(d.sqm)
            FROM sales_daily d
            LEFT JOIN products p
                   ON p.customer_no = d.customer_no AND p.product_no = d.product_no
            LEFT JOIN sales_products sp
                   ON sp.customer_no = d.customer_no AND sp.product_no = d.product_no
            WHERE d.sale_date BETWEEN ? AND ?
            GROUP BY d.customer_no, d.product_no, sale_month
            ORDER BY d.customer_no, d.product_no COLLATE NOCASE, sale_month
        """.trimIndent()

        val companies = LinkedHashMap<String, SalesCompanyBuilder>()
        readableDatabase.rawQuery(sql, arrayOf(startDate, endDate)).use { c ->
            while (c.moveToNext()) {
                val customerNo = c.getString(0)
                val customerName = c.getString(1) ?: ""
                val company = companies.getOrPut(customerNo) {
                    SalesCompanyBuilder(customerNo, customerName)
                }
                if (company.customerName.isBlank() && customerName.isNotBlank()) {
                    company.customerName = customerName
                }

                val productNo = c.getString(2)
                val product = company.products.getOrPut(productNo) {
                    SalesMonthlyBuilder(productNo, c.getString(3) ?: productNo)
                }
                if (product.productName.isBlank() && !c.isNull(3)) {
                    product.productName = c.getString(3)
                }
                product.monthly[c.getString(4)] = SalesMetrics(
                    amount = c.getDouble(5),
                    quantity = c.getDouble(6),
                    sqm = c.getDouble(7)
                )
            }
        }

        return companies.values.map { company ->
            SalesCompanyMonthly(
                customer = Customer(company.customerNumber, company.customerName),
                products = company.products.values.map {
                    SalesProductMonthly(it.productNumber, it.productName, LinkedHashMap(it.monthly))
                }
            )
        }
    }

'''
if marker not in s:
    raise SystemExit("DataRepository listSalesDays marker not found")
s = s.replace(marker, method + marker, 1)

marker2 = '''    private data class SalesMonthlyBuilder(
'''
builder = '''    private data class SalesCompanyBuilder(
        val customerNumber: String,
        var customerName: String,
        val products: LinkedHashMap<String, SalesMonthlyBuilder> = LinkedHashMap()
    )

'''
if marker2 not in s:
    raise SystemExit("DataRepository builder marker not found")
s = s.replace(marker2, builder + marker2, 1)
p.write_text(s)

# 4) 売上分析に「全社をまとめて表示」を追加
p = root / "MainActivity.kt"
s = p.read_text()

button_marker = '''        controls.addView(customerButton, fullWidth(dp(48)))

        val dateRow = LinearLayout(this).apply {
'''
button_replacement = '''        controls.addView(customerButton, fullWidth(dp(48)))
        controls.addView(actionButton("全社をまとめて表示", false).apply {
            setOnClickListener { showAllSales() }
        }, fullWidth(dp(48)).apply { topMargin = dp(8) })

        val dateRow = LinearLayout(this).apply {
'''
if button_marker not in s:
    raise SystemExit("MainActivity sales button marker not found")
s = s.replace(button_marker, button_replacement, 1)

appbar_marker = '''    private fun appBar(title: String, onBack: () -> Unit): LinearLayout {
'''
all_sales_methods = '''    private fun showAllSales() {
        currentScreen = Screen.SALES
        ensureSalesDateRange()

        val root = rootLayout()
        root.addView(appBar("売上分析・全社") { showSales() }, fullWidth(dp(64)))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.WHITE)
        }
        controls.addView(text("全社を取引先ごとに連続表示", 15f, Color.rgb(15, 23, 42), Typeface.BOLD))

        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        salesStartButton = actionButton("", false).apply {
            textSize = 12.5f
            setOnClickListener { chooseAllSalesDate(true) }
        }
        salesEndButton = actionButton("", false).apply {
            textSize = 12.5f
            setOnClickListener { chooseAllSalesDate(false) }
        }
        dateRow.addView(salesStartButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        dateRow.addView(text("～", 16f, Color.rgb(100, 116, 139), Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(30), dp(48)))
        dateRow.addView(salesEndButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        controls.addView(dateRow, fullWidth(dp(48)).apply { topMargin = dp(8) })
        updateSalesDateButtons()

        val countView = text("", 11.5f, Color.rgb(100, 116, 139), Typeface.NORMAL).apply {
            setPadding(dp(2), dp(7), 0, 0)
        }
        controls.addView(countView)
        root.addView(controls, fullWidthWrap())

        val table = AllSalesTableView(this).apply {
            setOnMonthClickListener { customer, product, month ->
                val previous = selectedSalesCustomer
                selectedSalesCustomer = customer
                showSalesDayDetail(product, month)
                selectedSalesCustomer = previous
            }
        }
        root.addView(table, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setRootContent(root)

        val start = salesStartDate
        val end = salesEndDate
        if (start == null || end == null) {
            table.setData(emptyList(), currentMonthKeys())
            countView.text = "期間を指定してください"
            return
        }

        val sections = repository.listSalesAll(start.toString(), end.toString())
        table.setData(sections, currentMonthKeys())
        val productCount = sections.sumOf { it.products.size }
        countView.text = "${sections.size}取引先 / ${productCount}商品"
    }

    private fun chooseAllSalesDate(start: Boolean) {
        val base = (if (start) salesStartDate else salesEndDate) ?: LocalDate.now()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selected = LocalDate.of(year, month + 1, day)
                if (start) {
                    salesStartDate = selected
                    if (salesEndDate == null || selected > salesEndDate) salesEndDate = selected
                } else {
                    salesEndDate = selected
                    if (salesStartDate == null || selected < salesStartDate) salesStartDate = selected
                }
                saveSalesDateRange()
                showAllSales()
            },
            base.year,
            base.monthValue - 1,
            base.dayOfMonth
        ).show()
    }

'''
if appbar_marker not in s:
    raise SystemExit("MainActivity appBar marker not found")
s = s.replace(appbar_marker, all_sales_methods + appbar_marker, 1)
p.write_text(s)

print("MiniCoPaTis v0.5.0 source patches applied")
