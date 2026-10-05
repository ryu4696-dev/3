from pathlib import Path
import re

p = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger/SalesDashboardActivity.kt")
s = p.read_text()

s = s.replace('    private var expandedCustomer = "全体"', '    private var expandedCustomer = ""', 1)

old = '''        val months = months()
        val allTarget = monthTotal("sd_target", "全体", months, selectedMetric, selectedCategory)
        val allActual = monthTotal("sd_actual", "全体", months, selectedMetric, selectedCategory)'''
new = '''        val months = months()
        val periodMonths = activePeriodMonths()
        val allTarget = monthTotal("sd_target", "全体", periodMonths, selectedMetric, selectedCategory)
        val allActual = monthTotal("sd_actual", "全体", periodMonths, selectedMetric, selectedCategory)'''
if old not in s:
    raise SystemExit("period summary anchor not found")
s = s.replace(old, new, 1)

old = '''        val summary = card().apply {
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
        body.addView(summary, margin())'''
new = '''        val summary = card().apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 18f
                setStroke(dp(1), Color.rgb(220, 224, 232))
            }
        }
        summary.addView(label("全社　${periodLabel()}　$selectedCategory・$selectedMetric", 13f, Color.rgb(31, 41, 55), true))
        val values = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(9), 0, 0) }
        listOf(
            "目標" to "${if (allTarget > 0) shown(allTarget) else "—"} $unitLabel",
            "実績" to "${shown(allActual)} $unitLabel",
            "達成率" to percent(allActual, allTarget)
        ).forEach { (name, value) ->
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            cell.addView(label(name, 10.5f, Color.rgb(107, 114, 128), false))
            cell.addView(label(value, 15.5f, Color.rgb(17, 24, 39), true).apply { setPadding(0, dp(3), 0, 0) })
            values.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        summary.addView(values)
        body.addView(summary, margin())'''
if old not in s:
    raise SystemExit("blue summary block not found")
s = s.replace(old, new, 1)

old = '''            val periodMonths = when (sortPeriod) {
                "前期" -> months.take(6)
                "後期" -> months.drop(6)
                "月" -> listOf(sortMonth)
                else -> months
            }
'''
if old not in s:
    raise SystemExit("sort period block not found")
s = s.replace(old, "", 1)

old = '''            val target = monthTotal("sd_target", no, months, selectedMetric, selectedCategory)
            val actual = monthTotal("sd_actual", no, months, selectedMetric, selectedCategory)'''
new = '''            val target = monthTotal("sd_target", no, periodMonths, selectedMetric, selectedCategory)
            val actual = monthTotal("sd_actual", no, periodMonths, selectedMetric, selectedCategory)'''
if old not in s:
    raise SystemExit("company period values anchor not found")
s = s.replace(old, new, 1)

old = '''            val item = card().apply { setOnClickListener { expandedCustomer = if (open) "" else no; render() } }'''
new = '''            val item = card().apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = 18f
                    setStroke(dp(if (open) 2 else 1), if (open) Color.rgb(15, 91, 70) else Color.rgb(225, 229, 236))
                }
                setOnClickListener { expandedCustomer = if (open) "" else no; render() }
            }'''
if old not in s:
    raise SystemExit("company card anchor not found")
s = s.replace(old, new, 1)

old = '''            item.addView(label("目標 ${if (target > 0) shown(target) else "—"}　実績 ${shown(actual)}　達成率 ${percent(actual, target)}", 12f, Color.GRAY, false).apply { setPadding(0, 6, 0, 0) })'''
new = '''            item.addView(label("${periodLabel()}　目標 ${if (target > 0) shown(target) else "—"}　実績 ${shown(actual)}　達成率 ${percent(actual, target)}", 12f, Color.rgb(105, 111, 123), false).apply { setPadding(0, 6, 0, 0) })'''
if old not in s:
    raise SystemExit("company subline anchor not found")
s = s.replace(old, new, 1)

old = '''                listOf(months.take(6), months.drop(6)).forEachIndexed { halfIndex, halfMonths ->
                    val halfLabel=if(halfIndex==0) "4月〜9月　上期" else "10月〜3月　下期"'''
new = '''                periodGroups().forEach { (halfLabel, halfMonths) ->'''
if old not in s:
    raise SystemExit("expanded period groups anchor not found")
s = s.replace(old, new, 1)

old = '''                    addCell(heading,if(halfIndex==0)"上期計" else "下期計",bold=true,color=Color.rgb(52,81,200))'''
new = '''                    addCell(heading,"期間計",bold=true,color=Color.rgb(15,91,70))'''
if old not in s:
    raise SystemExit("expanded period total heading anchor not found")
s = s.replace(old, new, 1)

s = s.replace(
    'addCell(row,total,bold=true,color=Color.rgb(52,81,200))',
    'addCell(row,total,bold=true,color=Color.rgb(15,91,70))',
    1
)

old = '''        addSelectorRow("表示", listOf("金額", "平米"), selectedMetric, { selectedMetric = it }, true)
        addSelectorRow("並び替え", listOf("金額", "平米", "達成率"), sortMetric, { sortMetric = it })'''
new = '''        addSelectorRow("表示", listOf("金額", "平米"), selectedMetric, {
            selectedMetric = it
            if (sortMetric != "達成率") sortMetric = it
        }, true)
        addSelectorRow("並び替え", listOf("金額", "平米", "達成率"), sortMetric, {
            sortMetric = it
            if (it == "金額" || it == "平米") selectedMetric = it
        })'''
if old not in s:
    raise SystemExit("sort selector anchor not found")
s = s.replace(old, new, 1)

insert_anchor = '''    private fun renderOrders(body: LinearLayout) {'''
helpers = '''    private fun activePeriodMonths(): List<String> = when (sortPeriod) {
        "前期" -> months().take(6)
        "後期" -> months().drop(6)
        "月" -> listOf(sortMonth)
        else -> months()
    }

    private fun periodLabel(): String = when (sortPeriod) {
        "前期" -> "前期（4〜9月）"
        "後期" -> "後期（10〜3月）"
        "月" -> "${sortMonth.substring(5).toInt()}月"
        else -> "年間"
    }

    private fun periodGroups(): List<Pair<String, List<String>>> = when (sortPeriod) {
        "前期" -> listOf("前期（4〜9月）" to months().take(6))
        "後期" -> listOf("後期（10〜3月）" to months().drop(6))
        "月" -> listOf("${sortMonth.substring(5).toInt()}月" to listOf(sortMonth))
        else -> listOf(
            "前期（4〜9月）" to months().take(6),
            "後期（10〜3月）" to months().drop(6)
        )
    }

'''
if insert_anchor not in s:
    raise SystemExit("renderOrders anchor not found")
s = s.replace(insert_anchor, helpers + insert_anchor, 1)

old = '''                box.addView(card().apply {
                    background=rounded(Color.rgb(230,238,250))
                    addView(label("$date　${companies.size}社・$dayCount件",15f,Color.rgb(35,58,115),true))
                },margin())'''
new = '''                box.addView(card().apply {
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background=android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.rgb(244,245,247))
                        cornerRadius=16f
                        setStroke(dp(1), Color.rgb(226,229,234))
                    }
                    addView(label("$date　${companies.size}社・$dayCount件",15f,Color.rgb(55,65,81),true))
                },margin())'''
if old not in s:
    raise SystemExit("order day header anchor not found")
s = s.replace(old, new, 1)

old = '''                    val group=card().apply { setOnClickListener { if(open) expandedOrderGroups.remove(key) else expandedOrderGroups.add(key);showOrderRows() } }'''
new = '''                    val group=card().apply {
                        background=android.graphics.drawable.GradientDrawable().apply {
                            setColor(Color.WHITE)
                            cornerRadius=18f
                            setStroke(dp(if(open) 2 else 1), if(open) Color.rgb(15,91,70) else Color.rgb(225,229,236))
                        }
                        setOnClickListener { if(open) expandedOrderGroups.remove(key) else expandedOrderGroups.add(key);showOrderRows() }
                    }'''
if old not in s:
    raise SystemExit("order company card anchor not found")
s = s.replace(old, new, 1)

s = s.replace(
    'heading.addView(label("${rows.size}件　${money.format(amount/1000)}千円　${DecimalFormat("#,##0.##").format(sqm)}㎡　${if(open)"⌃" else "⌄"}",11f,Color.GRAY,true))',
    'heading.addView(label("${rows.size}件　${money.format(amount)}円　${DecimalFormat("#,##0.##").format(sqm)}㎡　${if(open)"⌃" else "⌄"}",11f,Color.GRAY,true))',
    1
)

s = s.replace(
    'val detail=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(0,10,0,8) }',
    'val detail=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(9),dp(10),dp(9));background=rounded(Color.rgb(248,249,251)) }',
    1
)
s = s.replace(
    'detail.addView(label("数量 ${line.qty}　金額 ${money.format(line.amount/1000)} 千円　平米 ${DecimalFormat("#,##0.##").format(line.sqm)} ㎡",11.5f,Color.DKGRAY,false).apply { setPadding(0,4,0,0) })',
    'detail.addView(label("数量 ${line.qty}　金額 ${money.format(line.amount)}円　平米 ${DecimalFormat("#,##0.##").format(line.sqm)}㎡",11.5f,Color.DKGRAY,false).apply { setPadding(0,4,0,0) })',
    1
)
s = s.replace(
    'group.addView(detail)',
    'group.addView(detail, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })',
    1
)

p.write_text(s)

build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, c1 = re.subn(r"versionCode\s+\d+", "versionCode 30", b, count=1)
b, c2 = re.subn(r"versionName\s+'[^']+'", "versionName '1.4.0'", b, count=1)
if c1 != 1 or c2 != 1:
    raise SystemExit("version declarations not found")
build.write_text(b)
