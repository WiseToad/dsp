package com.groupstp.dsp.reporting

import com.groupstp.dsp.domain.utils.AppParseUtils
import com.groupstp.dsp.domain.utils.streamedcache.StreamedCache
import com.groupstp.dsp.security.SecurityUtils
import java.util.*

/**
 * Описатель экземпляра отчета.
 *
 * Структура для хранения данных, отображаемых в отчете, а также метаданных, необходимых для окончательного
 * формирования экземпляра отчета в требуемом формате из шаблона.
 */
class Report (
    val config: ReportConfig
) {
    val id: UUID = UUID.randomUUID()
    val createTs = Date()
    var accessTs = createTs
    val user: String? = SecurityUtils.getCurrentUserLogin().orElse(null)

    val name = config.reportName
    val title = config.reportTitle

    var params: Map<String, Any?>? = null
    var data = mapOf<String, Any?>()
    val content = mutableMapOf<String, StreamedCache>()

    fun getFileName(format: ReportDataFormat) =
        AppParseUtils.interpolate(config.exportTitle, data)
            .replace(Regex("[<>:\"/\\\\|?*]"), "_") + format.fileExt?.let { ".$it" }

    override fun equals(other: Any?): Boolean
        = other is Report && other.id == id

    override fun hashCode(): Int
        = id.hashCode()
}
