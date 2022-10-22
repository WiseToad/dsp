package com.groupstp.dsp.reporting.convert

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.slf4j.LoggerFactory
import java.awt.Color
import java.io.Closeable
import java.io.OutputStream
import kotlin.math.*

//TODO: Оптимизация потока (не дублировать вывод одинаковых идущих друг за другом атрибутов)

class PdfDocument: Closeable {

    val document = PDDocument()

    // Все в пунктах:

    var pageWidth = PDRectangle.A4.width
        private set
    var pageHeight = PDRectangle.A4.height
        private set

    var marginLeft = 0f
        private set
    var marginTop = 0f
        private set
    var marginRight = 0f
        private set
    var marginBottom = 0f
        private set

    val printWidth: Float
        get() {
            return max(0f, pageWidth - marginLeft - marginRight)
        }
    val printHeight: Float
        get() {
            return max(0f, pageHeight - marginTop - marginBottom)
        }

    var cellPaddingHorizontal = 0f
        set(value) {
            field = max(0f, value)
        }
    var cellPaddingVertical = 0f
        set(value) {
            field = max(0f, value)
        }

    data class FontKey (
        val isBold: Boolean,
        val isItalic: Boolean
    )

    private val fonts = mutableMapOf<FontKey, PDFont>()

    fun getFont(fontKey: FontKey, provideFont: (FontKey, PDDocument) -> PDFont): PDFont {
        return fonts.getOrPut(fontKey) {
            provideFont(fontKey, document)
        }
    }

    // Базовые метрики шрифта для размера 1pt
    class FontMetrics (
        val height: Float,
        val ascent: Float,
        val descent: Float
    )

    private val fontMetrics = mutableMapOf<String, FontMetrics>()

    fun getFontMetrics(font: PDFont): FontMetrics {

        return fontMetrics.getOrPut(font.name) {
            val descriptor = font.fontDescriptor
            val base = descriptor.xHeight / 1000f
            val ascent = descriptor.ascent / 1000f - base
            val descent = descriptor.descent / 1000f
            FontMetrics(base + ascent - descent, ascent, descent)
        }
    }

    class Row {
        var left: Float = 0f
        var top: Float = 0f
        var cells = mutableListOf<Cell>()
    }

    class Cell {
        var left: Float = 0f // относительно Row.left
        var top: Float = 0f  // относительно Row.top
        var width: Float = 0f
        var height: Float = 0f
        var backgroundColor: Color? = null
        var borderLeft: Border? = null
        var borderTop: Border? = null
        var borderRight: Border? = null
        var borderBottom: Border? = null
        var paragraph: Paragraph? = null
        var picture: Picture? = null
    }

    //TODO: Переопределить equals() и hashCode() для data-класса, ибо наличествует FloatArray
    data class LineStyle (
        val width: Float,
        val dashPattern: FloatArray,
        val dashPhase: Float
    )

    data class Border (
        val lineStyle: LineStyle,
        val color: Color
    )

    class Paragraph (
        val font: PDFont,
        val fontSize: Float,
        val color: Color,
        val horizontalAlignment: HorizontalAlignment,
        val verticalAlignment: VerticalAlignment,
        val textLines: List<String>
    )

    class Picture (
        val data: ByteArray,
        val width: Float,
        val height: Float
    )

    enum class HorizontalAlignment {
        LEFT,
        CENTER,
        RIGHT
    }

    enum class VerticalAlignment {
        TOP,
        CENTER,
        BOTTOM
    }

    private var page: PDPage? = null
    private var output: PDPageContentStream? = null

    @Suppress("NAME_SHADOWING")
    private fun setPageLayout(pageWidth: Float, pageHeight: Float, marginLeft: Float, marginTop: Float,
                              marginRight: Float, marginBottom: Float)
    {
        val marginLeft = max(0f, marginLeft)
        val marginTop = max(0f, marginTop)
        val marginRight = max(0f, marginRight)
        val marginBottom = max(0f, marginBottom)

        if(pageWidth <= marginLeft + marginRight || pageHeight <= marginTop + marginBottom) {
            throw IllegalArgumentException("Неверные поля печати, либо размеры страницы")
        }

        this.pageWidth = pageWidth
        this.pageHeight = pageHeight
        this.marginLeft = marginLeft
        this.marginTop = marginTop
        this.marginRight = marginRight
        this.marginBottom = marginBottom
    }

    fun startPage(pageWidth: Float, pageHeight: Float, marginLeft: Float, marginTop: Float,
                  marginRight: Float, marginBottom: Float)
    {
        setPageLayout(pageWidth, pageHeight, marginLeft, marginTop, marginRight, marginBottom)
        startPage()
    }

    fun startPage() {

        endPage()

        page = PDPage(PDRectangle(pageWidth, pageHeight))
        document.addPage(page)

        output = PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)
    }

    private fun endPage() {

        output?.close()
        output = null
    }

    fun drawRowContent(row: Row) {

        output?.apply {
            // Прорисовка фона
            val rowLeft = marginLeft + row.left
            val rowTop = pageHeight - marginTop - row.top
            for(cell in row.cells) {
                val cellLeft = rowLeft + cell.left
                val cellTop = rowTop - cell.top
                cell.backgroundColor?.let {
                    setNonStrokingColor(it)
                    addRect(cellLeft, cellTop - cell.height, cell.width, cell.height)
                    fill()
                }
            }

            // Прорисовка текста
            for(cell in row.cells) {
                cell.paragraph?.let { paragraph ->
                    val isTextPresent = paragraph.textLines.find { it.isNotBlank() } != null
                    if(isTextPresent) {
                        val textLeft = rowLeft + cell.left + cellPaddingHorizontal
                        val textTop = rowTop - cell.top - cellPaddingVertical
                        val textWidth = cell.width - 2 * cellPaddingHorizontal
                        val textHeight = cell.height - 2 * cellPaddingVertical

                        //TODO: Добавить клиппинг текста, для этого требуется анализ соседних ячеек - какие пустые какие нет
                        //saveGraphicsState()
                        //try {
                        //    addRect(textLeft, textTop - textHeight, textWidth, textHeight)
                        //    clip()

                            beginText()
                            try {
                                val fontMetrics = getFontMetrics(paragraph.font)
                                val lineHeight = fontMetrics.height * paragraph.fontSize
                                val lineX = textLeft
                                //TODO: Здесь явно какая-то неточность в понятиях метрик шрифта (почему descent вычитается??):
                                var lineY = textTop - lineHeight - fontMetrics.descent * paragraph.fontSize

                                //TODO: Сделать поизящнее:
                                val offsetY = when (paragraph.verticalAlignment) {
                                    VerticalAlignment.TOP ->
                                        0f
                                    VerticalAlignment.CENTER ->
                                        (textHeight - lineHeight * paragraph.textLines.size) / 2f
                                    VerticalAlignment.BOTTOM ->
                                        textHeight - lineHeight * paragraph.textLines.size
                                }
                                lineY -= offsetY

                                setFont(paragraph.font, paragraph.fontSize)
                                setNonStrokingColor(paragraph.color)

                                // Прорисовка идет от базовой линии шрифта, вместо (left, top) обозначаем (x, y)
                                var cursorX = 0f
                                var cursorY = 0f
                                for(line in paragraph.textLines) {
                                    //TODO: Сделать поизящнее:
                                    val offsetX = when (paragraph.horizontalAlignment) {
                                        HorizontalAlignment.LEFT ->
                                            0f
                                        HorizontalAlignment.CENTER ->
                                            (textWidth - paragraph.font.getStringWidth(line) / 1000f * paragraph.fontSize) / 2f
                                        HorizontalAlignment.RIGHT ->
                                            textWidth - paragraph.font.getStringWidth(line) / 1000f * paragraph.fontSize
                                    }

                                    newLineAtOffset(lineX + offsetX - cursorX, lineY - cursorY)
                                    cursorX = lineX + offsetX
                                    cursorY = lineY

                                    showText(line)

                                    lineY -= lineHeight
                                }
                            }
                            finally {
                                endText()
                            }
                        //}
                        //finally {
                        //    restoreGraphicsState()
                        //}
                    }
                }
            }

            // Прорисовка картинок
            for(cell in row.cells) {
                cell.picture?.let { picture ->
                    val cellLeft = rowLeft + cell.left
                    val cellTop  = rowTop - cell.top
                    val image = PDImageXObject.createFromByteArray(document, picture.data, "Picture")
                    drawImage(image, cellLeft, cellTop - picture.height, picture.width, picture.height)
                }
            }
        }
    }

    fun drawRowBorders(row: Row) {

        output?.apply {
            val rowLeft = marginLeft + row.left
            val rowTop  = pageHeight - marginTop - row.top
            for(cell in row.cells) {
                val cellLeft = rowLeft + cell.left
                val cellTop  = rowTop - cell.top
                cell.borderLeft?.let { borderLeft ->
                    drawBorder(cellLeft, cellTop - cell.height, cellLeft, cellTop, borderLeft)
                }
                cell.borderTop?.let { borderTop ->
                    drawBorder(cellLeft, cellTop, cellLeft + cell.width, cellTop, borderTop)
                }
                cell.borderRight?.let { borderRight ->
                    drawBorder(cellLeft + cell.width, cellTop, cellLeft + cell.width, cellTop - cell.height, borderRight)
                }
                cell.borderBottom?.let { borderBottom ->
                    drawBorder(cellLeft + cell.width, cellTop - cell.height, cellLeft, cellTop - cell.height, borderBottom)
                }
            }
        }
    }

    private fun drawBorder(xStart: Float, yStart: Float, xEnd: Float, yEnd: Float, border: Border) {

        output?.apply {
            setLineWidth(border.lineStyle.width)
            setLineDashPattern(border.lineStyle.dashPattern, border.lineStyle.dashPhase)
            setStrokingColor(border.color)
            setLineCapStyle(0)

            moveTo(xStart, yStart)
            lineTo(xEnd, yEnd)
            stroke()
        }
    }

    fun save(output: OutputStream) {
        endPage()
        document.save(output)
    }

    override fun close() {
        endPage()
        document.close()
    }
}
