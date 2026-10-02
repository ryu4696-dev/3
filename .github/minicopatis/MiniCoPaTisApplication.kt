package jp.co.ichika.salesledger

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class MiniCoPaTisApplication : Application(), Application.ActivityLifecycleCallbacks {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.javaClass.name != QUOTE_ACTIVITY) return
        activity.window.decorView.post { installQuoteWarning(activity) }
    }

    private fun installQuoteWarning(activity: Activity) {
        runCatching {
            val cls = activity.javaClass
            val detailCardField = cls.getDeclaredField("detailCard").apply { isAccessible = true }
            val detailTextField = cls.getDeclaredField("detailText").apply { isAccessible = true }

            val detailCard = detailCardField.get(activity) as? LinearLayout ?: return
            val detailText = detailTextField.get(activity) as? TextView ?: return

            if (detailCard.findViewWithTag<View>(WARNING_TAG) != null) return

            val warning = TextView(activity).apply {
                tag = WARNING_TAG
                setTextColor(Color.rgb(198, 40, 40))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(activity, 12), 0, 0)
                setSingleLine(true)
                visibility = View.GONE
            }
            detailCard.addView(
                warning,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    updateWarning(warning, s?.toString().orEmpty())
                }
            }
            detailText.addTextChangedListener(watcher)
            updateWarning(warning, detailText.text?.toString().orEmpty())
        }
    }

    private fun updateWarning(warning: TextView, detail: String) {
        val panel1 = readMillimeters(detail, "一面")
        val panel2 = readMillimeters(detail, "二面")
        val widthTotal = readMillimeters(detail, "巾合計")
        val panelSum = if (panel1 != null && panel2 != null) panel1 + panel2 else null

        val message = when {
            widthTotal != null && widthTotal > 1450.0 ->
                "幅合計が1450mmを超えた為 印刷機不可です"
            panelSum != null && panelSum > 2300.0 ->
                "1面+2面が2300mmを超えた為 印刷機不可です"
            panel1 != null && panel1 > 900.0 ->
                "1面が900mmを超えた為 2Pです"
            panel2 != null && panel2 > 750.0 ->
                "2面が750mmを超えた為 2Pです"
            panelSum != null && panelSum > 1335.0 ->
                "1面+2面が1335mmを超えた為 2Pです"
            else -> null
        }

        warning.text = message.orEmpty()
        warning.visibility = if (message == null) View.GONE else View.VISIBLE
    }

    private fun readMillimeters(detail: String, label: String): Double? {
        val pattern = Regex("(?m)^" + Regex.escape(label) + "\\s+([0-9]+(?:\\.[0-9]+)?)\\s*mm")
        return pattern.find(detail)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        private const val QUOTE_ACTIVITY = "jp.co.kobayashi.cardboardquote.MainActivity"
        private const val WARNING_TAG = "minicopatis_quote_warning"
    }
}
