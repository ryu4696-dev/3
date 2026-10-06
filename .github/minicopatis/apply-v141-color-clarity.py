from pathlib import Path
import re

p = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger/SalesDashboardActivity.kt")
s = p.read_text()

# Selected controls: use the same muted green accent as expanded cards.
s = s.replace(
    'setTextColor(if (item == selectedCategory) Color.rgb(52, 81, 200) else Color.rgb(89, 97, 116))',
    'setTextColor(if (item == selectedCategory) Color.WHITE else Color.rgb(89, 97, 116))'
)
s = s.replace(
    'background = rounded(if (item == selectedCategory) Color.WHITE else Color.TRANSPARENT)',
    'background = rounded(if (item == selectedCategory) Color.rgb(15, 91, 70) else Color.TRANSPARENT)'
)
s = s.replace(
    'setTextColor(if (option == selected) Color.rgb(52, 81, 200) else Color.rgb(89, 97, 116))',
    'setTextColor(if (option == selected) Color.WHITE else Color.rgb(89, 97, 116))'
)
s = s.replace(
    'background = rounded(if (option == selected) Color.WHITE else Color.TRANSPARENT)',
    'background = rounded(if (option == selected) Color.rgb(15, 91, 70) else Color.TRANSPARENT)'
)
s = s.replace(
    'setTextColor(if (sortGoodFirst == goodFirst) Color.rgb(52, 81, 200) else Color.rgb(89, 97, 116))',
    'setTextColor(if (sortGoodFirst == goodFirst) Color.WHITE else Color.rgb(89, 97, 116))'
)
s = s.replace(
    'background = rounded(if (sortGoodFirst == goodFirst) Color.WHITE else Color.TRANSPARENT)',
    'background = rounded(if (sortGoodFirst == goodFirst) Color.rgb(15, 91, 70) else Color.TRANSPARENT)'
)

# Achievement helpers: color is semantic, not decorative.
anchor = '''    private fun renderOrders(body: LinearLayout) {'''
helpers = '''    private fun achievementColor(actual: Double, target: Double): Int {
        if (target <= 0.0) return Color.rgb(107, 114, 128)
        val ratio = actual / target
        return when {
            ratio >= 1.0 -> Color.rgb(15, 118, 84)
            ratio >= 0.9 -> Color.rgb(29, 112, 135)
            ratio >= 0.7 -> Color.rgb(181, 107, 19)
            else -> Color.rgb(190, 58, 58)
        }
    }

    private fun achievementBgColor(actual: Double, target: Double): Int {
        if (target <= 0.0) return Color.rgb(243, 244, 246)
        val ratio = actual / target
        return when {
            ratio >= 1.0 -> Color.rgb(231, 247, 239)
            ratio >= 0.9 -> Color.rgb(232, 244, 248)
            ratio >= 0.7 -> Color.rgb(253, 244, 225)
            else -> Color.rgb(253, 235, 235)
        }
    }

'''
if helpers.strip() not in s:
    if anchor not in s:
        raise SystemExit("renderOrders anchor missing")
    s = s.replace(anchor, helpers + anchor, 1)

# Summary card: keep it calm, but add enough color to separate target/actual/rate.
old = '''        val summary = card().apply {
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
new = '''        val summary = card().apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(248, 251, 250))
                cornerRadius = 18f
                setStroke(dp(1), Color.rgb(199, 219, 211))
            }
        }
        summary.addView(label("全社　${periodLabel()}　$selectedCategory・$selectedMetric", 13f, Color.rgb(25, 58, 50), true))
        val values = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(9), 0, 0) }
        listOf(
            "目標" to "${if (allTarget > 0) shown(allTarget) else "—"} $unitLabel",
            "実績" to "${shown(allActual)} $unitLabel",
            "達成率" to percent(allActual, allTarget)
        ).forEachIndexed { index, (name, value) ->
            val valueColor = when (name) {
                "目標" -> Color.rgb(52, 81, 160)
                "実績" -> Color.rgb(15, 105, 76)
                else -> achievementColor(allActual, allTarget)
            }
            val bg = when (name) {
                "目標" -> Color.rgb(239, 243, 253)
                "実績" -> Color.rgb(235, 247, 241)
                else -> achievementBgColor(allActual, allTarget)
            }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(7), dp(8), dp(7))
                background = rounded(bg)
            }
            cell.addView(label(name, 10.5f, Color.rgb(107, 114, 128), false))
            cell.addView(label(value, 15.5f, valueColor, true).apply { setPadding(0, dp(3), 0, 0) })
            values.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) leftMargin = dp(6)
            })
        }
        summary.addView(values)
        body.addView(summary, margin())'''
if old not in s:
    raise SystemExit("summary v1.4.0 block missing")
s = s.replace(old, new, 1)

# Expanded company card should look selected without becoming a neon sign.
s = s.replace(
    'setColor(Color.WHITE)\n                    cornerRadius = 18f\n                    setStroke(dp(if (open) 2 else 1), if (open) Color.rgb(15, 91, 70) else Color.rgb(225, 229, 236))',
    'setColor(if (open) Color.rgb(247, 252, 249) else Color.WHITE)\n                    cornerRadius = 18f\n                    setStroke(dp(if (open) 2 else 1), if (open) Color.rgb(15, 91, 70) else Color.rgb(225, 229, 236))',
    1
)

old = '''            item.addView(label("${periodLabel()}　目標 ${if (target > 0) shown(target) else "—"}　実績 ${shown(actual)}　達成率 ${percent(actual, target)}", 12f, Color.rgb(105, 111, 123), false).apply { setPadding(0, 6, 0, 0) })'''
new = '''            val meta = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
            meta.addView(label(periodLabel(), 11.5f, Color.rgb(105, 111, 123), false))
            meta.addView(label("　目標 ${if (target > 0) shown(target) else "—"}", 11.5f, Color.rgb(52, 81, 160), false))
            meta.addView(label("　実績 ${shown(actual)}", 11.5f, Color.rgb(15, 105, 76), false))
            meta.addView(label("　達成率 ${percent(actual, target)}", 11.5f, achievementColor(actual, target), true))
            item.addView(meta)'''
if old not in s:
    raise SystemExit("company meta line missing")
s = s.replace(old, new, 1)

# Color target and actual rows, then make every achievement cell use its own rate color.
old = '''                    addDataRow("目標",targetValues.map{if(it>0)shown(it) else "—"},if(targetHalf>0)shown(targetHalf) else "—",0)
                    addDataRow("実績",actualValues.map{shown(it)},shown(actualHalf),1)
                    addDataRow("達成率",actualValues.indices.map{percent(actualValues[it],targetValues[it])},percent(actualHalf,targetHalf),2,Color.rgb(175,113,25))'''
new = '''                    addDataRow("目標",targetValues.map{if(it>0)shown(it) else "—"},if(targetHalf>0)shown(targetHalf) else "—",0,Color.rgb(52,81,160))
                    addDataRow("実績",actualValues.map{shown(it)},shown(actualHalf),1,Color.rgb(15,105,76))
                    val rateRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setBackgroundColor(Color.rgb(252,249,242)) }
                    addCell(rateRow,"達成率",first=true,bold=true,color=Color.GRAY)
                    actualValues.indices.forEach { idx ->
                        addCell(rateRow,percent(actualValues[idx],targetValues[idx]),color=achievementColor(actualValues[idx],targetValues[idx]))
                    }
                    addCell(rateRow,percent(actualHalf,targetHalf),bold=true,color=achievementColor(actualHalf,targetHalf))
                    grid.addView(rateRow)'''
if old not in s:
    raise SystemExit("achievement rows anchor missing")
s = s.replace(old, new, 1)

# Order date separators: muted blue instead of grey.
old = '''                    background=android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.rgb(244,245,247))
                        cornerRadius=16f
                        setStroke(dp(1), Color.rgb(226,229,234))
                    }
                    addView(label("$date　${companies.size}社・${dayCount}件",15f,Color.rgb(55,65,81),true))'''
new = '''                    background=android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.rgb(237,243,251))
                        cornerRadius=16f
                        setStroke(dp(1), Color.rgb(211,224,241))
                    }
                    addView(label("$date　${companies.size}社・${dayCount}件",15f,Color.rgb(42,67,108),true))'''
if old not in s:
    raise SystemExit("order date header anchor missing")
s = s.replace(old, new, 1)

# Expanded order group uses the same pale green selection treatment.
old = '''                        background=android.graphics.drawable.GradientDrawable().apply {
                            setColor(Color.WHITE)
                            cornerRadius=18f
                            setStroke(dp(if(open) 2 else 1), if(open) Color.rgb(15,91,70) else Color.rgb(225,229,236))
                        }'''
new = '''                        background=android.graphics.drawable.GradientDrawable().apply {
                            setColor(if(open) Color.rgb(247,252,249) else Color.WHITE)
                            cornerRadius=18f
                            setStroke(dp(if(open) 2 else 1), if(open) Color.rgb(15,91,70) else Color.rgb(225,229,236))
                        }'''
if old not in s:
    raise SystemExit("order group card anchor missing")
s = s.replace(old, new, 1)

old = '''                    heading.addView(label("${rows.size}件　${money.format(amount)}円　${DecimalFormat("#,##0.##").format(sqm)}㎡　${if(open)"⌃" else "⌄"}",11f,Color.GRAY,true))'''
new = '''                    heading.addView(label("${rows.size}件　${money.format(amount)}円　${DecimalFormat("#,##0.##").format(sqm)}㎡　${if(open)"⌃" else "⌄"}",11f,if(open) Color.rgb(15,91,70) else Color.rgb(76,89,109),true))'''
if old not in s:
    raise SystemExit("order heading metrics anchor missing")
s = s.replace(old, new, 1)

old = '''                        detail.addView(label("$category　${line.item}",13f,Color.DKGRAY,true))
                        detail.addView(label("数量 ${line.qty}　金額 ${money.format(line.amount)}円　平米 ${DecimalFormat("#,##0.##").format(line.sqm)}㎡",11.5f,Color.DKGRAY,false).apply { setPadding(0,4,0,0) })'''
new = '''                        detail.addView(label("$category　${line.item}",13f,Color.rgb(42,55,73),true))
                        val metrics = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(4),0,0) }
                        metrics.addView(label("数量 ${line.qty}",11.5f,Color.rgb(89,97,116),false),LinearLayout.LayoutParams(0,-2,1f))
                        metrics.addView(label("金額 ${money.format(line.amount)}円",11.5f,Color.rgb(15,105,76),true),LinearLayout.LayoutParams(0,-2,1f))
                        metrics.addView(label("平米 ${DecimalFormat("#,##0.##").format(line.sqm)}㎡",11.5f,Color.rgb(52,81,160),true),LinearLayout.LayoutParams(0,-2,1f))
                        detail.addView(metrics)'''
if old not in s:
    raise SystemExit("order detail metrics anchor missing")
s = s.replace(old, new, 1)

p.write_text(s)

build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, c1 = re.subn(r"versionCode\s+\d+", "versionCode 31", b, count=1)
b, c2 = re.subn(r"versionName\s+'[^']+'", "versionName '1.4.1'", b, count=1)
if c1 != 1 or c2 != 1:
    raise SystemExit("version declarations not found")
build.write_text(b)
