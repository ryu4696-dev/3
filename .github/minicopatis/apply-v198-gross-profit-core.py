from pathlib import Path
import re

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

# Models
p = root / "Models.kt"
s = p.read_text()

old = """    val sqm: Double,
    val unitPrice: Double,
    val ratio: Double,
    val process: String,
"""
new = """    val sqm: Double,
    val unitPrice: Double,
    val ratio: Double,
    val rawSqm: Double = 0.0,
    val costRatio: Double = 1.0,
    val process: String,
"""
if old not in s: raise SystemExit("ComponentInfo anchor missing")
s = s.replace(old, new, 1)

old = """data class SalesDailyRecord(
    val customerNumber: String,
    val productNumber: String,
    val saleDate: String,
    val amount: Double,
    val quantity: Double,
    val sqm: Double
)"""
new = """data class SalesDailyRecord(
    val customerNumber: String,
    val productNumber: String,
    val saleDate: String,
    val amount: Double,
    val quantity: Double,
    val sqm: Double,
    val purchaseAmount: Double = 0.0,
    val purchaseQuantity: Double = 0.0
)"""
if old not in s: raise SystemExit("SalesDailyRecord anchor missing")
s = s.replace(old, new, 1)

old = """data class SalesMetrics(
    val amount: Double = 0.0,
    val quantity: Double = 0.0,
    val sqm: Double = 0.0
)"""
new = """data class SalesMetrics(
    val amount: Double = 0.0,
    val quantity: Double = 0.0,
    val sqm: Double = 0.0,
    val cost: Double = 0.0,
    val costKnown: Boolean = true
) {
    val grossProfit: Double get() = amount - cost
    val grossRate: Double? get() = if (costKnown && kotlin.math.abs(amount) > 0.000001) grossProfit / amount else null
}"""
if old not in s: raise SystemExit("SalesMetrics anchor missing")
s = s.replace(old, new, 1)

marker = "data class SalesStats(\n"
addition = """data class OrderCostEstimate(
    val customerNumber: String,
    val productNumber: String,
    val cost: Double?,
    val source: String = ""
)

"""
if addition not in s:
    if marker not in s: raise SystemExit("SalesStats anchor missing")
    s = s.replace(marker, addition + marker, 1)
p.write_text(s)

# CsvImporter
p = root / "CsvImporter.kt"
s = p.read_text()
old = """        val sqmIdx = optional("売上平米")
        val productPriceIdx = optional("単価_商品")
"""
new = """        val sqmIdx = optional("売上平米")
        val rawSqmIdx = optional("板取平米")
        val productPriceIdx = optional("単価_商品")
"""
if old not in s: raise SystemExit("raw sqm index anchor missing")
s = s.replace(old, new, 1)

old = """                sqm = number(sqmIdx),
                unitPrice = partPrice,
                ratio = 1.0,
                process = process,
"""
new = """                sqm = number(sqmIdx),
                unitPrice = partPrice,
                ratio = 1.0,
                rawSqm = number(rawSqmIdx),
                costRatio = ratio,
                process = process,
"""
if old not in s: raise SystemExit("RawComponent constructor anchor missing")
s = s.replace(old, new, 1)

old = """        val sqm: Double,
        val unitPrice: Double,
        val ratio: Double,
        val process: String,
"""
new = """        val sqm: Double,
        val unitPrice: Double,
        val ratio: Double,
        val rawSqm: Double,
        val costRatio: Double,
        val process: String,
"""
if old not in s: raise SystemExit("RawComponent model anchor missing")
s = s.replace(old, new, 1)

old = """                    sqm = it.sqm,
                    unitPrice = it.unitPrice,
                    ratio = it.ratio,
                    process = it.process.ifBlank { "-" },
"""
new = """                    sqm = it.sqm,
                    unitPrice = it.unitPrice,
                    ratio = it.ratio,
                    rawSqm = it.rawSqm,
                    costRatio = it.costRatio,
                    process = it.process.ifBlank { "-" },
"""
if old not in s: raise SystemExit("ComponentInfo build anchor missing")
s = s.replace(old, new, 1)
p.write_text(s)

# SalesCsvImporter
p = root / "SalesCsvImporter.kt"
s = p.read_text()
old = '        fun required(name: String): Int = index[name] ?: throw MissingColumnsException("必要な列「$name」がありません")\n'
new = old + '        fun optional(name: String): Int = index[name] ?: -1\n'
if old not in s: raise SystemExit("sales optional anchor missing")
s = s.replace(old, new, 1)

old = """        val amountIdx = required("売上金額")
        val categoryIdx = index["商品区分名"] ?: index["商品区分"] ?: -1
"""
new = """        val amountIdx = required("売上金額")
        val purchaseUnitIdx = optional("売上日基準商品仕入単価")
        val purchaseAmountIdx = optional("売上数量分商品仕入金額")
        val categoryIdx = index["商品区分名"] ?: index["商品区分"] ?: -1
"""
if old not in s: raise SystemExit("purchase column anchor missing")
s = s.replace(old, new, 1)

old = """            fun cell(i: Int): String = row[i].trim()
            fun number(i: Int): Double = cell(i).replace(",", "").toDoubleOrNull() ?: 0.0
"""
new = """            fun cell(i: Int): String = if (i >= 0 && i < row.size) row[i].trim() else ""
            fun number(i: Int): Double = cell(i).replace(",", "").toDoubleOrNull() ?: 0.0
"""
if old not in s: raise SystemExit("sales cell anchor missing")
s = s.replace(old, new, 1)

old = """            accumulator.amount += number(amountIdx)
            accumulator.quantity += number(quantityIdx)
            accumulator.sqm += number(sqmIdx)
"""
new = """            val rowQuantity = number(quantityIdx)
            val purchaseUnit = number(purchaseUnitIdx)
            val purchaseAmountRaw = number(purchaseAmountIdx)
            val effectivePurchaseAmount = when {
                kotlin.math.abs(purchaseAmountRaw) > 0.000001 -> purchaseAmountRaw
                kotlin.math.abs(purchaseUnit) > 0.000001 -> purchaseUnit * rowQuantity
                else -> 0.0
            }
            accumulator.amount += number(amountIdx)
            accumulator.quantity += rowQuantity
            accumulator.sqm += number(sqmIdx)
            if (kotlin.math.abs(effectivePurchaseAmount) > 0.000001 || kotlin.math.abs(purchaseUnit) > 0.000001) {
                accumulator.purchaseAmount += effectivePurchaseAmount
                accumulator.purchaseQuantity += rowQuantity
            }
"""
if old not in s: raise SystemExit("sales accumulator anchor missing")
s = s.replace(old, new, 1)

old = """                amount = it.amount,
                quantity = it.quantity,
                sqm = it.sqm
"""
new = """                amount = it.amount,
                quantity = it.quantity,
                sqm = it.sqm,
                purchaseAmount = it.purchaseAmount,
                purchaseQuantity = it.purchaseQuantity
"""
if old not in s: raise SystemExit("sales record build anchor missing")
s = s.replace(old, new, 1)

old = """        var amount: Double = 0.0,
        var quantity: Double = 0.0,
        var sqm: Double = 0.0
    )"""
new = """        var amount: Double = 0.0,
        var quantity: Double = 0.0,
        var sqm: Double = 0.0,
        var purchaseAmount: Double = 0.0,
        var purchaseQuantity: Double = 0.0
    )"""
if old not in s: raise SystemExit("daily accumulator model anchor missing")
s = s.replace(old, new, 1)
p.write_text(s)

# DataRepository schema and inserts
p = root / "DataRepository.kt"
s = p.read_text()
old = 'class DataRepository(context: Context) : SQLiteOpenHelper(context, "sales_ledger.db", null, 3) {'
if old not in s: raise SystemExit("db version anchor missing")
s = s.replace(old, 'class DataRepository(context: Context) : SQLiteOpenHelper(context, "sales_ledger.db", null, 4) {', 1)

upgrade = """        if (oldVersion < 3) {
            val additions = listOf(
                "ALTER TABLE products ADD COLUMN dim_length REAL NOT NULL DEFAULT 0",
                "ALTER TABLE products ADD COLUMN dim_width REAL NOT NULL DEFAULT 0",
                "ALTER TABLE products ADD COLUMN dim_depth REAL NOT NULL DEFAULT 0",
                "ALTER TABLE products ADD COLUMN glue_margin REAL NOT NULL DEFAULT 0",
                "ALTER TABLE products ADD COLUMN top_flap REAL NOT NULL DEFAULT 0",
                "ALTER TABLE products ADD COLUMN bottom_flap REAL NOT NULL DEFAULT 0"
            )
            for (sql in additions) {
                runCatching { db.execSQL(sql) }
            }
        }
"""
if upgrade not in s: raise SystemExit("upgrade anchor missing")
s = s.replace(upgrade, upgrade + """        if (oldVersion < 4) {
            val additions = listOf(
                "ALTER TABLE components ADD COLUMN raw_sqm REAL NOT NULL DEFAULT 0",
                "ALTER TABLE components ADD COLUMN cost_ratio REAL NOT NULL DEFAULT 1",
                "ALTER TABLE sales_daily ADD COLUMN purchase_amount REAL NOT NULL DEFAULT 0",
                "ALTER TABLE sales_daily ADD COLUMN purchase_quantity REAL NOT NULL DEFAULT 0"
            )
            for (sql in additions) runCatching { db.execSQL(sql) }
            createGrossProfitTables(db)
        }
""", 1)

old = """                sqm REAL NOT NULL,
                unit_price REAL NOT NULL,
                ratio REAL NOT NULL,
                process TEXT NOT NULL,
"""
new = """                sqm REAL NOT NULL,
                unit_price REAL NOT NULL,
                ratio REAL NOT NULL,
                raw_sqm REAL NOT NULL DEFAULT 0,
                cost_ratio REAL NOT NULL DEFAULT 1,
                process TEXT NOT NULL,
"""
if old not in s: raise SystemExit("component schema anchor missing")
s=s.replace(old,new,1)

old = """                amount REAL NOT NULL,
                quantity REAL NOT NULL,
                sqm REAL NOT NULL,
                PRIMARY KEY(customer_no, product_no, sale_date)
"""
new = """                amount REAL NOT NULL,
                quantity REAL NOT NULL,
                sqm REAL NOT NULL,
                purchase_amount REAL NOT NULL DEFAULT 0,
                purchase_quantity REAL NOT NULL DEFAULT 0,
                PRIMARY KEY(customer_no, product_no, sale_date)
"""
if old not in s: raise SystemExit("sales schema anchor missing")
s=s.replace(old,new,1)

old = '''        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_daily_product ON sales_daily(customer_no, product_no)")
    }
'''
new = '''        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_daily_product ON sales_daily(customer_no, product_no)")
        createGrossProfitTables(db)
    }

    private fun createGrossProfitTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS product_costs (
                customer_no TEXT NOT NULL,
                product_no TEXT NOT NULL,
                material_unit_cost REAL NOT NULL,
                complete INTEGER NOT NULL,
                PRIMARY KEY(customer_no, product_no)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_product_costs_product ON product_costs(product_no)")
    }
'''
if old not in s: raise SystemExit("create gross table anchor missing")
s=s.replace(old,new,1)

old = """                    put("ratio", c.ratio)
                    put("process", c.process)
"""
new = """                    put("ratio", c.ratio)
                    put("raw_sqm", c.rawSqm)
                    put("cost_ratio", c.costRatio)
                    put("process", c.process)
"""
if old not in s: raise SystemExit("component insert anchor missing")
s=s.replace(old,new,1)

old = """                db.insertOrThrow("components", null, values)
            }
            db.setTransactionSuccessful()
"""
new = """                db.insertOrThrow("components", null, values)
            }
            rebuildProductCosts(db, data.components)
            db.setTransactionSuccessful()
"""
if old not in s: raise SystemExit("replaceAll tail missing")
s=s.replace(old,new,1)

old = """                    put("quantity", record.quantity)
                    put("sqm", record.sqm)
                })
"""
new = """                    put("quantity", record.quantity)
                    put("sqm", record.sqm)
                    put("purchase_amount", record.purchaseAmount)
                    put("purchase_quantity", record.purchaseQuantity)
                })
"""
if old not in s: raise SystemExit("sales insert anchor missing")
s=s.replace(old,new,1)

# Aggregate queries. The all-company query has an extra sales_customers join,
# so patch the common SELECT and then insert costing joins after sales_products.
old = """                   substr(d.sale_date, 1, 7) AS sale_month,
                   SUM(d.amount), SUM(d.quantity), SUM(d.sqm)
"""
new = """                   substr(d.sale_date, 1, 7) AS sale_month,
                   SUM(d.amount), SUM(d.quantity), SUM(d.sqm),
                   SUM(
                       d.purchase_amount +
                       CASE
                           WHEN (d.quantity - d.purchase_quantity) > 0.000001
                                AND COALESCE(pc.complete, pg.complete, 0) = 1
                           THEN (d.quantity - d.purchase_quantity) * COALESCE(pc.material_unit_cost, pg.material_unit_cost, 0)
                           ELSE 0
                       END
                   ) AS gross_cost,
                   SUM(
                       CASE
                           WHEN (d.quantity - d.purchase_quantity) > 0.000001
                                AND COALESCE(pc.complete, pg.complete, 0) <> 1
                           THEN 1 ELSE 0
                       END
                   ) AS unknown_cost_rows
"""
if s.count(old) < 2: raise SystemExit("aggregate select anchors missing")
s=s.replace(old,new,2)

old_join = """            LEFT JOIN sales_products sp
                   ON sp.customer_no = d.customer_no AND sp.product_no = d.product_no
"""
new_join = """            LEFT JOIN sales_products sp
                   ON sp.customer_no = d.customer_no AND sp.product_no = d.product_no
            LEFT JOIN product_costs pc
                   ON pc.customer_no = d.customer_no AND pc.product_no = d.product_no
            LEFT JOIN product_costs pg
                   ON pg.customer_no = '*' AND pg.product_no = d.product_no
"""
if s.count(old_join) < 2: raise SystemExit("aggregate join anchors missing")
s=s.replace(old_join,new_join,2)

old = """                    amount = c.getDouble(3),
                    quantity = c.getDouble(4),
                    sqm = c.getDouble(5)
"""
new = """                    amount = c.getDouble(3),
                    quantity = c.getDouble(4),
                    sqm = c.getDouble(5),
                    cost = c.getDouble(6),
                    costKnown = c.getInt(7) == 0
"""
if old not in s: raise SystemExit("monthly metrics anchor missing")
s=s.replace(old,new,1)

old = """                    amount = c.getDouble(5),
                    quantity = c.getDouble(6),
                    sqm = c.getDouble(7)
"""
new = """                    amount = c.getDouble(5),
                    quantity = c.getDouble(6),
                    sqm = c.getDouble(7),
                    cost = c.getDouble(8),
                    costKnown = c.getInt(9) == 0
"""
if old not in s: raise SystemExit("all metrics anchor missing")
s=s.replace(old,new,1)

old = """            SELECT product_key, part_no, part_name, material, dimension, sqm,
                   unit_price, ratio, process, last_date
"""
new = """            SELECT product_key, part_no, part_name, material, dimension, sqm,
                   unit_price, ratio, raw_sqm, cost_ratio, process, last_date
"""
if old not in s: raise SystemExit("component select anchor missing")
s=s.replace(old,new,1)

old = """                    unitPrice = c.getDouble(6),
                    ratio = c.getDouble(7),
                    process = c.getString(8),
                    lastDeliveryDate = c.getString(9)
"""
new = """                    unitPrice = c.getDouble(6),
                    ratio = c.getDouble(7),
                    rawSqm = c.getDouble(8),
                    costRatio = c.getDouble(9),
                    process = c.getString(10),
                    lastDeliveryDate = c.getString(11)
"""
if old not in s: raise SystemExit("component cursor anchor missing")
s=s.replace(old,new,1)

anchor = """    private fun scalarInt(db: SQLiteDatabase, sql: String): Int {
"""
helpers = r'''    private fun rebuildProductCosts(db: SQLiteDatabase, components: List<ComponentInfo>) {
        db.delete("product_costs", null, null)
        data class CostPart(val rawSqm: Double, val ratio: Double, val material: String, val signature: String)
        val groups = linkedMapOf<Pair<String,String>, MutableList<CostPart>>()

        components.forEach { c ->
            val sep = c.productKey.indexOf('\u001F')
            if (sep <= 0 || sep >= c.productKey.lastIndex) return@forEach
            val customer = c.productKey.substring(0, sep)
            val displayed = c.productKey.substring(sep + 1)
            val base = displayed.replace(Regex("-\\d+$"), "")
            val signature = listOf(c.partNumber.toString(), c.partName, c.material, c.rawSqm.toString(), c.costRatio.toString()).joinToString("|")
            groups.getOrPut(customer to base) { mutableListOf() }.add(CostPart(c.rawSqm, c.costRatio, c.material, signature))
        }

        val completeByProduct = linkedMapOf<String, MutableList<Pair<String,Double>>>()
        groups.forEach { (key, parts) ->
            var complete = true
            var cost = 0.0
            val seen = mutableSetOf<String>()
            parts.forEach partLoop@ { part ->
                if (!seen.add(part.signature)) return@partLoop
                val rate = GrossProfitUtil.materialRate(part.material)
                if (part.rawSqm <= 0.000001 || rate == null) complete = false
                else cost += part.rawSqm * part.ratio * rate
            }
            db.insertOrThrow("product_costs", null, ContentValues().apply {
                put("customer_no", key.first)
                put("product_no", key.second)
                put("material_unit_cost", cost)
                put("complete", if (complete && cost > 0.000001) 1 else 0)
            })
            if (complete && cost > 0.000001) completeByProduct.getOrPut(key.second) { mutableListOf() }.add(key.first to cost)
        }

        completeByProduct.forEach { (productNo, values) ->
            if (values.size == 1) {
                db.insertWithOnConflict("product_costs", null, ContentValues().apply {
                    put("customer_no", "*")
                    put("product_no", productNo)
                    put("material_unit_cost", values.first().second)
                    put("complete", 1)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    private fun materialUnitCost(customerNo: String, productNo: String): Double? {
        readableDatabase.rawQuery(
            """
            SELECT material_unit_cost,complete
            FROM product_costs
            WHERE (customer_no=? OR customer_no='*') AND product_no=?
            ORDER BY CASE WHEN customer_no=? THEN 0 ELSE 1 END
            LIMIT 1
            """.trimIndent(),
            arrayOf(customerNo, productNo, customerNo)
        ).use { c ->
            if (c.moveToFirst() && c.getInt(1) == 1) return c.getDouble(0)
        }
        return null
    }

    fun estimateOrderCost(customerNumberHint: String, customerName: String, productNumberHint: String, itemName: String, quantity: Double): OrderCostEstimate {
        if (quantity <= 0.000001) return OrderCostEstimate("", "", null)
        val match = resolveOrderProduct(customerNumberHint, customerName, productNumberHint, itemName)
            ?: return OrderCostEstimate("", "", null)
        val customerNo = match.first
        val productNo = match.second

        data class History(val qty: Double, val purchaseAmount: Double, val purchaseQty: Double, val date: String)
        val history = mutableListOf<History>()
        readableDatabase.rawQuery(
            "SELECT quantity,purchase_amount,purchase_quantity,sale_date FROM sales_daily WHERE customer_no=? AND product_no=? AND quantity>0.000001 ORDER BY sale_date DESC LIMIT 300",
            arrayOf(customerNo, productNo)
        ).use { c ->
            while (c.moveToNext()) history += History(c.getDouble(0), c.getDouble(1), c.getDouble(2), c.getString(3))
        }

        val ownUnit = materialUnitCost(customerNo, productNo)
        val nearest = history.minWithOrNull(compareBy<History>(
            {
                val diff = kotlin.math.abs(it.qty - quantity)
                val tolerance = kotlin.math.max(50.0, kotlin.math.max(it.qty, quantity) * 0.01)
                if (diff <= tolerance) 0.0 else diff / kotlin.math.max(1.0, quantity)
            },
            { kotlin.math.abs(it.qty - quantity) / kotlin.math.max(1.0, quantity) },
            { -it.date.replace("-", "").toDoubleOrNull().orZero() }
        ))

        if (nearest != null) {
            val purchasedFraction = (nearest.purchaseQty / nearest.qty).coerceIn(0.0, 1.0)
            val purchaseUnit = if (kotlin.math.abs(nearest.purchaseQty) > 0.000001) nearest.purchaseAmount / nearest.purchaseQty else 0.0
            if (purchasedFraction > 0.000001 && kotlin.math.abs(purchaseUnit) > 0.000001) {
                val purchasedCost = quantity * purchasedFraction * purchaseUnit
                val ownFraction = 1.0 - purchasedFraction
                if (ownFraction <= 0.000001) return OrderCostEstimate(customerNo, productNo, purchasedCost, "売上分析実績")
                if (ownUnit != null) return OrderCostEstimate(customerNo, productNo, purchasedCost + quantity * ownFraction * ownUnit, "売上分析実績＋板取")
                return OrderCostEstimate(customerNo, productNo, null)
            }
            if (ownUnit != null) return OrderCostEstimate(customerNo, productNo, quantity * ownUnit, "板取平米")
        }
        return OrderCostEstimate(customerNo, productNo, ownUnit?.times(quantity), if (ownUnit != null) "板取平米" else "")
    }

    private fun Double?.orZero(): Double = this ?: 0.0

    private fun resolveOrderProduct(customerNumberHint: String, customerName: String, productNumberHint: String, itemName: String): Pair<String,String>? {
        if (customerNumberHint.isNotBlank() && productNumberHint.isNotBlank()) return customerNumberHint to productNumberHint

        data class Candidate(val customerNo:String,val productNo:String,val productName:String,val customerName:String,val lastDate:String)
        val candidates = mutableListOf<Candidate>()
        readableDatabase.rawQuery(
            """
            SELECT sp.customer_no,sp.product_no,sp.product_name,
                   COALESCE(NULLIF(sc.customer_name,''),MAX(p.customer_name),''),sp.last_date
            FROM sales_products sp
            LEFT JOIN sales_customers sc ON sc.customer_no=sp.customer_no
            LEFT JOIN products p ON p.customer_no=sp.customer_no
            GROUP BY sp.customer_no,sp.product_no,sp.product_name,sc.customer_name,sp.last_date
            """.trimIndent(), null
        ).use { c ->
            while (c.moveToNext()) candidates += Candidate(c.getString(0),c.getString(1),c.getString(2).orEmpty(),c.getString(3).orEmpty(),c.getString(4).orEmpty())
        }

        val wantedCustomer = GrossProfitUtil.normalizeName(customerName)
        val wantedItem = GrossProfitUtil.normalizeName(itemName)
        val scored = candidates.mapNotNull { c ->
            if (productNumberHint.isNotBlank() && c.productNo != productNumberHint) return@mapNotNull null
            val cn = GrossProfitUtil.normalizeName(c.customerName)
            val pn = GrossProfitUtil.normalizeName(c.productName)
            val customerScore = when {
                customerNumberHint.isNotBlank() && c.customerNo == customerNumberHint -> 0
                wantedCustomer.isNotBlank() && cn == wantedCustomer -> 0
                wantedCustomer.isNotBlank() && (cn.contains(wantedCustomer) || wantedCustomer.contains(cn)) -> 1
                customerNumberHint.isNotBlank() || wantedCustomer.isNotBlank() -> 4
                else -> 2
            }
            val itemScore = when {
                wantedItem.isNotBlank() && pn == wantedItem -> 0
                wantedItem.isNotBlank() && (pn.contains(wantedItem) || wantedItem.contains(pn)) -> 1
                else -> 5
            }
            if (itemScore >= 5 && productNumberHint.isBlank()) null else Triple(customerScore * 10 + itemScore, c.lastDate, c)
        }.sortedWith(compareBy<Triple<Int,String,Candidate>> { it.first }.thenByDescending { it.second })

        return scored.firstOrNull()?.third?.let { it.customerNo to it.productNo }
    }

'''
if anchor not in s: raise SystemExit("scalar anchor missing")
s=s.replace(anchor,helpers+anchor,1)
p.write_text(s)

# Version
build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, c1 = re.subn(r"versionCode\\s+\\d+", "versionCode 44", b, count=1)
b, c2 = re.subn(r"versionName\\s+'[^']+'", "versionName '1.9.8'", b, count=1)
if c1 != 1 or c2 != 1: raise SystemExit("version declarations not found")
build.write_text(b)
