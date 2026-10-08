from pathlib import Path
import re

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

def patch(name, old, new, count=1):
    p = root / name
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"{name}: anchor missing: {old[:80]!r}")
    s = s.replace(old, new, count)
    p.write_text(s)

# Product model: keep sell tiers, add category + current purchase price/tiers.
patch("Models.kt",
'''    val tiers: List<LotTier>,
    val partCount: Int
)''',
'''    val tiers: List<LotTier>,
    val partCount: Int,
    val category: String = "",
    val purchasePrice: Double = 0.0,
    val purchaseTiers: List<LotTier> = emptyList()
)''')

# CSV columns.
patch("CsvImporter.kt",
'''        val materialIdx = optional("材質名")
        val innerLIdx = optional("内寸長さ")''',
'''        val materialIdx = optional("材質名")
        val categoryIdx = optional("商品区分名")
        val innerLIdx = optional("内寸長さ")''')

patch("CsvImporter.kt",
'''        val partPriceIdx = optional("売単価_現_標準単価")
        val fallbackPartPriceIdx = optional("売単価_付属")
        val lastDateIdx = optional("最終売上日")''',
'''        val partPriceIdx = optional("売単価_現_標準単価")
        val fallbackPartPriceIdx = optional("売単価_付属")
        val purchasePriceIdx = optional("仕入単価_現_標準単価")
        val fallbackPurchasePriceIdx = optional("仕入単価")
        val lastDateIdx = optional("最終売上日")''')

patch("CsvImporter.kt",
'''        val tierQtyIdx = (1..9).map { optional("売単価_現_格差枚数$it") }
        val tierDeltaIdx = (1..9).map { optional("売単価_現_格差金額$it") }''',
'''        val tierQtyIdx = (1..9).map { optional("売単価_現_格差枚数$it") }
        val tierDeltaIdx = (1..9).map { optional("売単価_現_格差金額$it") }
        val purchaseTierQtyIdx = (1..9).map { optional("仕入単価_現_格差枚数$it") }
        val purchaseTierDeltaIdx = (1..9).map { optional("仕入単価_現_格差金額$it") }''')

patch("CsvImporter.kt",
'''            val partPriceRaw = number(partPriceIdx)
            val partPrice = if (partPriceRaw != 0.0) partPriceRaw else number(fallbackPartPriceIdx)
            val tiers = mutableListOf<TierAdjustment>()''',
'''            val partPriceRaw = number(partPriceIdx)
            val partPrice = if (partPriceRaw != 0.0) partPriceRaw else number(fallbackPartPriceIdx)
            val purchaseRaw = number(purchasePriceIdx)
            val purchasePrice = if (purchaseRaw != 0.0) purchaseRaw else number(fallbackPurchasePriceIdx)
            val tiers = mutableListOf<TierAdjustment>()''')

patch("CsvImporter.kt",
'''            tiers.sortBy { it.quantity }

            val deleted = integer(deleteFlagIdx) != 0 || cell(deleteTextIdx).isNotBlank()''',
'''            tiers.sortBy { it.quantity }
            val purchaseTiers = mutableListOf<TierAdjustment>()
            for (i in 0 until 9) {
                val qty = integer(purchaseTierQtyIdx[i])
                if (qty <= 0) continue
                purchaseTiers += TierAdjustment(qty, number(purchaseTierDeltaIdx[i]))
            }
            purchaseTiers.sortBy { it.quantity }

            val deleted = integer(deleteFlagIdx) != 0 || cell(deleteTextIdx).isNotBlank()''')

patch("CsvImporter.kt",
'''                partName = cell(partNameIdx).ifBlank { productName },
                material = cell(materialIdx),
                dimension = dimension,''',
'''                partName = cell(partNameIdx).ifBlank { productName },
                material = cell(materialIdx),
                category = cell(categoryIdx),
                dimension = dimension,''')

patch("CsvImporter.kt",
'''                unitPrice = partPrice,
                ratio = 1.0,''',
'''                unitPrice = partPrice,
                purchaseUnitPrice = purchasePrice,
                ratio = 1.0,''')

patch("CsvImporter.kt",
'''                deleted = deleted,
                tierAdjustments = tiers
            )''',
'''                deleted = deleted,
                tierAdjustments = tiers,
                purchaseTierAdjustments = purchaseTiers
            )''')

# RawComponent fields.
patch("CsvImporter.kt",
'''        val partName: String,
        val material: String,
        val dimension: String,''',
'''        val partName: String,
        val material: String,
        val category: String,
        val dimension: String,''')

patch("CsvImporter.kt",
'''        val sqm: Double,
        val unitPrice: Double,
        val ratio: Double,''',
'''        val sqm: Double,
        val unitPrice: Double,
        val purchaseUnitPrice: Double,
        val ratio: Double,''')

patch("CsvImporter.kt",
'''        val deleted: Boolean,
        val tierAdjustments: List<TierAdjustment>
    ) {''',
'''        val deleted: Boolean,
        val tierAdjustments: List<TierAdjustment>,
        val purchaseTierAdjustments: List<TierAdjustment>
    ) {''')

patch("CsvImporter.kt",
'''        fun adjustmentAt(quantity: Int): Double {
            if (tierAdjustments.isEmpty()) return 0.0
            for (tier in tierAdjustments) {
                if (quantity <= tier.quantity) return tier.delta else break
            }
            return tierAdjustments.last().delta
        }''',
'''        fun adjustmentAt(quantity: Int): Double {
            if (tierAdjustments.isEmpty()) return 0.0
            for (tier in tierAdjustments) {
                if (quantity <= tier.quantity) return tier.delta
            }
            return tierAdjustments.last().delta
        }

        fun purchaseAdjustmentAt(quantity: Int): Double {
            if (purchaseTierAdjustments.isEmpty()) return 0.0
            for (tier in purchaseTierAdjustments) {
                if (quantity <= tier.quantity) return tier.delta
            }
            return purchaseTierAdjustments.last().delta
        }''')

# The current source already has the corrected sell adjustment loop; if the older
# exact block above was not present, patch only by inserting purchase helper.
p = root / "CsvImporter.kt"
s = p.read_text()
if "fun purchaseAdjustmentAt(quantity: Int)" not in s:
    anchor = '''        fun adjustmentAt(quantity: Int): Double {
            if (tierAdjustments.isEmpty()) return 0.0
            for (tier in tierAdjustments) {
                if (quantity <= tier.quantity) return tier.delta
            }
            return tierAdjustments.last().delta
        }
'''
    if anchor not in s:
        raise SystemExit("CsvImporter.kt: adjustment helper anchor missing")
    s = s.replace(anchor, anchor + '''
        fun purchaseAdjustmentAt(quantity: Int): Double {
            if (purchaseTierAdjustments.isEmpty()) return 0.0
            for (tier in purchaseTierAdjustments) {
                if (quantity <= tier.quantity) return tier.delta
            }
            return purchaseTierAdjustments.last().delta
        }
''', 1)
    p.write_text(s)

# ProductBuilder.
patch("CsvImporter.kt",
'''            val summedPrice = parts.sumOf { it.unitPrice * it.ratio }
            val price = if (declaredProductPrice != 0.0) declaredProductPrice else summedPrice
            val sqm = parts.sumOf { it.sqm * it.ratio }''',
'''            val summedPrice = parts.sumOf { it.unitPrice * it.ratio }
            val price = if (declaredProductPrice != 0.0) declaredProductPrice else summedPrice
            val purchasePrice = parts.sumOf { it.purchaseUnitPrice * it.ratio }
            val sqm = parts.sumOf { it.sqm * it.ratio }''')

patch("CsvImporter.kt",
'''            val material = summarize(parts.map { it.material })
            val dimension = summarize(parts.map { it.dimension })''',
'''            val material = summarize(parts.map { it.material })
            val category = summarize(parts.map { it.category })
            val dimension = summarize(parts.map { it.dimension })''')

patch("CsvImporter.kt",
'''            val meaningfulTiers = if (tiers.any { abs(it.price - price) > 0.000001 }) tiers else emptyList()

            val product = ProductSummary(''',
'''            val meaningfulTiers = if (tiers.any { abs(it.price - price) > 0.000001 }) tiers else emptyList()

            val purchaseBreakpoints = parts.flatMap { part -> part.purchaseTierAdjustments.map { it.quantity } }.distinct().sorted()
            val purchaseTiers = purchaseBreakpoints.map { quantity ->
                val adjustment = parts.sumOf { it.purchaseAdjustmentAt(quantity) * it.ratio }
                LotTier(quantity, purchasePrice + adjustment)
            }
            val meaningfulPurchaseTiers = if (purchaseTiers.any { abs(it.price - purchasePrice) > 0.000001 }) purchaseTiers else emptyList()

            val product = ProductSummary(''')

patch("CsvImporter.kt",
'''                tiers = meaningfulTiers,
                partCount = parts.size
            )''',
'''                tiers = meaningfulTiers,
                partCount = parts.size,
                category = category,
                purchasePrice = purchasePrice,
                purchaseTiers = meaningfulPurchaseTiers
            )''')

# Database v5.
p = root / "DataRepository.kt"
s = p.read_text()
s, n = re.subn(r'SQLiteOpenHelper\(context, "sales_ledger\.db", null, 4\)',
               'SQLiteOpenHelper(context, "sales_ledger.db", null, 5)', s, count=1)
if n != 1: raise SystemExit("DataRepository.kt: db version anchor missing")

anchor = '''        if (oldVersion < 4) {
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
if anchor not in s: raise SystemExit("DataRepository.kt: v4 upgrade anchor missing")
s = s.replace(anchor, anchor + '''        if (oldVersion < 5) {
            val additions = listOf(
                "ALTER TABLE products ADD COLUMN category TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE products ADD COLUMN purchase_price REAL NOT NULL DEFAULT 0",
                "ALTER TABLE products ADD COLUMN purchase_tiers TEXT NOT NULL DEFAULT ''"
            )
            for (sql in additions) runCatching { db.execSQL(sql) }
        }
''', 1)

old = '''                tiers TEXT NOT NULL,
                part_count INTEGER NOT NULL
            )'''
new = '''                tiers TEXT NOT NULL,
                part_count INTEGER NOT NULL,
                category TEXT NOT NULL DEFAULT '',
                purchase_price REAL NOT NULL DEFAULT 0,
                purchase_tiers TEXT NOT NULL DEFAULT ''
            )'''
if old not in s: raise SystemExit("DataRepository.kt: schema anchor missing")
s = s.replace(old,new,1)

old = '''                    put("tiers", encodeTiers(p.tiers))
                    put("part_count", p.partCount)
                }'''
new = '''                    put("tiers", encodeTiers(p.tiers))
                    put("part_count", p.partCount)
                    put("category", p.category)
                    put("purchase_price", p.purchasePrice)
                    put("purchase_tiers", encodeTiers(p.purchaseTiers))
                }'''
if old not in s: raise SystemExit("DataRepository.kt: insert anchor missing")
s = s.replace(old,new,1)

old = '''                   sqm, price, sqm_price, process, last_date, deleted, tiers, part_count
            FROM products'''
new = '''                   sqm, price, sqm_price, process, last_date, deleted, tiers, part_count,
                   category, purchase_price, purchase_tiers
            FROM products'''
if old not in s: raise SystemExit("DataRepository.kt: select anchor missing")
s = s.replace(old,new,1)

old = '''            tiers = decodeTiers(c.getString(19)),
            partCount = c.getInt(20)
        )'''
new = '''            tiers = decodeTiers(c.getString(19)),
            partCount = c.getInt(20),
            category = c.getString(21).orEmpty(),
            purchasePrice = c.getDouble(22),
            purchaseTiers = decodeTiers(c.getString(23))
        )'''
if old not in s: raise SystemExit("DataRepository.kt: cursor anchor missing")
s = s.replace(old,new,1)
p.write_text(s)

# Version.
build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, c1 = re.subn(r"versionCode\s+\d+", "versionCode 45", b, count=1)
b, c2 = re.subn(r"versionName\s+'[^']+'", "versionName '1.9.9'", b, count=1)
if c1 != 1 or c2 != 1: raise SystemExit("build.gradle version anchor missing")
build.write_text(b)

print("v1.9.9 ledger purchase data patch applied")
