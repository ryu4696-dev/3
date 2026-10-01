package jp.co.ichika.salesledger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import java.text.DecimalFormat
import kotlin.math.max
import kotlin.math.min

class AllSalesTableView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val headerHeight = dp(50f)
    private val rowHeight = dp(94f)
    private val fixedWidth = dp(230f)
    private val monthWidth = dp(172f)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(215, 222, 232)
        strokeWidth = dp(1f)
    }
    private val scroller = OverScroller(context)

    private var offsetX = 0f
    private var offsetY = 0f
    private var months: List<String> = emptyList()
    private var rows: List<Row> = emptyList()
    private var monthClickListener: ((Customer, SalesProductMonthly, String) -> Unit)? = null
    private val valueFormat = DecimalFormat("#,##0.##")

    private sealed class Row {
        data class CompanyHeader(val customer: Customer) : Row()
        data class ProductRow(val customer: Customer, val product: SalesProductMonthly) : Row()
        data class CompanyTotal(val customer: Customer, val monthly: Map<String, SalesMetrics>, val grand: SalesMetrics) : Row()
        data class GrandTotal(val monthly: Map<String, SalesMetrics>, val grand: SalesMetrics) : Row()
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            if (!scroller.isFinished) scroller.forceFinished(true)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            offsetX = (offsetX + distanceX).coerceIn(0f, maxScrollX())
            offsetY = (offsetY + distanceY).coerceIn(0f, maxScrollY())
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            scroller.fling(
                offsetX.toInt(), offsetY.toInt(),
                (-velocityX).toInt(), (-velocityY).toInt(),
                0, maxScrollX().toInt(),
                0, maxScrollY().toInt()
            )
            postInvalidateOnAnimation()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (e.y <= headerHeight || e.x < fixedWidth) return true
            val rowIndex = ((e.y - headerHeight + offsetY) / rowHeight).toInt()
            val columnIndex = ((e.x - fixedWidth + offsetX) / monthWidth).toInt()
            val row = rows.getOrNull(rowIndex) as? Row.ProductRow ?: return true
            val month = months.getOrNull(columnIndex) ?: return true
            if (row.product.monthly[month] != null) monthClickListener?.invoke(row.customer, row.product, month)
            return true
        }
    })

    init {
        isClickable = true
        setBackgroundColor(Color.WHITE)
    }

    fun setData(sections: List<SalesCompanyMonthly>, monthKeys: List<String>) {
        months = monthKeys
        val built = mutableListOf<Row>()
        val globalMonthly = LinkedHashMap<String, SalesMetrics>()
        for (month in months) globalMonthly[month] = SalesMetrics()
        var globalGrand = SalesMetrics()

        for (section in sections) {
            if (section.products.isEmpty()) continue
            built += Row.CompanyHeader(section.customer)

            val companyMonthly = LinkedHashMap<String, SalesMetrics>()
            for (month in months) companyMonthly[month] = SalesMetrics()
            var companyGrand = SalesMetrics()

            for (product in section.products) {
                built += Row.ProductRow(section.customer, product)
                for (month in months) {
                    val metric = product.monthly[month] ?: continue
                    companyMonthly[month] = (companyMonthly[month] ?: SalesMetrics()) + metric
                    globalMonthly[month] = (globalMonthly[month] ?: SalesMetrics()) + metric
                    companyGrand += metric
                    globalGrand += metric
                }
            }

            built += Row.CompanyTotal(section.customer, companyMonthly, companyGrand)
        }

        if (built.isNotEmpty()) built += Row.GrandTotal(globalMonthly, globalGrand)
        rows = built
        offsetX = 0f
        offsetY = 0f
        scroller.forceFinished(true)
        invalidate()
    }

    fun setOnMonthClickListener(listener: (Customer, SalesProductMonthly, String) -> Unit) {
        monthClickListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.currX.toFloat()
            offsetY = scroller.currY.toFloat()
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        offsetX = offsetX.coerceIn(0f, maxScrollX())
        offsetY = offsetY.coerceIn(0f, maxScrollY())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawScrollableRows(canvas)
        drawFixedRows(canvas)
        drawHeader(canvas)
        drawFrozenDivider(canvas)

        if (rows.isEmpty()) {
            paint.color = Color.rgb(100, 116, 139)
            paint.textSize = dp(14f)
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("期間を指定すると全社の商品別実績が表示されます", width / 2f, headerHeight + dp(52f), paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawScrollableRows(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(fixedWidth, headerHeight, width.toFloat(), height.toFloat())

        val start = max(0, (offsetY / rowHeight).toInt())
        val visible = ((height - headerHeight) / rowHeight).toInt() + 2
        val end = min(rows.size, start + visible)

        for (i in start until end) {
            val row = rows[i]
            val top = headerHeight + i * rowHeight - offsetY
            val bottom = top + rowHeight
            drawRowBackground(canvas, fixedWidth, top, width.toFloat(), bottom, row, i)

            var x = fixedWidth - offsetX
            for (month in months) {
                if (x + monthWidth >= fixedWidth && x <= width && row !is Row.CompanyHeader) {
                    drawMetricCell(canvas, metricsFor(row, month), x, top, monthWidth, rowHeight, row !is Row.ProductRow)
                }
                canvas.drawLine(x + monthWidth, top, x + monthWidth, bottom, gridPaint)
                x += monthWidth
            }

            if (x + monthWidth >= fixedWidth && x <= width && row !is Row.CompanyHeader) {
                drawMetricCell(canvas, totalFor(row), x, top, monthWidth, rowHeight, true)
            }
            canvas.drawLine(x + monthWidth, top, x + monthWidth, bottom, gridPaint)
            canvas.drawLine(fixedWidth, bottom, width.toFloat(), bottom, gridPaint)
        }
        canvas.restore()
    }

    private fun drawFixedRows(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(0f, headerHeight, fixedWidth, height.toFloat())

        val start = max(0, (offsetY / rowHeight).toInt())
        val visible = ((height - headerHeight) / rowHeight).toInt() + 2
        val end = min(rows.size, start + visible)

        for (i in start until end) {
            val row = rows[i]
            val top = headerHeight + i * rowHeight - offsetY
            val bottom = top + rowHeight
            drawRowBackground(canvas, 0f, top, fixedWidth, bottom, row, i)

            paint.textAlign = Paint.Align.LEFT
            when (row) {
                is Row.CompanyHeader -> {
                    paint.color = Color.rgb(30, 64, 175)
                    paint.textSize = dp(11.5f)
                    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    drawEllipsized(canvas, row.customer.number, dp(12f), top + dp(31f), fixedWidth - dp(24f))
                    paint.color = Color.rgb(15, 23, 42)
                    paint.textSize = dp(15f)
                    drawEllipsized(canvas, row.customer.name.ifBlank { row.customer.number }, dp(12f), top + dp(64f), fixedWidth - dp(24f))
                }
                is Row.ProductRow -> {
                    paint.color = Color.rgb(71, 85, 105)
                    paint.textSize = dp(11.5f)
                    paint.typeface = android.graphics.Typeface.DEFAULT
                    drawEllipsized(canvas, row.product.productNumber, dp(12f), top + dp(31f), fixedWidth - dp(24f))
                    paint.color = Color.rgb(15, 23, 42)
                    paint.textSize = dp(13.5f)
                    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    drawEllipsized(canvas, row.product.productName, dp(12f), top + dp(64f), fixedWidth - dp(24f))
                }
                is Row.CompanyTotal -> {
                    paint.color = Color.rgb(30, 64, 175)
                    paint.textSize = dp(14f)
                    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    canvas.drawText("取引先合計", dp(12f), top + rowHeight / 2f + dp(5f), paint)
                }
                is Row.GrandTotal -> {
                    paint.color = Color.rgb(15, 23, 42)
                    paint.textSize = dp(15f)
                    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    canvas.drawText("全社合計", dp(12f), top + rowHeight / 2f + dp(5f), paint)
                }
            }

            canvas.drawLine(0f, bottom, fixedWidth, bottom, gridPaint)
        }
        canvas.restore()
    }

    private fun drawHeader(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(241, 245, 249)
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight, paint)

        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = dp(12.5f)
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("取引先 / 品番 / 商品名", dp(12f), headerHeight / 2f + dp(5f), paint)

        canvas.save()
        canvas.clipRect(fixedWidth, 0f, width.toFloat(), headerHeight)
        var x = fixedWidth - offsetX
        for (month in months) {
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(monthLabel(month), x + monthWidth / 2f, headerHeight / 2f + dp(5f), paint)
            canvas.drawLine(x + monthWidth, 0f, x + monthWidth, headerHeight, gridPaint)
            x += monthWidth
        }
        paint.color = Color.rgb(30, 64, 175)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("合計", x + monthWidth / 2f, headerHeight / 2f + dp(5f), paint)
        canvas.drawLine(x + monthWidth, 0f, x + monthWidth, headerHeight, gridPaint)
        canvas.restore()

        paint.textAlign = Paint.Align.LEFT
        canvas.drawLine(0f, headerHeight, width.toFloat(), headerHeight, gridPaint)
    }

    private fun metricsFor(row: Row, month: String): SalesMetrics? = when (row) {
        is Row.CompanyHeader -> null
        is Row.ProductRow -> row.product.monthly[month]
        is Row.CompanyTotal -> row.monthly[month]
        is Row.GrandTotal -> row.monthly[month]
    }

    private fun totalFor(row: Row): SalesMetrics? = when (row) {
        is Row.CompanyHeader -> null
        is Row.ProductRow -> row.product.monthly.values.fold(SalesMetrics()) { acc, metric -> acc + metric }
        is Row.CompanyTotal -> row.grand
        is Row.GrandTotal -> row.grand
    }

    private fun drawMetricCell(canvas: Canvas, metrics: SalesMetrics?, x: Float, y: Float, w: Float, h: Float, emphasized: Boolean) {
        if (emphasized) {
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(247, 250, 255)
            canvas.drawRect(x, y, x + w, y + h, paint)
        }
        val left = x + dp(10f)
        if (metrics == null) {
            paint.color = Color.rgb(148, 163, 184)
            paint.textSize = dp(13f)
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("-", x + w / 2f, y + h / 2f + dp(4f), paint)
            return
        }
        drawMetricLine(canvas, "金額", "¥${valueFormat.format(metrics.amount)}", left, y + dp(25f), w - dp(20f), emphasized)
        drawMetricLine(canvas, "数量", valueFormat.format(metrics.quantity), left, y + dp(51f), w - dp(20f), emphasized)
        drawMetricLine(canvas, "平米", valueFormat.format(metrics.sqm), left, y + dp(77f), w - dp(20f), emphasized)
    }

    private fun drawMetricLine(canvas: Canvas, label: String, value: String, x: Float, baseline: Float, maxWidth: Float, emphasized: Boolean) {
        paint.textAlign = Paint.Align.LEFT
        paint.color = if (emphasized) Color.rgb(30, 64, 175) else Color.rgb(100, 116, 139)
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = dp(10.5f)
        canvas.drawText(label, x, baseline, paint)

        val labelWidth = dp(34f)
        paint.color = Color.rgb(30, 41, 59)
        paint.typeface = if (emphasized) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        paint.textSize = dp(12f)
        canvas.drawText(ellipsize(value, maxWidth - labelWidth), x + labelWidth, baseline, paint)
    }

    private fun drawRowBackground(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, row: Row, index: Int) {
        paint.style = Paint.Style.FILL
        paint.color = when (row) {
            is Row.CompanyHeader -> Color.rgb(232, 240, 255)
            is Row.CompanyTotal -> Color.rgb(239, 246, 255)
            is Row.GrandTotal -> Color.rgb(224, 242, 254)
            is Row.ProductRow -> if (index % 2 == 0) Color.WHITE else Color.rgb(250, 252, 255)
        }
        canvas.drawRect(left, top, right, bottom, paint)
    }

    private fun drawFrozenDivider(canvas: Canvas) {
        paint.color = Color.rgb(100, 116, 139)
        paint.strokeWidth = dp(1.5f)
        canvas.drawLine(fixedWidth, 0f, fixedWidth, height.toFloat(), paint)
        paint.strokeWidth = 1f
    }

    private fun monthLabel(month: String): String {
        val parts = month.split('-')
        return if (parts.size == 2) "${parts[0]}年${parts[1].toIntOrNull() ?: parts[1]}月" else month
    }

    private fun drawEllipsized(canvas: Canvas, text: String, x: Float, baseline: Float, maxWidth: Float) {
        canvas.drawText(ellipsize(text, maxWidth), x, baseline, paint)
    }

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val target = maxWidth - paint.measureText(ellipsis)
        if (target <= 0f) return ellipsis
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (paint.measureText(text, 0, mid) <= target) low = mid else high = mid - 1
        }
        return text.substring(0, low) + ellipsis
    }

    private fun maxScrollX(): Float {
        val columns = months.size + if (rows.isNotEmpty()) 1 else 0
        return max(0f, columns * monthWidth - max(0f, width - fixedWidth))
    }

    private fun maxScrollY(): Float = max(0f, rows.size * rowHeight - max(0f, height - headerHeight))

    private operator fun SalesMetrics.plus(other: SalesMetrics): SalesMetrics = SalesMetrics(
        amount = amount + other.amount,
        quantity = quantity + other.quantity,
        sqm = sqm + other.sqm
    )
}
