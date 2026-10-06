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

class AllSalesTableView(context: Context) : ScrollView(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density + 0.5f).toInt()

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(18))
    }

    private var sections: List<SalesCompanyMonthly> = emptyList()
    private var months: List<String> = emptyList()
    private var monthClickListener: ((Customer, SalesProductMonthly, String) -> Unit)? = null
    private val expandedCompanies = mutableSetOf<String>()
    private val expandedProducts = mutableSetOf<String>()
    private val fmt = DecimalFormat("#,##0.##")

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        setBackgroundColor(Color.rgb(246, 248, 251))
        addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun setOnMonthClickListener(listener: (Customer, SalesProductMonthly, String) -> Unit) {
        monthClickListener = listener
    }

    fun setData(sections: List<SalesCompanyMonthly>, monthKeys: List<String>) {
        this.sections = sections
        this.months = monthKeys
        expandedCompanies.retainAll(sections.map { it.customer.number }.toSet())
        rebuild()
    }

    private fun rebuild() {
        content.removeAllViews()
        if (sections.isEmpty()) {
            content.addView(label("販売目標対象の売上データがありません", 13.5f, Color.rgb(100,116,139), false).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(42), dp(12), dp(42))
            })
            return
        }

        val total = sections.fold(SalesMetrics()) { acc, section ->
            acc + section.products.fold(SalesMetrics()) { pacc, p -> pacc + totalOf(p) }
        }

        val summary = card(Color.rgb(248,251,250), Color.rgb(180,213,200))
        summary.addView(label("全社合計", 15f, Color.rgb(25,58,50), true))
        summary.addView(metricRow(total))
        content.addView(summary, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })

        sections.forEach { section ->
            content.addView(companyCard(section), LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(9) })
        }
    }

    private fun companyCard(section: SalesCompanyMonthly): LinearLayout {
        val no = section.customer.number
        val open = no in expandedCompanies
        val total = section.products.fold(SalesMetrics()) { acc, p -> acc + totalOf(p) }
        val card = card(
            if (open) Color.rgb(247,252,249) else Color.WHITE,
            if (open) Color.rgb(15,91,70) else Color.rgb(200,210,221)
        )

        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener {
                if (open) expandedCompanies.remove(no) else expandedCompanies.add(no)
                rebuild()
            }
        }

        val names = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        names.addView(label(section.customer.name.ifBlank { no }, 14.5f, Color.rgb(15,23,42), true).apply { maxLines=2 })
        names.addView(label(no, 10.5f, Color.rgb(100,116,139), false).apply { setPadding(0,dp(3),0,0) })
        head.addView(names, LinearLayout.LayoutParams(0,-2,1f))
        head.addView(label(if (open) "⌃" else "⌄", 18f, Color.rgb(100,116,139), true))
        card.addView(head)
        card.addView(metricRow(total))

        if (open) {
            section.products.forEach { product ->
                card.addView(productCard(section.customer, product), LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(7) })
            }
        }
        return card
    }

    private fun productCard(customer: Customer, product: SalesProductMonthly): LinearLayout {
        val key = customer.number + "\u001F" + product.productNumber
        val open = key in expandedProducts
        val total = totalOf(product)
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10),dp(9),dp(10),dp(9))
            background = rounded(Color.rgb(250,251,253), Color.rgb(222,228,235))
        }

        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener {
                if (open) expandedProducts.remove(key) else expandedProducts.add(key)
                rebuild()
            }
        }
        val names = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        names.addView(label(product.productName.ifBlank { product.productNumber }, 12.8f, Color.rgb(42,55,73), true).apply { maxLines=2 })
        names.addView(label(product.productNumber, 10f, Color.rgb(107,114,128), false).apply { setPadding(0,dp(2),0,0) })
        head.addView(names, LinearLayout.LayoutParams(0,-2,1f))
        head.addView(label("¥" + fmt.format(total.amount), 11.5f, Color.rgb(15,105,76), true))
        head.addView(label(if (open) "　⌃" else "　⌄", 14f, Color.rgb(100,116,139), true))
        box.addView(head)

        if (open) {
            months.forEachIndexed { index, month ->
                val m = product.monthly[month] ?: return@forEachIndexed
                val row = monthRow(month, m, index % 2 == 0).apply {
                    isClickable = true
                    setOnClickListener { monthClickListener?.invoke(customer, product, month) }
                }
                box.addView(row, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(5) })
            }
        }
        return box
    }

    private fun metricRow(metrics: SalesMetrics): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0,dp(9),0,0)
        addView(metric("金額", "¥" + fmt.format(metrics.amount), Color.rgb(15,105,76), Color.rgb(228,246,237), Color.rgb(184,224,205)), LinearLayout.LayoutParams(0,-2,1f))
        addView(metric("平米", fmt.format(metrics.sqm) + "㎡", Color.rgb(52,81,160), Color.rgb(232,239,252), Color.rgb(196,210,239)), LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) })
        addView(metric("数量", fmt.format(metrics.quantity), Color.rgb(71,85,105), Color.rgb(241,243,246), Color.rgb(214,219,227)), LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) })
    }

    private fun metric(name: String, value: String, color: Int, fill: Int, stroke: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8),dp(7),dp(8),dp(7))
        background = rounded(fill, stroke)
        addView(label(name, 9.5f, Color.rgb(107,114,128), false))
        addView(label(value, 12.5f, color, true).apply { setPadding(0,dp(2),0,0); maxLines=1 })
    }

    private fun monthRow(month: String, metrics: SalesMetrics, alt: Boolean): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(9),dp(8),dp(9),dp(8))
        background = rounded(if (alt) Color.rgb(245,248,251) else Color.WHITE, Color.rgb(218,224,233))
        addView(label(monthLabel(month), 11f, Color.rgb(55,65,81), true), LinearLayout.LayoutParams(dp(58),-2))
        addView(label("¥" + fmt.format(metrics.amount), 11f, Color.rgb(15,105,76), true).apply { gravity=Gravity.END }, LinearLayout.LayoutParams(0,-2,1f))
        addView(label(fmt.format(metrics.sqm) + "㎡", 11f, Color.rgb(52,81,160), false).apply { gravity=Gravity.END }, LinearLayout.LayoutParams(0,-2,1f))
    }

    private fun totalOf(product: SalesProductMonthly): SalesMetrics =
        product.monthly.values.fold(SalesMetrics()) { acc, value -> acc + value }

    private operator fun SalesMetrics.plus(other: SalesMetrics) = SalesMetrics(
        amount + other.amount,
        quantity + other.quantity,
        sqm + other.sqm
    )

    private fun monthLabel(month: String): String {
        val p = month.split("-")
        return if (p.size == 2) p[1].toIntOrNull()?.let { it.toString() + "月" } ?: month else month
    }

    private fun card(fill: Int, stroke: Int) = LinearLayout(context).apply {
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
