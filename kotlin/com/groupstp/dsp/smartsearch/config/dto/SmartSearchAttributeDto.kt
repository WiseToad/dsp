package com.groupstp.dsp.smartsearch.config.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Свойства поискового атрибута.
 *
 * DTO для считывания конфигурации, заданной в ресурсном json-е.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class SmartSearchAttributeDto {
    var attributeName: String? = null
    var valueType: String? = null
}
