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
    private val db by lazy { DataRepository(this).writableDatabase }
    private val money = DecimalFormat("#,##0.0")
    private var filter = ""
    private var orderContainer: LinearLayout? = null
    private var selectedTab = 0
    private var targetCustomer = "全体"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db.execSQL("CREATE TABLE IF NOT EXISTS sd_target(customer_no TEXT, customer_name TEXT, category TEXT, metric TEXT, month TEXT, value REAL, PRIMARY KEY(customer_no,category,metric,month))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sd_actual(customer_no TEXT, customer_name TEXT, category TEXT, month TEXT, amount REAL, sqm REAL, PRIMARY KEY(customer_no,category,month))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sd_order(id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT, delivery TEXT, customer TEXT, item TEXT, qty TEXT, amount REAL, sqm REAL, sales TEXT, receiver TEXT)")
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246,248,251)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(16,12,16,12); setBackgroundColor(Color.WHITE) }
        val back = label("‹ 戻る",16f,Color.rgb(15,91,70),true).apply { setOnClickListener { finish() } }
        top.addView(back)
        top.addView(label("販売目標・受注",20f,Color.rgb(15,23,42),true), LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=18 })
        root.addView(top)
        val tabs = LinearLayout(this).apply { setPadding(12,10,12,10); setBackgroundColor(Color.WHITE) }
        listOf("目標と実績","受注情報").forEachIndexed { i, title ->
            val b = button(title, i==selectedTab).apply { setOnClickListener { selectedTab=i; render() } }
            tabs.addView(b, LinearLayout.LayoutParams(0,46,1f).apply { leftMargin=4; rightMargin=4 })
        }
        root.addView(tabs)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(14,14,14,24) }
        if (selectedTab==0) renderPerformance(body) else renderOrders(body)
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }

    private fun renderPerformance(body: LinearLayout) {
        val c = card()
        c.addView(label("データを読み込む",18f,Color.rgb(15,23,42),true))
        c.addView(label("Excel・CSVを選ぶと、この画面で更新します。",13f,Color.DKGRAY,false).apply { setPadding(0,7,0,10) })
        c.addView(button("販売目標表（Excel）を読み込む", false).apply { setOnClickListener { choose(11, "application/vnd.ms-excel") } })
        c.addView(button("売上実績（CSV）を読み込む", false).apply { setOnClickListener { choose(12, "text/*") } }.also { (it.layoutParams as? LinearLayout.LayoutParams)?.topMargin=8 })
        c.addView(button("対象：$targetCustomer を変更", false).apply { setOnClickListener { chooseCustomer() } })
        body.addView(c, margin())
        body.addView(section("実績 / 目標（$targetCustomer）"))
        val categories = listOf("段ボール","商品","版代型代","運賃","その他")
        val months = months()
        for (cat in categories) {
            val targetAmount = sum("sd_target","category=? AND customer_no=? AND metric='金額' AND month IN (${months.joinToString { "?" }})", listOf(cat,targetCustomer)+months)
            val actual = actuals(cat, months, targetCustomer)
            val targetArea = sum("sd_target","category=? AND customer_no=? AND metric='平米' AND month IN (${months.joinToString { "?" }})", listOf(cat,targetCustomer)+months)
            val card=card()
            card.addView(label(cat,17f,Color.rgb(15,91,70),true))
            card.addView(label("金額　${money.format(actual.first/1000)} / ${if(targetAmount==0.0) "—" else "${money.format(targetAmount)} 千円"}",15f,Color.rgb(30,41,59),true).apply { setPadding(0,9,0,4) })
            val pct=if(targetAmount>0) "　(${money.format(actual.first/1000/targetAmount*100)}%)" else ""
            card.addView(label("実績 ${money.format(actual.first/1000)} 千円$pct",12f,Color.DKGRAY,false))
            val areaLine=if(targetArea>0) "平米　${money.format(actual.second/1000)} / ${money.format(targetArea)} 千㎡" else "平米　${money.format(actual.second/1000)} 千㎡　/ 目標なし"
            card.addView(label(areaLine,13f,Color.DKGRAY,false).apply { setPadding(0,5,0,0) })
            body.addView(card,margin())
        }
        body.addView(label("表示単位：金額 千円、平米 千㎡。実績は売上CSVの読み込み後に表示されます。",11f,Color.GRAY,false).apply { setPadding(4,2,4,8) })
    }


    private fun chooseCustomer() {
        val values= mutableListOf("全体")
        db.rawQuery("SELECT DISTINCT customer_no FROM sd_target WHERE customer_no<>'全体' ORDER BY customer_no",null).use { while(it.moveToNext()) values+=it.getString(0) }
        android.app.AlertDialog.Builder(this).setTitle("対象を選択").setItems(values.toTypedArray()) { _, which -> targetCustomer=values[which];render() }.show()
    }

    private fun renderOrders(body: LinearLayout) {
        val bar=card()
        bar.addView(label("受注明細",18f,Color.rgb(15,23,42),true))
        bar.addView(button("受注明細表（Excel）を読み込む",false).apply { setOnClickListener { choose(13,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") } })
        val search=EditText(this).apply { hint="得意先・品名で検索"; singleLine=true; setText(filter); addTextChangedListener(object: android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){filter=s?.toString().orEmpty(); showOrderRows()}; override fun afterTextChanged(s:android.text.Editable?){} }) }
        bar.addView(search)
        body.addView(bar,margin())
        orderContainer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        body.addView(orderContainer)
        showOrderRows()
    }

    private fun showOrderRows() {
        val box=orderContainer ?: return
        box.removeAllViews()
        val query="SELECT category,delivery,customer,item,qty,amount,sqm,sales,receiver FROM sd_order WHERE customer LIKE ? OR item LIKE ? ORDER BY delivery DESC LIMIT 250"
        db.rawQuery(query,arrayOf("%$filter%","%$filter%")).use { c ->
            if(c.count==0){box.addView(card().apply { addView(label("受注データがありません。Excelを読み込んでください。",14f,Color.DKGRAY,false)) }); return}
            while(c.moveToNext()) {
                val v=card(); v.addView(label("${c.getString(1)}　${c.getString(0)}",12f,Color.GRAY,true))
                v.addView(label(c.getString(2),17f,Color.rgb(15,23,42),true).apply { setPadding(0,4,0,2) })
                v.addView(label(c.getString(3),14f,Color.DKGRAY,false))
                v.addView(label("数量 ${c.getString(4)}　金額 ${money.format(c.getDouble(5)/1000)} 千円　平米 ${money.format(c.getDouble(6)/1000)} 千㎡",12f,Color.DKGRAY,false).apply { setPadding(0,5,0,0) })
                val reps=listOf(c.getString(7),c.getString(8)).filter { !it.isNullOrBlank() }.joinToString(" / ")
                if(reps.isNotBlank()) v.addView(label("担当 $reps",11f,Color.GRAY,false))
                box.addView(v,margin())
            }
        }
    }

    private fun choose(code:Int,mime:String) { val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type=mime; putExtra(Intent.EXTRA_MIME_TYPES,arrayOf(mime,"application/vnd.ms-excel.sheet.macroEnabled.12","application/zip","text/csv")) }; startActivityForResult(i,code) }
    @Deprecated("Deprecated") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) { super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK)return; val uri=data?.data?:return; thread { try { when(requestCode){11->importTargets(uri);12->importSales(uri);13->importOrders(uri)}; runOnUiThread { Toast.makeText(this,"読み込みました",Toast.LENGTH_LONG).show(); render() } } catch(e:Exception) { runOnUiThread { Toast.makeText(this,e.message?:"読み込みに失敗しました",Toast.LENGTH_LONG).show() } } } }

    private fun importTargets(uri:Uri) {
        val book=OpenXml.read(contentResolver.openInputStream(uri)!!)
        val db=db; db.beginTransaction(); try { db.delete("sd_target",null,null)
            book.forEach { (_,rows) ->
                if(rows.size<6)return@forEach
                val id=rows.getOrNull(1)?.getOrNull(1).orEmpty().trim(); val name=rows.getOrNull(1)?.getOrNull(2).orEmpty().trim()
                val customer=if(id.isBlank()||id=="全体") "全体" else normalizeNo(id); val customerName=if(customer=="全体")"全体" else name
                var category=""
                for(r in 2 until rows.size) {
                    val row=rows[r]; val a=row.getOrNull(0).orEmpty().trim(); val b=row.getOrNull(1).orEmpty().trim()
                    if(a in listOf("段ボール","商品","版代型代","運賃","その他")) category=a
                    if(b!="今期目標"||category.isBlank()) continue
                    val metric=when(a){"（千㎡）","(千㎡)"->"平米";"（千円）","(千円)"->"金額";else->""}
                    if(metric.isBlank()) continue
                    val prior=rows.getOrNull(r-1)?.getOrNull(0).orEmpty().trim()
                    if((metric=="平米"&&prior!="売上平米")||(metric=="金額"&&prior!="売上金額")) continue
                    listOf(2,3,4,5,6,7,9,10,11,12,13,14).forEachIndexed { mi, col ->
                        val value=row.getOrNull(col)?.toDoubleOrNull()?:0.0
                        val month=months()[mi]
                        db.insert("sd_target",null,ContentValues().apply { put("customer_no",customer);put("customer_name",customerName);put("category",category);put("metric",metric);put("month",month);put("value",value) })
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun importSales(uri:Uri) {
        val ins=contentResolver.openInputStream(uri)!!
        val reader=BufferedReader(InputStreamReader(ins, Charset.forName("MS932")))
        fun row():List<String>? { val out= mutableListOf<String>(); val b=StringBuilder(); var q=false; var got=false; while(true){val k=reader.read(); if(k<0){if(!got&&b.isEmpty()&&out.isEmpty())return null;out.add(b.toString());return out};got=true;val ch=k.toChar();if(q){if(ch=='"'){reader.mark(1);val n=reader.read();if(n=='"')b.append('"') else {q=false;if(n>=0)reader.reset()} }else b.append(ch)}else when(ch){'"'->q=true;','->{out.add(b.toString());b.setLength(0)};'\n'->{out.add(b.toString().trimEnd('\r'));return out};else->b.append(ch)}} }
        val h=(row()?:error("CSVが空です")).mapIndexed { i,s->if(i==0)s.removePrefix("\uFEFF").trim() else s.trim() }; val ix=h.withIndex().associate{it.value to it.index}
        fun at(r:List<String>, name:String)=r.getOrNull(ix[name]?:-1).orEmpty().trim()
        val dateCol=ix["売上日付"]?:error("売上日付列がありません"); val noCol=ix["得意先番号"]?:error("得意先番号列がありません"); val nameCol=ix["得意先名称"]?:-1; val catCol=ix["商品区分名"]?:ix["商品区分"]?:-1; val amountCol=ix["売上金額"]?:error("売上金額列がありません");val sqmCol=ix["売上平米"]?:error("売上平米列がありません")
        val sums=linkedMapOf<String,DoubleArray>(); var count=0
        while(true){val r=row()?:break;val raw=r.getOrNull(noCol).orEmpty().trim();val date=r.getOrNull(dateCol).orEmpty().trim();if(raw.isBlank()||date.length<7)continue;val dm=Regex("(\\d{4})[/.-](\\d{1,2})").find(date)?:continue;val ym="%04d-%02d".format(dm.groupValues[1].toInt(),dm.groupValues[2].toInt());val customer=normalizeNo(raw);val cat=category(r.getOrNull(catCol).orEmpty());val key="$customer|$cat|$ym";val a=sums.getOrPut(key){doubleArrayOf(0.0,0.0)};a[0]+=r.getOrNull(amountCol).orEmpty().replace(",","").toDoubleOrNull()?:0.0;a[1]+=r.getOrNull(sqmCol).orEmpty().replace(",","").toDoubleOrNull()?:0.0;count++ }
        reader.close(); if(count==0)error("有効な売上行がありません")
        val totals=linkedMapOf<String,DoubleArray>()
        sums.forEach { (key,a) -> val p=key.split('|'); val k="全体|${p[1]}|${p[2]}"; val t=totals.getOrPut(k){doubleArrayOf(0.0,0.0)};t[0]+=a[0];t[1]+=a[1] }
        sums.putAll(totals)
        val db=db;db.beginTransaction();try{db.delete("sd_actual",null,null);sums.forEach{(key,a)->val p=key.split('|');db.insert("sd_actual",null,ContentValues().apply{put("customer_no",p[0]);put("customer_name","");put("category",p[1]);put("month",p[2]);put("amount",a[0]);put("sqm",a[1])})};db.setTransactionSuccessful()}finally{db.endTransaction()}
    }

    private fun importOrders(uri:Uri) {
        val book=OpenXml.read(contentResolver.openInputStream(uri)!!); val all= mutableListOf<Array<String>>()
        book.forEach { (sheet,rows)->
            var header=-1; var map=emptyMap<String,Int>()
            rows.forEachIndexed { i,r->if(header<0&&r.any{it.contains("納期")}&&r.any{it.contains("請求先名")}&&r.any{it.contains("品名")}){header=i;map=r.mapIndexedNotNull{j,v->v.trim().takeIf{it.isNotBlank()}?.let{it to j}}.toMap()} }
            if(header<0)return@forEach
            val category=if(sheet.contains("商品"))"商品" else "段ボール"
            for(i in header+1 until rows.size){val r=rows[i];val item=r.getOrNull(map["品名"]?:-1).orEmpty().trim();if(item.isBlank())continue
                fun v(vararg keys:String)=keys.firstNotNullOfOrNull{r.getOrNull(map[it]?:-1)?.takeIf{it.isNotBlank()}}.orEmpty()
                all+=arrayOf(category,displayDate(v("納期")),v("請求先名","商品得意先名"),item,v("受注数"),v("受注金額").replace(",",""),v("平米").replace(",",""),v("営業"),v("受担"))
            }
        }
        if(all.isEmpty())error("受注明細の列を見つけられませんでした")
        val db=db;db.beginTransaction();try{db.delete("sd_order",null,null);all.forEach{r->db.insert("sd_order",null,ContentValues().apply{put("category",r[0]);put("delivery",r[1]);put("customer",r[2]);put("item",r[3]);put("qty",r[4]);put("amount",r[5].toDoubleOrNull()?:0.0);put("sqm",r[6].toDoubleOrNull()?:0.0);put("sales",r[7]);put("receiver",r[8])})};db.setTransactionSuccessful()}finally{db.endTransaction()}
    }

    private fun actuals(cat:String, months:List<String>, customer:String):Pair<Double,Double>{var amount=0.0;var sqm=0.0;db.rawQuery("SELECT SUM(amount),SUM(sqm) FROM sd_actual WHERE category=? AND customer_no=? AND month IN (${months.joinToString{ "?" }})",(listOf(cat,customer)+months).toTypedArray()).use{if(it.moveToFirst()){amount=it.getDouble(0);sqm=it.getDouble(1)}};return amount to sqm}
    private fun sum(table:String,where:String,args:List<String>):Double{var x=0.0;db.rawQuery("SELECT SUM(value) FROM $table WHERE $where",args.toTypedArray()).use{if(it.moveToFirst())x=it.getDouble(0)};return x}
    private fun months(): List<String> =(4..15).map{m->val year=2026+(m-1)/12;val month=(m-1)%12+1;"%04d-%02d".format(year,month)}
    private fun category(raw:String)=when{raw.contains("段ボール")||raw.contains("ダンボール")||raw.contains("箱") ->"段ボール";raw.contains("商品") ->"商品";raw.contains("版")||raw.contains("型") ->"版代型代";raw.contains("運賃")||raw.contains("送料") ->"運賃";else->"その他"}
    private fun displayDate(value:String):String { val n=value.toDoubleOrNull(); return if(n!=null&&n>30000&&n<80000) java.time.LocalDate.of(1899,12,30).plusDays(n.toLong()).toString() else value }
    private fun normalizeNo(s:String):String{val v=s.trim();return if(v.length>1&&v.last() in '1'..'9')v.dropLast(1)+"0" else v}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,15,16,15);background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=18f}}
    private fun margin()=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=12}
    private fun label(s:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply{text=s;textSize=size;setTextColor(color);if(bold)setTypeface(null,Typeface.BOLD)}
    private fun section(s:String)=label(s,16f,Color.rgb(15,23,42),true).apply{setPadding(4,8,4,12)}
    private fun button(s:String,primary:Boolean)=TextView(this).apply{text=s;textSize=14f;gravity=Gravity.CENTER;setTextColor(if(primary)Color.WHITE else Color.rgb(15,91,70));setPadding(10,12,10,12);background=android.graphics.drawable.GradientDrawable().apply{setColor(if(primary)Color.rgb(15,91,70) else Color.rgb(235,245,240));cornerRadius=12f};layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=6}}

    private object OpenXml {
        fun read(input:java.io.InputStream):List<Pair<String,List<List<String>>>> {
            val entries=linkedMapOf<String,ByteArray>();ZipInputStream(input).use{z->while(true){val e=z.nextEntry?:break;if(!e.isDirectory)entries[e.name]=z.readBytes()}}
            fun parseStrings(bytes:ByteArray?):List<String>{if(bytes==null)return emptyList();val p=Xml.newPullParser();p.setInput(bytes.inputStream(),"UTF-8");val out= mutableListOf<String>();var inside=false;val b=StringBuilder();var event=p.eventType;while(event!=XmlPullParser.END_DOCUMENT){if(event==XmlPullParser.START_TAG&&p.name=="si"){inside=true;b.setLength(0)}else if(event==XmlPullParser.START_TAG&&p.name=="t"&&inside){b.append(p.nextText())}else if(event==XmlPullParser.END_TAG&&p.name=="si"){out+=b.toString();inside=false};event=p.next()};return out}
            val shared=parseStrings(entries["xl/sharedStrings.xml"]);val sheets=entries.keys.filter{it.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml"))}.sortedBy{it.substringAfter("sheet").substringBefore(".").toInt()}
            val names=linkedMapOf<String,String>();val workbook=entries["xl/workbook.xml"];val rels=entries["xl/_rels/workbook.xml.rels"]
            if(workbook!=null&&rels!=null){val targetById=linkedMapOf<String,String>();val rp=Xml.newPullParser();rp.setInput(rels.inputStream(),"UTF-8");var ev=rp.eventType;while(ev!=XmlPullParser.END_DOCUMENT){if(ev==XmlPullParser.START_TAG&&rp.name=="Relationship")targetById[rp.getAttributeValue(null,"Id")]=rp.getAttributeValue(null,"Target");ev=rp.next()};val wp=Xml.newPullParser();wp.setInput(workbook.inputStream(),"UTF-8");ev=wp.eventType;while(ev!=XmlPullParser.END_DOCUMENT){if(ev==XmlPullParser.START_TAG&&wp.name=="sheet"){val nm=wp.getAttributeValue(null,"name")?:"";val id=wp.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id") ?: (0 until wp.attributeCount).firstOrNull{wp.getAttributeName(it)=="id"}?.let{wp.getAttributeValue(it)};val target=targetById[id];if(target!=null)names["xl/"+target.removePrefix("/").removePrefix("xl/")]=nm};ev=wp.next()}}
            return sheets.map{path->val rows= mutableListOf<List<String>>();val p=Xml.newPullParser();p.setInput(entries[path]!!.inputStream(),"UTF-8");var row= mutableListOf<String>();var cellRef="";var cellType="";var value="";var currentRow=0;var ev=p.eventType;while(ev!=XmlPullParser.END_DOCUMENT){if(ev==XmlPullParser.START_TAG){when(p.name){"row"->{currentRow=p.getAttributeValue(null,"r")?.toIntOrNull()?:rows.size+1;while(rows.size<currentRow-1)rows+=emptyList();row= mutableListOf()};"c"->{cellRef=p.getAttributeValue(null,"r")?:"";cellType=p.getAttributeValue(null,"t")?:"";value=""};"v","t"->{value=p.nextText();val col=cellRef.takeWhile{it.isLetter()};val index=col.fold(0){a,ch->a*26+(ch.uppercaseChar()-'A'+1)}-1;while(row.size<=index)row.add("");row[index]=if(cellType=="s")shared.getOrNull(value.toIntOrNull()?:-1).orEmpty() else value}}}else if(ev==XmlPullParser.END_TAG&&p.name=="row"){rows+=row};ev=p.next()};(names[path]?:path.substringAfterLast('/')) to rows}
        }
    }
}
