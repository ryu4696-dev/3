from pathlib import Path
root=Path('/tmp/salestarget')

p=root/'app/src/main/res/layout/activity_main.xml'
s=p.read_text()
s=s.replace('<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"\n    android:layout_width="match_parent"', '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"\n    android:id="@+id/rootView"\n    android:layout_width="match_parent"')
s=s.replace('    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"\n        android:orientation="vertical" android:background="@color/navy" android:padding="18dp">', '    <LinearLayout android:id="@+id/topBar" android:layout_width="match_parent" android:layout_height="wrap_content"\n        android:orientation="vertical" android:background="@color/navy" android:padding="18dp">')
s=s.replace('''        <EditText android:id="@+id/searchBox" android:layout_width="match_parent" android:layout_height="48dp" android:layout_marginTop="14dp"
            android:hint="得意先名・番号で検索" android:singleLine="true" android:textSize="15sp" android:textColor="@color/text" android:textColorHint="#8A94A6"
            android:background="@drawable/edit_bg" android:paddingLeft="14dp" android:paddingRight="14dp"/>''','''        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="14dp"
            android:text="企業" android:textColor="#BFD0EA" android:textSize="11sp" android:textStyle="bold"/>
        <Spinner android:id="@+id/customerSpinner" android:layout_width="match_parent" android:layout_height="50dp" android:layout_marginTop="5dp"
            android:background="@drawable/edit_bg" android:paddingLeft="10dp" android:paddingRight="10dp"/>''')
s=s.replace('<ScrollView android:layout_width="match_parent" android:layout_height="match_parent" android:fillViewport="true">', '<ScrollView android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:fillViewport="true">')
s=s.replace('            <LinearLayout android:id="@+id/searchResults" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical" />\n\n','')
old='''                    <TextView android:id="@+id/tabCardboard" android:layout_width="0dp" android:layout_height="42dp" android:layout_weight="1" android:gravity="center" android:text="段ボール" android:textStyle="bold" android:textColor="#FFFFFF" android:background="@drawable/tab_on"/>
                    <TextView android:id="@+id/tabProduct" android:layout_width="0dp" android:layout_height="42dp" android:layout_weight="1" android:layout_marginLeft="8dp" android:gravity="center" android:text="商品" android:textStyle="bold" android:textColor="@color/text_muted" android:background="@drawable/tab_off"/>
                    <TextView android:id="@+id/tabPlate" android:layout_width="0dp" android:layout_height="42dp" android:layout_weight="1" android:layout_marginLeft="8dp" android:gravity="center" android:text="判型" android:textStyle="bold" android:textColor="@color/text_muted" android:background="@drawable/tab_off"/>'''
new='''                    <TextView android:id="@+id/tabTotal" android:layout_width="0dp" android:layout_height="42dp" android:layout_weight="1" android:gravity="center" android:text="合計" android:textSize="13sp" android:textStyle="bold" android:textColor="#FFFFFF" android:background="@drawable/tab_on"/>
                    <TextView android:id="@+id/tabCardboard" android:layout_width="0dp" android:layout_height="42dp" android:layout_weight="1" android:layout_marginLeft="6dp" android:gravity="center" android:text="段ボール" android:textSize="12sp" android:textStyle="bold" android:textColor="@color/text_muted" android:background="@drawable/tab_off"/>
                    <TextView android:id="@+id/tabProduct" android:layout_width="0dp" android:layout_height="42dp" android:layout_weight="1" android:layout_marginLeft="6dp" android:gravity="center" android:text="商品" android:textSize="13sp" android:textStyle="bold" android:textColor="@color/text_muted" android:background="@drawable/tab_off"/>
                    <TextView android:id="@+id/tabPlate" android:layout_width="0dp" android:layout_height="42dp" android:layout_weight="1" android:layout_marginLeft="6dp" android:gravity="center" android:text="判型" android:textSize="13sp" android:textStyle="bold" android:textColor="@color/text_muted" android:background="@drawable/tab_off"/>'''
s=s.replace(old,new)
s=s.replace('android:text="期間合計｜段ボール"','android:text="期間合計｜合計"')
p.write_text(s)

p=root/'app/src/main/java/dev/ryu4696/salestarget/MainActivity.kt'
s=p.read_text()
s=s.replace('import android.os.Bundle\n','import android.os.Bundle\nimport android.os.Build\n')
s=s.replace('import android.view.View\n','import android.view.View\nimport android.view.WindowInsets\n')
s=s.replace('import android.text.Editable\nimport android.text.TextWatcher\n','').replace('import android.view.inputmethod.InputMethodManager\n','')
s=s.replace('private var selectedCategory = "段ボール"','private var selectedCategory = "合計"')
s=s.replace('    private lateinit var searchBox: EditText\n    private lateinit var searchResults: LinearLayout\n','    private lateinit var customerSpinner: Spinner\n')
s=s.replace('    private lateinit var importBtn: Button\n    private lateinit var tabCardboard: TextView\n','    private lateinit var importBtn: Button\n    private lateinit var tabTotal: TextView\n    private lateinit var tabCardboard: TextView\n')
s=s.replace('''        bindViews()
        loadState()
        setupUi()
        selectedCustomer = customers.firstOrNull { it.id == "ALL" } ?: customers.firstOrNull()
        renderAll()''','''        bindViews()
        applySystemBarInsets()
        loadState()
        selectedCustomer = customers.firstOrNull { it.id == "ALL" } ?: customers.firstOrNull()
        setupUi()
        renderAll()''')
s=s.replace('        searchBox = findViewById(R.id.searchBox)\n        searchResults = findViewById(R.id.searchResults)\n','        customerSpinner = findViewById(R.id.customerSpinner)\n')
s=s.replace('        importBtn = findViewById(R.id.importBtn)\n        tabCardboard = findViewById(R.id.tabCardboard)\n','        importBtn = findViewById(R.id.importBtn)\n        tabTotal = findViewById(R.id.tabTotal)\n        tabCardboard = findViewById(R.id.tabCardboard)\n')
start=s.index('    private fun setupUi() {')
end=s.index('    private fun simpleItemSelected',start)
setup='''    private fun setupUi() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, MONTH_LABELS)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fromSpinner.adapter = adapter
        toSpinner.adapter = adapter
        fromSpinner.setSelection(0)
        toSpinner.setSelection(defaultEndMonthIndex())
        fromSpinner.onItemSelectedListener = simpleItemSelected { ensureValidPeriod(true); renderDetail() }
        toSpinner.onItemSelectedListener = simpleItemSelected { ensureValidPeriod(false); renderDetail() }
        refreshCustomerSpinner()
        customerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val c = customers.getOrNull(position) ?: return
                if (selectedCustomer?.id != c.id) { selectedCustomer = c; renderDetail() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        tabTotal.setOnClickListener { selectCategory("合計") }
        tabCardboard.setOnClickListener { selectCategory("段ボール") }
        tabProduct.setOnClickListener { selectCategory("商品") }
        tabPlate.setOnClickListener { selectCategory("判型") }
        importBtn.setOnClickListener { showImportDialog() }
    }

    private fun refreshCustomerSpinner(selectAll: Boolean = false) {
        val labels = customers.map { if (it.id == "ALL") "全体（販売目標対象すべて）" else "${it.name}　[${it.id}]" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        customerSpinner.adapter = adapter
        val wanted = if (selectAll) "ALL" else selectedCustomer?.id
        val pos = customers.indexOfFirst { it.id == wanted }.let { if (it >= 0) it else 0 }
        customerSpinner.setSelection(pos, false)
        selectedCustomer = customers.getOrNull(pos)
    }

    private fun applySystemBarInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val root = findViewById<View>(R.id.rootView)
        val topBar = findViewById<View>(R.id.topBar)
        topBar.setOnApplyWindowInsetsListener { v, insets ->
            val status = insets.getInsets(WindowInsets.Type.statusBars())
            val nav = insets.getInsets(WindowInsets.Type.navigationBars())
            v.setPadding(dp(18), dp(18) + status.top, dp(18), dp(18))
            root.setPadding(0, 0, 0, nav.bottom)
            insets
        }
        topBar.requestApplyInsets()
    }

'''
s=s[:start]+setup+s[end:]
s=s.replace('''        listOf(
            tabCardboard to "段ボール",
            tabProduct to "商品",
            tabPlate to "判型"''','''        listOf(
            tabTotal to "合計",
            tabCardboard to "段ボール",
            tabProduct to "商品",
            tabPlate to "判型"''')
s=s.replace('''    private fun renderAll() {
        updateTabs()
        renderSearchResults(searchBox.text?.toString().orEmpty())
        renderDetail()
    }

''','''    private fun renderAll() {
        updateTabs()
        renderDetail()
    }

''')
start=s.index('    private fun renderSearchResults(query: String) {')
end=s.index('    private fun renderDetail()',start)
s=s[:start]+s[end:]
series='''    private fun seriesFor(source: Map<String, DoubleArray>, category: String): DoubleArray {
        if (category != "合計") return source[category]?.copyOf() ?: DoubleArray(12)
        val out = DoubleArray(12)
        CATEGORIES.forEach { cat ->
            val src = source[cat] ?: return@forEach
            for (i in 0..11) out[i] += src.getOrElse(i) { 0.0 }
        }
        return out
    }

'''
s=s.replace('    private fun renderDetail() {',series+'    private fun renderDetail() {')
s=s.replace('        val targets = c.target[selectedCategory] ?: DoubleArray(12)\n        val actuals = c.actual[selectedCategory] ?: DoubleArray(12)','        val targets = seriesFor(c.target, selectedCategory)\n        val actuals = seriesFor(c.actual, selectedCategory)')
s=s.replace('''                runOnUiThread {
                    setBusy(false)
                    searchBox.setText("")
                    renderAll()''','''                runOnUiThread {
                    setBusy(false)
                    refreshCustomerSpinner(selectAll = true)
                    renderAll()''')
s=s.replace('初期データ読込済み｜右上のCSV更新から差し替えできます','初期データ読込済み｜上部のCSV更新から差し替えできます')
p.write_text(s)

p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 1','versionCode = 2').replace('versionName = "1.0"','versionName = "1.1"')
p.write_text(s)
