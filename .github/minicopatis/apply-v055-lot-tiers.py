from pathlib import Path
import shutil

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

p = root / "CsvImporter.kt"
s = p.read_text()

old = '''        fun adjustmentAt(quantity: Int): Double {
            var result = 0.0
            for (tier in tierAdjustments) {
                if (tier.quantity <= quantity) result = tier.delta else break
            }
            return result
        }
'''
new = '''        fun adjustmentAt(quantity: Int): Double {
            if (tierAdjustments.isEmpty()) return 0.0
            for (tier in tierAdjustments) {
                if (quantity <= tier.quantity) return tier.delta
            }
            return tierAdjustments.last().delta
        }
'''
if old not in s:
    raise SystemExit("CsvImporter adjustmentAt block not found")
s = s.replace(old, new, 1)
p.write_text(s)

p = root / "MainActivity.kt"
s = p.read_text()

old = '''            for (tier in product.tiers) {
                body.addView(text("${tier.quantity}枚～　¥${money.format(tier.price)}", 15f, Color.rgb(30, 41, 59), Typeface.BOLD).apply {
                    setPadding(dp(8), dp(7), dp(8), dp(7))
                })
            }
'''
new = '''            for ((index, tier) in product.tiers.withIndex()) {
                val previousUpper = if (index > 0) product.tiers[index - 1].quantity else 0
                val rangeLabel = when {
                    product.tiers.size == 1 -> "～${number.format(tier.quantity)}枚"
                    index == 0 -> "～${number.format(tier.quantity)}枚"
                    index == product.tiers.lastIndex -> "${number.format(previousUpper + 1)}枚～"
                    else -> "${number.format(previousUpper + 1)}～${number.format(tier.quantity)}枚"
                }
                body.addView(text("$rangeLabel　¥${money.format(tier.price)}", 15f, Color.rgb(30, 41, 59), Typeface.BOLD).apply {
                    setPadding(dp(8), dp(7), dp(8), dp(7))
                })
            }
'''
if old not in s:
    raise SystemExit("MainActivity lot tier display block not found")
s = s.replace(old, new, 1)
p.write_text(s)

# Install the quote-detail warning hook without modifying the imported quote DEX.
hook_source = Path(".github/minicopatis/MiniCoPaTisApplication.kt")
hook_dest = root / "MiniCoPaTisApplication.kt"
shutil.copyfile(hook_source, hook_dest)

manifest = Path("ledgerapp/app/src/main/AndroidManifest.xml")
manifest_text = manifest.read_text()
if 'android:name=".MiniCoPaTisApplication"' not in manifest_text:
    manifest_text = manifest_text.replace(
        "    <application\n",
        "    <application\n        android:name=\".MiniCoPaTisApplication\"\n",
        1
    )
manifest.write_text(manifest_text)

gradle = Path("ledgerapp/app/build.gradle")
gradle_text = gradle.read_text()
gradle_text = gradle_text.replace("versionCode 15", "versionCode 16")
gradle_text = gradle_text.replace("versionName '0.5.5'", "versionName '0.6.0'")
gradle.write_text(gradle_text)

print("MiniCoPaTis v0.6.0 lot tiers + quote warning hook applied")
