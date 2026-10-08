from pathlib import Path

p = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger/MainActivity.kt")
s = p.read_text()

old = """                SalesMetrics(acc.amount + v.amount, acc.quantity + v.quantity, acc.sqm + v.sqm)
"""
new = """                SalesMetrics(
                    acc.amount + v.amount,
                    acc.quantity + v.quantity,
                    acc.sqm + v.sqm,
                    acc.cost + v.cost,
                    acc.costKnown && v.costKnown
                )
"""
if s.count(old) < 3:
    raise SystemExit("sales export aggregate anchors missing")
s = s.replace(old, new, 3)

old = """            width(5, 16.0); width(6, 14.0); width(7, 14.0)
            row(ExcelExport.text("売上分析", ExcelExport.TITLE))
            merge(1, 1, 1, 7)
"""
new = """            width(5, 16.0); width(6, 14.0); width(7, 14.0); width(8, 16.0); width(9, 13.0)
            row(ExcelExport.text("売上分析", ExcelExport.TITLE))
            merge(1, 1, 1, 9)
"""
if old not in s: raise SystemExit("sales export widths anchor missing")
s=s.replace(old,new,1)

old = """                ExcelExport.num(grand.amount, ExcelExport.MONEY),
                ExcelExport.num(grand.sqm, ExcelExport.SQM),
                ExcelExport.num(grand.quantity, ExcelExport.QTY)
            )
"""
new = """                ExcelExport.num(grand.amount, ExcelExport.MONEY),
                ExcelExport.num(grand.sqm, ExcelExport.SQM),
                ExcelExport.num(grand.quantity, ExcelExport.QTY),
                if (grand.costKnown) ExcelExport.num(grand.grossProfit, ExcelExport.ACTUAL) else ExcelExport.text("—", ExcelExport.MUTED),
                if (grand.grossRate != null) ExcelExport.num(grand.grossRate!!, ExcelExport.rateStyle(grand.grossProfit, grand.amount)) else ExcelExport.text("—", ExcelExport.MUTED)
            )
"""
if old not in s: raise SystemExit("grand export row anchor missing")
s=s.replace(old,new,1)

old = """                ExcelExport.text("金額", ExcelExport.HEADER),
                ExcelExport.text("平米", ExcelExport.HEADER),
                ExcelExport.text("数量", ExcelExport.HEADER)
            )
"""
new = """                ExcelExport.text("金額", ExcelExport.HEADER),
                ExcelExport.text("平米", ExcelExport.HEADER),
                ExcelExport.text("数量", ExcelExport.HEADER),
                ExcelExport.text("粗利", ExcelExport.HEADER),
                ExcelExport.text("粗利率", ExcelExport.HEADER)
            )
"""
if old not in s: raise SystemExit("list header anchor missing")
s=s.replace(old,new,1)

old = """                ExcelExport.num(company.amount, ExcelExport.MONEY),
                ExcelExport.num(company.sqm, ExcelExport.SQM),
                ExcelExport.num(company.quantity, ExcelExport.QTY)
            )
"""
new = """                ExcelExport.num(company.amount, ExcelExport.MONEY),
                ExcelExport.num(company.sqm, ExcelExport.SQM),
                ExcelExport.num(company.quantity, ExcelExport.QTY),
                if (company.costKnown) ExcelExport.num(company.grossProfit, ExcelExport.ACTUAL) else ExcelExport.text("—", ExcelExport.MUTED),
                if (company.grossRate != null) ExcelExport.num(company.grossRate!!, ExcelExport.rateStyle(company.grossProfit, company.amount)) else ExcelExport.text("—", ExcelExport.MUTED)
            )
"""
if old not in s: raise SystemExit("company row anchor missing")
s=s.replace(old,new,1)

old = """                    ExcelExport.num(total.amount, ExcelExport.MONEY),
                    ExcelExport.num(total.sqm, ExcelExport.SQM),
                    ExcelExport.num(total.quantity, ExcelExport.QTY)
                )
"""
new = """                    ExcelExport.num(total.amount, ExcelExport.MONEY),
                    ExcelExport.num(total.sqm, ExcelExport.SQM),
                    ExcelExport.num(total.quantity, ExcelExport.QTY),
                    if (total.costKnown) ExcelExport.num(total.grossProfit, ExcelExport.ACTUAL) else ExcelExport.text("—", ExcelExport.MUTED),
                    if (total.grossRate != null) ExcelExport.num(total.grossRate!!, ExcelExport.rateStyle(total.grossProfit, total.amount)) else ExcelExport.text("—", ExcelExport.MUTED)
                )
"""
if old not in s: raise SystemExit("product row anchor missing")
s=s.replace(old,new,1)

old = """            width(5, 10.0); width(6, 16.0); width(7, 14.0); width(8, 14.0)
            row(ExcelExport.text("売上分析・月別", ExcelExport.TITLE))
            merge(1, 1, 1, 8)
"""
new = """            width(5, 10.0); width(6, 16.0); width(7, 14.0); width(8, 14.0); width(9, 16.0); width(10, 13.0)
            row(ExcelExport.text("売上分析・月別", ExcelExport.TITLE))
            merge(1, 1, 1, 10)
"""
if old not in s: raise SystemExit("month sheet widths anchor missing")
s=s.replace(old,new,1)

# Change the month-sheet header only, after its declaration.
pos = s.find('val monthSheet = ExcelExport.Sheet("月別")')
if pos < 0: raise SystemExit("month sheet declaration missing")
q = s.find('                ExcelExport.text("数量", ExcelExport.HEADER)', pos)
if q < 0: raise SystemExit("month quantity header missing")
needle = '                ExcelExport.text("数量", ExcelExport.HEADER)'
replacement = needle + ',\n                ExcelExport.text("粗利", ExcelExport.HEADER),\n                ExcelExport.text("粗利率", ExcelExport.HEADER)'
s = s[:q] + s[q:].replace(needle, replacement, 1)

old = """                        ExcelExport.num(v.amount, ExcelExport.MONEY),
                        ExcelExport.num(v.sqm, ExcelExport.SQM),
                        ExcelExport.num(v.quantity, ExcelExport.QTY)
                    )
"""
new = """                        ExcelExport.num(v.amount, ExcelExport.MONEY),
                        ExcelExport.num(v.sqm, ExcelExport.SQM),
                        ExcelExport.num(v.quantity, ExcelExport.QTY),
                        if (v.costKnown) ExcelExport.num(v.grossProfit, ExcelExport.ACTUAL) else ExcelExport.text("—", ExcelExport.MUTED),
                        if (v.grossRate != null) ExcelExport.num(v.grossRate!!, ExcelExport.rateStyle(v.grossProfit, v.amount)) else ExcelExport.text("—", ExcelExport.MUTED)
                    )
"""
if old not in s: raise SystemExit("month data row anchor missing")
s=s.replace(old,new,1)

p.write_text(s)
