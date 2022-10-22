package com.groupstp.dsp.smartsearch.service.dto.find

/**
 * Область поиска.
 *
 * DTO для входящего запроса на поиск. Запрос может включать в себя несколько таких областей.
 */
class SmartSearchRealm(
    val contextName: String?,
    val entityName: String?, // Оставлено для обратной совместимости, используется только в методе findInstances
    val attributeNames: List<String>,
    val isArchive: Boolean?,
    val tags: List<SmartSearchTag>?
)
