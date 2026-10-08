from pathlib import Path
import re
root=Path('ledgerapp/app/src/main/java/jp/co/ichika/salesledger')

# Models
p=root/'Models.kt'; s=p.read_text()
old='''    val tiers: List<LotTier>,
    val partCount: Int
)'''
new='''    val tiers: List<LotTier>,
    val partCount: Int,
    val category: String = "",
    val purchasePrice: Double = 0.0,
    val purchaseTiers: List<LotTier> = emptyList()
)'''
assert old in s
s=s.replace(old,new,1); p.write_text(s)

# CsvImporter
p=root/'CsvImporter.kt'; s=p.read_text()
old='''        val materialIdx = optional("材質名")
        val innerLIdx = optional("内寸長さ")'''
new='''        val materialIdx = optional("材質名")
        val categoryIdx = optional("商品区分名")
        val innerLIdx = optional("内寸長さ")'''
assert old in s; s=s.replace(old,new,1)
old='''        val partPriceIdx = optional("売単価_現_標準単価")
        val fallbackPartPriceIdx = optional("売単価_付属")
        val lastDateIdx = optional("最終売上日")'''
new='''        val partPriceIdx = optional("売単価_現_標準単価")
        val fallbackPartPriceIdx = optional("売単価_付属")
        val purchasePriceIdx = optional("仕入単価_現_標準単価")
        val fallbackPurchasePriceIdx = optional("仕入単価")
        val lastDateIdx = optional("最終売上日")'''
assert old in s; s=s.replace(old,new,1)
old='''        val tierQtyIdx = (1..9).map { optional("売単価_現_格差枚数$it") }
        val tierDeltaIdx = (1..9).map { optional("売単価_現_格差金額$it") }
'''
new='''        val tierQtyIdx = (1..9).map { optional("売単価_現_格差枚数$it") }
        val tierDeltaIdx = (1..9).map { optional("売単価_現_格差金額$it") }
        val purchaseTierQtyIdx = (1..9).map { optional("仕入単価_現_格差枚数$it") }
        val purchaseTierDeltaIdx = (1..9).map { optional("仕入単価_現_格差金額$it") }
'''
assert old in s; s=s.replace(old,new,1)
old='''            val partPriceRaw = number(partPriceIdx)
            val partPrice = if (partPriceRaw != 0.0) partPriceRaw else number(fallbackPartPriceIdx)
            val tiers = mutableListOf<TierAdjustment>()'''
new='''            val partPriceRaw = number(partPriceIdx)
            val partPrice = if (partPriceRaw != 0.0) partPriceRaw else number(fallbackPartPriceIdx)
            val purchasePriceRaw = number(purchasePriceIdx)
            val purchasePrice = if (purchasePriceRaw != 0.0) purchasePriceRaw else number(fallbackPurchasePriceIdx)
            val tiers = mutableListOf<TierAdjustment>()'''
assert old in s; s=s.replace(old,new,1)
old='''            tiers.sortBy { it.quantity }

            val deleted = integer(deleteFlagIdx) != 0 || cell(deleteTextIdx).isNotBlank()'''
new='''            tiers.sortBy { it.quantity }
            val purchaseTiers = mutableListOf<TierAdjustment>()
            for (i in 0 until 9) {
                val qty = integer(purchaseTierQtyIdx[i])
                if (qty <= 0) continue
                purchaseTiers += TierAdjustment(qty, number(purchaseTierDeltaIdx[i]))
            }
            purchaseTiers.sortBy { it.quantity }

            val deleted = integer(deleteFlagIdx) != 0 || cell(deleteTextIdx).isNotBlank()'''
assert old in s; s=s.replace(old,new,1)
old='''                partName = cell(partNameIdx).ifBlank { productName },
                material = cell(materialIdx),
                dimension = dimension,'''
new='''                partName = cell(partNameIdx).ifBlank { productName },
                material = cell(materialIdx),
                category = cell(categoryIdx),
                dimension = dimension,'''
assert old in s; s=s.replace(old,new,1)
old='''                unitPrice = partPrice,
                ratio = 1.0,
                rawSqm = number(rawSqmIdx),'''
new='''                unitPrice = partPrice,
                purchaseUnitPrice = purchasePrice,
                ratio = 1.0,
                rawSqm = number(rawSqmIdx),'''
assert old in s; s=s.replace(old,new,1)
old='''                deleted = deleted,
                tierAdjustments = tiers
            )'''
new='''                deleted = deleted,
                tierAdjustments = tiers,
                purchaseTierAdjustments = purchaseTiers
            )'''
assert old in s; s=s.replace(old,new,1)
old='''        val partName: String,
        val material: String,
        val dimension: String,'''
new='''        val partName: String,
        val material: String,
        val category: String,
        val dimension: String,'''
assert old in s; s=s.replace(old,new,1)
old='''        val sqm: Double,
        val unitPrice: Double,
        val ratio: Double,'''
new='''        val sqm: Double,
        val unitPrice: Double,
        val purchaseUnitPrice: Double,
        val ratio: Double,'''
assert old in s; s=s.replace(old,new,1)
old='''        val lastDeliveryDate: String,
        val deleted: Boolean,
        val tierAdjustments: List<TierAdjustment>
    ) {'''
new='''        val lastDeliveryDate: String,
        val deleted: Boolean,
        val tierAdjustments: List<TierAdjustment>,
        val purchaseTierAdjustments: List<TierAdjustment>
    ) {'''
assert old in s; s=s.replace(old,new,1)
# generalized adjustment helper for both sell/purchase lists
old='''        fun adjustmentAt(quantity: Int): Double {
            if (tierAdjustments.isEmpty()) return 0.0
            for (tier in tierAdjustments) {
                if (quantity <= tier.quantity) return tier.delta
            }
            return tierAdjustments.last().delta
        }
'''
new='''        private fun adjustmentAt(quantity: Int, adjustments: List<TierAdjustment>): Double {
            if (adjustments.isEmpty()) return 0.0
            for (tier in adjustments) {
                if (quantity <= tier.quantity) return tier.delta
            }
            return adjustments.last().delta
        }

        fun sellAdjustmentAt(quantity: Int): Double = adjustmentAt(quantity, tierAdjustments)
        fun purchaseAdjustmentAt(quantity: Int): Double = adjustmentAt(quantity, purchaseTierAdjustments)
'''
assert old in s; s=s.replace(old,new,1)
old='''            val summedPrice = parts.sumOf { it.unitPrice * it.ratio }
            val price = if (declaredProductPrice != 0.0) declaredProductPrice else summedPrice
            val sqm = parts.sumOf { it.sqm * it.ratio }'''
new='''            val summedPrice = parts.sumOf { it.unitPrice * it.ratio }
            val price = if (declaredProductPrice != 0.0) declaredProductPrice else summedPrice
            val purchasePrice = parts.sumOf { it.purchaseUnitPrice * it.ratio }
            val sqm = parts.sumOf { it.sqm * it.ratio }'''
assert old in s; s=s.replace(old,new,1)
old='''            val material = summarize(parts.map { it.material })
            val dimension = summarize(parts.map { it.dimension })'''
new='''            val material = summarize(parts.map { it.material })
            val category = summarize(parts.map { it.category }, "複数")
            val dimension = summarize(parts.map { it.dimension })'''
assert old in s; s=s.replace(old,new,1)
old='''            val breakpoints = parts.flatMap { part -> part.tierAdjustments.map { it.quantity } }.distinct().sorted()
            val tiers = breakpoints.map { quantity ->
                val adjustment = parts.sumOf { it.adjustmentAt(quantity) * it.ratio }
                LotTier(quantity, price + adjustment)
            }
            val meaningfulTiers = if (tiers.any { abs(it.price - price) > 0.000001 }) tiers else emptyList()

            val purchaseBreakpoints = parts.flatMap { part -> part.purchaseTierAdjustments.map { it.quantity } }.distinct().sorted()
            val purchaseTiers = purchaseBreakpoints.map { quantity ->
                val adjustment = parts.sumOf { it.purchaseAdjustmentAt(quantity) * it.ratio }
                LotTier(quantity, purchasePrice + adjustment)
            }
            val meaningfulPurchaseTiers = if (purchaseTiers.any { abs(it.price - purchasePrice) > 0.000001 }) purchaseTiers else emptyList()
'''
assert old in s; s=s.replace(old,new,1)
old='''                tiers = meaningfulTiers,
                partCount = parts.size
            )'''
new='''                tiers = meaningfulTiers,
                partCount = parts.size,
                category = category,
                purchasePrice = purchasePrice,
                purchaseTiers = meaningfulPurchaseTiers
            )'''
assert old in s; s=s.replace(old,new,1)
p.write_text(s)

# DataRepository
p=root/'DataRepository.kt'; s=p.read_text()
s=s.replace('SQLiteOpenHolper(context, "sales_ledger.db", null, 4)', 'SQLiteOpenHolper(context, "sales_ledger.db", null, 5)',1)
old='''        if (oldVersion < 4) {
            val additions = listOf(
                "ALTER TABLE components ADD COLUMN raw_sqm REAL NOT NULL DEFAULT 0",
                "ALTER TABLE components ADD COLUMN cost_ratio REAL NOT NULL DEFAULT 1",
                "ALTER TABLE sales_daily ADD COLUMN purchase_amount REAL NOT NULL DEFAULT 0",
                "ALTER TABLE sales_daily ADD COLUMN purchase_quantity REAL NOT NULL DEFAULT 0"
            )
            for (sql in additions) runCatching { db.execSQL(sql) }
            createGrossProfitTables(db)
        }
'''
new=old+'''        if (oldVersion < 5) {
            val additions = listOf(
                "ALTER TABLE products ADD COLUMN category TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE products ADD COLUMN purchase_price REAL NOT NULL DEFAULT 0",
                "ALTER TABLE products ADD COLUMN purchase_tiers TEXT NOT NULL DEFAULT ''
            )
            for (sql in additions) runCatching { db.execSQL(sql) }
        }
'''
assert old in s; s=s.replace(old,new,1)
old='''                tiers TEXT NOT NULL,
                part_count INTEGER NOT NULL
            )'''
new='''                tiers TEXT NOT NULL,
                part_count INTEGER NOT NULL,
                category TEXT NOT NULL DEFAULT '',
                purchase_price REAL NOT NULL DEFAULT 0,
                purchase_tiers TEXT NOT NULL DEFAULT ''
            )'''
assert old in s; s=s.replace(old,new,1)
old='''                    put("tiers", encodeTiers(p.tiers))
                    put("part_count", p.partCount)
                }'''
new='''                    put("tiers", encodeTiers(p.tiers))
                    put("part_count", p.partCount)
                    put("category", p.category)
                    put("purchase_price", p.purchasePrice)
                    put("purchase_tiers", encodeTiers(p.purchaseTiers))
                }'''
assert old in s; s=s.replace(old,new,1)
old='''                   sqm, price, sqm_price, process, last_date, deleted, tiers, part_count
            FROM products'''
new='''                   sqm, price, sqm_price, process, last_date, deleted, tiers, part_count,
                   category, purchase_price, purchase_tiers
            FROM products'''
assert old in s; s=s.replace(old,new,1)
old='''            tiers = decodeTiers(c.getString(19)),
            partCount = c.getInt(20)
        )'''
new='''            tiers = decodeTiers(c.getString(19)),
            partCount = c.getInt(20),
            category = c.getString(21).orEmpty(),
            purchasePrice = c.getDouble(22),
            purchaseTiers = decodeTiers(c.getString(23))
        )'''
assert old in s; s=s.replace(old,new,1)
p.write_text(s)

# LedgerTableView
p=root/'LedgerTableView.kt'; s=p.read_text()
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
                metric("仕入値", if (p.purchasePrice != 0.0) "¥" + number.format(p.purchasePrice) else "—", Color.rgb(181,107,19))
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
assert old in s; s=s.replace(old,new,1)
old='''    private fun processRatePerSqm(p: ProductSummary): Double? {
        if (p.sqmPrice <= 0.000001) return null
        val materialRate = quoteMaterialRate(p.material) ?: return null
        return p.sqmPrice - materialRate
    }
'''
new=old+'''\n    private fun processFeePerPiece(p: ProductSummary): Double? {
        val rate = processRatePerSqm(p) ?: return null
        if (p.sqm <= 0.000001) return null
        return rate * p.sqm
    }
'''
assert old in s; s=s.replace(old,new,1)
p.write_text(s)

# MainActivity detail
p=root/'MainActivity.kt'; s=p.read_text()
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
assert old in s; s=s.replace(old,new,1)
# add purchase tiers after sell tier section and before components
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
assert anchor in s; s=s.replace(anchor,block+anchor,1)
p.write_text(s)

# Version
build=Path("ledgerapp/app/build.gradle")
b=build.read_text()
b,c1=re.subn(r"versionCode\s+\d+","versionCode 45",b,count=1)
b,c2=re.subn(r"versionName\s+'[^']+'","versionName '1.9.9'",b,count=1)
if c1!=1 or c2!=1:
    raise SystemExit("version declarations missing")
build.write_text(b)

print("MiniCoPaTis v1.9.9 product purchase price + processing fee applied")
