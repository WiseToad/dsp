package com.groupstp.dsp.reporting.convert

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.groupstp.dsp.domain.utils.AppIoUtils
import com.groupstp.dsp.reporting.ReportDataFormat
import com.groupstp.dsp.reporting.ReportingProperties
import org.apache.poi.hssf.usermodel.HSSFCellStyle
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hssf.util.HSSFColor.HSSFColorPredefined
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.*
import java.lang.Math.round
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Конвертер данных из формата Excel в формат HTML с использованием библиотеки Apache POI.
 */
@Component
class XlsxToHtmlConverter(
    private val reportingProperties: ReportingProperties
): FormatConverter {

    val log = LoggerFactory.getLogger(javaClass)

    override val inputFormat = ReportDataFormat.XLSX
    override val outputFormat = ReportDataFormat.HTML

    private val alignmentStyles = mapOf(
        // HorizontalAlignment.GENERAL - skipped
        HorizontalAlignment.LEFT to "left",
        HorizontalAlignment.CENTER to "center",
        HorizontalAlignment.RIGHT to "right",
        HorizontalAlignment.FILL to "left",
        HorizontalAlignment.JUSTIFY to "left",
        HorizontalAlignment.CENTER_SELECTION to "center",
        HorizontalAlignment.DISTRIBUTED to "left"
    )
    private val defaultAlignment = "right"

    private val verticalAlignmentStyles = mapOf(
        VerticalAlignment.TOP to "top",
        VerticalAlignment.CENTER to "middle",
        VerticalAlignment.BOTTOM to "bottom",
        VerticalAlignment.JUSTIFY to "top",
        VerticalAlignment.DISTRIBUTED to "top"
    )

    private val borderStyles = mapOf(
        BorderStyle.NONE to "none",
        BorderStyle.THIN to "solid 1px",
        BorderStyle.MEDIUM to "solid 2px",
        BorderStyle.DASHED to "dashed 1px",
        BorderStyle.DOTTED to "dotted 1px",
        BorderStyle.THICK to "solid 3px",
        BorderStyle.DOUBLE to "double 3px",
        BorderStyle.HAIR to "solid 1px",
        BorderStyle.MEDIUM_DASHED to "dashed 2px",
        BorderStyle.DASH_DOT to "dashed 1px",
        BorderStyle.MEDIUM_DASH_DOT to "dashed 2px",
        BorderStyle.DASH_DOT_DOT to "dashed 1px",
        BorderStyle.MEDIUM_DASH_DOT_DOT to "dashed 2px",
        BorderStyle.SLANTED_DASH_DOT to "dashed 2px"
    )

    // Твипы (twips) в пиксели
    private val TW_TO_PX_FACTOR = 96f / 1440f

    private interface ConvertHelper {
        fun writeColorStyles(style: CellStyle)
    }

    override fun convert(input: InputStream, params: Map<String, Any?>, output: OutputStream) {

        val converter = Converter(jacksonObjectMapper().convertValue(params, Params::class.java))
        converter.convert(input, output)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Params (
        val cropLeft: Int = 0,
        val cropTop: Int = 0,
        val cropRight: Int = 0,
        val cropBottom: Int = 0,
        val maxRowCount: Int = Int.MAX_VALUE
    )

    private inner class Converter (
        private val params: Params
    ) {
        private lateinit var xlsxWorkbook: XlsxWorkbook
        private lateinit var writer: Writer

        private lateinit var convertHelper: ConvertHelper

        // HSSF (xls) convert helper
        private inner class HssfConvertHelper: ConvertHelper {

            private val colors = (xlsxWorkbook.workbook as HSSFWorkbook).customPalette
            private val automaticColorIndex = HSSFColorPredefined.AUTOMATIC.color.index

            override fun writeColorStyles(style: CellStyle) {
                with(style as HSSFCellStyle) {
                    writeColorStyle("background-color", fillForegroundColor)
                    writeColorStyle("color", getFont(xlsxWorkbook.workbook).color)
                    writeColorStyle("border-left-color", leftBorderColor)
                    writeColorStyle("border-right-color", rightBorderColor)
                    writeColorStyle("border-top-color", topBorderColor)
                    writeColorStyle("border-bottom-color", bottomBorderColor)
                }
            }

            private fun writeColorStyle(attr: String, colorIndex: Short?) {
                if (colorIndex != null && colorIndex != automaticColorIndex) {
                    val color = colors.getColor(colorIndex)
                    if (color != null) {
                        val rgb = color.triplet
                        writer.append(String.format("  %s: #%02x%02x%02x;\n", attr, rgb[0], rgb[1], rgb[2]))
                    }
                }
            }
        }

        // XSSF (xlsx) convert helper
        private inner class XssfConvertHelper: ConvertHelper {

            override fun writeColorStyles(style: CellStyle) {
                with(style as XSSFCellStyle) {
                    writeColorStyle("background-color", fillForegroundXSSFColor)
                    writeColorStyle("color", font.xssfColor)
                    //TODO: Добить цвета бордеров (см. как сделано в конвертере в PDF)
                }
            }

            private fun writeColorStyle(attr: String, color: XSSFColor?) {
                if(color?.isAuto == false) {
                    val rgb = if(color.hasTint()) color.rgbWithTint else color.rgb
                    if(rgb != null) {
                        writer.append(String.format("  %s: #%02x%02x%02x;\n", attr, rgb[0], rgb[1], rgb[2]))
                    }
                }
            }
        }

        fun convert(input: InputStream, output: OutputStream) {

            xlsxWorkbook = XlsxWorkbook(input)

            writer = BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8))
            writer.use {
                convertHelper = when(xlsxWorkbook.workbook){
                    is HSSFWorkbook -> HssfConvertHelper()
                    is XSSFWorkbook -> XssfConvertHelper()
                    else -> throw IllegalArgumentException("Неподдерживаемый формат Excel-файла")
                }

                writer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                    .append("<!DOCTYPE html>\n")
                    .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"ru\">\n")
                    .append("<head>\n")
                    .append("<title>Report</title>\n")
                    .append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>\n")

                writer.append("<style>\n")
                writer.append(AppIoUtils.loadResourceText(reportingProperties.resourceLocation + "excel-defaults.css"))
                xlsxWorkbook.usedStyles.values.forEach(this::writeStyle)
                writer.append("</style>\n")

                writer.append("</head>\n")
                    .append("<body>\n")

                for(sheetIndex in 0 until xlsxWorkbook.workbook.numberOfSheets) {
                    val xlsxTable = xlsxWorkbook.getTableFromSheet(sheetIndex,
                        params.cropLeft, params.cropTop, params.cropRight, params.cropBottom)
                    convertTable(xlsxTable)
                }

                writer.append("</body>\n")
                    .append("</html>\n")
            }
        }

        private fun writeStyle(style: CellStyle) {

            writer.append(".excelDefaults .style_${style.index} {\n")

            // Wrap text
            if(style.wrapText) {
                writer.append("  overflow-wrap: break-word;\n")
            }

            // Alignment
            alignmentStyles[style.alignment]?.let { value ->
                writer.append("  text-align: $value;\n")
            }
            verticalAlignmentStyles[style.verticalAlignment]?.let { value ->
                writer.append("  vertical-align: $value;\n")
            }

            // Font
            val font = xlsxWorkbook.workbook.getFontAt(style.fontIndexAsInt)
            if (font.bold) {
                writer.append("  font-weight: bold;\n")
            }
            if (font.italic) {
                writer.append("  font-style: italic;\n")
            }
            val fontHeight = font.fontHeightInPoints.toInt().let {
                if(it == 9) 10 else it
            }
            writer.append("  font-size: ${fontHeight}pt;\n")

            // Border
            borderStyles[style.borderLeft]?.let { value ->
                writer.append("  border-left: $value;\n")
            }
            borderStyles[style.borderRight]?.let { value ->
                writer.append("  border-right: $value;\n")
            }
            borderStyles[style.borderTop]?.let { value ->
                writer.append("  border-top: $value;\n")
            }
            borderStyles[style.borderBottom]?.let { value ->
                writer.append("  border-bottom: $value;\n")
            }

            // Colors
            convertHelper.writeColorStyles(style)

            writer.append("}\n")
        }

        private fun convertTable(xlsxTable: XlsxWorkbook.Table) {

            writer.append("<table class=\"excelDefaults\" style=\"width: ${xlsxTable.width}px;\">\n")
            for(columnNum in xlsxTable.firstColumnNum..xlsxTable.lastColumnNum) {
                if(!xlsxTable.sheet.isColumnHidden(columnNum)) {
                    writer.append("<col style=\"width: ${xlsxTable.columnWidths[columnNum]}px;\"/>\n")
                }
            }

            writer.append("<tbody>\n")
            val skipRowCount = xlsxTable.traverse(params.maxRowCount) { cellLayout, cellContent, cellPicture ->
                when(cellLayout.type) {
                    XlsxWorkbook.CellLayoutType.ROW_START -> {
                        val rowHeight = round(
                            (xlsxTable.sheet.getRow(cellLayout.rowNum)?.height ?: xlsxTable.sheet.defaultRowHeight)
                                .toFloat() * TW_TO_PX_FACTOR * 10f
                        ).toFloat() / 10f
                        writer.append("  <tr style=\"height: ${rowHeight}px;\">\n")
                    }

                    XlsxWorkbook.CellLayoutType.ROW_END -> {
                        writer.append("  </tr>\n")
                    }

                    XlsxWorkbook.CellLayoutType.CELL -> {
                        if(cellPicture != null) {
                            val encodedData = Base64.getEncoder().encodeToString(cellPicture.data.data)
                            writer.append("    <td class=\"style_0\" style=\"padding: 0; position: relative;\">" +
                                (cellContent?.url?.let { "<a href=\"$it\">" } ?: "") +
                                "<img style=\"width: ${cellPicture.width}px; height: ${cellPicture.height}px; " +
                                "position: absolute; left: 0; top: 0;\" " +
                                "src=\"data:${cellPicture.data.mimeType};base64,$encodedData\">" +
                                (cellContent?.url?.let { "</a>" } ?: "") + "</td>\n")
                        } else if(cellContent == null) {
                            writer.append("    <td class=\"style_0\">&#160;</td>\n")
                        } else {
                            var attrs = "class=\"style_${cellContent.style.index}\""

                            val styleAlignment = alignmentStyles[cellContent.style.alignment] ?: defaultAlignment
                            val alignment = alignmentStyles[cellContent.alignment] ?: styleAlignment
                            if(alignment != styleAlignment) {
                                attrs += " style=\"text-align: $alignment;\""
                            }

                            if(cellLayout.columnSpan > 1) {
                                attrs += " colspan=\"${cellLayout.columnSpan}\""
                            }
                            if (cellLayout.rowSpan > 1) {
                                attrs += " rowspan=\"${cellLayout.rowSpan}\""
                            }

                            var text = cellContent.text.replace("\n", "<br>")
                            if (text.isEmpty()) {
                                text = "&#160;"
                            } else if (!cellContent.style.wrapText) {
                                text = text.replace(Regex("\\s"), "&#160;")
                            }
                            if (cellContent.url != null) {
                                text = "<a href=\"${cellContent.url}\">$text</a>"
                            }

                            writer.append("    <td $attrs>$text</td>\n")
                        }
                    }

                    else -> {} // get rid of annoying warns
                }
            }

            if(skipRowCount > 0) {
                writer.append("  <tr>\n")
                writer.append("    <td style=\"border: none; text-align: left;\">(еще&#160;$skipRowCount&#160;строк)</td>\n")
                writer.append("  </tr>\n")
            }

            writer.append("</tbody>\n")
                .append("</table>\n")
        }
    }
}
