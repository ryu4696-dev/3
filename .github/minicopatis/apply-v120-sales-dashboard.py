from pathlib import Path
import re

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
        root.addView(orderCard, fullWidthWrap().apply { bottomMargin = dp(16) })

        '''
    s = s.replace(marker, marker + extra, 1)
    main.write_text(s)

# Put quick quote first on the home screen so it cannot be pushed below the fold.
home_anchor = '        root.setPadding(dp(20), dp(22), dp(20), dp(24))'
if home_anchor not in s:
    raise SystemExit("home content anchor not found")
if "val homeQuoteCard = card()" not in s:
    home_quote = '''

        val homeQuoteCard = card()
        homeQuoteCard.addView(text("簡易見積", 19f, Color.rgb(15, 23, 42), Typeface.BOLD).apply { setPadding(0, 0, 0, dp(12)) })
        homeQuoteCard.addView(actionButton("簡易見積を開く", true).apply { setOnClickListener { openQuickQuote() } }, fullWidth(dp(52)))
        root.addView(homeQuoteCard, fullWidthWrap().apply { bottomMargin = dp(16) })'''
    s = s.replace(home_anchor, home_anchor + home_quote, 1)
s, quote_count = re.subn(r'\n\s*val quoteCard = card\(\).*?root\.addView\(quoteCard, fullWidthWrap\(\)\)', '', s, count=1, flags=re.S)
if quote_count != 1:
    raise SystemExit("old quick quote card block not found")
main.write_text(s)

manifest = Path("ledgerapp/app/src/main/AndroidManifest.xml")
m = manifest.read_text()
if 'android:name=".SalesDashboardActivity"' not in m:
    anchors = ['        <activity android:name=".MainActivity"', '        <activity\n            android:name=".MainActivity"']
    anchor = next((item for item in anchors if item in m), None)
    if anchor is None:
        raise SystemExit("MainActivity manifest entry not found")
    m = m.replace(anchor, '        <activity android:name=".SalesDashboardActivity" android:screenOrientation="unspecified" android:exported="false" />\n' + anchor, 1)

# Android 15 draws edge-to-edge by default. Keep app content below status/navigation bars.
for activity_name in ('.MainActivity', '.SalesDashboardActivity'):
    def add_theme(match):
        attrs = match.group('attrs')
        if 'android:theme=' in attrs:
            return match.group(0)
        self_closing = attrs.rstrip().endswith('/')
        if self_closing:
            attrs = attrs.rstrip()[:-1].rstrip()
        attrs += ' android:theme="@style/QuoteCompatTheme"'
        if self_closing:
            attrs += ' /'
        return '<activity' + attrs + '>'
    m = re.sub(
        r'<activity(?P<attrs>[^>]*android:name="' + re.escape(activity_name) + r'"[^>]*)>',
        add_theme,
        m,
        count=1,
        flags=re.S,
    )
manifest.write_text(m)

build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, code_count = re.subn(r"versionCode\s+\d+", "versionCode 25", b, count=1)
b, name_count = re.subn(r"versionName\s+'[^']+'", "versionName '1.3.5'", b, count=1)
if code_count != 1 or name_count != 1:
    raise SystemExit("app version declarations not found")
build.write_text(b)

activity = Path(".github/minicopatis/SalesDashboardActivity.kt")
if not activity.exists():
    raise SystemExit("SalesDashboardActivity.kt is missing")
(base / "SalesDashboardActivity.kt").write_text(activity.read_text())
