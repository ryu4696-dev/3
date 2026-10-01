from pathlib import Path

p = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger/MainActivity.kt")
s = p.read_text()

old = '''        fun dimensionValue(value: Double): String = if (value > 0.000001) "${number.format(value)} mm" else "-"
        addDetail(body, "材質", product.material)
        addDetail(body, "長（外寸優先）", dimensionValue(product.dimLength))
        addDetail(body, "巾（外寸優先）", dimensionValue(product.dimWidth))
        addDetail(body, "深さ（外寸優先）", dimensionValue(product.dimDepth))
        addDetail(body, "糊代", dimensionValue(product.glueMargin))
'''
new = '''        fun dimensionValue(value: Double): String = if (value > 0.000001) "${number.format(value)} mm" else "-"
        val innerOnly = product.dimension.startsWith("内 ")
        val innerSuffix = if (innerOnly) " (内寸)" else ""
        addDetail(body, "材質", product.material)
        addDetail(body, "長$innerSuffix", dimensionValue(product.dimLength))
        addDetail(body, "巾$innerSuffix", dimensionValue(product.dimWidth))
        addDetail(body, "深さ$innerSuffix", dimensionValue(product.dimDepth))
        addDetail(body, "糊代", dimensionValue(product.glueMargin))
'''
if old not in s:
    raise SystemExit("MainActivity outer-priority detail block not found")
s = s.replace(old, new, 1)
p.write_text(s)

print("MiniCoPaTis v0.5.4 inner-dimension label patch applied")
