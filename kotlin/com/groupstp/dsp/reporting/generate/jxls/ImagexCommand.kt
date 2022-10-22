package com.groupstp.dsp.reporting.generate.jxls

import org.apache.poi.common.usermodel.HyperlinkType
import org.jxls.command.ImageCommand
import org.jxls.common.CellRef
import org.jxls.common.Context
import org.jxls.common.Size
import org.jxls.transform.poi.PoiTransformer


class ImagexCommand: ImageCommand() {

    override fun getName() = "imagex"

    var url: String? = null

    override fun applyAt(cellRef: CellRef, context: Context): Size {

        val size = super.applyAt(cellRef, context)

        val urlValue = transformationConfig.expressionEvaluator.evaluate(url, context.toMap()).toString()
        if(urlValue.isNotBlank()) {
            val workbook = (transformer as? PoiTransformer)?.workbook
            if(workbook != null) {
                val sheet = workbook.getSheet(cellRef.sheetName) ?: workbook.createSheet(cellRef.sheetName)
                val row = sheet.getRow(cellRef.row) ?: sheet.createRow(cellRef.row)
                val cell = row.getCell(cellRef.col) ?: row.createCell(cellRef.col)
                val hyperlink = workbook.creationHelper.createHyperlink(HyperlinkType.URL)
                hyperlink.address = urlValue
                cell.hyperlink = hyperlink
                cell.setCellValue("image")
            }
        }

        return size
    }
}
