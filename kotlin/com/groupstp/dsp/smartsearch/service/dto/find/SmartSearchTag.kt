package com.groupstp.dsp.smartsearch.service.dto.find

/**
 * Именованный тег поиска.
 *
 * DTO для входящего запроса на поиск.
 */
class SmartSearchTag(
    val name: String,
    val values: List<String>
)
