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

class SalesTableView(context: Context) : ScrollView(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density + 0.5f).toInt()
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(18))
    }
    private var months: List<String> = emptyList()
    private var products: List<SalesProductMonthly> = emptyList()
    private var monthClickListener: ((SalesProductMonthly, String) -> Unit)? = null
    private var productTotalClickListener: ((SalesProductMonthly) -> Unit)? = null
    private var monthTotalClickListener: ((String) -> Unit)? = null
    private val expandedProducts = mutableSetOf<String>()
    private var showMonthTotals = false
    private val fmt = DecimalFormat("#,##0.##")

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        setBackgroundColor(Color.rgb(246,248,251))
        addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun setOnMonthClickListener(listener: (SalesProductMonthly, String) -> Unit) { monthClickListener = listener }
    fun setOnProductTotalClickListener(listener: (SalesProductMonthly) -> Unit) { productTotalClickListener = listener }
    fun setOnMonthTotalClickListener(listener: (String) -> Unit) { monthTotalClickListener = listener }

    fun setData(items: List<SalesProductMonthly>, monthKeys: List<String>) {
        products = items
        months = monthKeys
        expandedProducts.retainAll(items.map { it.productNumber }.toSet())
        rebuild()
    }

    private fun rebuild() {
        content.removeAllViews()
        if (products.isEmpty()) return

        content.addView(summaryCard(), LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        products.forEach { product ->
            content.addView(productCard(product), LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(9) })
        }
    }

    private fun summaryCard(): LinearLayout {
        val total = products.fold(SalesMetrics()) { acc, p -> acc + totalOf(p) }
        val card = baseCard(Color.rgb(248,251,250), Color.rgb(199,219,211))
        card.addView(label("期間合計", 14f, Color.rgb(25,58,50), true))
        card.addView(metricRow(total))
        val toggle = label(if (showMonthTotals) "月別合計を閉じる　⌃" else "月別合計を表示　⌄", 11.5f, Color.rgb(30,91,73), true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0,dp(9),0,dp(4))
            isClickable = true
            setOnClickListener { showMonthTotals = !showMonthTotals; rebuild() }
        }
        card.addView(toggle)
        if (showMonthTotals) {
            months.forEachIndexed { index, month ->
                val totalMonth = products.fold(SalesMetrics()) { acc, p -> acc + (p.monthly[month] ?: SalesMetrics()) }
                val row = monthRow(month, totalMonth, index % 2 == 0).apply {
                    isClickable = true
                    setOnClickListener { monthTotalClickListener?.invoke(month) }
                }
                card.addView(row, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(3) })
            }
        }
        return card
    }

    private fun productCard(product: SalesProductMonthly): LinearLayout {
        val open = product.productNumber in expandedProducts
        val total = totalOf(product)
        val card = baseCard(if (open) Color.rgb(247,252,249) else Color.WHITE,
            if (open) Color.rgb(15,91,70) else Color.rgb(216,223,232))

        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener {
                if (open) expandedProducts.remove(product.productNumber) else expandedProducts.add(product.productNumber)
                rebuild()
            }
        }
        val names = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        names.addView(label(product.productName.ifBlank { product.productNumber }, 14.5f, Color.rgb(15,23,42), true).apply { maxLines=2 })
        names.addView(label(product.productNumber, 10.5f, Color.rgb(100,116,139), false).apply { setPadding(0,dp(3),0,0) })
        head.addView(names, LinearLayout.LayoutParams(0,-2,1f))
        head.addView(label(if (open) "⌃" else "⌄", 18f, Color.rgb(100,116,139), true))
        card.addView(head)
        card.addView(metricRow(total))

        if (open) {
            card.addView(label("月別", 11f, Color.rgb(100,116,139), true).apply { setPadding(0,dp(7),0,dp(2)) })
            months.forEachIndexed { index, month ->
                val metrics = product.monthly[month]
                if (metrics != null) {
                    val row = monthRow(month, metrics, index % 2 == 0).apply {
                        isClickable = true
                        setOnClickListener { monthClickListener?.invoke(product, month) }
                    }
                    card.addView(row, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(3) })
                }
            }
            card.addView(label("期間の日別を見る", 11.5f, Color.rgb(30,91,73), true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8),dp(9),dp(8),dp(9))
                background = rounded(Color.rgb(236,247,242), Color.rgb(199,224,212))
                isClickable = true
                setOnClickListener { productTotalClickListener?.invoke(product) }
            }, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        }
        return card
    }

    private fun metricRow(metrics: SalesMetrics): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0,dp(9),0,0)
        addView(metric("金額", "¥" + fmt.format(metrics.amount), Color.rgb(15,105,76), Color.rgb(235,247,241)), LinearLayout.LayoutParams(0,-2,1f))
        addView(metric("平米", fmt.format(metrics.sqm) + "㎡", Color.rgb(52,81,160), Color.rgb(239,243,253)), LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) })
        addView(metric("数量", fmt.format(metrics.quantity), Color.rgb(71,85,105), Color.rgb(243,244,246)), LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) })
    }

    private fun monthRow(month: String, metrics: SalesMetrics, alt: Boolean): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(9),dp(8),dp(9),dp(8))
        background = rounded(if (alt) Color.rgb(248,250,252) else Color.WHITE, Color.rgb(232,236,242))
        addView(label(monthLabel(month), 11.5f, Color.rgb(55,65,81), true), LinearLayout.LayoutParams(dp(62),-2))
        addView(label("¥" + fmt.format(metrics.amount), 11.5f, Color.rgb(15,105,76), true).apply { gravity=Gravity.END }, LinearLayout.LayoutParams(0,-2,1f))
        addView(label(fmt.format(metrics.sqm) + "㎡", 11.5f, Color.rgb(52,81,160), false).apply { gravity=Gravity.END }, LinearLayout.LayoutParams(0,-2,1f))
    }

    private fun metric(name: String, value: String, color: Int, fill: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8),dp(7),dp(8),dp(7))
        background = rounded(fill, null)
        addView(label(name, 9.5f, Color.rgb(107,114,128), false))
        addView(label(value, 12.5f, color, true).apply { setPadding(0,dp(2),0,0); maxLines=1 })
    }

    private fun totalOf(product: SalesProductMonthly): SalesMetrics =
        product.monthly.values.fold(SalesMetrics()) { acc, value -> acc + value }

    private operator fun SalesMetrics.plus(other: SalesMetrics) = SalesMetrics(
        amount + other.amount, quantity + other.quantity, sqm + other.sqm
    )

    private fun monthLabel(month: String): String {
        val p = month.split("-")
        return if (p.size == 2) p[1].toIntOrNull()?.let { it.toString() + "月" } ?: month else month
    }

    private fun baseCard(fill: Int, stroke: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14),dp(12),dp(14),dp(12))
        background = rounded(fill, stroke)
    }

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
