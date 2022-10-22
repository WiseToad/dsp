package com.groupstp.dsp.reporting.generate

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.groupstp.dsp.reporting.ReportDataFormat
import com.groupstp.dsp.reporting.generate.jxls.ImagexCommand
import com.groupstp.dsp.service.rest.report.AutoRowHeightCommand
import org.jxls.builder.xls.XlsCommentAreaBuilder
import org.jxls.common.CellRef
import org.jxls.transform.poi.PoiContext
import org.jxls.transform.poi.PoiTransformer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.OutputStream
import javax.annotation.PostConstruct

/**
 * Генератор отчетов из Excel-шаблонов с использованием библиотеки JXLS.
 */
@Component
class XlsxGenerator: ReportGenerator {

    override val templateFormat = ReportDataFormat.XLSX

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        XlsCommentAreaBuilder.addCommandMapping("imagex", ImagexCommand::class.java)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private class Params (
        val deleteSheet: String? = null
    )

    override fun generate(template: InputStream, data: Map<String, Any?>, params: Map<String, Any?>, report: OutputStream) {

        log.trace("generate: enter")

        @Suppress("NAME_SHADOWING")
        val params = jacksonObjectMapper().convertValue(params, Params::class.java)

        val context = PoiContext(data)

        val transformer = PoiTransformer.createTransformer(template, report)

        // Добавление поддержки авто выравнивания столбцов по ширине
        XlsCommentAreaBuilder.addCommandMapping("autoSize", AutoRowHeightCommand::class.java)

        val xlsAreas = XlsCommentAreaBuilder(transformer).build()

        // Подстановка в шаблон отчетных данных
        log.trace("generate: substituting data")
        xlsAreas.forEach { xlsArea ->
            xlsArea.applyAt(CellRef(xlsArea.startCellRef.cellName), context)
        }

        // Адаптация формул под измененный в результате подстановки лист
        log.trace("generate: processing formulas")
        xlsAreas.forEach { xlsArea ->
            xlsArea.processFormulas()
        }

        // Расчет формул
        log.trace("generate: evaluating formulas")
        val formulaEvaluator = transformer.workbook.creationHelper.createFormulaEvaluator()
        formulaEvaluator.evaluateAll()

        if(params.deleteSheet != null) {
            log.trace("generate: deleting template sheet")
            transformer.deleteSheet(params.deleteSheet)
        }

        log.trace("generate: writing stream")
        transformer.writeButNotCloseStream()

        log.trace("generate: return")
    }
}
