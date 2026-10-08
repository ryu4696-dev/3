from pathlib import Path

p = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger/SalesDashboardActivity.kt")
s = p.read_text()

old = '''    private data class OrderLine(val item:String,val qty:String,val amount:Double,val sqm:Double,val sales:String,val receiver:String)
    private val db by lazy { DataRepository(this).writableDatabase }
'''
new = '''    private data class OrderLine(
        val item:String,val qty:String,val amount:Double,val sqm:Double,val sales:String,val receiver:String,
        val customerNo:String="",val productNo:String="",val cost:Double?=null
    ) {
        val grossProfit: Double? get() = cost?.let { amount - it }
        val grossRate: Double? get() = grossProfit?.let { if (kotlin.math.abs(amount) > 0.000001) it / amount else null }
    }
    private val repository by lazy { DataRepository(this) }
    private val db by lazy { repository.writableDatabase }
    private val orderCostCache = mutableMapOf<String,OrderCostEstimate>()
'''
if old not in s: raise SystemExit("order model anchor missing")
s=s.replace(old,new,1)

old = '''        db.execSQL("CREATE TABLE IF NOT EXISTS sd_order(id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT, delivery TEXT, customer TEXT, item TEXT, qty TEXT, amount REAL, sqm REAL, sales TEXT, receiver TEXT)")
'''
new = '''        db.execSQL("CREATE TABLE IF NOT EXISTS sd_order(id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT, delivery TEXT, customer TEXT, item TEXT, qty TEXT, amount REAL, sqm REAL, sales TEXT, receiver TEXT, customer_no TEXT NOT NULL DEFAULT '', product_no TEXT NOT NULL DEFAULT '')")
        runCatching { db.execSQL("ALTER TABLE sd_order ADD COLUMN customer_no TEXT NOT NULL DEFAULT ''") }
        runCatching { db.execSQL("ALTER TABLE sd_order ADD COLUMN product_no TEXT NOT NULL DEFAULT ''") }
'''
if old not in s: raise SystemExit("order schema anchor missing")
s=s.replace(old,new,1)

old = '''        val query="SELECT category,delivery,customer,item,qty,amount,sqm,sales,receiver FROM sd_order WHERE (customer LIKE ? OR item LIKE ?) AND ABS(amount) > 0.000001 ORDER BY delivery DESC,customer COLLATE NOCASE,item COLLATE NOCASE LIMIT 250"
'''
new = '''        val query="SELECT category,delivery,customer,item,qty,amount,sqm,sales,receiver,customer_no,product_no FROM sd_order WHERE (customer LIKE ? OR item LIKE ?) AND ABS(amount) > 0.000001 ORDER BY delivery DESC,customer COLLATE NOCASE,item COLLATE NOCASE LIMIT 250"
'''
if old not in s: raise SystemExit("order query anchor missing")
s=s.replace(old,new,1)

old = '''                val line=OrderLine(c.getString(3).orEmpty(),c.getString(4).orEmpty(),c.getDouble(5),c.getDouble(6),c.getString(7).orEmpty(),c.getString(8).orEmpty())
                days.getOrPut(date){linkedMapOf()}.getOrPut(company){mutableListOf()} += c.getString(0).orEmpty() to line
'''
new = '''                val qtyText=c.getString(4).orEmpty()
                val qtyValue=qtyText.replace(",","").toDoubleOrNull() ?: 0.0
                val customerNo=c.getString(9).orEmpty()
                val productNo=c.getString(10).orEmpty()
                val costKey=listOf(customerNo,company,productNo,c.getString(3).orEmpty(),qtyValue.toString()).joinToString("\u001F")
                val estimate=orderCostCache.getOrPut(costKey) {
                    repository.estimateOrderCost(customerNo,company,productNo,c.getString(3).orEmpty(),qtyValue)
                }
                val line=OrderLine(
                    c.getString(3).orEmpty(),qtyText,c.getDouble(5),c.getDouble(6),c.getString(7).orEmpty(),c.getString(8).orEmpty(),
                    estimate.customerNumber,estimate.productNumber,estimate.cost
                )
                days.getOrPut(date){linkedMapOf()}.getOrPut(company){mutableListOf()} += c.getString(0).orEmpty() to line
'''
if old not in s: raise SystemExit("order line anchor missing")
s=s.replace(old,new,1)

old = '''                dayHeader.addView(label("${money.format(dayAmount)}円　・　${DecimalFormat("#,##0.##").format(daySqm)}㎡",11.5f,Color.rgb(63,78,101),true).apply {
                    setPadding(0,dp(5),0,0)
                })
'''
new = '''                dayHeader.addView(label("${money.format(dayAmount)}円　・　${DecimalFormat("#,##0.##").format(daySqm)}㎡",11.5f,Color.rgb(63,78,101),true).apply {
                    setPadding(0,dp(5),0,0)
                })
                val dayLines=companies.values.flatten().map { it.second }
                val dayCostKnown=dayLines.all { it.cost != null }
                val dayProfit=if(dayCostKnown) dayLines.sumOf { it.grossProfit ?: 0.0 } else null
                val dayRate=dayProfit?.let { if(kotlin.math.abs(dayAmount)>0.000001) it/dayAmount else null }
                dayHeader.addView(label(
                    if(dayProfit!=null) "粗利 ${money.format(dayProfit)}円　・　粗利率 ${DecimalFormat("0.0%").format(dayRate ?: 0.0)}" else "粗利 —",
                    11.5f, if(dayProfit!=null && dayProfit<0) Color.rgb(190,58,58) else Color.rgb(15,105,76), true
                ).apply { setPadding(0,dp(4),0,0) })
'''
if old not in s: raise SystemExit("day summary anchor missing")
s=s.replace(old,new,1)

old = '''                    groupBody.addView(label("${rows.size}件　　${money.format(amount)}円　　${DecimalFormat("#,##0.##").format(sqm)}㎡",11.5f,Color.rgb(71,85,105),true).apply {
                        setPadding(0,dp(6),0,0)
                    })
'''
new = '''                    groupBody.addView(label("${rows.size}件　　${money.format(amount)}円　　${DecimalFormat("#,##0.##").format(sqm)}㎡",11.5f,Color.rgb(71,85,105),true).apply {
                        setPadding(0,dp(6),0,0)
                    })
                    val companyLines=rows.map { it.second }
                    val companyCostKnown=companyLines.all { it.cost != null }
                    val companyProfit=if(companyCostKnown) companyLines.sumOf { it.grossProfit ?: 0.0 } else null
                    val companyRate=companyProfit?.let { if(kotlin.math.abs(amount)>0.000001) it/amount else null }
                    groupBody.addView(label(
                        if(companyProfit!=null) "粗利 ${money.format(companyProfit)}円　　${DecimalFormat("0.0%").format(companyRate ?: 0.0)}" else "粗利 —",
                        11.2f, if(companyProfit!=null && companyProfit<0) Color.rgb(190,58,58) else Color.rgb(15,105,76), true
                    ).apply { setPadding(0,dp(4),0,0) })
'''
if old not in s: raise SystemExit("company summary anchor missing")
s=s.replace(old,new,1)

old = '''                            detail.addView(label("数量 ${line.qty}　｜　${money.format(line.amount)}円　｜　${DecimalFormat("#,##0.##").format(line.sqm)}㎡",10.8f,Color.rgb(71,85,105),false).apply {
                                setPadding(0,dp(6),0,0)
                            })
'''
new = '''                            detail.addView(label("数量 ${line.qty}　｜　${money.format(line.amount)}円　｜　${DecimalFormat("#,##0.##").format(line.sqm)}㎡",10.8f,Color.rgb(71,85,105),false).apply {
                                setPadding(0,dp(6),0,0)
                            })
                            detail.addView(label(
                                if(line.grossProfit!=null) "粗利 ${money.format(line.grossProfit)}円　｜　粗利率 ${DecimalFormat("0.0%").format(line.grossRate ?: 0.0)}" else "粗利 —",
                                10.8f, if((line.grossProfit ?: 0.0)<0) Color.rgb(190,58,58) else Color.rgb(15,105,76), true
                            ).apply { setPadding(0,dp(4),0,0) })
'''
if old not in s: raise SystemExit("detail profit anchor missing")
s=s.replace(old,new,1)

old = '''            val category=if(sheet.contains("商品"))"商品" else "段ボール"
            for(i in header+1 until rows.size){
'''
new = '''            val category=if(sheet.contains("商品"))"商品" else "段ボール"
            val customerNoCol=col("得意先番号","請求先番号")
            val productNoCol=col("商品番号")
            for(i in header+1 until rows.size){
'''
if old not in s: raise SystemExit("order import columns anchor missing")
s=s.replace(old,new,1)

old = '''                all+=arrayOf(category,displayDate(v("納期")),v("請求先名","商品得意先名"),item,v("受注数"),amountText,v("平米").replace(",",""),v("営業"),v("受担"))
'''
new = '''                all+=arrayOf(
                    category,displayDate(v("納期")),v("請求先名","商品得意先名"),item,v("受注数"),amountText,
                    v("平米").replace(",",""),v("営業"),v("受担"),
                    r.getOrNull(customerNoCol).orEmpty().trim(),r.getOrNull(productNoCol).orEmpty().trim()
                )
'''
if old not in s: raise SystemExit("order import row anchor missing")
s=s.replace(old,new,1)

old = '''put("sales",r[7]);put("receiver",r[8])'''
new = '''put("sales",r[7]);put("receiver",r[8]);put("customer_no",r.getOrNull(9).orEmpty());put("product_no",r.getOrNull(10).orEmpty())'''
if old not in s: raise SystemExit("order insert anchor missing")
s=s.replace(old,new,1)

s=s.replace(
'''        val db=db;db.beginTransaction();try{db.delete("sd_order",null,null);all.forEach{r->db.insert("sd_order",null,ContentValues().apply{''',
'''        val db=db;orderCostCache.clear();db.beginTransaction();try{db.delete("sd_order",null,null);all.forEach{r->db.insert("sd_order",null,ContentValues().apply{''',
1
)

p.write_text(s)
