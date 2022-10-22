package com.groupstp.dsp.smartsearch.service.dto.storage

/**
 * Формат записи, возвращаемый из БД при поисковой индексации в Elasticsearch.
 */
class SmartSearchDbRecord(
    val key: String,
    val searchValues: Array<String?>,
    val displayValues: Array<String?>,
    val isArchive: Boolean,
    val tags: Array<String?>
)
