package jp.co.ichika.salesledger

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Xml
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.text.DecimalFormat
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

class SalesDashboardActivity : Activity() {
    private data class OrderLine(val item:String,val qty:String,val amount:Double,val sqm:Double,val sales:String,val receiver:String)
    private val db by lazy { DataRepository(this).writableDatabase }
    private val money = DecimalFormat("#,##0")
    private var filter = ""
    private var orderContainer: LinearLayout? = null
    private var selectedCategory = "合計"
    private var selectedMetric = "金額"
    private var sortMetric = "金額"
    private var sortPeriod = "年間"
    private var sortGoodFirst = true
    private var sortMonth = "2026-04"
    private var expandedCustomer = "全体"
    private var lastScrollY = 0
    private val expandedOrderGroups = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db.execSQL("CREATE TABLE IF NOT EXISTS sd_target(customer_no TEXT, customer_name TEXT, category TEXT, metric TEXT, month TEXT, value REAL, PRIMARY KEY(customer_no,category,metric,month))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sd_actual(customer_no TEXT, customer_name TEXT, category TEXT, month TEXT, amount REAL, sqm REAL, PRIMARY KEY(customer_no,category,month))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sd_order(id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT, delivery TEXT, customer TEXT, item TEXT, qty TEXT, amount REAL, sqm REAL, sales TEXT, receiver TEXT)")
        if (intent.getBooleanExtra("import_only", false)) {
            renderImportStart()
            window.decorView.post {
                val code = when (intent.getStringExtra("import_type")) { "target" -> 11; "sales" -> 12; "orders" -> 13; else -> 11 }
                val mime = when (code) { 11, 13 -> "application/vnd.ms-excel.sheet.macroEnabled.12"; else -> "text/csv" }
                choose(code, mime)
            }
        } else render()
    }

    private fun renderImportStart() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246,248,251)); setPadding(dp(18),dp(18),dp(18),dp(18)) }
        root.addView(label("‹ ホームへ戻る",16f,Color.rgb(15,91,70),true).apply { setOnClickListener { finish() } })
        root.addView(label("データを読み込む",20f,Color.rgb(15,23,42),true).apply { setPadding(0,dp(18),0,dp(8)) })
        root.addView(label("ファイルを選択してください。Excelは .xlsx / .xlsm、CSVは .csv に対応します。",14f,Color.GRAY,false))
        setContentView(root)
    }

    private fun render() {
        val ordersOnly = intent.getBooleanExtra("orders_only", false)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246, 248, 251)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(16, 12, 16, 12); setBackgroundColor(Color.WHITE) }
        top.addView(label("‹ 戻る", 16f, Color.rgb(15, 91, 70), true).apply { setOnClickListener { finish() } })
        top.addView(label(if (ordersOnly) "受注情報" else "販売目標", 20f, Color.rgb(15, 23, 42), true), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 18 })
        root.addView(top)
        val scroll = ScrollView(this)
        scroll.setOnScrollChangeListener { _, _, y, _, _ -> if (!ordersOnly) lastScrollY = y }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 24) }
        if (ordersOnly) renderOrders(body) else renderPerformance(body)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.isFocusableInTouchMode = true
        root.requestFocus()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (ordersOnly) window.decorView.post { root.requestFocus() }
        setContentView(root)
        if (!ordersOnly) scroll.post { scroll.scrollTo(0, lastScrollY) }
    }

    private fun renderPerformance(body: LinearLayout) {
        val categories = listOf("合計", "段ボール", "商品", "版代型代", "運賃", "その他")
        val categoryRail = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(6, 6, 6, 6); background = rounded(Color.rgb(238, 240, 247)) }
        val categoryScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        categories.forEach { item ->
            val label = TextView(this).apply {
                text = item; gravity = Gravity.CENTER; textSize = 12f; setPadding(14, 10, 14, 10)
                setTextColor(if (item == selectedCategory) Color.rgb(52, 81, 200) else Color.rgb(89, 97, 116))
                background = rounded(if (item == selectedCategory) Color.WHITE else Color.TRANSPARENT)
                setOnClickListener { selectedCategory = item; render() }
            }
            categoryRail.addView(label)
        }
        categoryScroll.addView(categoryRail)
        val catCard = card().apply { addView(categoryScroll) }
        body.addView(catCard, margin())

        val unitRail = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("金額", "平米").forEach { item ->
            unitRail.addView(button(item, item == selectedMetric).apply { setOnClickListener { selectedMetric = item; render() } }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 4; rightMargin = 4 })
        }
        val unitCard = card().apply { addView(unitRail) }
        body.addView(unitCard, margin())

        addSortControls(body)

        val months = months()
        val allTarget = monthTotal("sd_target", "全体", months, selectedMetric, selectedCategory)
        val allActual = monthTotal("sd_actual", "全体", months, selectedMetric, selectedCategory)
        val summary = card().apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(50, 65, 160), Color.rgb(105, 125, 234))).apply { cornerRadius = 22f }
        }
        summary.addView(label("全社 / 2026年度 / $selectedCategory / $selectedMetric", 13f, Color.rgb(229, 234, 255), true))
        val values = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 10, 0, 0) }
        listOf("目標" to shown(allTarget), "実績" to shown(allActual), "達成率" to percent(allActual, allTarget)).forEach { (name, value) ->
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            cell.addView(label(name, 11f, Color.rgb(225, 230, 255), false))
            cell.addView(label(value, 17f, Color.WHITE, true).apply { setPadding(0, 3, 0, 0) })
            values.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        summary.addView(values)
        body.addView(summary, margin())
        body.addView(label("全社・得意先別　タップすると月別に展開", 16f, Color.rgb(15, 23, 42), true).apply { setPadding(4, 4, 4, 10) })

        val companies = mutableListOf<Pair<String, String>>("全体" to "全社")
        db.rawQuery("SELECT customer_no,MAX(customer_name) FROM (SELECT customer_no,customer_name FROM sd_target UNION ALL SELECT customer_no,customer_name FROM sd_actual) WHERE customer_no<>'全体' GROUP BY customer_no ORDER BY MAX(customer_name)", null).use {
            while (it.moveToNext()) companies += it.getString(0) to it.getString(1).ifBlank { it.getString(0) }
        }
        val known = companies.map { it.first }.toSet()
        db.rawQuery("SELECT customer_no FROM sd_actual WHERE customer_no<>'全体' GROUP BY customer_no ORDER BY customer_no", null).use { while (it.moveToNext()) if (it.getString(0) !in known) companies += it.getString(0) to it.getString(0) }
        val orderedCompanies = companies.take(1) + companies.drop(1).sortedWith { left, right ->
            val periodMonths = when (sortPeriod) {
                "前期" -> months.take(6)
                "後期" -> months.drop(6)
                "月" -> listOf(sortMonth)
                else -> months
            }
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
                else -> (if (sortGoodFirst) b.compareTo(a) else a.compareTo(b)).takeIf { it != 0 } ?: left.second.compareTo(right.second)
            }
        }
        orderedCompanies.forEach { (no, name) ->
            val target = monthTotal("sd_target", no, months, selectedMetric, selectedCategory)
            val actual = monthTotal("sd_actual", no, months, selectedMetric, selectedCategory)
            val open = expandedCustomer == no
            val item = card().apply { setOnClickListener { expandedCustomer = if (open) "" else no; render() } }
            val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            head.addView(label(if (no == "全体") "全社" else name, 17f, Color.rgb(15, 23, 42), true), LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(label("${shown(actual)} / ${if (target > 0) shown(target) else "—"} $unitLabel", 12f, Color.rgb(47, 58, 81), true))
            head.addView(label(if (open) "　⌃" else "　⌄", 18f, Color.GRAY, true))
            item.addView(head)
            item.addView(label("目標 ${if (target > 0) shown(target) else "—"}　実績 ${shown(actual)}　達成率 ${percent(actual, target)}", 12f, Color.GRAY, false).apply { setPadding(0, 6, 0, 0) })
            if (open) {
                item.addView(TextView(this).apply { setBackgroundColor(Color.rgb(229, 233, 241)); layoutParams = LinearLayout.LayoutParams(-1, 1).apply { topMargin = 10; bottomMargin = 6 } })
                listOf(months.take(6), months.drop(6)).forEachIndexed { halfIndex, halfMonths ->
                    val halfLabel=if(halfIndex==0) "4月〜9月　上期" else "10月〜3月　下期"
                    item.addView(label(halfLabel,12f,Color.rgb(70,78,98),true).apply { setPadding(0,dp(7),0,dp(3)) })
                    val grid=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                    val heading=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
                    fun addCell(row:LinearLayout,value:String,first:Boolean=false,bold:Boolean=false,color:Int=Color.DKGRAY) {
                        val cell=label(value,9.5f,color,bold).apply { gravity=Gravity.CENTER;setPadding(dp(1),dp(5),dp(1),dp(5)) }
                        row.addView(cell,LinearLayout.LayoutParams(dp(if(first)42 else 46),-2))
                    }
                    addCell(heading,"",first=true)
                    halfMonths.forEach { addCell(heading,it.substring(5).toInt().toString()+"月",bold=true,color=Color.GRAY) }
                    addCell(heading,if(halfIndex==0)"上期計" else "下期計",bold=true,color=Color.rgb(52,81,200))
                    grid.addView(heading)
                    val targetValues=halfMonths.map { oneValue("sd_target",no,it,selectedMetric,selectedCategory) }
                    val actualValues=halfMonths.map { oneValue("sd_actual",no,it,selectedMetric,selectedCategory) }
                    val targetHalf=targetValues.sum();val actualHalf=actualValues.sum()
                    fun addDataRow(title:String,values:List<String>,total:String,index:Int,color:Int=Color.DKGRAY) {
                        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;if(index%2==0)setBackgroundColor(Color.rgb(247,248,252)) }
                        addCell(row,title,first=true,bold=true,color=Color.GRAY)
                        values.forEach { addCell(row,it,color=color) }
                        addCell(row,total,bold=true,color=Color.rgb(52,81,200))
                        grid.addView(row)
                    }
                    addDataRow("目標",targetValues.map{if(it>0)shown(it) else "—"},if(targetHalf>0)shown(targetHalf) else "—",0)
                    addDataRow("実績",actualValues.map{shown(it)},shown(actualHalf),1)
                    addDataRow("達成率",actualValues.indices.map{percent(actualValues[it],targetValues[it])},percent(actualHalf,targetHalf),2,Color.rgb(175,113,25))
                    val horizontal=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false;addView(grid) }
                    item.addView(horizontal,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(4) })
                }
            }
            body.addView(item, margin())
        }
    }

    private fun addSortControls(body: LinearLayout) {
        fun rail(options: List<String>, selected: String, onSelect: (String) -> Unit) {
            val strip = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(dp(3),dp(3),dp(3),dp(3)); background=rounded(Color.rgb(238,240,247)) }
            options.forEach { option ->
                val chip=TextView(this).apply { text=option; gravity=Gravity.CENTER; textSize=12f; setPadding(dp(9),dp(9),dp(9),dp(9)); setTextColor(if(option==selected)Color.rgb(52,81,200) else Color.rgb(89,97,116)); background=rounded(if(option==selected)Color.WHITE else Color.TRANSPARENT); setOnClickListener { onSelect(option); render() } }
                strip.addView(chip,LinearLayout.LayoutParams(-2,-2))
            }
            val scroller=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(strip) }
            body.addView(card().apply { addView(scroller) },margin())
        }
        rail(listOf("金額","平米","達成率"),sortMetric) { sortMetric=it }
        rail(listOf("前期","後期","年間","月"),sortPeriod) { sortPeriod=it }
        rail(listOf("良い順","悪い順"),if(sortGoodFirst)"良い順" else "悪い順") { sortGoodFirst=it=="良い順" }
        if(sortPeriod=="月") {
            val monthNames=months().associateBy({ it },{ "${it.substring(5).toInt()}月" })
            rail(monthNames.values.toList(),monthNames[sortMonth]?:"4月") { chosen -> sortMonth=monthNames.entries.first{it.value==chosen}.key }
        }
    }

    private fun renderOrders(body: LinearLayout) {
        val bar=card()
        bar.addView(label("受注明細",18f,Color.rgb(15,23,42),true))
        bar.addView(label("納期ごと・得意先ごとにまとめています。行をタップすると明細が開きます。",12f,Color.GRAY,false).apply { setPadding(0,4,0,6) })
        val search=EditText(this).apply { hint="得意先・品名で検索"; isSingleLine=true; setText(filter); addTextChangedListener(object: android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){filter=s?.toString().orEmpty(); showOrderRows()}; override fun afterTextChanged(s:android.text.Editable?){} }) }
        bar.addView(search)
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
            if(c.count==0){box.addView(card().apply { addView(label("受注データがありません。Excelを読み込んでください。",14f,Color.DKGRAY,false)) }); return}
            val days=linkedMapOf<String,LinkedHashMap<String,MutableList<Pair<String,OrderLine>>>>()
            while(c.moveToNext()) {
                val date=c.getString(1).orEmpty().ifBlank { "納期未登録" }
                val company=c.getString(2).orEmpty().ifBlank { "得意先名未登録" }
                val line=OrderLine(c.getString(3).orEmpty(),c.getString(4).orEmpty(),c.getDouble(5),c.getDouble(6),c.getString(7).orEmpty(),c.getString(8).orEmpty())
                days.getOrPut(date){linkedMapOf()}.getOrPut(company){mutableListOf()} += c.getString(0).orEmpty() to line
            }
            days.forEach { (date,companies) ->
                val dayCount=companies.values.sumOf { it.size }
                box.addView(card().apply {
                    background=rounded(Color.rgb(230,238,250))
                    addView(label("$date　${companies.size}社・${dayCount}件",15f,Color.rgb(35,58,115),true))
                },margin())
                companies.forEach { (company,rows) ->
                    val key="$date|$company";val open=key in expandedOrderGroups
                    val amount=rows.sumOf { it.second.amount };val sqm=rows.sumOf { it.second.sqm }
                    val group=card().apply { setOnClickListener { if(open) expandedOrderGroups.remove(key) else expandedOrderGroups.add(key);showOrderRows() } }
                    val heading=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
                    heading.addView(label(company,15f,Color.rgb(15,23,42),true),LinearLayout.LayoutParams(0,-2,1f))
                    heading.addView(label("${rows.size}件　${money.format(amount/1000)}千円　${money.format(sqm/1000)}千㎡　${if(open)"⌃" else "⌄"}",11f,Color.GRAY,true))
                    group.addView(heading)
                    if(open) rows.forEach { (category,line) ->
                        val detail=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(0,10,0,8) }
                        detail.addView(label("$category　${line.item}",13f,Color.DKGRAY,true))
                        detail.addView(label("数量 ${line.qty}　金額 ${money.format(line.amount/1000)} 千円　平米 ${money.format(line.sqm/1000)} 千㎡",11.5f,Color.DKGRAY,false).apply { setPadding(0,4,0,0) })
                        val reps=listOf(line.sales,line.receiver).filter { it.isNotBlank() }.joinToString(" / ")
                        if(reps.isNotBlank()) detail.addView(label("担当 $reps",10.5f,Color.GRAY,false).apply { setPadding(0,3,0,0) })
                        group.addView(detail)
                    }
                    box.addView(group,margin())
                }
            }
        }
    }

    private fun choose(code:Int,mime:String) {
        val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type="*/*"
            putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/vnd.ms-excel.sheet.macroEnabled.12","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel","application/zip","application/octet-stream","text/csv","text/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i,code)
    }
    @Deprecated("Deprecated") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK) { if(intent.getBooleanExtra("import_only",false)) finish(); return }
        val uri=data?.data?:return
        try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception) {}
        thread {
            try {
                val message=when(requestCode){11->"販売目標を読み込みました（${importTargets(uri)}件）";12->{importSales(uri);"売上実績を読み込みました"};13->{importOrders(uri);"受注明細を読み込みました"};else->"読み込みました"}
                runOnUiThread { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); if(intent.getBooleanExtra("import_only",false)) { setResult(RESULT_OK); finish() } else render() }
            } catch(e:Exception) {
                android.util.Log.e("SalesDashboard","Import failed: uri=$uri mime=${contentResolver.getType(uri)}",e)
                runOnUiThread { Toast.makeText(this,e.message?:"読み込みに失敗しました",Toast.LENGTH_LONG).show(); if(intent.getBooleanExtra("import_only",false)) finish() else render() }
            }
        }
    }

    private fun importTargets(uri:Uri):Int {
        val book=OpenXml.read(contentResolver.openInputStream(uri) ?: error("選択したファイルを開けませんでした。端末に保存してから再度お試しください。"))
        val db=db; var imported=0; db.beginTransaction(); try { db.delete("sd_target",null,null)
            book.forEach { (_,rows) ->
                if(rows.size<6)return@forEach
                val id=rows.getOrNull(2)?.getOrNull(1).orEmpty().trim(); val name=rows.getOrNull(2)?.getOrNull(2).orEmpty().trim()
                val customer=if(id.isBlank()||id=="全体") "全体" else normalizeNo(id); val customerName=if(customer=="全体")"全体" else name
                var category=""
                for(r in 2 until rows.size) {
                    val row=rows[r]; val a=row.getOrNull(0).orEmpty().trim(); val b=row.getOrNull(1).orEmpty().trim()
                    val baseCategory=listOf("段ボール","商品","版代型代","運賃","その他").firstOrNull { a.startsWith(it) }
                    if(baseCategory!=null) category=baseCategory
                    if(!b.startsWith("今期目標")||category.isBlank()) continue
                    val metric=when { a.startsWith("（千㎡")||a.startsWith("(千㎡")->"平米";a.startsWith("（千円")||a.startsWith("(千円")->"金額";else->"" }
                    if(metric.isBlank()) continue
                    val prior=rows.getOrNull(r-1)?.getOrNull(0).orEmpty().trim()
                    if((metric=="平米"&&!prior.startsWith("売上平米"))||(metric=="金額"&&!prior.startsWith("売上金額"))) continue
                    listOf(2,3,4,5,6,7,9,10,11,12,13,14).forEachIndexed { mi, col ->
                        val value=row.getOrNull(col)?.toDoubleOrNull()?:0.0
                        val month=months()[mi]
                        db.insert("sd_target",null,ContentValues().apply { put("customer_no",customer);put("customer_name",customerName);put("category",category);put("metric",metric);put("month",month);put("value",value) }); imported++
                    }
                }
            }
            if(imported==0)error("今期目標のデータが見つかりません。販売目標表（.xlsm）を選択してください")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return imported
    }

    private fun importSales(uri:Uri) {
        val ins=contentResolver.openInputStream(uri)!!
        val reader=BufferedReader(InputStreamReader(ins, Charset.forName("MS932")))
        fun row():List<String>? { val out= mutableListOf<String>(); val b=StringBuilder(); var q=false; var got=false; while(true){val k=reader.read(); if(k<0){if(!got&&b.isEmpty()&&out.isEmpty())return null;out.add(b.toString());return out};got=true;val ch=k.toChar();if(q){if(ch=='"'){reader.mark(1);val n=reader.read();if(n=='"'.code)b.append('"') else {q=false;if(n>=0)reader.reset()} }else b.append(ch)}else when(ch){'"'->q=true;','->{out.add(b.toString());b.setLength(0)};'\n'->{out.add(b.toString().trimEnd('\r'));return out};else->b.append(ch)}} }
        val h=(row()?:error("CSVが空です")).mapIndexed { i,s->if(i==0)s.removePrefix("\uFEFF").trim() else s.trim() }; val ix=h.withIndex().associate{it.value to it.index}
        fun at(r:List<String>, name:String)=r.getOrNull(ix[name]?:-1).orEmpty().trim()
        val dateCol=ix["売上日付"]?:error("売上日付列がありません"); val noCol=ix["得意先番号"]?:error("得意先番号列がありません"); val nameCol=ix["得意先名称"]?:-1; val catCol=ix["商品区分名"]?:ix["商品区分"]?:-1; val amountCol=ix["売上金額"]?:error("売上金額列がありません");val sqmCol=ix["売上平米"]?:error("売上平米列がありません")
        val sums=linkedMapOf<String,DoubleArray>(); val customerNames=linkedMapOf<String,String>(); var count=0
        while(true){val r=row()?:break;val raw=r.getOrNull(noCol).orEmpty().trim();val date=r.getOrNull(dateCol).orEmpty().trim();if(raw.isBlank()||date.length<7)continue;val dm=Regex("(\\d{4})[/.-](\\d{1,2})").find(date)?:continue;val ym="%04d-%02d".format(dm.groupValues[1].toInt(),dm.groupValues[2].toInt());val customer=normalizeNo(raw);val name=r.getOrNull(nameCol).orEmpty().trim();if(name.isNotBlank())customerNames[customer]=name;val cat=category(r.getOrNull(catCol).orEmpty());val key="$customer|$cat|$ym";val a=sums.getOrPut(key){doubleArrayOf(0.0,0.0)};a[0]+=r.getOrNull(amountCol).orEmpty().replace(",","").toDoubleOrNull()?:0.0;a[1]+=r.getOrNull(sqmCol).orEmpty().replace(",","").toDoubleOrNull()?:0.0;count++ }
        reader.close(); if(count==0)error("有効な売上行がありません")
        val totals=linkedMapOf<String,DoubleArray>()
        sums.forEach { (key,a) -> val p=key.split('|'); val k="全体|${p[1]}|${p[2]}"; val t=totals.getOrPut(k){doubleArrayOf(0.0,0.0)};t[0]+=a[0];t[1]+=a[1] }
        sums.putAll(totals)
        val db=db;db.beginTransaction();try{db.delete("sd_actual",null,null);sums.forEach{(key,a)->val p=key.split('|');db.insert("sd_actual",null,ContentValues().apply{put("customer_no",p[0]);put("customer_name",customerNames[p[0]].orEmpty());put("category",p[1]);put("month",p[2]);put("amount",a[0]);put("sqm",a[1])})};db.setTransactionSuccessful()}finally{db.endTransaction()}
    }

    private fun importOrders(uri:Uri) {
        val book=OpenXml.read(contentResolver.openInputStream(uri) ?: error("選択したファイルを開けませんでした。端末に保存してから再度お試しください。")); val all= mutableListOf<Array<String>>()
        book.forEach { (sheet,rows)->
            var header=-1; var map=emptyMap<String,Int>()
            rows.forEachIndexed { i,r->if(header<0&&r.any{it.contains("納期")}&&r.any{it.contains("請求先名")}&&r.any{it.contains("品名")}){header=i;map=r.mapIndexedNotNull{j,v->v.trim().takeIf{it.isNotBlank()}?.let{it to j}}.toMap()} }
            if(header<0)return@forEach
            fun col(vararg keys:String):Int=keys.firstNotNullOfOrNull { key -> map.entries.firstOrNull { it.key.startsWith(key) }?.value } ?: -1
            val category=if(sheet.contains("商品"))"商品" else "段ボール"
            for(i in header+1 until rows.size){val r=rows[i];val item=r.getOrNull(col("品名")).orEmpty().trim();if(item.isBlank())continue
                fun v(vararg keys:String)=keys.firstNotNullOfOrNull{key->r.getOrNull(col(key))?.takeIf{it.isNotBlank()}}.orEmpty()
                all+=arrayOf(category,displayDate(v("納期")),v("請求先名","商品得意先名"),item,v("受注数"),v("受注金額").replace(",",""),v("平米").replace(",",""),v("営業"),v("受担"))
            }
        }
        if(all.isEmpty())error("受注明細の列を見つけられませんでした")
        val db=db;db.beginTransaction();try{db.delete("sd_order",null,null);all.forEach{r->db.insert("sd_order",null,ContentValues().apply{put("category",r[0]);put("delivery",r[1]);put("customer",r[2]);put("item",r[3]);put("qty",r[4]);put("amount",r[5].toDoubleOrNull()?:0.0);put("sqm",r[6].toDoubleOrNull()?:0.0);put("sales",r[7]);put("receiver",r[8])})};db.setTransactionSuccessful()}finally{db.endTransaction()}
    }

    private fun dp(value:Int)=(value*resources.displayMetrics.density+0.5f).toInt()
    private fun rounded(color:Int)=android.graphics.drawable.GradientDrawable().apply{setColor(color);cornerRadius=16f}
    private val unitLabel get() = if(selectedMetric=="金額") "千円" else "千㎡"
    private fun shown(v:Double)=if(selectedMetric=="金額") money.format(v/1000) else DecimalFormat("#,##0.0").format(v/1000)
    private fun percent(a:Double,t:Double)=if(t>0) "${DecimalFormat("0.0").format(a/t*100)}%" else "—"
    private fun monthTotal(table:String,no:String,months:List<String>,metric:String,cat:String):Double {
        val field=if(table=="sd_target") "value" else if(metric=="金額") "amount" else "sqm"
        val where=StringBuilder("customer_no=? AND month IN (${months.joinToString { "?" }})")
        val args=mutableListOf(no).apply{addAll(months)}
        if(cat!="合計"){where.append(" AND category=?");args.add(cat)}
        if(table=="sd_target"){where.append(" AND metric=?");args.add(if(metric=="金額")"金額" else "平米")}
        var value=0.0;db.rawQuery("SELECT SUM($field) FROM $table WHERE $where",args.toTypedArray()).use{if(it.moveToFirst())value=it.getDouble(0)};return value
    }
    private fun oneValue(table:String,no:String,month:String,metric:String,cat:String):Double {
        val field=if(table=="sd_target") "value" else if(metric=="金額") "amount" else "sqm"
        val where=StringBuilder("customer_no=? AND month=?");val args=mutableListOf(no,month)
        if(cat!="合計"){where.append(" AND category=?");args.add(cat)}
        if(table=="sd_target"){where.append(" AND metric=?");args.add(if(metric=="金額")"金額" else "平米")}
        var value=0.0;db.rawQuery("SELECT SUM($field) FROM $table WHERE $where",args.toTypedArray()).use{if(it.moveToFirst())value=it.getDouble(0)};return value
    }

    private fun actuals(cat:String, months:List<String>, customer:String):Pair<Double,Double>{var amount=0.0;var sqm=0.0;db.rawQuery("SELECT SUM(amount),SUM(sqm) FROM sd_actual WHERE category=? AND customer_no=? AND month IN (${months.joinToString{ "?" }})",(listOf(cat,customer)+months).toTypedArray()).use{if(it.moveToFirst()){amount=it.getDouble(0);sqm=it.getDouble(1)}};return amount to sqm}
    private fun sum(table:String,where:String,args:List<String>):Double{var x=0.0;db.rawQuery("SELECT SUM(value) FROM $table WHERE $where",args.toTypedArray()).use{if(it.moveToFirst())x=it.getDouble(0)};return x}
    private fun months(): List<String> =(4..15).map{m->val year=2026+(m-1)/12;val month=(m-1)%12+1;"%04d-%02d".format(year,month)}
    private fun category(raw:String)=when{raw.contains("段ボール")||raw.contains("ダンボール")||raw.contains("箱") ->"段ボール";raw.contains("商品") ->"商品";raw.contains("版")||raw.contains("型") ->"版代型代";raw.contains("運賃")||raw.contains("送料") ->"運賃";else->"その他"}
    private fun displayDate(value:String):String { val d=value.filter{it.isDigit()}; if(d.length==8) return "${d.substring(0,4)}-${d.substring(4,6)}-${d.substring(6,8)}"; val n=value.toDoubleOrNull(); return if(n!=null&&n>30000&&n<80000) java.time.LocalDate.of(1899,12,30).plusDays(n.toLong()).toString() else value }
    private fun normalizeNo(s:String):String{val v=s.trim();return if(v.length>1&&v.last() in '1'..'9')v.dropLast(1)+"0" else v}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,15,16,15);background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=18f}}
    private fun margin()=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=12}
    private fun label(s:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply{text=s;textSize=size;setTextColor(color);if(bold)setTypeface(null,Typeface.BOLD)}
    private fun section(s:String)=label(s,16f,Color.rgb(15,23,42),true).apply{setPadding(4,8,4,12)}
    private fun button(s:String,primary:Boolean)=TextView(this).apply{text=s;textSize=14f;gravity=Gravity.CENTER;setTextColor(if(primary)Color.WHITE else Color.rgb(15,91,70));setPadding(10,5,10,5);minHeight=(44*resources.displayMetrics.density).toInt();background=android.graphics.drawable.GradientDrawable().apply{setColor(if(primary)Color.rgb(48,85,220) else Color.rgb(235,245,240));cornerRadius=12f};layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=6}}

    private object OpenXml {
        fun read(input:java.io.InputStream):List<Pair<String,List<List<String>>>> {
            val entries=linkedMapOf<String,ByteArray>();ZipInputStream(input).use{z->while(true){val e=z.nextEntry?:break;if(!e.isDirectory)entries[e.name]=z.readBytes()}}
            if (entries["xl/workbook.xml"] == null || entries.keys.none { it.startsWith("xl/worksheets/") }) error("Excelブックを確認できません。対応形式は .xlsx / .xlsm です（.xls は非対応）。")
            fun parseStrings(bytes:ByteArray?):List<String>{if(bytes==null)return emptyList();val p=Xml.newPullParser();p.setInput(bytes.inputStream(),"UTF-8");val out= mutableListOf<String>();var inside=false;val b=StringBuilder();var event=p.eventType;while(event!=XmlPullParser.END_DOCUMENT){if(event==XmlPullParser.START_TAG&&p.name=="si"){inside=true;b.setLength(0)}else if(event==XmlPullParser.START_TAG&&p.name=="t"&&inside){b.append(p.nextText())}else if(event==XmlPullParser.END_TAG&&p.name=="si"){out+=b.toString();inside=false};event=p.next()};return out}
            val shared=parseStrings(entries["xl/sharedStrings.xml"]);val sheets=entries.keys.filter{it.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml"))}.sortedBy{Regex("sheet(\\d+)\\.xml$").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE}
            val names=linkedMapOf<String,String>();val workbook=entries["xl/workbook.xml"];val rels=entries["xl/_rels/workbook.xml.rels"]
            if(workbook!=null&&rels!=null){val targetById=linkedMapOf<String,String>();val rp=Xml.newPullParser();rp.setInput(rels.inputStream(),"UTF-8");var ev=rp.eventType;while(ev!=XmlPullParser.END_DOCUMENT){if(ev==XmlPullParser.START_TAG&&rp.name=="Relationship")targetById[rp.getAttributeValue(null,"Id")]=rp.getAttributeValue(null,"Target");ev=rp.next()};val wp=Xml.newPullParser();wp.setInput(workbook.inputStream(),"UTF-8");ev=wp.eventType;while(ev!=XmlPullParser.END_DOCUMENT){if(ev==XmlPullParser.START_TAG&&wp.name=="sheet"){val nm=wp.getAttributeValue(null,"name")?:"";val id=wp.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id") ?: (0 until wp.attributeCount).firstOrNull{wp.getAttributeName(it)=="id"}?.let{wp.getAttributeValue(it)};val target=targetById[id];if(target!=null)names["xl/"+target.removePrefix("/").removePrefix("xl/")]=nm};ev=wp.next()}}
            return sheets.map{path->val rows= mutableListOf<List<String>>();val p=Xml.newPullParser();p.setInput(entries[path]!!.inputStream(),"UTF-8");var row= mutableListOf<String>();var cellRef="";var cellType="";var value="";var currentRow=0;var ev=p.eventType;while(ev!=XmlPullParser.END_DOCUMENT){if(ev==XmlPullParser.START_TAG){when(p.name){"row"->{currentRow=p.getAttributeValue(null,"r")?.toIntOrNull()?:rows.size+1;while(rows.size<currentRow-1)rows.add(emptyList<String>());row= mutableListOf()};"c"->{cellRef=p.getAttributeValue(null,"r")?:"";cellType=p.getAttributeValue(null,"t")?:"";value=""};"v","t"->{value=p.nextText();val col=cellRef.takeWhile{it.isLetter()};val index=col.fold(0){a,ch->a*26+(ch.uppercaseChar()-'A'+1)}-1;while(row.size<=index)row.add("");row[index]=if(cellType=="s")shared.getOrNull(value.toIntOrNull()?:-1).orEmpty() else value}}}else if(ev==XmlPullParser.END_TAG&&p.name=="row"){rows+=row};ev=p.next()};(names[path]?:path.substringAfterLast('/')) to rows}
        }
    }
}
