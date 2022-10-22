package com.groupstp.dsp.smartsearch.service.dto.storage

import org.springframework.data.annotation.Id

/**
 * Формат записи ("документа"), отправляемой в Elasticsearch при индексации.
 *
 * Представляет собой тип SmartSearchDbRecord, дополненный полями contextName, entityName и attributeName.
 * Также изменен тип поля tags, чтобы адаптироваться к особенностям хранения данных в Elasticsearch.
 */
class SmartSearchEsDocument(
    @Id
    var key: String,
    val contextName: String,
    val entityName: String?,
    val attributeName: String,
    val searchValue: String?,
    val displayValue: String?,
    val isArchive: Boolean,
    val tags: Map<String, List<String>>?
)
