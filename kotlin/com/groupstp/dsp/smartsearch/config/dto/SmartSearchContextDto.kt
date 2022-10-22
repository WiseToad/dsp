package com.groupstp.dsp.smartsearch.config.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Свойства поискового контекста.
 *
 * DTO для считывания конфигурации, заданной в ресурсном json-е.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class SmartSearchContextDto {
    var contextName: String? = null // Null значение в настройках обозначает контекст по умолчанию для заданной сущности.
                                    // При этом в API-вызовах подразумевается, что имя у контекста по умолчанию такое же как и у сущности.
    var entityName: String? = null  // Non-null значение означает, что ключ результата поиска содержит только id экземпляра.
                                    // А также возможность использования данного поискового контекста в методе findInstances.
    var indexAlias: String? = null
    var attributes: List<SmartSearchAttributeDto>? = null
    var tags: List<String>? = null
    var permittedCarrierTags: List<String>? = null
    var permittedRegionTags: List<String>? = null
}
