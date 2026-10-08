package jp.co.ichika.salesledger

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExcelExport {
    const val NORMAL = 0
    const val TITLE = 1
    const val HEADER = 2
    const val SECTION = 3
    const val MONEY = 4
    const val SQM = 5
    const val QTY = 6
    const val TARGET = 7
    const val ACTUAL = 8
    const val RATE_GOOD = 9
    const val RATE_NEAR = 10
    const val RATE_WARN = 11
    const val RATE_BAD = 12
    const val MUTED = 13

    data class Cell(val text: String? = null, val number: Double? = null, val style: Int = NORMAL)
    data class Merge(val row1: Int, val col1: Int, val row2: Int, val col2: Int)

    class Sheet(val name: String) {
        val rows = mutableListOf<List<Cell>>()
        val widths = linkedMapOf<Int, Double>()
        val merges = mutableListOf<Merge>()

        fun row(vararg cells: Cell) { rows.add(cells.toList()) }
        fun blank() { rows.add(emptyList()) }
        fun width(column1Based: Int, width: Double) { widths[column1Based] = width }
        fun merge(row1: Int, col1: Int, row2: Int, col2: Int) { merges += Merge(row1, col1, row2, col2) }
    }

    fun text(value: String, style: Int = NORMAL) = Cell(text = value, style = style)
    fun num(value: Double, style: Int = NORMAL) = Cell(number = value, style = style)

    fun rateStyle(actual: Double, target: Double): Int {
        if (target <= 0.0) return MUTED
        val r = actual / target
        return when {
            r >= 1.0 -> RATE_GOOD
            r >= 0.9 -> RATE_NEAR
            r >= 0.7 -> RATE_WARN
            else -> RATE_BAD
        }
    }

    fun save(context: Context, fileName: String, sheets: List<Sheet>): String {
        require(sheets.isNotEmpty())
        val clean = sanitizeFileName(if (fileName.endsWith(".xlsx", true)) fileName else "$fileName.xlsx")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, clean)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MiniCoPaTis")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("保存先を作成できませんでした")
            try {
                resolver.openOutputStream(uri)?.use { writeWorkbook(it, sheets) }
                    ?: error("保存先を開けませんでした")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return "Download/MiniCoPaTis/$clean"
        }

        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MiniCoPaTis")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, clean)
        FileOutputStream(file).use { writeWorkbook(it, sheets) }
        return file.absolutePath
    }

    private fun writeWorkbook(output: OutputStream, sheets: List<Sheet>) {
        ZipOutputStream(output).use { zip ->
            fun entry(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            entry("[Content_Types].xml", contentTypes(sheets.size))
            entry("_rels/.rels", rootRels())
            entry("xl/workbook.xml", workbookXml(sheets))
            entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            entry("xl/styles.xml", stylesXml())
            sheets.forEachIndexed { index, sheet ->
                entry("xl/worksheets/sheet${index + 1}.xml", sheetXml(sheet))
            }
        }
    }

    private fun contentTypes(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..count) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun rootRels(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbookXml(sheets: List<Sheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { index, sheet ->
            append("""<sheet name="${escAttr(safeSheetName(sheet.name))}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..count) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${count + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun sheetXml(sheet: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (sheet.widths.isNotEmpty()) {
            append("<cols>")
            sheet.widths.forEach { (col, width) ->
                append("""<col min="$col" max="$col" width="$width" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        sheet.rows.forEachIndexed { rowIndex, row ->
            val r = rowIndex + 1
            append("""<row r="$r">""")
            row.forEachIndexed { colIndex, cell ->
                val ref = columnName(colIndex + 1) + r
                if (cell.number != null) {
                    append("""<c r="$ref" s="${cell.style}"><v>${finite(cell.number)}</v></c>""")
                } else {
                    append("""<c r="$ref" s="${cell.style}" t="inlineStr"><is><t xml:space="preserve">${escText(cell.text.orEmpty())}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        if (sheet.merges.isNotEmpty()) {
            append("""<mergeCells count="${sheet.merges.size}">""")
            sheet.merges.forEach {
                append("""<mergeCell ref="${columnName(it.col1)}${it.row1}:${columnName(it.col2)}${it.row2}"/>""")
            }
            append("</mergeCells>")
        }
        append("</worksheet>")
    }

    private fun stylesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="4">
  <numFmt numFmtId="164" formatCode="#,##0"/>
  <numFmt numFmtId="165" formatCode="#,##0.0"/>
  <numFmt numFmtId="166" formatCode="#,##0.##"/>
  <numFmt numFmtId="167" formatCode="0.0%"/>
</numFmts>
<fonts count="7">
  <font><sz val="11"/><name val="Calibri"/></font>
  <font><b/><sz val="16"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  <font><b/><sz val="11"/><color rgb="FF0F6950"/><name val="Calibri"/></font>
  <font><b/><sz val="11"/><color rgb="FF3451A0"/><name val="Calibri"/></font>
  <font><b/><sz val="11"/><color rgb="FFB56B13"/><name val="Calibri"/></font>
  <font><b/><sz val="11"/><color rgb="FFBE3A3A"/><name val="Calibri"/></font>
</fonts>
<fills count="8">
  <fill><patternFill patternType="none"/></fill>
  <fill><patternFill patternType="gray125"/></fill>
  <fill><patternFill patternType="solid"><fgColor rgb="FF0F5B46"/><bgColor indexed="64"/></patternFill></fill>
  <fill><patternFill patternType="solid"><fgColor rgb="FFEAF6EF"/><bgColor indexed="64"/></patternFill></fill>
  <fill><patternFill patternType="solid"><fgColor rgb="FFEFF3FD"/><bgColor indexed="64"/></patternFill></fill>
  <fill><patternFill patternType="solid"><fgColor rgb="FFF3F4F6"/><bgColor indexed="64"/></patternFill></fill>
  <fill><patternFill patternType="solid"><fgColor rgb="FFFDF4E1"/><bgColor indexed="64"/></patternFill></fill>
  <fill><patternFill patternType="solid"><fgColor rgb="FFFDEBEB"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
  <border><left/><right/><top/><bottom/><diagonal/></border>
  <border><left style="thin"><color rgb="FFD9E0E8"/></left><right style="thin"><color rgb="FFD9E0E8"/></right><top style="thin"><color rgb="FFD9E0E8"/></top><bottom style="thin"><color rgb="FFD9E0E8"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="14">
  <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"><alignment vertical="center"/></xf>
  <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="center"/></xf>
  <xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
  <xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="center"/></xf>
  <xf numFmtId="164" fontId="3" fillId="3" borderId="1" xfId="0" applyNumberFormat="1"/>
  <xf numFmtId="165" fontId="4" fillId="4" borderId="1" xfId="0" applyNumberFormat="1"/>
  <xf numFmtId="166" fontId="0" fillId="5" borderId="1" xfId="0" applyNumberFormat="1"/>
  <xf numFmtId="165" fontId="4" fillId="4" borderId="1" xfId="0" applyNumberFormat="1"/>
  <xf numFmtId="165" fontId="3" fillId="3" borderId="1" xfId="0" applyNumberFormat="1"/>
  <xf numFmtId="167" fontId="3" fillId="3" borderId="1" xfId="0" applyNumberFormat="1"/>
  <xf numFmtId="167" fontId="3" fillId="4" borderId="1" xfId="0" applyNumberFormat="1"/>
  <xf numFmtId="167" fontId="5" fillId="6" borderId="1" xfId="0" applyNumberFormat="1"/>
  <xf numFmtId="167" fontId="6" fillId="7" borderId="1" xfId="0" applyNumberFormat="1"/>
  <xf numFmtId="0" fontId="0" fillId="5" borderId="1" xfId="0"><alignment vertical="center"/></xf>
</cellXfs>
</styleSheet>"""

    private fun finite(value: Double): String =
        if (value.isFinite()) java.lang.Double.toString(value) else "0"

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|]"""), "_").take(120)

    private fun safeSheetName(value: String): String =
        value.replace(Regex("""[\\/?*\[\]:]"""), "_").take(31).ifBlank { "Sheet" }

    private fun columnName(index1Based: Int): String {
        var n = index1Based
        val b = StringBuilder()
        while (n > 0) {
            n--
            b.append(('A'.code + (n % 26)).toChar())
            n /= 26
        }
        return b.reverse().toString()
    }

    private fun escText(value: String): String = buildString {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> if (ch.code >= 0x20 || ch == '\n' || ch == '\t' || ch == '\r') append(ch)
            }
        }
    }

    private fun escAttr(value: String): String =
        escText(value).replace("\"", "&quot;").replace("'", "&apos;")
}
