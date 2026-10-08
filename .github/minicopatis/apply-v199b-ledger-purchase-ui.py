from pathlib import Path

root=Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

# Compact ledger cards.
p=root/"LedgerTableView.kt"
s=p.read_text()
old='''        val processRate = processRatePerSqm(p)
        val metricsBottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(8)) }
        metricsBottom.addView(
            metric("平米単価", if (p.sqmPrice > 0.000001) "¥" + number.format(p.sqmPrice) else "—", Color.rgb(15,105,76)),
            LinearLayout.LayoutParams(0,-2,1f)
        )
        metricsBottom.addView(
            metric(
                "平米加工賃",
                processRate?.let { "¥" + number.format(it) } ?: "—",
                if (processRate != null && processRate < 0.0) Color.rgb(190,58,58) else Color.rgb(181,107,19)
            ),
            LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) }
        )
        card.addView(metricsBottom)
'''
new='''        val processRate = processRatePerSqm(p)
        val processPerPiece = processFeePerPiece(p)
        val isGoods = p.category == "商品"
        val metricsBottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(8)) }
        metricsBottom.addView(
            metric("平米単価", if (p.sqmPrice > 0.000001) "¥" + number.format(p.sqmPrice) else "—", Color.rgb(15,105,76)),
            LinearLayout.LayoutParams(0,-2,1f)
        )
        metricsBottom.addView(
            if (isGoods) {
                metric(
                    "仕入値",
                    if (p.purchasePrice != 0.0) "¥" + number.format(p.purchasePrice) else "—",
                    Color.rgb(181,107,19)
                )
            } else {
                metric(
                    "平米加工賃",
                    processRate?.let { "¥" + number.format(it) } ?: "—",
                    if (processRate != null && processRate < 0.0) Color.rgb(190,58,58) else Color.rgb(181,107,19)
                )
            },
            LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) }
        )
        if (!isGoods) {
            metricsBottom.addView(
                metric(
                    "加工賃/枚",
                    processPerPiece?.let { "¥" + number.format(it) } ?: "—",
                    if (processPerPiece != null && processPerPiece < 0.0) Color.rgb(190,58,58) else Color.rgb(181,107,19)
                ),
                LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) }
            )
        }
        card.addView(metricsBottom)
'''
if old not in s: raise SystemExit("LedgerTableView.kt: metrics anchor missing")
s=s.replace(old,new,1)

anchor='''    private fun processRatePerSqm(p: ProductSummary): Double? {
        if (p.sqmPrice <= 0.000001) return null
        val materialRate = quoteMaterialRate(p.material) ?: return null
        return p.sqmPrice - materialRate
    }
'''
if anchor not in s: raise SystemExit("LedgerTableView.kt: process helper anchor missing")
s=s.replace(anchor,anchor+'''
    private fun processFeePerPiece(p: ProductSummary): Double? {
        val rate = processRatePerSqm(p) ?: return null
        if (p.sqm <= 0.000001) return null
        return rate * p.sqm
    }
''',1)
p.write_text(s)

# Product detail.
p=root/"MainActivity.kt"
s=p.read_text()
old='''        addDetail(body, "商品売価", if (product.price != 0.0) "¥${money.format(product.price)}" else "-")
        addDetail(body, "平米売価", if (product.sqmPrice > 0.000001) "¥${money.format(product.sqmPrice)} /㎡" else "-")
        addDetail(body, "工程", product.process)
'''
new='''        addDetail(body, "商品売価", if (product.price != 0.0) "¥${money.format(product.price)}" else "-")
        addDetail(body, "平米売価", if (product.sqmPrice > 0.000001) "¥${money.format(product.sqmPrice)} /㎡" else "-")
        if (product.category == "商品") {
            addDetail(body, "仕入値", if (product.purchasePrice != 0.0) "¥${money.format(product.purchasePrice)}" else "-")
        } else {
            val materialRate = GrossProfitUtil.materialRate(product.material)
            val processRate = if (materialRate != null && product.sqmPrice > 0.000001) product.sqmPrice - materialRate else null
            val processPerPiece = if (processRate != null && product.sqm > 0.000001) processRate * product.sqm else null
            addDetail(body, "平米加工賃", processRate?.let { "¥${money.format(it)} /㎡" } ?: "-")
            addDetail(body, "加工賃/枚", processPerPiece?.let { "¥${money.format(it)}" } ?: "-")
        }
        addDetail(body, "工程", product.process)
'''
if old not in s: raise SystemExit("MainActivity.kt: detail pricing anchor missing")
s=s.replace(old,new,1)

anchor='''        val components = repository.getComponents(product.key)
'''
block='''        if (product.category == "商品") {
            body.addView(sectionTitle("仕入ロット格差").apply { setPadding(0, dp(16), 0, dp(8)) })
            if (product.purchaseTiers.isEmpty()) {
                body.addView(text("なし", 14f, Color.rgb(71, 85, 105), Typeface.NORMAL))
            } else {
                for ((index, tier) in product.purchaseTiers.withIndex()) {
                    val previousUpper = if (index > 0) product.purchaseTiers[index - 1].quantity else 0
                    val rangeLabel = when {
                        product.purchaseTiers.size == 1 -> "～${number.format(tier.quantity)}枚"
                        index == 0 -> "～${number.format(tier.quantity)}枚"
                        index == product.purchaseTiers.lastIndex -> "${number.format(previousUpper + 1)}枚～"
                        else -> "${number.format(previousUpper + 1)}～${number.format(tier.quantity)}枚"
                    }
                    body.addView(text("$rangeLabel　¥${money.format(tier.price)}", 15f, Color.rgb(181, 107, 19), Typeface.BOLD).apply {
                        setPadding(dp(8), dp(7), dp(8), dp(7))
                    })
                }
            }
        }

'''
if anchor not in s: raise SystemExit("MainActivity.kt: components anchor missing")
s=s.replace(anchor,block+anchor,1)
p.write_text(s)

print("v1.9.9 ledger purchase-price UI patch applied")
