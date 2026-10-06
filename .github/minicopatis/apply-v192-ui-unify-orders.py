from pathlib import Path
import re

root=Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

# MainActivity: unify app bar appearance only.
p=root/"MainActivity.kt"
s=p.read_text()
old='''    private fun appBar(title: String, onBack: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(14), dp(8))
            setBackgroundColor(Color.rgb(248, 250, 252))
            addView(text("‹", 36f, Color.rgb(30, 64, 175), Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener { onBack() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(text(title, 21f, Color.rgb(15, 23, 42), Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
'''
new='''    private fun appBar(title: String, onBack: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(14), dp(8))
            setBackgroundColor(Color.WHITE)
            addView(text("‹ 戻る", 15.5f, Color.rgb(15, 91, 70), Typeface.BOLD).apply {
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                setPadding(0, 0, dp(8), 0)
                setOnClickListener { onBack() }
            }, LinearLayout.LayoutParams(dp(72), dp(48)))
            addView(text(title, 21f, Color.rgb(15, 23, 42), Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
'''
if old not in s:
    raise SystemExit("Main appBar anchor missing")
s=s.replace(old,new,1)
p.write_text(s)

# Sales dashboard: unify top chrome, search styling, cards, orders layout, and filter provisional orders on import.
p=root/"SalesDashboardActivity.kt"
s=p.read_text()
old='''        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246, 248, 251)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(16, 12, 16, 12); setBackgroundColor(Color.WHITE) }
        top.addView(label("‹ 戻る", 16f, Color.rgb(15, 91, 70), true).apply { setOnClickListener { finish() } })
        top.addView(label(if (ordersOnly) "受注情報" else "販売目標", 20f, Color.rgb(15, 23, 42), true), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 18 })
        root.addView(top)
'''
new='''        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246, 248, 251)) }
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(14), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        top.addView(label("‹ 戻る", 15.5f, Color.rgb(15, 91, 70), true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(8), 0)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(72), dp(48)))
        top.addView(label(if (ordersOnly) "受注情報" else "販売目標", 21f, Color.rgb(15, 23, 42), true), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top, LinearLayout.LayoutParams(-1, dp(64)))
'''
if old not in s:
    raise SystemExit("dashboard top anchor missing")
s=s.replace(old,new,1)
s=s.replace(
    'val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 24) }',
    'val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(20)) }',
    1
)

s=s.replace(
'''            setText(customerSearchQuery); background = rounded(Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(8))''',
'''            setText(customerSearchQuery)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(11).toFloat()
                setStroke(dp(1), Color.rgb(218, 224, 233))
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))''',
1
)

pattern=r'''    private fun renderOrders\(body: LinearLayout\) \{.*?\n    \}\n\n    private fun showOrderRows\(\) \{.*?\n    \}\n\n    private fun choose\(code:Int,mime:String\) \{'''
new_orders=r'''    private fun renderOrders(body: LinearLayout) {
        val bar=card().apply { setPadding(dp(12),dp(10),dp(12),dp(10)) }
        val search=EditText(this).apply {
            hint="得意先・品名で検索"
            isSingleLine=true
            textSize=13.5f
            setText(filter)
            setPadding(dp(13),0,dp(13),0)
            background=android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius=dp(11).toFloat()
                setStroke(dp(1),Color.rgb(218,224,233))
            }
            addTextChangedListener(object: android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                    filter=s?.toString().orEmpty()
                    showOrderRows()
                }
                override fun afterTextChanged(s:android.text.Editable?){}
            })
        }
        bar.addView(search,LinearLayout.LayoutParams(-1,dp(44)))
        body.addView(bar,margin())
        orderContainer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        body.addView(orderContainer)
        showOrderRows()
    }

    private fun showOrderRows() {
        val box=orderContainer ?: return
        box.removeAllViews()
        val query="SELECT category,delivery,customer,item,qty,amount,sqm,sales,receiver FROM sd_order WHERE customer LIKE ? OR item LIKE ? ORDER BY delivery DESC,customer COLLATE NOCASE,item COLLATE NOCASE LIMIT 250"
        db.rawQuery(query,arrayOf("%$filter%","%$filter%")).use { c ->
            if(c.count==0){
                box.addView(card().apply {
                    addView(label("受注データがありません。Excelを読み込んでください。",14f,Color.rgb(100,116,139),false))
                })
                return
            }
            val days=linkedMapOf<String,LinkedHashMap<String,MutableList<Pair<String,OrderLine>>>>()
            while(c.moveToNext()) {
                val date=c.getString(1).orEmpty().ifBlank { "納期未登録" }
                val company=c.getString(2).orEmpty().ifBlank { "得意先名未登録" }
                val line=OrderLine(c.getString(3).orEmpty(),c.getString(4).orEmpty(),c.getDouble(5),c.getDouble(6),c.getString(7).orEmpty(),c.getString(8).orEmpty())
                days.getOrPut(date){linkedMapOf()}.getOrPut(company){mutableListOf()} += c.getString(0).orEmpty() to line
            }

            days.forEach { (date,companies) ->
                val dayCount=companies.values.sumOf { it.size }
                val dayAmount=companies.values.flatten().sumOf { it.second.amount }
                val daySqm=companies.values.flatten().sumOf { it.second.sqm }

                val dayBlock=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL
                    setPadding(dp(8),dp(8),dp(8),dp(9))
                    background=android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.rgb(242,246,250))
                        cornerRadius=dp(16).toFloat()
                        setStroke(dp(1),Color.rgb(205,216,228))
                    }
                }

                val dayHeader=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL
                    setPadding(dp(11),dp(9),dp(11),dp(9))
                    background=android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.rgb(227,236,247))
                        cornerRadius=dp(12).toFloat()
                    }
                }
                val topLine=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
                topLine.addView(label(date,15.5f,Color.rgb(42,67,108),true),LinearLayout.LayoutParams(0,-2,1f))
                topLine.addView(label("${companies.size}社・${dayCount}件",11f,Color.rgb(78,94,120),true))
                dayHeader.addView(topLine)
                dayHeader.addView(label("${money.format(dayAmount)}円　・　${DecimalFormat("#,##0.##").format(daySqm)}㎡",11.5f,Color.rgb(63,78,101),true).apply {
                    setPadding(0,dp(5),0,0)
                })
                dayBlock.addView(dayHeader)

                companies.entries.forEachIndexed { index, entry ->
                    val company=entry.key
                    val rows=entry.value
                    val key="$date|$company"
                    val open=key in expandedOrderGroups
                    val amount=rows.sumOf { it.second.amount }
                    val sqm=rows.sumOf { it.second.sqm }

                    val group=LinearLayout(this).apply {
                        orientation=LinearLayout.HORIZONTAL
                        setPadding(0,0,0,0)
                        background=android.graphics.drawable.GradientDrawable().apply {
                            setColor(if(open) Color.rgb(246,252,249) else Color.WHITE)
                            cornerRadius=dp(13).toFloat()
                            setStroke(dp(if(open) 2 else 1),if(open) Color.rgb(15,91,70) else Color.rgb(211,220,230))
                        }
                        setOnClickListener {
                            if(open) expandedOrderGroups.remove(key) else expandedOrderGroups.add(key)
                            showOrderRows()
                        }
                    }
                    val accent=TextView(this).apply {
                        setBackgroundColor(if(open) Color.rgb(15,91,70) else Color.rgb(143,166,193))
                    }
                    group.addView(accent,LinearLayout.LayoutParams(dp(4),-1))

                    val groupBody=LinearLayout(this).apply {
                        orientation=LinearLayout.VERTICAL
                        setPadding(dp(11),dp(10),dp(10),dp(10))
                    }
                    val heading=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
                    heading.addView(label(company,14.5f,Color.rgb(15,23,42),true).apply { maxLines=2 },LinearLayout.LayoutParams(0,-2,1f))
                    heading.addView(label(if(open)"⌃" else "⌄",17f,Color.rgb(100,116,139),true))
                    groupBody.addView(heading)
                    groupBody.addView(label("${rows.size}件　　${money.format(amount)}円　　${DecimalFormat("#,##0.##").format(sqm)}㎡",11.5f,Color.rgb(71,85,105),true).apply {
                        setPadding(0,dp(6),0,0)
                    })

                    if(open) {
                        rows.forEachIndexed { rowIndex, pair ->
                            val category=pair.first
                            val line=pair.second
                            val detail=LinearLayout(this).apply {
                                orientation=LinearLayout.VERTICAL
                                setPadding(dp(10),dp(9),dp(10),dp(9))
                                background=android.graphics.drawable.GradientDrawable().apply {
                                    setColor(if(rowIndex%2==0) Color.rgb(249,250,252) else Color.WHITE)
                                    cornerRadius=dp(10).toFloat()
                                    setStroke(dp(1),Color.rgb(224,229,235))
                                }
                            }
                            val titleLine=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
                            titleLine.addView(label(category.ifBlank { "その他" },9.5f,Color.rgb(52,81,160),true).apply {
                                setPadding(dp(6),dp(3),dp(6),dp(3))
                                background=rounded(Color.rgb(232,239,252))
                            })
                            titleLine.addView(label(line.item,12.5f,Color.rgb(42,55,73),true).apply {
                                setPadding(dp(7),0,0,0);maxLines=2
                            },LinearLayout.LayoutParams(0,-2,1f))
                            detail.addView(titleLine)
                            detail.addView(label("数量 ${line.qty}　｜　${money.format(line.amount)}円　｜　${DecimalFormat("#,##0.##").format(line.sqm)}㎡",10.8f,Color.rgb(71,85,105),false).apply {
                                setPadding(0,dp(6),0,0)
                            })
                            val reps=listOf(line.sales,line.receiver).filter { it.isNotBlank() }.joinToString(" / ")
                            if(reps.isNotBlank()) detail.addView(label("担当　$reps",10.2f,Color.rgb(120,128,140),false).apply { setPadding(0,dp(4),0,0) })
                            groupBody.addView(detail,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })
                        }
                    }
                    group.addView(groupBody,LinearLayout.LayoutParams(0,-2,1f))
                    dayBlock.addView(group,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(if(index==0) 8 else 6) })
                }
                box.addView(dayBlock,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
            }
        }
    }

    private fun choose(code:Int,mime:String) {'''
s,n=re.subn(pattern,lambda m:new_orders,s,count=1,flags=re.S)
if n!=1:
    raise SystemExit("orders block replacement failed")

old='''            for(i in header+1 until rows.size){val r=rows[i];val item=r.getOrNull(col("品名")).orEmpty().trim();if(item.isBlank())continue
                fun v(vararg keys:String)=keys.firstNotNullOfOrNull{key->r.getOrNull(col(key))?.takeIf{it.isNotBlank()}}.orEmpty()
                all+=arrayOf(category,displayDate(v("納期")),v("請求先名","商品得意先名"),item,v("受注数"),v("受注金額").replace(",",""),v("平米").replace(",",""),v("営業"),v("受担"))
            }
'''
new='''            for(i in header+1 until rows.size){
                val r=rows[i]
                if(r.any { it.contains("仮受注") }) continue
                val item=r.getOrNull(col("品名")).orEmpty().trim();if(item.isBlank())continue
                fun v(vararg keys:String)=keys.firstNotNullOfOrNull{key->r.getOrNull(col(key))?.takeIf{it.isNotBlank()}}.orEmpty()
                all+=arrayOf(category,displayDate(v("納期")),v("請求先名","商品得意先名"),item,v("受注数"),v("受注金額").replace(",",""),v("平米").replace(",",""),v("営業"),v("受担"))
            }
'''
if old not in s:
    raise SystemExit("import orders anchor missing")
s=s.replace(old,new,1)

old='''    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,15,16,15);background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=18f}}
    private fun margin()=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=12}
'''
new='''    private fun card()=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(dp(14),dp(12),dp(14),dp(12))
        background=android.graphics.drawable.GradientDrawable().apply{
            setColor(Color.WHITE)
            cornerRadius=dp(14).toFloat()
            setStroke(dp(1),Color.rgb(218,224,233))
        }
    }
    private fun margin()=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)}
'''
if old not in s:
    raise SystemExit("card helper anchor missing")
s=s.replace(old,new,1)

p.write_text(s)

build=Path("ledgerapp/app/build.gradle")
b=build.read_text()
b,c1=re.subn(r"versionCode\s+\d+","versionCode 38",b,count=1)
b,c2=re.subn(r"versionName\s+'[^']+'","versionName '1.9.2'",b,count=1)
if c1!=1 or c2!=1:
    raise SystemExit("version declarations not found")
build.write_text(b)
