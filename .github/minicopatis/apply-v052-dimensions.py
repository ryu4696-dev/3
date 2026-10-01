from pathlib import Path

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

p = root / "Models.kt"
s = p.read_text()
old = '''    val material: String,
    val dimension: String,
    val sqm: Double,
'''
new = '''    val material: String,
    val dimension: String,
    val dimLength: Double,
    val dimWidth: Double,
    val dimDepth: Double,
    val glueMargin: Double,
    val topFlap: Double,
    val bottomFlap: Double,
    val sqm: Double,
'''
if old not in s:
    raise SystemExit("Models ProductSummary dimension block not found")
s = s.replace(old, new, 1)
p.write_text(s)

p = root / "CsvImporter.kt"
s = p.read_text()

old = '''        fun required(name: String): Int = index[name] ?: throw MissingColumnsException("必要な列「$name」がありません")
        fun optional(name: String): Int = index[name] ?: -1
'''
new = '''        fun required(name: String): Int = index[name] ?: throw MissingColumnsException("必要な列「$name」がありません")
        fun optional(name: String): Int = index[name] ?: -1
        fun optionalAny(vararg names: String): Int {
            for (name in names) {
                val found = index[name]
                if (found != null) return found
            }
            return -1
        }
'''
if old not in s:
    raise SystemExit("CsvImporter optional helper marker not found")
s = s.replace(old, new, 1)

old = '''        val outerLIdx = optional("外寸長さ")
        val outerWIdx = optional("外寸巾")
        val outerHIdx = optional("外寸深さ")
        val sqmIdx = optional("売上平米")
'''
new = '''        val outerLIdx = optional("外寸長さ")
        val outerWIdx = optional("外寸巾")
        val outerHIdx = optional("外寸深さ")
        val glueMarginIdx = optionalAny("糊代", "のりしろ", "ノリシロ", "糊しろ", "糊代寸法", "製造寸法糊代", "製造_糊代")
        val topFlapIdx = optionalAny("上フラップ", "上フラップ長", "上フラップ寸法", "製造寸法上フラップ", "製造_上フラップ")
        val bottomFlapIdx = optionalAny("下フラップ", "下フラップ長", "下フラップ寸法", "したフラップ", "製造寸法下フラップ", "製造_下フラップ")
        val sqmIdx = optional("売上平米")
'''
if old not in s:
    raise SystemExit("CsvImporter outer-dimension index block not found")
s = s.replace(old, new, 1)

old = '''            val inner = listOf(number(innerLIdx), number(innerWIdx), number(innerHIdx))
            val outer = listOf(number(outerLIdx), number(outerWIdx), number(outerHIdx))
            val dimension = dimensionText(inner, outer)
'''
new = '''            val innerL = number(innerLIdx)
            val innerW = number(innerWIdx)
            val innerH = number(innerHIdx)
            val outerL = number(outerLIdx)
            val outerW = number(outerWIdx)
            val outerH = number(outerHIdx)

            val dimLength = if (outerL > 0.0) outerL else innerL
            val dimWidth = if (outerW > 0.0) outerW else innerW
            val dimDepth = if (outerH > 0.0) outerH else innerH
            val usesOuter = outerL > 0.0 || outerW > 0.0 || outerH > 0.0
            val dimensionValues = listOf(dimLength, dimWidth, dimDepth)
            val dimension = if (dimensionValues.any { it > 0.0 }) {
                val prefix = if (usesOuter) "外" else "内"
                "$prefix ${dimensionValues.map { formatDimensionNumber(it) }.joinToString("×")}"
            } else {
                "-"
            }
            val glueMargin = number(glueMarginIdx)
            val topFlap = number(topFlapIdx)
            val bottomFlap = number(bottomFlapIdx)
'''
if old not in s:
    raise SystemExit("CsvImporter dimension calculation block not found")
s = s.replace(old, new, 1)

old = '''                material = cell(materialIdx),
                dimension = dimension,
                sqm = number(sqmIdx),
'''
new = '''                material = cell(materialIdx),
                dimension = dimension,
                dimLength = dimLength,
                dimWidth = dimWidth,
                dimDepth = dimDepth,
                glueMargin = glueMargin,
                topFlap = topFlap,
                bottomFlap = bottomFlap,
                sqm = number(sqmIdx),
'''
if old not in s:
    raise SystemExit("CsvImporter RawComponent creation block not found")
s = s.replace(old, new, 1)

old = '''        val material: String,
        val dimension: String,
        val sqm: Double,
'''
new = '''        val material: String,
        val dimension: String,
        val dimLength: Double,
        val dimWidth: Double,
        val dimDepth: Double,
        val glueMargin: Double,
        val topFlap: Double,
        val bottomFlap: Double,
        val sqm: Double,
'''
if old not in s:
    raise SystemExit("CsvImporter RawComponent model block not found")
s = s.replace(old, new, 1)

old = '''            val material = summarize(parts.map { it.material })
            val dimension = summarize(parts.map { it.dimension })
            val process = summarize(parts.map { it.process })
'''
new = '''            val material = summarize(parts.map { it.material })
            val dimension = summarize(parts.map { it.dimension })
            fun firstDimension(selector: (RawComponent) -> Double): Double =
                parts.map(selector).firstOrNull { it > 0.000001 } ?: 0.0
            val dimLength = firstDimension { it.dimLength }
            val dimWidth = firstDimension { it.dimWidth }
            val dimDepth = firstDimension { it.dimDepth }
            val glueMargin = firstDimension { it.glueMargin }
            val topFlap = firstDimension { it.topFlap }
            val bottomFlap = firstDimension { it.bottomFlap }
            val process = summarize(parts.map { it.process })
'''
if old not in s:
    raise SystemExit("CsvImporter ProductBuilder summary block not found")
s = s.replace(old, new, 1)

old = '''                material = material,
                dimension = dimension,
                sqm = sqm,
'''
new = '''                material = material,
                dimension = dimension,
                dimLength = dimLength,
                dimWidth = dimWidth,
                dimDepth = dimDepth,
                glueMargin = glueMargin,
                topFlap = topFlap,
                bottomFlap = bottomFlap,
                sqm = sqm,
'''
if old not in s:
    raise SystemExit("CsvImporter ProductSummary constructor block not found")
s = s.replace(old, new, 1)
p.write_text(s)

p = root / "DataRepository.kt"
s = p.read_text()

s = s.replace(
    'class DataRepository(context: Context) : SQLiteOpenHelper(context, "sales_ledger.db", null, 2) {',
    'class DataRepository(context: Context) : SQLiteOpenHelper(context, "sales_ledger.db", null, 3) {',
    1
)

old = '''        if (oldVersion < 2) {
            createSalesTables(db)
        }
'''
new = '''        if (oldVersion < 2) {
            createSalesTables(db)
        }
        if (oldVersion < 3) {
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
'''
if old not in s:
    raise SystemExit("DataRepository onUpgrade block not found")
s = s.replace(old, new, 1)

old = '''                material TEXT NOT NULL,
                dimension TEXT NOT NULL,
                sqm REAL NOT NULL,
'''
new = '''                material TEXT NOT NULL,
                dimension TEXT NOT NULL,
                dim_length REAL NOT NULL DEFAULT 0,
                dim_width REAL NOT NULL DEFAULT 0,
                dim_depth REAL NOT NULL DEFAULT 0,
                glue_margin REAL NOT NULL DEFAULT 0,
                top_flap REAL NOT NULL DEFAULT 0,
                bottom_flap REAL NOT NULL DEFAULT 0,
                sqm REAL NOT NULL,
'''
if old not in s:
    raise SystemExit("DataRepository products schema dimension block not found")
s = s.replace(old, new, 1)

old = '''                    put("material", p.material)
                    put("dimension", p.dimension)
                    put("sqm", p.sqm)
'''
new = '''                    put("material", p.material)
                    put("dimension", p.dimension)
                    put("dim_length", p.dimLength)
                    put("dim_width", p.dimWidth)
                    put("dim_depth", p.dimDepth)
                    put("glue_margin", p.glueMargin)
                    put("top_flap", p.topFlap)
                    put("bottom_flap", p.bottomFlap)
                    put("sqm", p.sqm)
'''
if old not in s:
    raise SystemExit("DataRepository product insert block not found")
s = s.replace(old, new, 1)

old = '''            SELECT product_key, customer_no, customer_name, product_no, product_name,
                   material, dimension, sqm, price, sqm_price, process, last_date,
                   deleted, tiers, part_count
'''
new = '''            SELECT product_key, customer_no, customer_name, product_no, product_name,
                   material, dimension, dim_length, dim_width, dim_depth, glue_margin, top_flap, bottom_flap,
                   sqm, price, sqm_price, process, last_date, deleted, tiers, part_count
'''
if old not in s:
    raise SystemExit("DataRepository listProducts select block not found")
s = s.replace(old, new, 1)

old = '''            material = c.getString(5),
            dimension = c.getString(6),
            sqm = c.getDouble(7),
            price = c.getDouble(8),
            sqmPrice = c.getDouble(9),
            process = c.getString(10),
            lastDeliveryDate = c.getString(11),
            deleted = c.getInt(12) != 0,
            tiers = decodeTiers(c.getString(13)),
            partCount = c.getInt(14)
'''
new = '''            material = c.getString(5),
            dimension = c.getString(6),
            dimLength = c.getDouble(7),
            dimWidth = c.getDouble(8),
            dimDepth = c.getDouble(9),
            glueMargin = c.getDouble(10),
            topFlap = c.getDouble(11),
            bottomFlap = c.getDouble(12),
            sqm = c.getDouble(13),
            price = c.getDouble(14),
            sqmPrice = c.getDouble(15),
            process = c.getString(16),
            lastDeliveryDate = c.getString(17),
            deleted = c.getInt(18) != 0,
            tiers = decodeTiers(c.getString(19)),
            partCount = c.getInt(20)
'''
if old not in s:
    raise SystemExit("DataRepository productFromCursor block not found")
s = s.replace(old, new, 1)
p.write_text(s)

p = root / "LedgerTableView.kt"
s = p.read_text()

old = '''    private val headers = listOf("材質", "寸法", "平米", "売価", "平米売価", "格差", "工程", "最終納品日")
    private val rightWidths = floatArrayOf(
        dp(105f), dp(175f), dp(90f), dp(105f), dp(115f), dp(85f), dp(230f), dp(135f)
    )
'''
new = '''    private val headers = listOf(
        "材質", "長", "巾", "深さ", "のりしろ", "上フラップ", "下フラップ",
        "平米", "売価", "平米売価", "格差", "工程", "最終納品日"
    )
    private val rightWidths = floatArrayOf(
        dp(105f), dp(85f), dp(85f), dp(85f), dp(95f), dp(105f), dp(105f),
        dp(90f), dp(105f), dp(115f), dp(85f), dp(230f), dp(135f)
    )
'''
if old not in s:
    raise SystemExit("LedgerTableView header block not found")
s = s.replace(old, new, 1)

s = s.replace(
    'drawTextCell(canvas, values[col], x, top, cellW, rowHeight, numeric = col in 2..4)',
    'drawTextCell(canvas, values[col], x, top, cellW, rowHeight, numeric = col in 1..9)',
    1
)
s = s.replace(
    'paint.textAlign = if (i in 2..4) Paint.Align.RIGHT else Paint.Align.LEFT',
    'paint.textAlign = if (i in 1..9) Paint.Align.RIGHT else Paint.Align.LEFT',
    1
)
s = s.replace(
    'val tx = if (i in 2..4) x + w - dp(10f) else x + dp(10f)',
    'val tx = if (i in 1..9) x + w - dp(10f) else x + dp(10f)',
    1
)

old = '''        return listOf(
            p.material,
            p.dimension,
            if (p.sqm > 0.000001) numberFormat.format(p.sqm) else "-",
            if (p.price != 0.0) "¥${moneyFormat.format(p.price)}" else "-",
            if (p.sqmPrice > 0.000001) "¥${moneyFormat.format(p.sqmPrice)}" else "-",
            if (p.tiers.isNotEmpty()) "あり" else "なし",
            p.process,
            p.lastDeliveryDate
        )
'''
new = '''        fun dim(value: Double): String = if (value > 0.000001) numberFormat.format(value) else "-"
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
if old not in s:
    raise SystemExit("LedgerTableView rowValues block not found")
s = s.replace(old, new, 1)
p.write_text(s)

p = root / "MainActivity.kt"
s = p.read_text()
old = '''        addDetail(body, "材質", product.material)
        addDetail(body, "寸法", if (product.dimension == "-") "-" else "${product.dimension} mm")
        addDetail(body, "平米", if (product.sqm > 0.000001) "${number.format(product.sqm)} ㎡" else "-")
'''
new = '''        fun dimensionValue(value: Double): String = if (value > 0.000001) "${number.format(value)} mm" else "-"
        addDetail(body, "材質", product.material)
        addDetail(body, "長（外寸優先）", dimensionValue(product.dimLength))
        addDetail(body, "巾（外寸優先）", dimensionValue(product.dimWidth))
        addDetail(body, "深さ（外寸優先）", dimensionValue(product.dimDepth))
        addDetail(body, "のりしろ", dimensionValue(product.glueMargin))
        addDetail(body, "上フラップ", dimensionValue(product.topFlap))
        addDetail(body, "下フラップ", dimensionValue(product.bottomFlap))
        addDetail(body, "平米", if (product.sqm > 0.000001) "${number.format(product.sqm)} ㎡" else "-")
'''
if old not in s:
    raise SystemExit("MainActivity product detail dimension block not found")
s = s.replace(old, new, 1)
p.write_text(s)

print("MiniCoPaTis dimension fields patch applied")
