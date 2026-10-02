package jp.co.ichika.salesledger

import android.app.Activity
import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MiniCoPaTisApplication : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.javaClass.name != QUOTE_ACTIVITY) return
        activity.window.decorView.post {
            installQuoteWarning(activity)
            installQuoteImageSave(activity)
        }
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
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(activity, 12), 0, 0)
                setSingleLine(true)
                visibility = View.GONE
            }
            detailCard.addView(warning, LinearLayout.LayoutParams(-1, -2))

            detailText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    updateWarning(warning, s?.toString().orEmpty())
                }
            })
            updateWarning(warning, detailText.text?.toString().orEmpty())
        }
    }

    private fun installQuoteImageSave(activity: Activity) {
        runCatching {
            val root = findQuoteRoot(activity) ?: return
            if (root.findViewWithTag<View>(MEMO_CONTAINER_TAG) != null) return

            val memoContainer = LinearLayout(activity).apply {
                tag = MEMO_CONTAINER_TAG
                orientation = LinearLayout.VERTICAL
                setPadding(dp(activity, 18), dp(activity, 16), dp(activity, 18), dp(activity, 16))
                background = roundedRect(activity, Color.WHITE, Color.TRANSPARENT, 0, 16)
            }
            val memoLabel = TextView(activity).apply {
                text = "メモ欄"
                textSize = 15f
                setTextColor(Color.rgb(30, 41, 59))
                setTypeface(typeface, Typeface.BOLD)
            }
            val memoEdit = EditText(activity).apply {
                tag = MEMO_EDIT_TAG
                hint = ""
                textSize = 16f
                setTextColor(Color.rgb(31, 41, 55))
                gravity = Gravity.TOP or Gravity.START
                minLines = 3
                maxLines = 5
                isSingleLine = false
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setPadding(0, dp(activity, 10), 0, 0)
                background = null
            }
            memoContainer.addView(memoLabel, LinearLayout.LayoutParams(-1, -2))
            memoContainer.addView(memoEdit, LinearLayout.LayoutParams(-1, -2))

            val existingDetailCard = reflectedView(activity, "detailCard")
            val memoLp = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(activity, 20)
                bottomMargin = dp(activity, 18)
            }
            if (existingDetailCard?.parent === root) {
                root.addView(memoContainer, root.indexOfChild(existingDetailCard) + 1, memoLp)
            } else {
                root.addView(memoContainer, memoLp)
            }

            val showButton = findTextView(root, "金額を見せる")
            val saveButton = TextView(activity).apply {
                tag = SAVE_BUTTON_TAG
                text = "見積内容を画像保存"
                textSize = 17f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(39, 110, 79))
                background = roundedRect(activity, Color.WHITE, Color.rgb(39, 110, 79), 1, 14)
                setOnClickListener { saveQuoteImage(activity, root, memoEdit) }
            }

            val lp = LinearLayout.LayoutParams(-1, dp(activity, 58)).apply {
                topMargin = dp(activity, 9)
            }
            if (showButton?.parent === root) {
                root.addView(saveButton, root.indexOfChild(showButton) + 1, lp)
            } else {
                root.addView(saveButton, lp)
            }
        }
    }

    private fun saveQuoteImage(activity: Activity, root: LinearLayout, memoEdit: EditText) {
        val memo = memoEdit.text?.toString()?.trim().orEmpty()
        findTextView(root, "計算する")?.performClick()
        root.postDelayed({
            runCatching {
                val detailCard = reflectedView(activity, "detailCard") as? LinearLayout
                val detailText = reflectedView(activity, "detailText") as? TextView
                val unitText = reflectedView(activity, "unitText") as? TextView

                if (detailCard == null || detailText == null || unitText == null ||
                    detailCard.visibility != View.VISIBLE ||
                    detailText.text.isNullOrBlank() ||
                    unitText.text.toString().contains("—")
                ) {
                    Toast.makeText(activity, "計算できる内容を入力してください", Toast.LENGTH_SHORT).show()
                    return@postDelayed
                }

                val imageView = buildQuoteImageView(activity, root, memo, detailText, unitText)
                val bitmap = renderToBitmap(imageView, 1080)
                val savedName = saveBitmapToPictures(activity, bitmap, memo)
                bitmap.recycle()
                Toast.makeText(
                    activity,
                    "画像を保存しました\nPictures/MiniCoPaTis/$savedName",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure {
                Toast.makeText(activity, "画像保存に失敗しました", Toast.LENGTH_LONG).show()
            }
        }, 100L)
    }

    private fun buildQuoteImageView(
        activity: Activity,
        quoteRoot: LinearLayout,
        memo: String,
        detailText: TextView,
        unitText: TextView
    ): LinearLayout {
        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 28), dp(activity, 30), dp(activity, 28), dp(activity, 30))
            setBackgroundColor(Color.rgb(247, 243, 234))
        }

        val length = findEditTextByHint(quoteRoot, "長")?.text?.toString().orEmpty()
        val width = findEditTextByHint(quoteRoot, "巾")?.text?.toString().orEmpty()
        val depth = findEditTextByHint(quoteRoot, "深")?.text?.toString().orEmpty()
        val process = findEditTextByHint(quoteRoot, "加工賃")?.text?.toString().orEmpty()
        val material = (reflectedView(activity, "materialButton") as? TextView)
            ?.text?.toString()?.replace("▼", "")?.trim().orEmpty()
        val flute = reflectedObject(activity, "selectedFlute")?.let { value ->
            runCatching { value.javaClass.getMethod("getLabel").invoke(value)?.toString() }.getOrNull()
                ?: value.toString()
        }.orEmpty()

        val summary = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 22), dp(activity, 20), dp(activity, 22), dp(activity, 20))
            background = roundedRect(activity, Color.WHITE, Color.TRANSPARENT, 0, 18)
        }
        summary.addView(sectionTitle(activity, "見積内容"))
        summary.addView(bodyText(activity, "フルート　${flute.ifBlank { "-" }}"))
        summary.addView(bodyText(activity, "寸法　　　${length.ifBlank { "-" }} × ${width.ifBlank { "-" }} × ${depth.ifBlank { "-" }} mm"))
        summary.addView(bodyText(activity, "材質　　　${material.ifBlank { "-" }}"))
        summary.addView(bodyText(activity, "加工賃　　${process.ifBlank { "-" }} 円/㎡"))
        summary.addView(TextView(activity).apply {
            text = "見積単価　${unitText.text}"
            textSize = 24f
            setTextColor(Color.rgb(39, 110, 79))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(activity, 14), 0, 0)
        })
        outer.addView(summary, fullWidthWrap().apply { bottomMargin = dp(activity, 16) })

        val detailCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 22), dp(activity, 20), dp(activity, 22), dp(activity, 20))
            background = roundedRect(activity, Color.WHITE, Color.TRANSPARENT, 0, 18)
        }
        detailCard.addView(sectionTitle(activity, "計算詳細"))
        detailCard.addView(TextView(activity).apply {
            text = detailText.text
            textSize = 15f
            setTextColor(Color.rgb(31, 41, 55))
            setLineSpacing(dp(activity, 2).toFloat(), 1.08f)
            setPadding(0, dp(activity, 10), 0, 0)
        })

        val warning = (reflectedView(activity, "detailCard") as? LinearLayout)
            ?.findViewWithTag<TextView>(WARNING_TAG)
        if (warning != null && warning.visibility == View.VISIBLE && warning.text.isNotBlank()) {
            detailCard.addView(TextView(activity).apply {
                text = warning.text
                textSize = 16f
                setTextColor(Color.rgb(198, 40, 40))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(activity, 14), 0, 0)
            })
        }
        outer.addView(detailCard, fullWidthWrap().apply { bottomMargin = dp(activity, 16) })

        val memoCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 22), dp(activity, 20), dp(activity, 22), dp(activity, 20))
            background = roundedRect(activity, Color.WHITE, Color.TRANSPARENT, 0, 18)
        }
        memoCard.addView(sectionTitle(activity, "メモ欄"))
        memoCard.addView(TextView(activity).apply {
            text = memo
            textSize = 15f
            setTextColor(Color.rgb(31, 41, 55))
            setLineSpacing(dp(activity, 2).toFloat(), 1.08f)
            setPadding(0, dp(activity, 10), 0, 0)
            minLines = 3
        })
        outer.addView(memoCard, fullWidthWrap())
        return outer
    }

    private fun renderToBitmap(view: View, widthPx: Int): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, widthPx, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(widthPx, view.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun saveBitmapToPictures(activity: Activity, bitmap: Bitmap, memo: String): String {
        val safeTitle = memo.lineSequence().firstOrNull().orEmpty()
            .replace(Regex("""[\\/:*?"<>|\r\n]"""), "_")
            .trim()
            .take(36)
            .ifBlank { "見積" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN).format(Date())
        val fileName = "MiniCoPaTis_${safeTitle}_$stamp.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MiniCoPaTis")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = activity.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) error("PNG compress failed")
                } ?: error("OutputStream unavailable")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Throwable) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: activity.filesDir
            val folder = java.io.File(dir, "MiniCoPaTis").apply { mkdirs() }
            java.io.File(folder, fileName).outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) error("PNG compress failed")
            }
        }
        return fileName
    }

    private fun updateWarning(warning: TextView, detail: String) {
        val panel1 = readMillimeters(detail, "一面")
        val panel2 = readMillimeters(detail, "二面")
        val widthTotal = readMillimeters(detail, "巾合計")
        val panelSum = if (panel1 != null && panel2 != null) panel1 + panel2 else null
        val message = when {
            widthTotal != null && widthTotal > 1450.0 -> "幅合計が1450mmを超えた為 印刷機不可です"
            panelSum != null && panelSum > 2300.0 -> "1面+2面が2300mmを超えた為 印刷機不可です"
            panel1 != null && panel1 > 900.0 -> "1面が900mmを超えた為 2Pです"
            panel2 != null && panel2 > 750.0 -> "2面が750mmを超えた為 2Pです"
            panelSum != null && panelSum > 1335.0 -> "1面+2面が1335mmを超えた為 2Pです"
            else -> null
        }
        warning.text = message.orEmpty()
        warning.visibility = if (message == null) View.GONE else View.VISIBLE
    }

    private fun findQuoteRoot(activity: Activity): LinearLayout? {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findScrollChildLinearLayout(content)
    }

    private fun findScrollChildLinearLayout(view: View): LinearLayout? {
        if (view is ScrollView && view.childCount > 0) {
            val child = view.getChildAt(0)
            if (child is LinearLayout) return child
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findScrollChildLinearLayout(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findTextView(view: View, exactText: String): TextView? {
        if (view is TextView && view.text?.toString() == exactText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findTextView(view.getChildAt(i), exactText)?.let { return it }
            }
        }
        return null
    }

    private fun findEditTextByHint(view: View, hint: String): EditText? {
        if (view is EditText && view.hint?.toString() == hint) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findEditTextByHint(view.getChildAt(i), hint)?.let { return it }
            }
        }
        return null
    }

    private fun reflectedView(activity: Activity, fieldName: String): View? =
        reflectedObject(activity, fieldName) as? View

    private fun reflectedObject(activity: Activity, fieldName: String): Any? =
        runCatching {
            activity.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }.get(activity)
        }.getOrNull()

    private fun sectionTitle(activity: Activity, value: String): TextView = TextView(activity).apply {
        text = value
        textSize = 16f
        setTextColor(Color.rgb(31, 41, 55))
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun bodyText(activity: Activity, value: String): TextView = TextView(activity).apply {
        text = value
        textSize = 15f
        setTextColor(Color.rgb(31, 41, 55))
        setPadding(0, dp(activity, 7), 0, 0)
    }

    private fun roundedRect(activity: Activity, fill: Int, stroke: Int, strokeWidth: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(activity, radius).toFloat()
            if (strokeWidth > 0) setStroke(dp(activity, strokeWidth), stroke)
        }

    private fun fullWidthWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)

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
        private const val MEMO_CONTAINER_TAG = "minicopatis_quote_memo_container"
        private const val MEMO_EDIT_TAG = "minicopatis_quote_memo_edit"
        private const val SAVE_BUTTON_TAG = "minicopatis_quote_save_button"
    }
}
