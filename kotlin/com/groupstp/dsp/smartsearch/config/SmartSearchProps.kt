package com.groupstp.dsp.smartsearch.config

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.groupstp.dsp.config.ElasticsearchProps
import com.groupstp.dsp.domain.exception.MissingArgumentException
import com.groupstp.dsp.domain.exception.MissingAttributeException
import com.groupstp.dsp.domain.utils.AppIoUtils
import com.groupstp.dsp.smartsearch.config.dto.SmartSearchContextDto
import org.springframework.context.annotation.Configuration

/**
 * Свойства поискового сервиса.
 */
@Configuration
class SmartSearchProps(
    elasticsearchProps: ElasticsearchProps,
) {
    final val resourceLocation = "classpath:smartsearch/"

    final val indexPrefix = elasticsearchProps.prefix + "smartsearch-"

    final val contexts = AppIoUtils.loadResource(resourceLocation + "context.json") { resourceStream ->
        jacksonObjectMapper().readValue(resourceStream, object: TypeReference<List<SmartSearchContextDto>>() {})
    }.map { contextDto ->
        val contextName = contextDto.contextName ?: contextDto.entityName ?: throw MissingAttributeException("contextName")
        val indexAlias = indexPrefix + (contextDto.indexAlias ?: throw MissingAttributeException("indexAlias"))
        SmartSearchContext(
            contextName,
            contextDto.entityName,
            contextDto.attributes
                ?.mapIndexed { attributeNumber, attributeDto ->
                    SmartSearchAttribute(
                        contextName,
                        attributeDto.attributeName ?: throw MissingAttributeException("attributeName"),
                        attributeNumber,
                        indexAlias + "-" + attributeNumber.toString().padStart(2, '0'),
                        attributeDto.valueType ?: throw MissingAttributeException("valueType"),
                    )
                }
                ?.associateBy(SmartSearchAttribute::attributeName)
                ?: throw MissingAttributeException("attributes"),
            contextDto.tags?.toSet() ?: emptySet(),
            contextDto.permittedCarrierTags?.toSet() ?: emptySet(),
            contextDto.permittedRegionTags?.toSet() ?: emptySet(),
            AppIoUtils.loadResourceText(resourceLocation + "context-sql/$contextName.sql")
        ).also { context ->
            context.attributes.values.forEach { attribute ->
                attribute.context = context
            }
        }
    }.associateBy(SmartSearchContext::contextName)

    fun getContext(contextName: String?, entityName: String?): SmartSearchContext {
        val contextName = contextName ?: entityName
            ?: throw MissingArgumentException("contextName")
        val context = contexts[contextName]
            ?: throw IllegalArgumentException("Неизвестный поисковый контекст: $contextName")
        if(!entityName.isNullOrBlank() && entityName != context.entityName) {
            throw IllegalArgumentException("Неверное имя сущности: $entityName")
        }
        return context
    }

    fun getAttribute(contextName: String?, entityName: String?, attributeName: String): SmartSearchAttribute {
        val context = getContext(contextName, entityName)
        return context.attributes[attributeName]
            ?: throw IllegalArgumentException("Неизвестный поисковый атрибут: ${context.contextName}.$attributeName")
    }

    final val maxSearchLimit = 100

    final val rebuildAcquireTimeout = 15 // secs
    final val rebuildAllAcquireTimeout = 0
    final val queueAcquireTimeout = 0

    final val rebuildIndexDbChunk = 500
    final val rebuildIndexEsChunk = 500

    final val queueCleanupPeriodicity = 15

    final val userCacheTtl = 5 * 60 // secs
}
