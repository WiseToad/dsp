package com.groupstp.dsp.reporting.convert

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.groupstp.dsp.domain.utils.AppIoUtils
import com.groupstp.dsp.reporting.ReportDataFormat
import com.groupstp.dsp.reporting.ReportingProperties
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.poi.hssf.usermodel.HSSFCellStyle
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hssf.util.HSSFColor
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.math.max
import kotlin.math.min

//TODO: Добавить входные параметры cell padding, шрифтов(?), центрирования по горизонтали, масштаба
//TODO: Обрабатывать все страницы экселя, возможно с разными входными параметрами

//TODO: Обработка картинок и (возможно) Excel-диаграмм
//TODO: Колонтитулы, номера страниц?

@Component
class XlsxToPdfConverter (
    reportingProperties: ReportingProperties
): FormatConverter {

    override val inputFormat = ReportDataFormat.XLSX
    override val outputFormat = ReportDataFormat.PDF

    private val fontLocation = reportingProperties.resourceLocation + "fonts/"

    private val fontLocations = mapOf (
        PdfDocument.FontKey(false, false) to fontLocation + "GentiumPlus/GentiumPlus-Regular.ttf",
        PdfDocument.FontKey(true,  false) to fontLocation + "GentiumPlus/GentiumPlus-Bold.ttf",
        PdfDocument.FontKey(false, true)  to fontLocation + "GentiumPlus/GentiumPlus-Italic.ttf",
        PdfDocument.FontKey(true,  true)  to fontLocation + "GentiumPlus/GentiumPlus-BoldItalic.ttf"
    )
    private val defaultFontLocation = fontLocation + "GentiumPlus/GentiumPlus-Regular.ttf"

    private fun solidLineStyle(width: Float)  = PdfDocument.LineStyle(width, floatArrayOf(), 0f)
    private fun dottedLineStyle(width: Float) = PdfDocument.LineStyle(width, floatArrayOf(1f), 0f)
    private fun dashedLineStyle(width: Float) = PdfDocument.LineStyle(width, floatArrayOf(5f), 0f)

    private val borderStyles = mapOf(
        BorderStyle.NONE to null,
        BorderStyle.THIN to solidLineStyle(1f),
        BorderStyle.MEDIUM to solidLineStyle(2f),
        BorderStyle.DASHED to dashedLineStyle(1f),
        BorderStyle.DOTTED to dottedLineStyle(1f),
        BorderStyle.THICK to solidLineStyle(3f),
        BorderStyle.DOUBLE to solidLineStyle(3f),
        BorderStyle.HAIR to solidLineStyle(1f),
        BorderStyle.MEDIUM_DASHED to dashedLineStyle(2f),
        BorderStyle.DASH_DOT to dashedLineStyle(1f),
        BorderStyle.MEDIUM_DASH_DOT to dashedLineStyle(2f),
        BorderStyle.DASH_DOT_DOT to dashedLineStyle(1f),
        BorderStyle.MEDIUM_DASH_DOT_DOT to dashedLineStyle(2f),
        BorderStyle.SLANTED_DASH_DOT to dashedLineStyle(2f)
    )

    private val horizontalAlignments = mapOf(
        HorizontalAlignment.GENERAL to PdfDocument.HorizontalAlignment.LEFT,
        HorizontalAlignment.LEFT to PdfDocument.HorizontalAlignment.LEFT,
        HorizontalAlignment.CENTER to PdfDocument.HorizontalAlignment.CENTER,
        HorizontalAlignment.RIGHT to PdfDocument.HorizontalAlignment.RIGHT,
        HorizontalAlignment.FILL to PdfDocument.HorizontalAlignment.LEFT,
        HorizontalAlignment.JUSTIFY to PdfDocument.HorizontalAlignment.LEFT,
        HorizontalAlignment.CENTER_SELECTION to PdfDocument.HorizontalAlignment.CENTER,
        HorizontalAlignment.DISTRIBUTED to PdfDocument.HorizontalAlignment.LEFT
    )

    private val verticalAlignments = mapOf(
        VerticalAlignment.TOP to PdfDocument.VerticalAlignment.TOP,
        VerticalAlignment.CENTER to PdfDocument.VerticalAlignment.CENTER,
        VerticalAlignment.BOTTOM to PdfDocument.VerticalAlignment.BOTTOM,
        VerticalAlignment.JUSTIFY to PdfDocument.VerticalAlignment.TOP,
        VerticalAlignment.DISTRIBUTED to PdfDocument.VerticalAlignment.TOP
    )

    private interface ConvertHelper {
        // Конвертация CellStyle (Apache POI) в шаблонную PdfDocument.Cell
        fun convertCellStyle(cellStyle: CellStyle): PdfDocument.Cell
    }

    // Миллиметры в пункты
    private val MM_TO_PT_FACTOR = 720f / 254f
    // Пиксели в пункты
    private val PX_TO_PT_FACTOR = 72f / 96f
    // Пункты в пиксели
    private val PT_TO_PX_FACTOR = 96f / 72f
    // Твипы (twips) в пункты
    private val TW_TO_PT_FACTOR = 1f / 20f

    override fun convert(input: InputStream, params: Map<String, Any?>, output: OutputStream) {

        val converter = Converter(jacksonObjectMapper().convertValue(params, Params::class.java))
        converter.convert(input, output)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private class Params (
        val cropLeft: Int = 0,
        val cropTop: Int = 0,
        val cropRight: Int = 0,
        val cropBottom: Int = 0,

        val isLandscape: Boolean = false,

        // Поля печати в миллиметрах
        val marginLeft: Int = 10,
        val marginTop: Int = 10,
        val marginRight: Int = 10,
        val marginBottom: Int = 10
    )

    private inner class Converter (
        private val params: Params
    ) {
        private lateinit var xlsxWorkbook: XlsxWorkbook
        private lateinit var pdfDocument: PdfDocument

        private lateinit var cellTemplates: Map<Int, PdfDocument.Cell>

        // HSSF (xls) convert helper
        private inner class HssfConvertHelper: ConvertHelper {

            private val palette = (xlsxWorkbook.workbook as HSSFWorkbook).customPalette

            override fun convertCellStyle(cellStyle: CellStyle): PdfDocument.Cell {

                return PdfDocument.Cell().apply {
                    val hssfCellStyle = cellStyle as HSSFCellStyle

                    backgroundColor = hssfCellStyle.fillForegroundColorColor?.let { hssfColor ->
                        convertColor(hssfColor)
                    }

                    borderLeft = borderStyles[cellStyle.borderLeft]?.let { lineStyle ->
                        PdfDocument.Border(lineStyle, convertColor(cellStyle.leftBorderColor))
                    }
                    borderTop = borderStyles[cellStyle.borderTop]?.let { lineStyle ->
                        PdfDocument.Border(lineStyle, convertColor(cellStyle.topBorderColor))
                    }
                    borderRight = borderStyles[cellStyle.borderRight]?.let { lineStyle ->
                        PdfDocument.Border(lineStyle, convertColor(cellStyle.rightBorderColor))
                    }
                    borderBottom = borderStyles[cellStyle.borderBottom]?.let { lineStyle ->
                        PdfDocument.Border(lineStyle, convertColor(cellStyle.bottomBorderColor))
                    }

                    val excelFont = xlsxWorkbook.workbook.getFontAt(cellStyle.fontIndexAsInt)

                    paragraph = PdfDocument.Paragraph (
                        convertFont(excelFont),
                        convertFontSize(excelFont.fontHeightInPoints),
                        convertColor(excelFont.color),
                        convertHorizontalAlignment(cellStyle.alignment),
                        convertVerticalAlignment(cellStyle.verticalAlignment),
                        emptyList()
                    )
                }
            }

            // TODO: Cделать аргумент nullable, для консистентности с аналогичным методом в XssfCellStyleConverter?
            private fun convertColor(colorIndex: Short): Color =
                convertColor(palette.getColor(colorIndex) ?: HSSFColor.HSSFColorPredefined.AUTOMATIC.color)

            // TODO: Cделать аргумент nullable, для консистентности с аналогичным методом в XssfCellStyleConverter?
            private fun convertColor(hssfColor: HSSFColor): Color {
                val rgb = hssfColor.triplet
                return Color(rgb[0].toInt(), rgb[1].toInt(), rgb[2].toInt())
            }
        }

        // XSSF (xlsx) convert helper
        private inner class XssfConvertHelper: ConvertHelper {

            override fun convertCellStyle(cellStyle: CellStyle): PdfDocument.Cell {

                return PdfDocument.Cell().apply {
                    val xssfCellStyle = cellStyle as XSSFCellStyle

                    backgroundColor = xssfCellStyle.fillForegroundColorColor?.let { xssfColor ->
                        convertColor(xssfColor)
                    }

                    borderLeft = borderStyles[cellStyle.borderLeft]?.let { lineStyle ->
                        PdfDocument.Border(lineStyle, convertColor(xssfCellStyle.leftBorderXSSFColor))
                    }
                    borderTop = borderStyles[cellStyle.borderTop]?.let { lineStyle ->
                        PdfDocument.Border(lineStyle, convertColor(xssfCellStyle.topBorderXSSFColor))
                    }
                    borderRight = borderStyles[cellStyle.borderRight]?.let { lineStyle ->
                        PdfDocument.Border(lineStyle, convertColor(xssfCellStyle.rightBorderXSSFColor))
                    }
                    borderBottom = borderStyles[cellStyle.borderBottom]?.let { lineStyle ->
                        PdfDocument.Border(lineStyle, convertColor(xssfCellStyle.bottomBorderXSSFColor))
                    }

                    val excelFont = xlsxWorkbook.workbook.getFontAt(cellStyle.fontIndexAsInt)

                    paragraph = PdfDocument.Paragraph (
                        convertFont(excelFont),
                        convertFontSize(excelFont.fontHeightInPoints),
                        convertColor(xssfCellStyle.font.xssfColor),
                        convertHorizontalAlignment(cellStyle.alignment),
                        convertVerticalAlignment(cellStyle.verticalAlignment),
                        emptyList()
                    )
                }
            }

            private fun convertColor(xssfColor: XSSFColor?): Color {
                var color = Color.BLACK
                if(xssfColor?.isAuto == false) {
                    val rgb = if(xssfColor.hasTint()) xssfColor.rgbWithTint else xssfColor.rgb
                    if(rgb != null) {
                        color = Color(rgb[0].toInt() and 0xFF, rgb[1].toInt() and 0xFF, rgb[2].toInt() and 0xFF)
                    }
                }
                return color
            }
        }

        private fun convertFont(excelFont: Font): PDFont {

            return pdfDocument.getFont(PdfDocument.FontKey(excelFont.bold, excelFont.italic)) { fontKey, document ->
                val fontLocation = fontLocations[fontKey] ?: defaultFontLocation
                AppIoUtils.loadResource(fontLocation) { resourceStream ->
                    PDType0Font.load(document, resourceStream)
                }
            }
        }

        private fun convertFontSize(excelFontSize: Short) =
            excelFontSize.toInt().let { if(it == 9) 10 else it }.toFloat()

        private fun convertHorizontalAlignment(horizontalAlignment: HorizontalAlignment) =
            horizontalAlignments[horizontalAlignment] ?: PdfDocument.HorizontalAlignment.LEFT

        private fun convertVerticalAlignment(verticalAlignment: VerticalAlignment) =
            verticalAlignments[verticalAlignment] ?: PdfDocument.VerticalAlignment.TOP

        fun convert(input: InputStream, output: OutputStream) {

            xlsxWorkbook = XlsxWorkbook(input)

            pdfDocument = PdfDocument()
            pdfDocument.use {
                val convertHelper = when(xlsxWorkbook.workbook) {
                    is HSSFWorkbook -> HssfConvertHelper()
                    is XSSFWorkbook -> XssfConvertHelper()
                    else -> throw IllegalArgumentException("Неподдерживаемый формат Excel-файла")
                }

                cellTemplates = xlsxWorkbook.usedStyles.map { (styleIndex, cellStyle) ->
                    styleIndex to convertHelper.convertCellStyle(cellStyle)
                }.toMap()

                for(sheetIndex in 0 until xlsxWorkbook.workbook.numberOfSheets) {
                    val xlsxTable = xlsxWorkbook.getTableFromSheet(sheetIndex,
                        params.cropLeft, params.cropTop, params.cropRight, params.cropBottom)
                    convertTable(xlsxTable)
                }
                pdfDocument.save(output)
            }
        }

        private fun convertTable(xlsxTable: XlsxWorkbook.Table) {

            fun getCellWidth(cellLayout: XlsxWorkbook.CellLayout, scale: Float): Float =
                cellLayout.width.toFloat() * scale * PX_TO_PT_FACTOR

            // Текущая строка таблицы
            var currentRow = PdfDocument.Row()
            // Предыдущая строка, для отложенной прорисовки границ ячейки, чтобы они не затирались контентом нижней строки
            var priorRow: PdfDocument.Row? = null

            // Координата текущей ячейки, накапливается при последовательной обработке ячеек строки
            var cellLeft = 0f

            // Вертикально объединенная ячейка
            class MergedCell (
                val cell: PdfDocument.Cell,
                val lastRowNum: Int
            )

            // Список вертикально объединенных ячеек (для горизонтального объединения идет другая, более простая обработка)
            val mergedCells = LinkedList<MergedCell>()

            // Отложенная строка, ожидающая прорисовки
            class PendingRow (
                val row: PdfDocument.Row,
                val height: Float
            )

            // Блок отложенных строк, который пока не может быть прорисован, т.к. пересекается вертикальным объединением
            val pendingRows = LinkedList<PendingRow>()

            // Высота уже прорисованной таблицы с начала страницы
            var drawnHeight = 0f
            // Высота отложенного блока, который пока накапливается
            var pendingHeight = 0f

            // Создание первой страницы, которая плюс ко всему создаст шаблонный лайаут для всех последующих страниц
            val (pageWidth, pageHeight) = if(params.isLandscape) {
                Pair(PDRectangle.A4.height, PDRectangle.A4.width)
            } else {
                Pair(PDRectangle.A4.width, PDRectangle.A4.height)
            }
            pdfDocument.startPage(pageWidth, pageHeight,
                params.marginLeft.toFloat() * MM_TO_PT_FACTOR,
                params.marginTop.toFloat() * MM_TO_PT_FACTOR,
                params.marginRight.toFloat() * MM_TO_PT_FACTOR,
                params.marginBottom.toFloat() * MM_TO_PT_FACTOR
            )

            // Масштаб "ужатия" Excel-таблицы в границы PDF-документа
            val scale = min(pdfDocument.printWidth * PT_TO_PX_FACTOR / xlsxTable.widthWithPictures.toFloat(), 1f)

            // Размер внутренних "полей" ячеек
            pdfDocument.cellPaddingHorizontal = 2f * scale
            pdfDocument.cellPaddingVertical = 0f * scale

            xlsxTable.traverse { cellLayout, cellContent, cellPicture ->
                when(cellLayout.type) {
                    XlsxWorkbook.CellLayoutType.ROW_START -> {
                        // Подготовка девственно чистой новой строки
                        currentRow = PdfDocument.Row()
                        cellLeft = 0f
                    }

                    XlsxWorkbook.CellLayoutType.CELL -> {
                        val cellWidth = getCellWidth(cellLayout, scale)
                        if(cellPicture != null && cellPicture.data.data.isNotEmpty()) {
                            val cell = PdfDocument.Cell().apply {
                                left = cellLeft
                                width = cellWidth
                                picture = PdfDocument.Picture(
                                    cellPicture.data.data,
                                    cellPicture.width.toFloat() * scale * PX_TO_PT_FACTOR,
                                    cellPicture.height.toFloat() * scale * PX_TO_PT_FACTOR
                                )
                            }
                            currentRow.cells.add(cell)
                        } else if(cellContent == null) {
                            val cell = PdfDocument.Cell().apply {
                                left = cellLeft
                                width = cellWidth
                            }
                            currentRow.cells.add(cell)
                        } else {
                            val cellTemplate = cellTemplates[cellContent.style.index.toInt()] ?: PdfDocument.Cell()
                            val textWidth = cellWidth - 2 * pdfDocument.cellPaddingHorizontal
                            val cell = makeCell(
                                cellTemplate, cellContent.text, textWidth,
                                cellContent.style.wrapText, cellContent.alignment, scale
                            ).apply {
                                left = cellLeft
                                width = cellWidth
                            }
                            if(cellLayout.rowSpan == 1) {
                                // Занесение ячейки в список текущей строки, если она не является вертикально объединенной
                                currentRow.cells.add(cell)
                            } else {
                                // Иначе - занесение ее в отдельный список
                                mergedCells.add(MergedCell(cell, cellLayout.lastRowNum))
                            }
                        }
                        cellLeft += cellWidth
                    }

                    XlsxWorkbook.CellLayoutType.SKIPPED_CELLS -> {
                        // Учет ширины ячеек, перекрытых вертикальным объединением
                        cellLeft += getCellWidth(cellLayout, scale)
                    }

                    XlsxWorkbook.CellLayoutType.ROW_END -> {
                        // Перенос вертикально объединенных ячеек, у которых достигнут нижний край, в список текущей строки
                        val i = mergedCells.iterator()
                        while(i.hasNext()) {
                            val mergedCell = i.next()
                            if(mergedCell.lastRowNum == cellLayout.rowNum) {
                                mergedCell.cell.top = -mergedCell.cell.height // смещаемся выше от верхнего края строки
                                currentRow.cells.add(mergedCell.cell)
                                i.remove()
                            }
                        }

                        // Расчет высоты строки по максимальной высоте составляющих ее ячеек
                        var rowHeight = (xlsxTable.sheet.getRow(cellLayout.rowNum)?.height
                            ?: xlsxTable.sheet.defaultRowHeight).toFloat() * scale * TW_TO_PT_FACTOR
                        var rowPictureHeight = 0f
                        for(cell in currentRow.cells) {
                            val textHeight = cell.paragraph?.let { paragraph ->
                                val fontMetrics = pdfDocument.getFontMetrics(paragraph.font)
                                fontMetrics.height * paragraph.fontSize * paragraph.textLines.size
                            } ?: 0f
                            // В cell.height для вертикально объединенных ячеек содержится накопленная высота, для прочих 0
                            rowHeight = max(rowHeight, textHeight + 2 * pdfDocument.cellPaddingVertical - cell.height)
                            rowPictureHeight = max(rowPictureHeight, cell.picture?.height ?: 0f)
                        }
                        val rowPictureOverheight = max(0f, rowPictureHeight - rowHeight)
                        // Корректировка высоты каждой ячейки в строке
                        for(cell in currentRow.cells) {
                            cell.height += rowHeight
                        }
                        // Накапливание высоты в вертикально объединенных ячейках
                        for(cell in mergedCells) {
                            cell.cell.height += rowHeight
                        }

                        // Вся дальнейшая прорисовка идет через добавление в отложенный блок, даже если строка одиночная
                        pendingRows.add(PendingRow(currentRow, rowHeight))
                        pendingHeight += rowHeight
                        if(pendingHeight + rowPictureOverheight > pdfDocument.printHeight) {
                            throw IllegalArgumentException("Невозможно уместить по высоте страницы блок строк: " +
                                "cлишком большая высота строки, либо слишком много перекрывающихся объединенных ячеек")
                        }

                        // Если отложенный блок (больше) не пересекается вериткальным объединением, то его прорисовка
                        if(mergedCells.isEmpty()) {
                            if(drawnHeight + pendingHeight + rowPictureOverheight > pdfDocument.printHeight) {
                                // Переход на новую страницу
                                priorRow?.let {
                                    pdfDocument.drawRowBorders(it)
                                }
                                priorRow = null

                                pdfDocument.startPage()
                                drawnHeight = 0f
                            }

                            // Прорисовка отложенного блока
                            while(pendingRows.isNotEmpty()) {
                                val pendingRow = pendingRows.remove()
                                pendingRow.row.left = 0f
                                pendingRow.row.top = drawnHeight
                                pdfDocument.drawRowContent(pendingRow.row)
                                drawnHeight += pendingRow.height

                                priorRow?.let {
                                    pdfDocument.drawRowBorders(it)
                                }
                                priorRow = pendingRow.row
                            }
                            pendingHeight = 0f
                        }
                    }

                    else -> {} // get rid of annoying warns
                }
            }

            priorRow?.let {
                pdfDocument.drawRowBorders(it)
            }
        }

        private fun makeCell(cellTemplate: PdfDocument.Cell, cellText: String, textWidth: Float,
                             textWrap: Boolean, textAlignment: HorizontalAlignment, scale: Float): PdfDocument.Cell
        {
            fun makeBorder(borderTemplate: PdfDocument.Border) =
                PdfDocument.Border(
                    PdfDocument.LineStyle(borderTemplate.lineStyle.width * scale,
                        borderTemplate.lineStyle.dashPattern, borderTemplate.lineStyle.dashPhase),
                    borderTemplate.color)

            return PdfDocument.Cell().apply {
                backgroundColor = cellTemplate.backgroundColor

                borderLeft = cellTemplate.borderLeft?.let { borderTemplate ->
                    makeBorder(borderTemplate)
                }
                borderTop = cellTemplate.borderTop?.let { borderTemplate ->
                    makeBorder(borderTemplate)
                }
                borderRight = cellTemplate.borderRight?.let { borderTemplate ->
                    makeBorder(borderTemplate)
                }
                borderBottom = cellTemplate.borderBottom?.let { borderTemplate ->
                    makeBorder(borderTemplate)
                }

                paragraph = cellTemplate.paragraph?.let { paragraphTemplate ->
                    val textLines = if(!textWrap) {
                        cellText.replace(Regex("[\\t]"), " ")
                            .split(Regex("[\\r\\n]"))
                    } else {
                        cellText.replace(Regex("[\\t]"), " ")
                            .split(Regex("[\\r\\n]")).flatMap { line ->
                                //TODO: Избавиться от этой единственной зависимости от библиотеки Boxable:
                                be.quodlibet.boxable.Paragraph(line,
                                    paragraphTemplate.font, paragraphTemplate.fontSize * scale,
                                    textWidth, be.quodlibet.boxable.HorizontalAlignment.LEFT
                                ).lines
                            }
                    }

                    PdfDocument.Paragraph (
                        paragraphTemplate.font,
                        paragraphTemplate.fontSize * scale,
                        paragraphTemplate.color,
                        convertHorizontalAlignment(textAlignment),
                        paragraphTemplate.verticalAlignment,
                        textLines
                    )
                }
            }
        }
    }
}
