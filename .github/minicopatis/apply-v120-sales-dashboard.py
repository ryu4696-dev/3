from pathlib import Path

base = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")
main = base / "MainActivity.kt"
s = main.read_text()
marker = '        root.addView(salesCard, fullWidthWrap().apply { bottomMargin = dp(16) })'
if marker not in s:
    raise SystemExit("sales card insertion point not found")
if "販売目標を開く" not in s:
    extra = '''

        val goalCard = card()
        goalCard.addView(text("販売目標", 19f, Color.rgb(15, 23, 42), Typeface.BOLD).apply { setPadding(0, 0, 0, dp(12)) })
        goalCard.addView(text("全社・得意先別の目標と月別実績", 12.5f, Color.rgb(100, 116, 139), Typeface.NORMAL).apply { setPadding(0, 0, 0, dp(12)) })
        goalCard.addView(actionButton("販売目標を開く", true).apply { setOnClickListener { startActivity(Intent(this@MainActivity, SalesDashboardActivity::class.java)) } }, fullWidth(dp(48)))
        root.addView(goalCard, fullWidthWrap().apply { bottomMargin = dp(16) })

        val orderCard = card()
        orderCard.addView(text("受注情報", 19f, Color.rgb(15, 23, 42), Typeface.BOLD).apply { setPadding(0, 0, 0, dp(12)) })
        orderCard.addView(text("段ボール・商品の受注明細", 12.5f, Color.rgb(100, 116, 139), Typeface.NORMAL).apply { setPadding(0, 0, 0, dp(12)) })
        orderCard.addView(actionButton("受注情報を開く", true).apply { setOnClickListener { startActivity(Intent(this@MainActivity, SalesDashboardActivity::class.java).putExtra("orders_only", true)) } }, fullWidth(dp(48)))
        root.addView(orderCard, fullWidthWrap().apply { bottomMargin = dp(16) })'''
    s = s.replace(marker, marker + extra, 1)
    main.write_text(s)

manifest = Path("ledgerapp/app/src/main/AndroidManifest.xml")
m = manifest.read_text()
if 'android:name=".SalesDashboardActivity"' not in m:
    anchors = ['        <activity android:name=".MainActivity"', '        <activity\n            android:name=".MainActivity"']
    anchor = next((item for item in anchors if item in m), None)
    if anchor is None:
        raise SystemExit("MainActivity manifest entry not found")
    m = m.replace(anchor, '        <activity android:name=".SalesDashboardActivity" android:screenOrientation="unspecified" android:exported="false" />\n' + anchor, 1)
    manifest.write_text(m)

build = Path("ledgerapp/app/build.gradle")
b = build.read_text().replace("versionCode 19", "versionCode 21").replace("versionName '0.6.3'", "versionName '1.3.1'")
build.write_text(b)

activity = Path(".github/minicopatis/SalesDashboardActivity.kt")
if not activity.exists():
    raise SystemExit("SalesDashboardActivity.kt is missing")
(base / "SalesDashboardActivity.kt").write_text(activity.read_text())
