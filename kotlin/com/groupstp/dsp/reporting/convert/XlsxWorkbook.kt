package com.groupstp.dsp.reporting.convert

import org.apache.poi.hssf.usermodel.HSSFPatriarch
import org.apache.poi.ss.format.CellFormat
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.ss.util.CellReference
import org.apache.poi.ss.util.ImageUtils
import org.apache.poi.util.Units
import org.apache.poi.xssf.usermodel.XSSFDrawing
import java.io.InputStream
import java.util.*
import kotlin.math.max
import kotlin.math.min

class XlsxWorkbook(
    input: InputStream
) {
    val workbook = WorkbookFactory.create(input)

    val usedStyles by lazy { // by lazy, ибо вдруг не пригодится (для сбора требуется пробежка по всем физ. ячейкам)
        val styleIndexes = mutableSetOf(0) // сразу добавляем стиль по умолчанию (индекс 0)
        for(sheet in workbook) {
            for(row in sheet) {
                for(cell in row) {
                    styleIndexes.add(cell.cellStyle.index.toInt())
                }
            }
        }
        styleIndexes.sorted().map { styleIndex ->
            styleIndex to workbook.getCellStyleAt(styleIndex)
        }.toMap()
    }
    val defaultStyle = workbook.getCellStyleAt(0)

    class CellLayout (
        val type: CellLayoutType,
        // Позиция ячейки
        val rowNum: Int,
        val columnNum: Int,
        // Координаты правого нижнего угла
        val lastRowNum: Int,    // в общем случае не равно (rowNum + rowSpan - 1) из-за скрытых строк
        val lastColumnNum: Int, // в общем случае не равно (columnNum + columnSpan - 1) из-за скрытых столбцов
        // Количество видимых строк и столбцов, на которые простирается (объединенная) ячейка
        val rowSpan: Int,
        val columnSpan: Int,
        // Ширина ячейки в пикселях
        val width: Int
    )

    enum class CellLayoutType {
        TABLE_START,
        TABLE_END,
        ROW_START,
        ROW_END,
        CELL,
        SKIPPED_CELLS // ряд ячеек в строке, перекрытых объединенной ячейкой
    }

    class CellContent (
        val style: CellStyle,
        val alignment: HorizontalAlignment, // Заданное в style выравнивание может быть переопределено индивидуально для ячейки
        val text: String,
        val url: String? = null
    )

    class CellPicture (
        val data: PictureData,
        // Размер в пикселях
        val width: Int,
        val height: Int
    )

    private class MergedRegion (
        val sourceRegion: CellRangeAddress
    ) {
        val adjustedRegion = CellRangeAddress(-1, -1, -1, -1).apply {
            firstRow = Int.MAX_VALUE
            lastRow = -1
            firstColumn = Int.MAX_VALUE
            lastColumn = -1
        }

        var columnSpan = 0
        var rowSpan = 0
        var width = 0
    }

    private val tables = mutableMapOf<Int, Table>()

    fun getTableFromSheet(sheetIndex: Int, cropLeft: Int = 0, cropTop: Int = 0,
                          cropRight: Int = 0, cropBottom: Int = 0): Table
    {
        return tables.getOrPut(sheetIndex) {
            Table(workbook.getSheetAt(sheetIndex), cropLeft, cropTop, cropRight, cropBottom)
        }
    }

    inner class Table (
        val sheet: Sheet,
        cropLeft: Int,
        cropTop: Int,
        cropRight: Int,
        cropBottom: Int
    ) {
        // Параметры обрезки таблицы по краям
        val cropLeft = max(0, cropLeft)
        val cropTop = max(0, cropTop)
        val cropRight = max(0, cropRight)
        val cropBottom = max(0, cropBottom)

        // Границы таблицы на Excel-листе
        val firstColumnNum: Int
        val lastColumnNum: Int
        val firstRowNum: Int
        val lastRowNum: Int

        // Содержит ли таблица хоть что-то
        val isEmpty: Boolean

        // Ширина колонок и самой таблицы в пикселях
        val columnWidths: List<Int>
        val width: Int
        val widthWithPictures: Int

        private val mergedRegions: Map<CellReference, MergedRegion>

        private val pictures: Map<CellReference, CellPicture>

        init {
            // Сбор xlsx-картинок
            val drawing = sheet.drawingPatriarch
            val shapes = when(drawing) {
                is XSSFDrawing -> drawing.shapes
                is HSSFPatriarch -> drawing.children
                else -> null
            }
            pictures = shapes?.mapNotNull { shape ->
                (shape as? Picture)?.let { picture ->
                    val clientAnchor = picture.clientAnchor
                    val dimension = ImageUtils.getDimensionFromAnchor(picture)
                    CellReference(clientAnchor.row1, clientAnchor.col1) to CellPicture(
                        picture.pictureData,
                        dimension.width * 9 / 7 / Units.EMU_PER_PIXEL,
                        dimension.height / Units.EMU_PER_PIXEL
                    )
                }
            }?.toMap() ?: emptyMap()
            val topPictureRowNum = pictures.keys.minOfOrNull { it.row } ?: Int.MAX_VALUE
            val bottomPictureRowNum = pictures.keys.maxOfOrNull { it.row } ?: -1
            val minPictureCellNum = pictures.keys.minOfOrNull { it.col }?.toInt() ?: Int.MAX_VALUE
            val maxPictureCellNum = pictures.keys.maxOfOrNull { it.col }?.toInt() ?: -1

            val initialMergedRegions = sheet.mergedRegions.filter { sourceRegion ->
                // Параноидальный подход: отбор только валидных регионов из файла
                sourceRegion.firstRow >= 0 &&
                sourceRegion.firstRow <= sourceRegion.lastRow &&
                sourceRegion.firstColumn >= 0 &&
                sourceRegion.firstColumn <= sourceRegion.lastColumn
            }.map { sourceRegion ->
                MergedRegion(sourceRegion)
            }.sortedWith { a, b ->
                // Сортировка по строкам
                if(a.sourceRegion.firstRow < b.sourceRegion.firstRow) -1
                else if(a.sourceRegion.firstRow > b.sourceRegion.firstRow) 1
                else 0
            }
            var mergedRegionIndex = 0 // указатель списка, возрастающий при дальнейшем проходе по строкам таблицы

            // Текущий список объединенных регионов в работе (те, что пересекаются текущей строкой таблицы)
            val currentMergedRegions = LinkedList<MergedRegion>()

            // Список "задержки" номеров строк для нахождения за один проход нижней строки с учетом обрезки снизу
            val bottomRowNums = MutableList(cropBottom + 1) { 0 }
            var bottomRowNumIndex = 0

            var topRowNum = Int.MAX_VALUE      // номер верхней строки известного на текущий момент видимого диапазона строк
            var bottomRowNum = -1              // номер нижней строки известного на текущий момент видимого диапазона строк
            var minCellNum = minPictureCellNum // минимальный номер ячейки по всем строкам
            var maxCellNum = maxPictureCellNum // максимальный номер ячейки по всем строкам

            var rowCount = 0 // общее количество видимых строк таблицы, без учета обрезки сверху и снизу

            //TODO: Переформатировать код ниже, убрать повторяющиеся участки

            // Обработка размерностей таблицы и объединенных регионов за один проход по строкам таблицы
            var rowNum = topPictureRowNum
            for(row in sheet) {
                // Обход промежутка из пустых логических строк, отсутствующих в списке физических
                // Т.к. переменной zeroHeight для таких строк не существует, принимаем что они не могут быть скрытыми
                while(rowNum < row.rowNum) {
                    // Нахождение номера верхней строки таблицы с учетом обрезки сверху
                    if(rowCount == cropTop) {
                        topRowNum = rowNum
                    }
                    rowCount++

                    // Нахождение номера нижней строки таблицы с учетом обрезки снизу, используя список "задержки"
                    bottomRowNums[bottomRowNumIndex++] = rowNum
                    if(bottomRowNumIndex >= bottomRowNums.size) {
                        bottomRowNumIndex = 0
                    }
                    if(rowCount > cropBottom) {
                        bottomRowNum = bottomRowNums[bottomRowNumIndex]
                    }

                    //TODO: Не нужно ли сюда добавить код обработки объединенных регионов, как сделано ниже?

                    rowNum++
                }
                rowNum = row.rowNum

                if(!row.zeroHeight) {
                    // Нахождение номера верхней строки таблицы с учетом обрезки сверху (копипаста кода выше)
                    if(rowCount == cropTop) {
                        topRowNum = rowNum
                    }
                    rowCount++

                    // Нахождение номера нижней строки таблицы с учетом обрезки снизу, используя список "задержки" (копипаста кода выше)
                    bottomRowNums[bottomRowNumIndex++] = rowNum
                    if(bottomRowNumIndex >= bottomRowNums.size) {
                        bottomRowNumIndex = 0
                    }
                    if(rowCount > cropBottom) {
                        bottomRowNum = bottomRowNums[bottomRowNumIndex]
                    }

                    // Определение левого и правого краев таблицы по минимальным и максимальным номерам ячеек в строках
                    val firstCellNum = row.firstCellNum.toInt()
                    if (firstCellNum >= 0) {
                        var lastCellNum = row.lastCellNum.toInt() - 1 // lastCellNum выбивается из общего правила и возвращает 1-based индекс
                        while(lastCellNum >= firstCellNum) {
                            if((row.getCell(lastCellNum)?.cellType ?: CellType.BLANK) != CellType.BLANK) break
                            lastCellNum--
                        }
                        if(lastCellNum >= firstCellNum) {
                            minCellNum = min(minCellNum, firstCellNum)
                            maxCellNum = max(maxCellNum, lastCellNum)
                        }
                    }

                    // Обработка объединенных регионов в пределах известного на текущий момент видимого диапазона строк
                    if(topRowNum <= bottomRowNum) {
                        // Пополнение текущего списка объединенных регионов теми, что начали пересекаться нижней строкой
                        while(mergedRegionIndex < initialMergedRegions.size) {
                            val mergedRegion = initialMergedRegions[mergedRegionIndex]
                            if(mergedRegion.sourceRegion.firstRow > bottomRowNum) {
                                break // объединенный регион и все следующие за ним еще не пересекаются нижней строкой
                            }
                            if(mergedRegion.sourceRegion.lastRow >= bottomRowNum) {
                                mergedRegion.adjustedRegion.firstRow = bottomRowNum
                                currentMergedRegions.add(mergedRegion)
                            }
                            mergedRegionIndex++
                        }

                        // Обработка текущего списка объединенных регионов
                        val i = currentMergedRegions.iterator()
                        while(i.hasNext()) {
                            val mergedRegion = i.next()
                            if(mergedRegion.sourceRegion.lastRow < bottomRowNum) {
                                i.remove() // объединенный регион больше не пересекается нижней строкой диапазона
                            } else {
                                mergedRegion.adjustedRegion.lastRow = bottomRowNum
                                mergedRegion.rowSpan++
                            }
                        }
                    }
                }
                rowNum++
            }
            // Добор низа таблицы за пределами массива сохраненных физических строк, если вдруг там осели картинки
            while(rowNum <= bottomPictureRowNum) {
                // Нахождение номера верхней строки таблицы с учетом обрезки сверху (копипаста кода выше)
                if(rowCount == cropTop) {
                    topRowNum = rowNum
                }
                rowCount++

                // Нахождение номера нижней строки таблицы с учетом обрезки снизу, используя список "задержки" (копипаста кода выше)
                bottomRowNums[bottomRowNumIndex++] = rowNum
                if(bottomRowNumIndex >= bottomRowNums.size) {
                    bottomRowNumIndex = 0
                }
                if(rowCount > cropBottom) {
                    bottomRowNum = bottomRowNums[bottomRowNumIndex]
                }

                //TODO: Не нужно ли сюда добавить код обработки объединенных регионов, как сделано выше?

                rowNum++
            }

            // Фиксация строковых размерностей таблицы
            if(topRowNum <= bottomRowNum) {
                firstRowNum = topRowNum
                lastRowNum = bottomRowNum
            } else {
                firstRowNum = 0
                lastRowNum = -1
            }

            // Подгонка колоночных размерностей таблицы с учетом скрытых столбцов, а также обрезки слева и справа
            var croppedLeft = 0
            while(minCellNum <= maxCellNum) {
                if(!sheet.isColumnHidden(minCellNum)) {
                    if(croppedLeft >= cropLeft) break
                    croppedLeft++
                }
                minCellNum++
            }
            var croppedRight = 0
            while(minCellNum <= maxCellNum) {
                if(!sheet.isColumnHidden(maxCellNum)) {
                    if(croppedRight >= cropRight) break
                    croppedRight++
                }
                maxCellNum--
            }

            // Фиксация колоночных размерностей таблицы
            if(minCellNum <= maxCellNum && croppedLeft == cropLeft && croppedRight == cropRight) {
                firstColumnNum = minCellNum
                lastColumnNum = maxCellNum
            } else {
                firstColumnNum = 0
                lastColumnNum = -1
            }

            isEmpty = firstRowNum > lastRowNum || firstColumnNum > lastColumnNum

            // Инициализация ширины колонок и таблицы в пикселях
            columnWidths = List(max(lastColumnNum + 1, 0)) { columnNum ->
                if(columnNum < firstColumnNum || sheet.isColumnHidden(columnNum)) 0
                else sheet.getColumnWidth(columnNum) * 9 / 256
            }
            width = columnWidths.sum()

            val maxPictureRightEdge = pictures.map { (cellReference, picture) ->
                //TODO: Оптимизировать, сейчас смещение для каждой картинки пересчитывается с нуля в каждой итерации:
                columnWidths.take(cellReference.col.toInt()).sum() + picture.width
            }.maxOrNull() ?: 0
            widthWithPictures = max(width, maxPictureRightEdge)

            // Окончательная обработка объединенных регионов
            val validMergedRegions = mutableMapOf<CellReference, MergedRegion>()
            for(mergedRegion in initialMergedRegions) {
                val sourceRegion = mergedRegion.sourceRegion
                val adjustedRegion = mergedRegion.adjustedRegion
                if(adjustedRegion.firstRow <= adjustedRegion.lastRow) {
                    // Расчет колоночных размерностей объединенного региона
                    adjustedRegion.firstColumn = max(sourceRegion.firstColumn, firstColumnNum)
                    adjustedRegion.lastColumn = min(sourceRegion.lastColumn, lastColumnNum)
                    while(adjustedRegion.firstColumn <= adjustedRegion.lastColumn) {
                        if(!sheet.isColumnHidden(adjustedRegion.firstColumn)) break
                        adjustedRegion.firstColumn++
                    }
                    while(adjustedRegion.firstColumn <= adjustedRegion.lastColumn) {
                        if(!sheet.isColumnHidden(adjustedRegion.lastColumn)) break
                        adjustedRegion.lastColumn--
                    }

                    if(adjustedRegion.firstColumn <= adjustedRegion.lastColumn) {
                        // Подсчет количества видимых столбцов в объединенном регионе
                        mergedRegion.columnSpan = 0
                        for(columnNum in adjustedRegion.firstColumn..adjustedRegion.lastColumn) {
                            if(!sheet.isColumnHidden(columnNum)) {
                                mergedRegion.columnSpan++
                            }
                        }

                        // Расчет ширины объединенного региона
                        mergedRegion.width = columnWidths.subList(
                            adjustedRegion.firstColumn,
                            adjustedRegion.lastColumn + 1
                        ).sum()

                        // Добавление в итоговый список всех строк региона для упрощения дальнейшего использования в traverse
                        for(regionRowNum in adjustedRegion.firstRow..adjustedRegion.lastRow) {
                            validMergedRegions[CellReference(regionRowNum, adjustedRegion.firstColumn)] = mergedRegion
                        }
                    }
                }
            }
            mergedRegions = validMergedRegions
        }

        fun traverse(maxRowCount: Int = Int.MAX_VALUE,
                     processCell: (cellLayout: CellLayout, cellContent: CellContent?, cellPicture: CellPicture?) -> Unit): Int
        {
            val rowCount = if(firstRowNum > lastRowNum) 0 else (lastRowNum - firstRowNum + 1)
            val skipRowCount = if(rowCount <= maxRowCount) 0 else (rowCount - maxRowCount)

            fun processVirtualCell(type: CellLayoutType, rowNum: Int, columnNum: Int) = processCell(
                CellLayout(type, rowNum, columnNum, rowNum, columnNum, 0, 0, 0),
                null, null)

            fun processTableStart() = processVirtualCell(CellLayoutType.TABLE_START,-1, -1)

            fun processTableEnd() = processVirtualCell(CellLayoutType.TABLE_END, Int.MAX_VALUE, Int.MAX_VALUE)

            fun processRowStart(rowNum: Int) = processVirtualCell(CellLayoutType.ROW_START, rowNum,-1)

            fun processRowEnd(rowNum: Int) = processVirtualCell(CellLayoutType.ROW_END, rowNum, Int.MAX_VALUE)

            processTableStart()

            //TODO: Переформатировать код ниже, убрать повторяющиеся участки.
            //      Возможно, сделать единый обходчик строк для этого кода и кода в init:

            var rowNum = firstRowNum
            for(row in sheet) {
                if(row.rowNum > lastRowNum - skipRowCount) break
                if(row.rowNum < rowNum) continue

                // Обход промежутка из пустых логических строк, отсутствующих в списке физических
                // Т.к. переменной zeroHeight для таких строк не существует, принимаем что они не могут быть скрытыми
                while(rowNum < row.rowNum) {
                    processRowStart(rowNum)
                    var columnNum = firstColumnNum
                    while(columnNum <= lastColumnNum) {
                        if(!sheet.isColumnHidden(columnNum)) {
                            columnNum = processCell(rowNum, columnNum, null, processCell)
                        }
                        columnNum++
                    }
                    processRowEnd(rowNum)
                    rowNum++
                }

                // Обход физической строки
                if(!row.zeroHeight) {
                    processRowStart(rowNum)
                    var columnNum = firstColumnNum
                    for(cell in row) {
                        if(cell.columnIndex > lastColumnNum) break
                        if(cell.columnIndex < columnNum) continue

                        // Обход промежутка из пустых логических ячеек, отсутствующих в списке физических
                        while(columnNum < cell.columnIndex) {
                            if(!sheet.isColumnHidden(columnNum)) {
                                columnNum = processCell(rowNum, columnNum, null, processCell)
                            }
                            columnNum++
                        }

                        // Обработка физической ячейки, если нас не выпихнуло дальше по строке из-за объединенного региона
                        if(columnNum == cell.columnIndex) {
                            if(!sheet.isColumnHidden(columnNum)) {
                                columnNum = processCell(rowNum, columnNum, cell, processCell)
                            }
                            columnNum++
                        }
                    }

                    // Обход конца логической строки
                    while(columnNum <= lastColumnNum) {
                        if(!sheet.isColumnHidden(columnNum)) {
                            columnNum = processCell(rowNum, columnNum, null, processCell)
                        }
                        columnNum++
                    }
                    processRowEnd(rowNum)
                }
                rowNum++
            }

            // Обход пустого низа таблицы, буде таковой имеется
            while(rowNum <= lastRowNum - skipRowCount) {
                processRowStart(rowNum)
                var columnNum = firstColumnNum
                while(columnNum <= lastColumnNum) {
                    if(!sheet.isColumnHidden(columnNum)) {
                        columnNum = processCell(rowNum, columnNum, null, processCell)
                    }
                    columnNum++
                }
                processRowEnd(rowNum)
                rowNum++
            }

            processTableEnd()

            return skipRowCount
        }

        private fun processCell(rowNum: Int, columnNum: Int, cell: Cell?,
                                processCell: (cellLayout: CellLayout, cellContent: CellContent?, cellPicture: CellPicture?) -> Unit
        ): Int {
            val mergedRegion = mergedRegions[CellReference(rowNum, columnNum)]

            val cellLayout = if(mergedRegion == null) {
                CellLayout(CellLayoutType.CELL, rowNum, columnNum, rowNum, columnNum,
                    1, 1, columnWidths[columnNum])
            } else {
                val adjustedRegion = mergedRegion.adjustedRegion
                if(adjustedRegion.firstRow == rowNum) {
                    CellLayout(CellLayoutType.CELL,
                        adjustedRegion.firstRow, adjustedRegion.firstColumn,
                        adjustedRegion.lastRow, adjustedRegion.lastColumn,
                        mergedRegion.rowSpan, mergedRegion.columnSpan,
                        mergedRegion.width)
                } else {
                    CellLayout(CellLayoutType.SKIPPED_CELLS,
                        rowNum, adjustedRegion.firstColumn,
                        rowNum, adjustedRegion.lastColumn,
                        1, mergedRegion.columnSpan,
                        mergedRegion.width)
                }
            }

            @Suppress("NAME_SHADOWING")
            val cell = if(mergedRegion == null) cell
            else {
                val sourceRegion = mergedRegion.sourceRegion
                if(sourceRegion.firstRow == rowNum && sourceRegion.firstColumn == columnNum) cell
                else sheet.getRow(sourceRegion.firstRow).getCell(sourceRegion.firstColumn)
            }

            if(cell == null) {
                processCell(cellLayout, null, pictures[CellReference(rowNum, columnNum)])
            } else {
                val style = cell.cellStyle

                var alignment = style.alignment
                if(alignment == HorizontalAlignment.GENERAL) {
                    var cellType = cell.cellType
                    if(cellType == CellType.FORMULA) {
                        cellType = cell.cachedFormulaResultType
                    }
                    alignment = when(cellType) {
                        CellType.STRING -> HorizontalAlignment.LEFT
                        CellType.BOOLEAN, CellType.ERROR -> HorizontalAlignment.CENTER
                        else -> HorizontalAlignment.RIGHT
                    }
                }

                val format = CellFormat.getInstance(style.dataFormatString)
                val text = format.apply(cell).text
                val url = cell.hyperlink?.address

                processCell(cellLayout, CellContent(style, alignment, text, url), pictures[CellReference(rowNum, columnNum)])
            }

            return cellLayout.lastColumnNum
        }
    }
}
