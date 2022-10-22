package com.groupstp.dsp.reporting.convert

import com.groupstp.dsp.reporting.ReportDataFormat
import java.io.InputStream
import java.io.OutputStream

/**
 * Интерфейс конвертации данных из формата в формат.
 */
interface FormatConverter {

    // Свойства используются для автоматического связывания при построении
    // цепочки обработки данных в отчетной подсистеме
    val inputFormat: ReportDataFormat
    val outputFormat: ReportDataFormat

    /**
     * Сконвертировать данные из формата в формат.
     *
     * @param input   входной поток данных для конвертации
     * @param params  параметры, специфичные для разных типов конвертеров
     * @param output  выходной поток сконвертированных данных
     */
    fun convert(input: InputStream, params: Map<String, Any?>, output: OutputStream)
}
