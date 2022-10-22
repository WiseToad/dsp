package com.groupstp.dsp.smartsearch.service.dto.find

import java.util.*

/**
 * Результат поиска, найденный экземпляр сущности.
 *
 * DTO для возврата из поискового сервиса в ответ на поисковый запрос.
 * Оставлено для обратной совместимости. Теперь во всех случаях должна использоваться более общая структура SmartSearchResult.
 */
class SmartSearchInstance(
    val instanceId: UUID,
    val contextName: String,
    val entityName: String,
    val attributeName: String,
    val displayValue: String?,
    val isArchive: Boolean
)
