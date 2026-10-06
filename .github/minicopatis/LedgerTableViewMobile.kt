package jp.co.ichika.salesledger

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DecimalFormat

class LedgerTableView(context: Context) : ScrollView(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density + 0.5f).toInt()
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(18))
    }
    private var rowClickListener: ((ProductSummary) -> Unit)? = null
    private val number = DecimalFormat("#,##0.##")

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        setBackgroundColor(Color.rgb(246, 248, 251))
        addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun setOnRowClickListener(listener: (ProductSummary) -> Unit) {
        rowClickListener = listener
    }

    fun setProducts(items: List<ProductSummary>) {
        content.removeAllViews()
        if (items.isEmpty()) {
            content.addView(label("取引先を選択するか、検索条件を変更してください", 13.5f, Color.rgb(100,116,139), false).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(42), dp(12), dp(42))
            })
            return
        }
        items.forEach { product -> content.addView(productCard(product), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }) }
    }

    private fun productCard(p: ProductSummary): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(
                if (p.deleted) Color.rgb(249,250,251) else Color.WHITE,
                if (p.deleted) Color.rgb(226,232,240) else Color.rgb(216,223,232)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { rowClickListener?.invoke(p) }
        }

        val titleRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(label(p.productName.ifBlank { p.productNumber }, 15f,
            if (p.deleted) Color.rgb(100,116,139) else Color.rgb(15,23,42), true).apply {
            maxLines = 2
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (p.deleted) {
            titleRow.addView(label("削除品", 10f, Color.rgb(148,75,75), true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(7), dp(4), dp(7), dp(4))
                background = rounded(Color.rgb(253,238,238), Color.rgb(244,199,199))
            })
        }
        card.addView(titleRow)
        card.addView(label(p.productNumber, 11f, Color.rgb(100,116,139), false).apply { setPadding(0, dp(3), 0, dp(8)) })

        val infoRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        infoRow.addView(pill(p.material.ifBlank { "材質未登録" }, Color.rgb(239,243,253), Color.rgb(52,81,160)),
            LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        infoRow.addView(pill(dimensionText(p), Color.rgb(243,244,246), Color.rgb(55,65,81)),
            LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(infoRow)

        val metricsTop = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        metricsTop.addView(metric("平米", if (p.sqm > 0.000001) number.format(p.sqm) + "㎡" else "—", Color.rgb(52,81,160)), LinearLayout.LayoutParams(0,-2,1f))
        metricsTop.addView(metric("売価", if (p.price != 0.0) "¥" + number.format(p.price) else "—", Color.rgb(15,105,76)), LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) })
        card.addView(metricsTop)

        val processRate = processRatePerSqm(p)
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

        val footer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(2), 0, 0) }
        footer.addView(label("工程　" + p.process.ifBlank { "—" }, 11.5f, Color.rgb(71,85,105), false).apply { maxLines=2 })
        footer.addView(label("最終納品　" + p.lastDeliveryDate.ifBlank { "—" }, 11.5f, Color.rgb(71,85,105), false).apply { setPadding(0,dp(4),0,0) })
        card.addView(footer)
        return card
    }

    private fun processRatePerSqm(p: ProductSummary): Double? {
        if (p.sqmPrice <= 0.000001) return null
        val materialRate = quoteMaterialRate(p.material) ?: return null
        return p.sqmPrice - materialRate
    }

    // Same material-rate table used by the embedded quick quote.
    // Quick quote formula: unit price = ceil((material rate + process rate) * area per piece).
    // Therefore the ledger-side reverse estimate is sqm unit price - material rate.
    private fun quoteMaterialRate(rawMaterial: String): Double? {
        val raw = rawMaterial.uppercase()
            .replace(" ", "")
            .replace("　", "")
            .replace("×", "X")
            .replace("・", "")
        if (raw.isBlank()) return null
        val wf = raw.contains("W")

        fun rate(abc: Double, wfRate: Double) = if (wf) wfRate else abc
        fun mediumCode(): String {
            return when {
                raw.contains("SKS200") || raw.contains("MM200") -> "SKS200"
                raw.contains("SKS180") || raw.contains("MM180") -> "SKS180"
                raw.contains("180") -> "180"
                raw.contains("160") -> "160"
                else -> ""
            }
        }
        val medium = mediumCode()

        val outer = when {
            raw.startsWith("OPB6") || raw.startsWith("OPC6") -> "OPB6"
            raw.startsWith("OPC5") -> "OPC5"
            raw.startsWith("K7") -> "K7"
            raw.startsWith("K6") -> "K6"
            raw.startsWith("K5") -> "K5"
            raw.startsWith("C5") -> "C5"
            else -> return null
        }

        val inner = when {
            raw.contains("/OPB6") || raw.contains("/OPC6") -> "OPB6"
            raw.contains("/OPC5") -> "OPC5"
            raw.contains("/K7") -> "K7"
            raw.contains("/K6") -> "K6"
            raw.contains("/K5") -> "K5"
            raw.contains("/C5") -> "C5"
            outer == "OPB6" -> "K6"
            outer == "OPC5" -> "C5"
            else -> outer
        }

        return when ("$outer/$inner/$medium") {
            "C5/C5/" -> rate(65.0, 98.0)
            "C5/C5/160" -> rate(69.0, 102.0)
            "C5/C5/180" -> rate(72.5, 105.5)
            "C5/C5/SKS180" -> rate(77.0, 110.0)
            "C5/C5/SKS200" -> rate(80.0, 113.0)

            "OPC5/C5/" -> rate(70.0, 103.0)
            "OPC5/C5/160" -> rate(74.0, 107.0)
            "OPC5/C5/180" -> rate(77.5, 110.5)
            "OPC5/C5/SKS180" -> rate(82.0, 115.0)
            "OPC5/OPC5/" -> rate(75.0, 108.0)
            "OPC5/OPC5/160" -> rate(79.0, 112.0)
            "OPC5/OPC5/SKS180" -> rate(87.0, 120.0)

            "K5/K5/" -> rate(70.0, 103.0)
            "K5/K5/160" -> rate(74.0, 107.0)
            "K5/K5/180" -> rate(77.5, 110.5)
            "K5/K5/SKS180" -> rate(82.0, 115.0)
            "K5/K5/SKS200" -> rate(85.0, 118.0)

            "K6/K6/" -> rate(76.0, 109.0)
            "K6/K6/160" -> rate(80.0, 113.0)
            "K6/K6/180" -> rate(83.5, 116.5)
            "K6/K6/SKS180" -> rate(88.0, 121.0)
            "K6/K6/SKS200" -> rate(91.0, 124.0)

            "OPB6/K6/" -> rate(81.0, 114.0)
            "OPB6/K6/160" -> rate(85.0, 118.0)
            "OPB6/K6/180" -> rate(88.5, 121.5)
            "OPB6/K6/SKS180" -> rate(93.0, 126.0)
            "OPB6/K6/SKS200" -> rate(96.0, 129.0)
            "OPB6/OPB6/" -> rate(86.0, 119.0)
            "OPB6/OPB6/160" -> rate(90.0, 123.0)
            "OPB6/OPB6/180" -> rate(93.5, 126.5)
            "OPB6/OPB6/SKS180" -> rate(98.0, 131.0)
            "OPB6/OPB6/SKS200" -> rate(101.0, 134.0)

            "K7/K7/" -> rate(87.0, 120.0)
            "K7/K7/160" -> rate(91.0, 124.0)
            "K7/K7/180" -> rate(94.5, 127.5)
            "K7/K7/SKS180" -> rate(99.0, 132.0)
            "K7/K7/SKS200" -> rate(102.0, 135.0)
            else -> null
        }
    }

    private fun metric(name: String, value: String, color: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(7), dp(8), dp(7))
        background = rounded(Color.rgb(248,250,252), Color.rgb(232,236,242))
        addView(label(name, 9.5f, Color.rgb(107,114,128), false))
        addView(label(value, 12.5f, color, true).apply { setPadding(0,dp(2),0,0) })
    }

    private fun pill(value: String, fill: Int, color: Int) = label(value, 11f, color, true).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(9), dp(6), dp(9), dp(6))
        background = rounded(fill, null)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun dimensionPart(v: Double): String = if (v > 0.000001) DecimalFormat("0.##").format(v) else "-"
    private fun dimensionText(p: ProductSummary): String =
        listOf(p.glueMargin,p.dimLength,p.dimWidth,p.dimDepth,p.topFlap,p.bottomFlap).joinToString("-") { dimensionPart(it) }

    private fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun rounded(fill: Int, stroke: Int?) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(13).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
}
