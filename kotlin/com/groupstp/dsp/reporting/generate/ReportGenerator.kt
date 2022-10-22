package com.groupstp.dsp.reporting.generate

import com.groupstp.dsp.reporting.ReportDataFormat
import java.io.InputStream
import java.io.OutputStream

/**
 * Интерфейс формирования отчета из шаблона.
 */
interface ReportGenerator {

    // Свойство используется для автоматического связывания при построении
    // цепочки обработки данных в отчетной подсистеме
    val templateFormat: ReportDataFormat

    /**
     * Сформировать отчет из шаблона в выходной поток.
     *
     * @param template  шаблон в виде входного потока
     * @param data      данные для отображения в отчете
     * @param params    параметры, специфичные для разных типов генераторов
     * @param report    выходной поток сформированного отчета
     */
    fun generate(template: InputStream, data: Map<String, Any?>, params: Map<String, Any?>, report: OutputStream)
}
