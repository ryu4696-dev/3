from pathlib import Path
import re

root=Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")
p=root/"MainActivity.kt"
s=p.read_text()

# Sales analysis only: remove the grey explanatory text shown before a customer is selected.
s=s.replace(
'''        salesCountView = text(
            if (selectedSalesCustomer == null) "取引先を選択してください" else "販売目標対象のみ",
            11.5f,
            Color.rgb(100, 116, 139),
            Typeface.NORMAL
        ).apply { setPadding(dp(3), dp(7), 0, 0) }''',
'''        salesCountView = text(
            "",
            11.5f,
            Color.rgb(100, 116, 139),
            Typeface.NORMAL
        ).apply { setPadding(dp(3), dp(7), 0, 0) }''',
1
)

s=s.replace(
'''            salesCountView?.text = "取引先を選択してください"
            return''',
'''            salesCountView?.text = ""
            return''',
1
)

p.write_text(s)

build=Path("ledgerapp/app/build.gradle")
b=build.read_text()
b,c1=re.subn(r"versionCode\s+\d+","versionCode 40",b,count=1)
b,c2=re.subn(r"versionName\s+'[^']+'","versionName '1.9.4'",b,count=1)
if c1!=1 or c2!=1:
    raise SystemExit("version declarations not found")
build.write_text(b)
