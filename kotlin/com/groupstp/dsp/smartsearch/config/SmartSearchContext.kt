package com.groupstp.dsp.smartsearch.config

/**
 * Свойства поискового контекста.
 */
class SmartSearchContext(
    val contextName: String,
    val entityName: String?, // См. коммент к данному полю в SmartSearchContextDto
    val attributes: Map<String, SmartSearchAttribute>,
    val tags: Set<String>,
    val permittedCarrierTags: Set<String>,
    val permittedRegionTags: Set<String>,
    val sql: String
)
