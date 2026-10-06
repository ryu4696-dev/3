from pathlib import Path
import re

root=Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

p=root/"SalesDashboardActivity.kt"
s=p.read_text()

# Hide provisional orders: in this data, provisional orders are the zero-yen rows.
old='''        val query="SELECT category,delivery,customer,item,qty,amount,sqm,sales,receiver FROM sd_order WHERE customer LIKE ? OR item LIKE ? ORDER BY delivery DESC,customer COLLATE NOCASE,item COLLATE NOCASE LIMIT 250"'''
new='''        val query="SELECT category,delivery,customer,item,qty,amount,sqm,sales,receiver FROM sd_order WHERE (customer LIKE ? OR item LIKE ?) AND ABS(amount) > 0.000001 ORDER BY delivery DESC,customer COLLATE NOCASE,item COLLATE NOCASE LIMIT 250"'''
if old not in s:
    raise SystemExit("order query anchor missing")
s=s.replace(old,new,1)

old='''                fun v(vararg keys:String)=keys.firstNotNullOfOrNull{key->r.getOrNull(col(key))?.takeIf{it.isNotBlank()}}.orEmpty()
                all+=arrayOf(category,displayDate(v("納期")),v("請求先名","商品得意先名"),item,v("受注数"),v("受注金額").replace(",",""),v("平米").replace(",",""),v("営業"),v("受担"))
'''
new='''                fun v(vararg keys:String)=keys.firstNotNullOfOrNull{key->r.getOrNull(col(key))?.takeIf{it.isNotBlank()}}.orEmpty()
                val amountText=v("受注金額").replace(",","")
                val amountValue=amountText.toDoubleOrNull() ?: 0.0
                if(kotlin.math.abs(amountValue) <= 0.000001) continue
                all+=arrayOf(category,displayDate(v("納期")),v("請求先名","商品得意先名"),item,v("受注数"),amountText,v("平米").replace(",",""),v("営業"),v("受担"))
'''
if old not in s:
    raise SystemExit("order import amount anchor missing")
s=s.replace(old,new,1)

p.write_text(s)

build=Path("ledgerapp/app/build.gradle")
b=build.read_text()
b,c1=re.subn(r"versionCode\s+\d+","versionCode 39",b,count=1)
b,c2=re.subn(r"versionName\s+'[^']+'","versionName '1.9.3'",b,count=1)
if c1!=1 or c2!=1:
    raise SystemExit("version declarations not found")
build.write_text(b)
