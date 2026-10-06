from pathlib import Path
import re

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

# 1) 売上分析で販売目標対象の得意先だけを選べるようにする
p = root / "DataRepository.kt"
s = p.read_text()
anchor = '''    fun listSalesAll(startDate: String, endDate: String): List<SalesCompanyMonthly> {
'''
method = '''    fun listTargetCustomers(): List<Customer> {
        writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS sd_target(customer_no TEXT, customer_name TEXT, category TEXT, metric TEXT, month TEXT, value REAL, PRIMARY KEY(customer_no,category,metric,month))")
        val out = mutableListOf<Customer>()
        readableDatabase.rawQuery(
            """
            SELECT customer_no, COALESCE(NULLIF(MAX(customer_name), ''), customer_no)
            FROM sd_target
            WHERE customer_no <> '全体'
            GROUP BY customer_no
            HAVING SUM(CASE WHEN ABS(value) > 0.000001 THEN 1 ELSE 0 END) > 0
            ORDER BY COALESCE(NULLIF(MAX(customer_name), ''), customer_no) COLLATE NOCASE
            """.trimIndent(), null
        ).use { c ->
            while (c.moveToNext()) out += Customer(c.getString(0), c.getString(1) ?: c.getString(0))
        }
        return out
    }

'''
if "fun listTargetCustomers()" not in s:
    if anchor not in s:
        raise SystemExit("DataRepository listSalesAll anchor missing")
    s = s.replace(anchor, method + anchor, 1)
p.write_text(s)

# 2) 売上分析: 個別も全社も販売目標対象のみ。全社も一社ずつ選択するUIにする
p = root / "MainActivity.kt"
s = p.read_text()

s = s.replace(
    'showCustomerPicker("取引先を選択", repository.listSalesCustomers(), this) { customer ->',
    'showCustomerPicker("取引先を選択", repository.listTargetCustomers(), this) { customer ->',
    1
)

old = '''        currentScreen = Screen.SALES
        ensureSalesDateRange()

        val root = rootLayout()
'''
new = '''        currentScreen = Screen.SALES
        ensureSalesDateRange()
        val targetNumbers = repository.listTargetCustomers().map { it.number }.toSet()
        if (selectedSalesCustomer?.number !in targetNumbers) selectedSalesCustomer = null

        val root = rootLayout()
'''
if old not in s:
    raise SystemExit("showSales start anchor missing")
s = s.replace(old, new, 1)

pattern = r'    private fun showAllSales\(\) \{.*?(?=    private fun chooseAllSalesDate\(start: Boolean\) \{)'
match = re.search(pattern, s, re.S)
if not match:
    raise SystemExit("showAllSales block missing")

new_show_all = '''    private fun showAllSales() {
        currentScreen = Screen.SALES
        ensureSalesDateRange()
        val targetCustomers = repository.listTargetCustomers()
        val targetNumbers = targetCustomers.map { it.number }.toSet()
        if (selectedSalesCustomer?.number !in targetNumbers) selectedSalesCustomer = null

        val root = rootLayout()
        root.addView(appBar("売上分析") { showHome() }, fullWidth(dp(64)))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(Color.rgb(246, 248, 251))
        }

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modeRow.addView(actionButton("個別", false).apply {
            textSize = 12.5f
            setOnClickListener { showSales() }
        }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(5) })
        modeRow.addView(text("全社", 12.5f, Color.rgb(15,91,70), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(236,247,242), dp(10).toFloat(), Color.rgb(199,224,212))
        }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(5) })
        controls.addView(modeRow, fullWidth(dp(38)))

        val customerButton = actionButton(
            selectedSalesCustomer?.let { if (it.name.isBlank()) it.number else it.name } ?: "取引先を選択",
            false
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14),0,dp(14),0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener {
                showCustomerPicker("販売目標の取引先を選択", targetCustomers, this) { customer ->
                    selectedSalesCustomer = customer
                    showAllSales()
                }
            }
        }
        controls.addView(customerButton, fullWidth(dp(44)).apply { topMargin = dp(8) })

        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        salesStartButton = actionButton("", false).apply {
            textSize = 11.5f
            setOnClickListener { chooseAllSalesDate(true) }
        }
        salesEndButton = actionButton("", false).apply {
            textSize = 11.5f
            setOnClickListener { chooseAllSalesDate(false) }
        }
        dateRow.addView(salesStartButton, LinearLayout.LayoutParams(0,dp(40),1f))
        dateRow.addView(text("〜",15f,Color.rgb(100,116,139),Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(28),dp(40)))
        dateRow.addView(salesEndButton, LinearLayout.LayoutParams(0,dp(40),1f))
        controls.addView(dateRow, fullWidth(dp(40)).apply { topMargin=dp(7) })
        updateSalesDateButtons()

        controls.addView(text(
            "販売目標に登録されている取引先のみ",
            10.5f,
            Color.rgb(100,116,139),
            Typeface.NORMAL
        ).apply { setPadding(dp(3),dp(6),0,0) })
        root.addView(controls, fullWidthWrap())

        salesCountView = text(
            "取引先を選択してください",
            11.5f,
            Color.rgb(100,116,139),
            Typeface.NORMAL
        ).apply { setPadding(dp(14),dp(7),0,0) }
        root.addView(salesCountView, fullWidthWrap())

        salesTableView = SalesTableView(this).apply {
            setOnMonthClickListener { product, month -> showSalesDayDetail(product, month) }
            setOnProductTotalClickListener { product -> showSalesPeriodDayDetail(product) }
            setOnMonthTotalClickListener { month -> showSalesMonthDayDetail(month) }
        }
        root.addView(
            salesTableView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f)
        )
        setRootContent(root)

        if (selectedSalesCustomer != null) {
            reloadSales()
        } else {
            salesTableView?.setData(emptyList(), currentMonthKeys())
        }
    }

'''
s = s[:match.start()] + new_show_all + s[match.end():]
p.write_text(s)

# 3) 販売目標: 全社の展開内訳も「販売目標対象企業だけ」の実績にする
p = root / "SalesDashboardActivity.kt"
s = p.read_text()

old = '''                    val targetValues=halfMonths.map { oneValue("sd_target",no,it,selectedMetric,selectedCategory) }
                    val actualValues=halfMonths.map { oneValue("sd_actual",no,it,selectedMetric,selectedCategory) }
'''
new = '''                    val targetValues=halfMonths.map { month ->
                        if (no == "全体") targetCompanies.sumOf { (customerNo, _) ->
                            oneValue("sd_target",customerNo,month,selectedMetric,selectedCategory)
                        } else oneValue("sd_target",no,month,selectedMetric,selectedCategory)
                    }
                    val actualValues=halfMonths.map { month ->
                        if (no == "全体") targetCompanies.sumOf { (customerNo, _) ->
                            oneValue("sd_actual",customerNo,month,selectedMetric,selectedCategory)
                        } else oneValue("sd_actual",no,month,selectedMetric,selectedCategory)
                    }
'''
if old not in s:
    raise SystemExit("all-company monthly values anchor missing")
s = s.replace(old, new, 1)

# 4) 受注: 日付・得意先・明細の階層と数値を視覚的に分離
old = '''                box.addView(card().apply {
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background=android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.rgb(237,243,251))
                        cornerRadius=16f
                        setStroke(dp(1), Color.rgb(211,224,241))
                    }
                    addView(label("$date　${companies.size}社・${dayCount}件",15f,Color.rgb(42,67,108),true))
                },margin())
'''
new = '''                val dayAmount=companies.values.flatten().sumOf { it.second.amount }
                val daySqm=companies.values.flatten().sumOf { it.second.sqm }
                box.addView(card().apply {
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background=android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.rgb(237,243,251))
                        cornerRadius=16f
                        setStroke(dp(1), Color.rgb(211,224,241))
                    }
                    val head=LinearLayout(this@SalesDashboardActivity).apply {
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                    }
                    head.addView(
                        label(date,15f,Color.rgb(42,67,108),true),
                        LinearLayout.LayoutParams(0,-2,1f)
                    )
                    head.addView(label("${companies.size}社・${dayCount}件",11f,Color.rgb(78,94,120),true))
                    addView(head)

                    val metrics=LinearLayout(this@SalesDashboardActivity).apply {
                        orientation=LinearLayout.HORIZONTAL
                        setPadding(0,dp(7),0,0)
                    }
                    metrics.addView(
                        label("${money.format(dayAmount)}円",11f,Color.rgb(15,105,76),true).apply {
                            gravity=Gravity.CENTER
                            setPadding(dp(8),dp(6),dp(8),dp(6))
                            background=rounded(Color.rgb(235,247,241))
                        },
                        LinearLayout.LayoutParams(0,-2,1f)
                    )
                    metrics.addView(
                        label("${DecimalFormat("#,##0.##").format(daySqm)}㎡",11f,Color.rgb(52,81,160),true).apply {
                            gravity=Gravity.CENTER
                            setPadding(dp(8),dp(6),dp(8),dp(6))
                            background=rounded(Color.rgb(239,243,253))
                        },
                        LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) }
                    )
                    addView(metrics)
                },margin())
'''
if old not in s:
    raise SystemExit("order day card anchor missing")
s = s.replace(old, new, 1)

old = '''                    val heading=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
                    heading.addView(label(company,15f,Color.rgb(15,23,42),true),LinearLayout.LayoutParams(0,-2,1f))
                    heading.addView(label("${rows.size}件　${money.format(amount)}円　${DecimalFormat("#,##0.##").format(sqm)}㎡　${if(open)"⌃" else "⌄"}",11f,if(open) Color.rgb(15,91,70) else Color.rgb(76,89,109),true))
                    group.addView(heading)
'''
new = '''                    val heading=LinearLayout(this).apply {
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                    }
                    heading.addView(
                        label(company,15f,Color.rgb(15,23,42),true).apply { maxLines=2 },
                        LinearLayout.LayoutParams(0,-2,1f)
                    )
                    heading.addView(label(if(open)"⌃" else "⌄",18f,Color.rgb(100,116,139),true))
                    group.addView(heading)

                    val summary=LinearLayout(this).apply {
                        orientation=LinearLayout.HORIZONTAL
                        setPadding(0,dp(8),0,0)
                    }
                    fun mini(value:String,color:Int,fill:Int)=label(value,10.8f,color,true).apply {
                        gravity=Gravity.CENTER
                        setPadding(dp(6),dp(6),dp(6),dp(6))
                        background=rounded(fill)
                    }
                    summary.addView(
                        mini("${rows.size}件",Color.rgb(71,85,105),Color.rgb(243,244,246)),
                        LinearLayout.LayoutParams(0,-2,1f)
                    )
                    summary.addView(
                        mini("${money.format(amount)}円",Color.rgb(15,105,76),Color.rgb(235,247,241)),
                        LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) }
                    )
                    summary.addView(
                        mini("${DecimalFormat("#,##0.##").format(sqm)}㎡",Color.rgb(52,81,160),Color.rgb(239,243,253)),
                        LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) }
                    )
                    group.addView(summary)
'''
if old not in s:
    raise SystemExit("order company heading anchor missing")
s = s.replace(old, new, 1)

old = '''                        val detail=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(9),dp(10),dp(9));background=rounded(Color.rgb(248,249,251)) }
                        detail.addView(label("$category　${line.item}",13f,Color.rgb(42,55,73),true))
                        val metrics = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(4),0,0) }
'''
new = '''                        val detail=LinearLayout(this).apply {
                            orientation=LinearLayout.VERTICAL
                            setPadding(dp(10),dp(9),dp(10),dp(9))
                            background=rounded(Color.rgb(248,249,251))
                        }
                        val title=LinearLayout(this).apply {
                            orientation=LinearLayout.HORIZONTAL
                            gravity=Gravity.CENTER_VERTICAL
                        }
                        title.addView(
                            label(category.ifBlank { "その他" },9.8f,Color.rgb(52,81,160),true).apply {
                                gravity=Gravity.CENTER
                                setPadding(dp(7),dp(4),dp(7),dp(4))
                                background=rounded(Color.rgb(239,243,253))
                            }
                        )
                        title.addView(
                            label(line.item,12.8f,Color.rgb(42,55,73),true).apply {
                                setPadding(dp(8),0,0,0)
                                maxLines=2
                            },
                            LinearLayout.LayoutParams(0,-2,1f)
                        )
                        detail.addView(title)
                        val metrics = LinearLayout(this).apply {
                            orientation=LinearLayout.HORIZONTAL
                            gravity=Gravity.CENTER_VERTICAL
                            setPadding(0,dp(7),0,0)
                        }
'''
if old not in s:
    raise SystemExit("order detail anchor missing")
s = s.replace(old, new, 1)
p.write_text(s)

# 5) version
build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, c1 = re.subn(r"versionCode\s+\d+", "versionCode 33", b, count=1)
b, c2 = re.subn(r"versionName\s+'[^']+'", "versionName '1.6.0'", b, count=1)
if c1 != 1 or c2 != 1:
    raise SystemExit("version declarations not found")
build.write_text(b)
