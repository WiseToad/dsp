package com.groupstp.dsp.reporting

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Структура данных для описания вида отчета в системе.
 *
 * В типовом сценарии список отчетов хранится в ресурсном json.
 */
class ReportConfig (
    @JsonProperty("reportName")
    val reportName: String,
    @JsonProperty("reportTitle")
    reportTitle: String?,
    @JsonProperty("exportTitle")
    exportTitle: String?,
    @JsonProperty("templateName")
    val templateName: String?,
    @JsonProperty("templateNames")
    templateNames: Map<String, String>?,
    @JsonProperty("dataFetcher")
    val dataFetcher: String?,
    @JsonProperty("generateParams")
    generateParams: Map<String, Any?>?,
    @JsonProperty("convertParams")
    convertParams: Map<String, Map<String, Any?>>?,
    @JsonProperty("permittedRoles")
    permittedRoles: List<String>?
) {
    val reportTitle = reportTitle ?: reportName
    val exportTitle = exportTitle ?: this.reportTitle
    val templateNames = templateNames ?: emptyMap()
    val generateParams = generateParams ?: emptyMap()
    val convertParams = convertParams ?: emptyMap()
    val permittedRoles = permittedRoles ?: emptyList()
}
