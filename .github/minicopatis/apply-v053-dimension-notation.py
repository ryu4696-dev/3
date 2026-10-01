from pathlib import Path

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

# CSVの実ヘッダーに合わせる:
# 継代 = 糊代、天フラップ = 上フラップ、底フラップ = 下フラップ
p = root / "CsvImporter.kt"
s = p.read_text()

old = '''        val glueMarginIdx = optionalAny("糊代", "のりしろ", "ノリシロ", "糊しろ", "糊代寸法", "製造寸法糊代", "製造_糊代")
        val topFlapIdx = optionalAny("上フラップ", "上フラップ長", "上フラップ寸法", "製造寸法上フラップ", "製造_上フラップ")
        val bottomFlapIdx = optionalAny("下フラップ", "下フラップ長", "下フラップ寸法", "したフラップ", "製造寸法下フラップ", "製造_下フラップ")
'''
new = '''        val glueMarginIdx = optionalAny("継代", "継ぎ代", "糊代", "のりしろ", "ノリシロ", "糊しろ", "糊代寸法", "製造寸法糊代", "製造_糊代")
        val topFlapIdx = optionalAny("天フラップ", "上フラップ", "上フラップ長", "上フラップ寸法", "製造寸法上フラップ", "製造_上フラップ")
        val bottomFlapIdx = optionalAny("底フラップ", "下フラップ", "下フラップ長", "下フラップ寸法", "したフラップ", "製造寸法下フラップ", "製造_下フラップ")
'''
if old not in s:
    raise SystemExit("CsvImporter flap header block not found")
s = s.replace(old, new, 1)
p.write_text(s)

# 台帳一覧は寸法を1列へ戻し、
# 糊代-長-巾-深さ-上フラップ-下フラップ の順で表示。
# 糊代とフラップだけ薄い色で描画する。
p = root / "LedgerTableView.kt"
s = p.read_text()

old = '''    private val headers = listOf(
        "材質", "長", "巾", "深さ", "のりしろ", "上フラップ", "下フラップ",
        "平米", "売価", "平米売価", "格差", "工程", "最終納品日"
    )
    private val rightWidths = floatArrayOf(
        dp(105f), dp(85f), dp(85f), dp(85f), dp(95f), dp(105f), dp(105f),
        dp(90f), dp(105f), dp(115f), dp(85f), dp(230f), dp(135f)
    )
'''
new = '''    private val headers = listOf("材質", "寸法", "平米", "売価", "平米売価", "格差", "工程", "最終納品日")
    private val rightWidths = floatArrayOf(
        dp(105f), dp(235f), dp(90f), dp(105f), dp(115f), dp(85f), dp(230f), dp(135f)
    )
'''
if old not in s:
    raise SystemExit("LedgerTableView detailed header block not found")
s = s.replace(old, new, 1)

old = '''                    drawTextCell(canvas, values[col], x, top, cellW, rowHeight, numeric = col in 1..9)
'''
new = '''                    if (col == 1) {
                        drawDimensionCell(canvas, products[i], x, top, cellW, rowHeight)
                    } else {
                        drawTextCell(canvas, values[col], x, top, cellW, rowHeight, numeric = col in 2..4)
                    }
'''
if old not in s:
    raise SystemExit("LedgerTableView row draw block not found")
s = s.replace(old, new, 1)

s = s.replace(
    'paint.textAlign = if (i in 1..9) Paint.Align.RIGHT else Paint.Align.LEFT',
    'paint.textAlign = if (i in 2..4) Paint.Align.RIGHT else Paint.Align.LEFT',
    1
)
s = s.replace(
    'val tx = if (i in 1..9) x + w - dp(10f) else x + dp(10f)',
    'val tx = if (i in 2..4) x + w - dp(10f) else x + dp(10f)',
    1
)

old = '''        fun dim(value: Double): String = if (value > 0.000001) numberFormat.format(value) else "-"
        return listOf(
            p.material,
            dim(p.dimLength),
            dim(p.dimWidth),
            dim(p.dimDepth),
            dim(p.glueMargin),
            dim(p.topFlap),
            dim(p.bottomFlap),
            if (p.sqm > 0.000001) numberFormat.format(p.sqm) else "-",
            if (p.price != 0.0) "¥${moneyFormat.format(p.price)}" else "-",
            if (p.sqmPrice > 0.000001) "¥${moneyFormat.format(p.sqmPrice)}" else "-",
            if (p.tiers.isNotEmpty()) "あり" else "なし",
            p.process,
            p.lastDeliveryDate
        )
'''
new = '''        return listOf(
            p.material,
            dimensionText(p),
            if (p.sqm > 0.000001) numberFormat.format(p.sqm) else "-",
            if (p.price != 0.0) "¥${moneyFormat.format(p.price)}" else "-",
            if (p.sqmPrice > 0.000001) "¥${moneyFormat.format(p.sqmPrice)}" else "-",
            if (p.tiers.isNotEmpty()) "あり" else "なし",
            p.process,
            p.lastDeliveryDate
        )
'''
if old not in s:
    raise SystemExit("LedgerTableView detailed rowValues block not found")
s = s.replace(old, new, 1)

marker = '''    private fun drawTextCell(canvas: Canvas, text: String, x: Float, y: Float, w: Float, h: Float, numeric: Boolean) {
'''
addition = '''    private fun dimensionPart(value: Double): String =
        if (value > 0.000001) DecimalFormat("0.##").format(value) else "-"

    private fun dimensionParts(p: ProductSummary): List<String> = listOf(
        dimensionPart(p.glueMargin),
        dimensionPart(p.dimLength),
        dimensionPart(p.dimWidth),
        dimensionPart(p.dimDepth),
        dimensionPart(p.topFlap),
        dimensionPart(p.bottomFlap)
    )

    private fun dimensionText(p: ProductSummary): String = dimensionParts(p).joinToString("-")

    private fun drawDimensionCell(canvas: Canvas, p: ProductSummary, x: Float, y: Float, w: Float, h: Float) {
        val parts = dimensionParts(p)
        val baseline = y + h / 2f + dp(4.5f)
        var tx = x + dp(10f)

        canvas.save()
        canvas.clipRect(x, y, x + w, y + h)

        paint.textSize = dp(12.5f)
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT

        for (i in parts.indices) {
            // 糊代(0)と上/下フラップ(4,5)は薄く。
            paint.color = if (i == 0 || i >= 4) {
                Color.rgb(148, 163, 184)
            } else {
                Color.rgb(30, 41, 59)
            }
            val part = parts[i]
            canvas.drawText(part, tx, baseline, paint)
            tx += paint.measureText(part)

            if (i < parts.lastIndex) {
                paint.color = Color.rgb(100, 116, 139)
                canvas.drawText("-", tx, baseline, paint)
                tx += paint.measureText("-")
            }
        }

        canvas.restore()
    }

'''
if marker not in s:
    raise SystemExit("LedgerTableView drawTextCell marker not found")
s = s.replace(marker, addition + marker, 1)
p.write_text(s)

# 詳細画面の名称もユーザーの呼び方に統一。
p = root / "MainActivity.kt"
s = p.read_text()
s = s.replace('addDetail(body, "のりしろ", dimensionValue(product.glueMargin))',
              'addDetail(body, "糊代", dimensionValue(product.glueMargin))', 1)
p.write_text(s)

print("MiniCoPaTis v0.5.3 dimension notation patch applied")
