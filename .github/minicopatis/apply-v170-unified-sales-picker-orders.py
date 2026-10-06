from pathlib import Path
import re

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

# ---------- Sales analysis: put 全社 into the same customer picker ----------
p = root / "MainActivity.kt"
s = p.read_text()

if "private var allSalesTableView" not in s:
    s = s.replace(
        "    private var salesTableView: SalesTableView? = null",
        "    private var salesTableView: SalesTableView? = null\n    private var allSalesTableView: AllSalesTableView? = null",
        1
    )

new_show_sales = r'''    private fun showSales() {
        currentScreen = Screen.SALES
        ensureSalesDateRange()

        val targetCustomers = repository.listTargetCustomers()
        val choices = listOf(Customer("全社", "")) + targetCustomers
        val validNumbers = choices.map { it.number }.toSet()
        if (selectedSalesCustomer?.number !in validNumbers) selectedSalesCustomer = null

        val root = rootLayout()
        root.addView(appBar("売上分析") { showHome() }, fullWidth(dp(64)))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(Color.rgb(246, 248, 251))
        }

        val customerButton = actionButton(
            selectedSalesCustomer?.label ?: "取引先を選択",
            false
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener {
                showCustomerPicker("取引先を選択", choices, this) { customer ->
                    selectedSalesCustomer = customer
                    showSales()
                }
            }
        }
        controls.addView(customerButton, fullWidth(dp(44)))

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
            hint = if (selectedSalesCustomer?.number == "全社") "取引先・品番・商品名で検索" else "品番・商品名で検索"
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

        salesCountView = text(
            if (selectedSalesCustomer == null) "取引先を選択してください" else "販売目標対象のみ",
            11.5f,
            Color.rgb(100, 116, 139),
            Typeface.NORMAL
        ).apply { setPadding(dp(3), dp(7), 0, 0) }
        controls.addView(salesCountView)
        root.addView(controls, fullWidthWrap())

        if (selectedSalesCustomer?.number == "全社") {
            salesTableView = null
            allSalesTableView = AllSalesTableView(this).apply {
                setOnMonthClickListener { customer, product, month ->
                    val previous = selectedSalesCustomer
                    selectedSalesCustomer = customer
                    showSalesDayDetail(product, month)
                    selectedSalesCustomer = previous
                }
            }
            root.addView(allSalesTableView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            allSalesTableView = null
            salesTableView = SalesTableView(this).apply {
                setOnMonthClickListener { product, month -> showSalesDayDetail(product, month) }
                setOnProductTotalClickListener { product -> showSalesPeriodDayDetail(product) }
                setOnMonthTotalClickListener { month -> showSalesMonthDayDetail(month) }
            }
            root.addView(salesTableView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        setRootContent(root)

        if (repository.salesStats().productCount == 0) {
            Toast.makeText(this, "先に売上分析CSVを読み込んでください", Toast.LENGTH_LONG).show()
        } else if (selectedSalesCustomer != null) {
            reloadSales()
        } else {
            salesTableView?.setData(emptyList(), currentMonthKeys())
            allSalesTableView?.setData(emptyList(), currentMonthKeys())
        }
    }
'''

s, n = re.subn(
    r'    private fun showSales\(\) \{.*?(?=    private fun showAllSales\(\) \{)',
    new_show_sales + "\n",
    s,
    count=1,
    flags=re.S
)
if n != 1:
    raise SystemExit("showSales replacement failed")

new_show_all = r'''    private fun showAllSales() {
        selectedSalesCustomer = Customer("全社", "")
        showSales()
    }

'''
s, n = re.subn(
    r'    private fun showAllSales\(\) \{.*?(?=    private fun chooseAllSalesDate\(start: Boolean\) \{)',
    new_show_all,
    s,
    count=1,
    flags=re.S
)
if n != 1:
    raise SystemExit("showAllSales replacement failed")

# The legacy date picker can stay, but return to the unified screen.
s = s.replace("                showAllSales()\n", "                showSales()\n", 1)

old_reload = r'''    private fun reloadSales() {
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
new_reload = r'''    private fun reloadSales() {
        val months = currentMonthKeys()
        val customer = selectedSalesCustomer ?: run {
            salesTableView?.setData(emptyList(), months)
            allSalesTableView?.setData(emptyList(), months)
            salesCountView?.text = "取引先を選択してください"
            return
        }
        val start = salesStartDate ?: return
        val end = salesEndDate ?: return
        val q = salesSearchQuery.trim().lowercase(Locale.JAPAN)

        if (customer.number == "全社") {
            val targetNumbers = repository.listTargetCustomers().map { it.number }.toSet()
            val sections = repository.listSalesAll(start.toString(), end.toString())
                .filter { it.customer.number in targetNumbers }
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
            allSalesTableView?.setData(sections, months)
            val productCount = sections.sumOf { it.products.size }
            salesCountView?.text = "${sections.size}社 / ${productCount}商品"
            return
        }

        val all = repository.listSalesMonthly(customer.number, start.toString(), end.toString())
        val products = if (q.isBlank()) all else all.filter {
            it.productNumber.lowercase(Locale.JAPAN).contains(q) ||
            it.productName.lowercase(Locale.JAPAN).contains(q)
        }
        salesTableView?.setData(products, months)
        salesCountView?.text = if (q.isBlank()) "${products.size}商品" else "${products.size}商品（検索中）"
    }
'''
if old_reload not in s:
    raise SystemExit("reloadSales block missing")
s = s.replace(old_reload, new_reload, 1)

p.write_text(s)

# ---------- Orders: increase contrast, borders and hierarchy ----------
p = root / "SalesDashboardActivity.kt"
s = p.read_text()

s = s.replace(
    '''                        setColor(Color.rgb(237,243,251))
                        cornerRadius=16f
                        setStroke(dp(1), Color.rgb(211,224,241))''',
    '''                        setColor(Color.rgb(226,237,250))
                        cornerRadius=16f
                        setStroke(dp(2), Color.rgb(157,184,216))''',
    1
)

s = s.replace(
    '''                            background=rounded(Color.rgb(235,247,241))''',
    '''                            background=android.graphics.drawable.GradientDrawable().apply {
                                setColor(Color.rgb(225,245,235))
                                cornerRadius=14f
                                setStroke(dp(1),Color.rgb(171,217,193))
                            }''',
    1
)
s = s.replace(
    '''                            background=rounded(Color.rgb(239,243,253))''',
    '''                            background=android.graphics.drawable.GradientDrawable().apply {
                                setColor(Color.rgb(229,237,252))
                                cornerRadius=14f
                                setStroke(dp(1),Color.rgb(179,198,235))
                            }''',
    1
)

s = s.replace(
    '''                companies.forEach { (company,rows) ->
                    val key="$date|$company";val open=key in expandedOrderGroups''',
    '''                companies.entries.forEachIndexed { companyIndex, entry ->
                    val company=entry.key
                    val rows=entry.value
                    val key="$date|$company";val open=key in expandedOrderGroups''',
    1
)

s = s.replace(
    '''                            setColor(if(open) Color.rgb(247,252,249) else Color.WHITE)
                            cornerRadius=18f
                            setStroke(dp(if(open) 2 else 1), if(open) Color.rgb(15,91,70) else Color.rgb(225,229,236))''',
    '''                            setColor(if(open) Color.rgb(241,250,245) else if(companyIndex%2==0) Color.WHITE else Color.rgb(248,250,253))
                            cornerRadius=18f
                            setStroke(dp(2), if(open) Color.rgb(15,91,70) else Color.rgb(188,201,216))''',
    1
)

old_mini = '''                    fun mini(value:String,color:Int,fill:Int)=label(value,10.8f,color,true).apply {
                        gravity=Gravity.CENTER
                        setPadding(dp(6),dp(6),dp(6),dp(6))
                        background=rounded(fill)
                    }
'''
new_mini = '''                    fun mini(value:String,color:Int,fill:Int,stroke:Int)=label(value,10.8f,color,true).apply {
                        gravity=Gravity.CENTER
                        setPadding(dp(6),dp(6),dp(6),dp(6))
                        background=android.graphics.drawable.GradientDrawable().apply {
                            setColor(fill)
                            cornerRadius=14f
                            setStroke(dp(1),stroke)
                        }
                    }
'''
if old_mini not in s:
    raise SystemExit("order mini helper missing")
s = s.replace(old_mini,new_mini,1)
s = s.replace(
    '''mini("${rows.size}件",Color.rgb(71,85,105),Color.rgb(243,244,246))''',
    '''mini("${rows.size}件",Color.rgb(64,75,92),Color.rgb(238,241,245),Color.rgb(203,210,220))''',
    1
)
s = s.replace(
    '''mini("${money.format(amount)}円",Color.rgb(15,105,76),Color.rgb(235,247,241))''',
    '''mini("${money.format(amount)}円",Color.rgb(10,103,72),Color.rgb(225,245,235),Color.rgb(171,217,193))''',
    1
)
s = s.replace(
    '''mini("${DecimalFormat("#,##0.##").format(sqm)}㎡",Color.rgb(52,81,160),Color.rgb(239,243,253))''',
    '''mini("${DecimalFormat("#,##0.##").format(sqm)}㎡",Color.rgb(45,76,157),Color.rgb(229,237,252),Color.rgb(179,198,235))''',
    1
)

s = s.replace(
    '''                            background=rounded(Color.rgb(248,249,251))''',
    '''                            background=android.graphics.drawable.GradientDrawable().apply {
                                setColor(Color.WHITE)
                                cornerRadius=14f
                                setStroke(dp(1),Color.rgb(207,216,226))
                            }''',
    1
)

s = s.replace(
    '''                                background=rounded(Color.rgb(239,243,253))''',
    '''                                background=android.graphics.drawable.GradientDrawable().apply {
                                    setColor(Color.rgb(225,234,251))
                                    cornerRadius=12f
                                    setStroke(dp(1),Color.rgb(177,197,234))
                                }''',
    1
)

p.write_text(s)

# ---------- Version ----------
build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, c1 = re.subn(r"versionCode\s+\d+", "versionCode 34", b, count=1)
b, c2 = re.subn(r"versionName\s+'[^']+'", "versionName '1.7.0'", b, count=1)
if c1 != 1 or c2 != 1:
    raise SystemExit("version declarations not found")
build.write_text(b)
