package com.groupstp.dsp.smartsearch.service.dto.find

/**
 * Результат поиска.
 *
 * DTO для возврата из поискового сервиса в ответ на поисковый запрос.
 */
class SmartSearchResult(
    val key: String,
    val contextName: String,
    val attributeName: String,
    val displayValue: String?,
    val isArchive: Boolean
)
