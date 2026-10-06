from pathlib import Path
import re

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

# Quote history screens use the same non-edge-to-edge compatibility theme as the original quote screen.
manifest = Path("ledgerapp/app/src/main/AndroidManifest.xml")
m = manifest.read_text()
for name in [".QuoteHistoryActivity", ".QuoteWorkspaceActivity"]:
    old = f'android:name="{name}"\n            android:screenOrientation="unspecified"'
    new = f'android:name="{name}"\n            android:theme="@style/QuoteCompatTheme"\n            android:screenOrientation="unspecified"'
    if old in m:
        m = m.replace(old, new, 1)
manifest.write_text(m)

# Rebuild the order screen as true date blocks with nested company cards.
p = root / "SalesDashboardActivity.kt"
s = p.read_text()

new_days = r'''            days.forEach { (date,companies) ->
                val dayCount=companies.values.sumOf { it.size }
                val dayAmount=companies.values.flatten().sumOf { it.second.amount }
                val daySqm=companies.values.flatten().sumOf { it.second.sqm }

                val dayBlock=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL
                    setPadding(dp(8),dp(8),dp(8),dp(10))
                    background=android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.rgb(242,247,252))
                        cornerRadius=dp(18).toFloat()
                        setStroke(dp(2),Color.rgb(157,184,216))
                    }
                }

                val dayHeader=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL
                    setPadding(dp(12),dp(10),dp(12),dp(10))
                    background=android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.rgb(221,233,248))
                        cornerRadius=dp(14).toFloat()
                        setStroke(dp(1),Color.rgb(157,184,216))
                    }
                }
                val dayHeadRow=LinearLayout(this).apply {
                    orientation=LinearLayout.HORIZONTAL
                    gravity=Gravity.CENTER_VERTICAL
                }
                dayHeadRow.addView(
                    label(date,15.5f,Color.rgb(42,67,108),true),
                    LinearLayout.LayoutParams(0,-2,1f)
                )
                dayHeadRow.addView(label("${companies.size}社・${dayCount}件",11f,Color.rgb(78,94,120),true))
                dayHeader.addView(dayHeadRow)

                fun headerMetric(title:String,value:String,color:Int,fill:Int,stroke:Int)=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL
                    gravity=Gravity.CENTER
                    setPadding(dp(8),dp(5),dp(8),dp(6))
                    background=android.graphics.drawable.GradientDrawable().apply {
                        setColor(fill)
                        cornerRadius=dp(11).toFloat()
                        setStroke(dp(1),stroke)
                    }
                    addView(label(title,9.5f,Color.rgb(105,116,132),false))
                    addView(label(value,11.5f,color,true).apply {
                        gravity=Gravity.CENTER
                        setPadding(0,dp(2),0,0)
                    })
                }
                val dayMetrics=LinearLayout(this).apply {
                    orientation=LinearLayout.HORIZONTAL
                    setPadding(0,dp(8),0,0)
                }
                dayMetrics.addView(
                    headerMetric("金額","${money.format(dayAmount)}円",Color.rgb(15,105,76),Color.rgb(225,245,235),Color.rgb(171,217,193)),
                    LinearLayout.LayoutParams(0,-2,1f)
                )
                dayMetrics.addView(
                    headerMetric("平米","${DecimalFormat("#,##0.##").format(daySqm)}㎡",Color.rgb(52,81,160),Color.rgb(229,237,252),Color.rgb(179,198,235)),
                    LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(7) }
                )
                dayHeader.addView(dayMetrics)
                dayBlock.addView(dayHeader)

                companies.entries.forEachIndexed { companyIndex, entry ->
                    val company=entry.key
                    val rows=entry.value
                    val key="$date|$company"
                    val open=key in expandedOrderGroups
                    val amount=rows.sumOf { it.second.amount }
                    val sqm=rows.sumOf { it.second.sqm }

                    val group=LinearLayout(this).apply {
                        orientation=LinearLayout.VERTICAL
                        setPadding(dp(11),dp(10),dp(11),dp(10))
                        background=android.graphics.drawable.GradientDrawable().apply {
                            setColor(if(open) Color.rgb(241,250,245) else Color.WHITE)
                            cornerRadius=dp(14).toFloat()
                            setStroke(dp(2), if(open) Color.rgb(15,91,70) else Color.rgb(196,207,220))
                        }
                        setOnClickListener {
                            if(open) expandedOrderGroups.remove(key) else expandedOrderGroups.add(key)
                            showOrderRows()
                        }
                    }

                    val heading=LinearLayout(this).apply {
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                    }
                    heading.addView(
                        label(company,15f,Color.rgb(15,23,42),true).apply { maxLines=2 },
                        LinearLayout.LayoutParams(0,-2,1f)
                    )
                    heading.addView(label(if(open)"⌃" else "⌄",18f,Color.rgb(100,116,139),true))
                    group.addView(heading)

                    fun companyMetric(title:String,value:String,color:Int,fill:Int,stroke:Int)=LinearLayout(this).apply {
                        orientation=LinearLayout.VERTICAL
                        gravity=Gravity.CENTER
                        setPadding(dp(5),dp(5),dp(5),dp(6))
                        background=android.graphics.drawable.GradientDrawable().apply {
                            setColor(fill)
                            cornerRadius=dp(11).toFloat()
                            setStroke(dp(1),stroke)
                        }
                        addView(label(title,9f,Color.rgb(105,116,132),false).apply { gravity=Gravity.CENTER })
                        addView(label(value,10.8f,color,true).apply {
                            gravity=Gravity.CENTER
                            setPadding(0,dp(2),0,0)
                        })
                    }

                    val summary=LinearLayout(this).apply {
                        orientation=LinearLayout.HORIZONTAL
                        setPadding(0,dp(8),0,0)
                    }
                    summary.addView(
                        companyMetric("件数","${rows.size}件",Color.rgb(64,75,92),Color.rgb(238,241,245),Color.rgb(203,210,220)),
                        LinearLayout.LayoutParams(0,-2,1f)
                    )
                    summary.addView(
                        companyMetric("金額","${money.format(amount)}円",Color.rgb(10,103,72),Color.rgb(225,245,235),Color.rgb(171,217,193)),
                        LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) }
                    )
                    summary.addView(
                        companyMetric("平米","${DecimalFormat("#,##0.##").format(sqm)}㎡",Color.rgb(45,76,157),Color.rgb(229,237,252),Color.rgb(179,198,235)),
                        LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(6) }
                    )
                    group.addView(summary)

                    if(open) {
                        val divider=TextView(this).apply {
                            setBackgroundColor(Color.rgb(216,224,233))
                        }
                        group.addView(divider,LinearLayout.LayoutParams(-1,dp(1)).apply {
                            topMargin=dp(9)
                            bottomMargin=dp(2)
                        })

                        rows.forEachIndexed { rowIndex, pair ->
                            val category=pair.first
                            val line=pair.second
                            val detail=LinearLayout(this).apply {
                                orientation=LinearLayout.VERTICAL
                                setPadding(dp(10),dp(9),dp(10),dp(9))
                                background=android.graphics.drawable.GradientDrawable().apply {
                                    setColor(if(rowIndex%2==0) Color.WHITE else Color.rgb(248,250,252))
                                    cornerRadius=dp(12).toFloat()
                                    setStroke(dp(1),Color.rgb(207,216,226))
                                }
                            }
                            val title=LinearLayout(this).apply {
                                orientation=LinearLayout.HORIZONTAL
                                gravity=Gravity.CENTER_VERTICAL
                            }
                            title.addView(
                                label(category.ifBlank { "その他" },9.5f,Color.rgb(52,81,160),true).apply {
                                    gravity=Gravity.CENTER
                                    setPadding(dp(7),dp(4),dp(7),dp(4))
                                    background=android.graphics.drawable.GradientDrawable().apply {
                                        setColor(Color.rgb(225,234,251))
                                        cornerRadius=dp(9).toFloat()
                                        setStroke(dp(1),Color.rgb(177,197,234))
                                    }
                                }
                            )
                            title.addView(
                                label(line.item,12.8f,Color.rgb(42,55,73),true).apply {
                                    setPadding(dp(8),0,0,0)
                                    maxLines=2
                                },
                                LinearLayout.LayoutParams(0,-2,1f)
                            )
                            detail.addView(title)

                            val metrics=LinearLayout(this).apply {
                                orientation=LinearLayout.HORIZONTAL
                                setPadding(0,dp(7),0,0)
                            }
                            metrics.addView(
                                label("数量\n${line.qty}",10.5f,Color.rgb(71,85,105),true).apply { gravity=Gravity.CENTER },
                                LinearLayout.LayoutParams(0,-2,1f)
                            )
                            metrics.addView(
                                label("金額\n${money.format(line.amount)}円",10.5f,Color.rgb(15,105,76),true).apply { gravity=Gravity.CENTER },
                                LinearLayout.LayoutParams(0,-2,1f)
                            )
                            metrics.addView(
                                label("平米\n${DecimalFormat("#,##0.##").format(line.sqm)}㎡",10.5f,Color.rgb(52,81,160),true).apply { gravity=Gravity.CENTER },
                                LinearLayout.LayoutParams(0,-2,1f)
                            )
                            detail.addView(metrics)

                            val reps=listOf(line.sales,line.receiver).filter { it.isNotBlank() }.joinToString(" / ")
                            if(reps.isNotBlank()) {
                                detail.addView(label("担当　$reps",10.5f,Color.GRAY,false).apply {
                                    setPadding(0,dp(6),0,0)
                                })
                            }
                            group.addView(detail,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })
                        }
                    }

                    dayBlock.addView(group,LinearLayout.LayoutParams(-1,-2).apply {
                        topMargin=dp(if(companyIndex==0) 9 else 7)
                    })
                }

                box.addView(dayBlock,LinearLayout.LayoutParams(-1,-2).apply {
                    leftMargin=dp(1)
                    rightMargin=dp(1)
                    bottomMargin=dp(14)
                })
            }
'''

pattern = r'            days\.forEach \{ \(date,companies\) ->.*?(?=        \}\n    \}\n\n    private fun choose)'
s, n = re.subn(pattern, lambda _m: new_days, s, count=1, flags=re.S)
if n != 1:
    raise SystemExit("order day block replacement failed")

p.write_text(s)

# Version.
build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, c1 = re.subn(r"versionCode\s+\d+", "versionCode 37", b, count=1)
b, c2 = re.subn(r"versionName\s+'[^']+'", "versionName '1.9.1'", b, count=1)
if c1 != 1 or c2 != 1:
    raise SystemExit("version declarations not found")
build.write_text(b)
