package com.groupstp.dsp.smartsearch.service

import ch.qos.logback.classic.Level
import com.groupstp.dsp.domain.utils.AppIoUtils
import com.groupstp.dsp.domain.utils.AppLogUtils
import com.groupstp.dsp.domain.utils.multithreading.CloseableLock
import com.groupstp.dsp.hibernate.StringArrayType
import com.groupstp.dsp.smartsearch.config.SmartSearchAttribute
import com.groupstp.dsp.smartsearch.config.SmartSearchContext
import com.groupstp.dsp.smartsearch.config.SmartSearchProps
import com.groupstp.dsp.smartsearch.service.dto.storage.SmartSearchDbRecord
import com.groupstp.dsp.smartsearch.service.dto.storage.SmartSearchEsDocument
import org.hibernate.jpa.TypedParameterValue
import org.hibernate.type.CustomType
import org.slf4j.LoggerFactory
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.IndexedObjectInformation
import org.springframework.data.elasticsearch.core.document.Document
import org.springframework.data.elasticsearch.core.index.AliasAction
import org.springframework.data.elasticsearch.core.index.AliasActionParameters
import org.springframework.data.elasticsearch.core.index.AliasActions
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.IndexQuery
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import javax.persistence.EntityManager

@Service
class SmartSearchEsIndexService(
    private val smartSearchProps: SmartSearchProps,
    private val elasticsearchOps: ElasticsearchOperations,
    private val transactionManager: PlatformTransactionManager,
    private val entityManager: EntityManager
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val rebuildAllLock = ReentrantLock(true)
    private val contextLocks = smartSearchProps.contexts.mapValues { ReentrantLock(true) }

    private var processQueueCount = 0

    private fun acquireContext(context: SmartSearchContext, timeout: Int): CloseableLock {
        val lock = contextLocks[context.contextName] ?: throw AssertionError()
        if(!lock.tryLock(timeout.toLong(), TimeUnit.SECONDS)) {
            throw TimeoutException("Не удалось заблокировать поисковый контекст ${context.contextName}")
        }
        return CloseableLock(lock)
    }

    /**
     * Обработчик джоба
     */
    fun rebuildAllIndexesJob() {
        rebuildAllIndexes()
    }

    /**
     * Обработчик административного REST API
     */
    @Async
    fun rebuildAllIndexes(delay: Int = 0) {
        if(delay > 0) {
            log.info("Запланировано перестроение поисковых индексов через $delay сек.")
            Thread.sleep(delay.toLong() * 1000L)
        }
        if(!rebuildAllLock.tryLock(smartSearchProps.rebuildAllAcquireTimeout.toLong(), TimeUnit.SECONDS)) {
            throw TimeoutException("Перестроение поисковых индексов для всех контекстов уже выполняется")
        }
        CloseableLock(rebuildAllLock).use {
            log.info("Запуск перестроения поисковых индексов для всех контекстов")
            smartSearchProps.contexts.values.forEach { context ->
                try {
                    rebuildIndexes(context)
                } catch(e: Exception) {
                    log.error("Ошибка перестроения поисковых индексов для контекста ${context.contextName}", e)
                }
            }
            log.info("Завершено перестроение поисковых индексов для всех контекстов")
        }
    }

    /**
     * Обработчик административного REST API
     */
    @Async
    fun rebuildIndexes(contextName: String?, entityName: String?) {
        rebuildIndexes(smartSearchProps.getContext(contextName, entityName))
    }

    private fun rebuildIndexes(context: SmartSearchContext) {
        acquireContext(context, smartSearchProps.rebuildAcquireTimeout).use {
            log.info("Запуск перестроения поисковых индексов для контекста ${context.contextName}")

            //TODO: Переделать на поточную обработку рез-тов запроса из БД
            //      Задействовать настройку smartSearchProps.rebuildIndexDbChunk
            val dbRecords = AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
                entityManager.createNativeQuery(context.sql, "SmartSearchDbRecord")
                    .resultList
                    .map { it as SmartSearchDbRecord }
            }
            log.debug("Для поискового контекста ${context.contextName} отобрано ${dbRecords.size} записей из БД")

            context.attributes.values.forEach { attribute ->
                try {
                    val indexName = createIndex(attribute)
                    indexDbRecords(attribute, dbRecords, indexName)
                    switchToIndex(attribute, indexName)
                    log.debug("Перестроен поисковый индекс для атрибута ${attribute.uniqueName}")
                }
                catch(e: Exception) {
                    log.error("Ошибка перестроения поискового индекса для атрибута ${attribute.uniqueName}", e)
                }
            }
            log.info("Завершено перестроение поисковых индексов для контекста ${context.contextName}")
        }
    }

    private fun createIndex(attribute: SmartSearchAttribute): String {
        val indexName = smartSearchProps.indexPrefix + UUID.randomUUID()

        val indexSettings = Document.parse(
            AppIoUtils.loadResourceText(smartSearchProps.resourceLocation + "index/settings.json")
        )

        val indexMappings = Document.create()
        indexMappings["properties"] = mapOf<String, Any?>(
            "key" to mapOf<String, Any?>(
                "type" to "keyword"
            ),
            "contextName" to mapOf<String, Any?>(
                "type" to "keyword"
            ),
            "entityName" to mapOf<String, Any?>(
                "type" to "keyword"
            ),
            "attributeName" to mapOf<String, Any?>(
                "type" to "keyword"
            ),
            "searchValue" to mapOf<String, Any?>(
                "type" to "text",
                "analyzer" to attribute.valueType,
                "search_analyzer" to attribute.valueType + "_search"
            ),
            "displayValue" to mapOf<String, Any?>(
                "type" to "text",
                "index" to false
            ),
            "isArchive" to mapOf<String, Any?>(
                "type" to "boolean"
            ),
            "tags" to mapOf<String, Any?>(
                "type" to "flattened"
            )
        )

        val indexOps = elasticsearchOps.indexOps(IndexCoordinates.of(indexName))
        if(!indexOps.create(indexSettings)) {
            throw RuntimeException("Запрос создания поискового индекса $indexName не подтвержден сервером ES")
        }
        if(!indexOps.putMapping(indexMappings)) {
            throw RuntimeException("Запрос добавления мэппинга в поисковый индекс $indexName не подтвержден сервером ES")
        }
        return indexName
    }

    private fun indexDbRecords(attribute: SmartSearchAttribute, dbRecords: List<SmartSearchDbRecord>, indexName: String? = null): List<String> {
        val indexQueriesChunks = dbRecords.map { dbRecord ->
            var displayValue = dbRecord.displayValues[attribute.attributeNumber]
            if(dbRecord.isArchive) {
                displayValue = (if(displayValue.isNullOrEmpty()) "" else "$displayValue ") + "(архив)"
            }
            IndexQuery().apply {
                `object` = SmartSearchEsDocument(
                    dbRecord.key,
                    attribute.context.contextName,
                    attribute.context.entityName,
                    attribute.attributeName,
                    dbRecord.searchValues[attribute.attributeNumber],
                    displayValue,
                    dbRecord.isArchive,
                    // Трансформация строки с тегами в map, для дальнейшей записи во flattened-поле Elasticsearch
                    dbRecord.tags.mapNotNull { tags ->
                        val tagName = tags?.substringBefore("=", "")
                        if(!tagName.isNullOrBlank()) {
                            tagName to tags.substringAfter("=", "")
                                .split(",")
                                .filter(String::isNotBlank)
                        } else {
                            null
                        }
                    }.associate { it }
                )
            }
        }.chunked(smartSearchProps.rebuildIndexEsChunk)

        if(indexQueriesChunks.isNotEmpty() && indexName == null && !hasIndex(attribute)) {
            switchToIndex(attribute, createIndex(attribute))
        }

        val indexedKeys = mutableListOf<String>()
        indexQueriesChunks.forEach { indexQueriesChunk ->
            val indexedInfos = elasticsearchOps.bulkIndex(indexQueriesChunk,
                IndexCoordinates.of(indexName ?: attribute.indexAlias))
            if(indexedInfos.size != indexQueriesChunk.size) {
                log.warn("Сохранено ${indexedInfos.size} документов поискового индекса вместо запрошенных ${indexQueriesChunk.size}")
            }
            indexedKeys.addAll(
                indexedInfos.mapNotNull(IndexedObjectInformation::getId)
            )
        }
        return indexedKeys
    }

    private fun switchToIndex(attribute: SmartSearchAttribute, indexName: String) {
        val indexOps = elasticsearchOps.indexOps(IndexCoordinates.of(indexName))

        val aliasActions = AliasActions()
        aliasActions.add(
            AliasAction.Add(
                AliasActionParameters.builder()
                    .withAliases(attribute.indexAlias)
                    .withIndices(indexName)
                    .build()
            )
        )

        val aliasIndexes = indexOps.getAliases(attribute.indexAlias).keys
        if(aliasIndexes.isNotEmpty()) {
            aliasActions.add(
                AliasAction.RemoveIndex(
                    AliasActionParameters.builder()
                        .withAliases(attribute.indexAlias)
                        .withIndices(*aliasIndexes.toTypedArray())
                        .build()
                )
            )
        }
        if(!indexOps.alias(aliasActions)) {
            throw RuntimeException("Запрос смены поисковых индексов в составе ${attribute.indexAlias} не подтвержден сервером ES")
        }
    }

    private fun hasIndex(attribute: SmartSearchAttribute): Boolean {
        val indexOps = elasticsearchOps.indexOps(IndexCoordinates.of(attribute.indexAlias))
        return indexOps.exists()
    }

    /**
     * Обработчик джоба
     */
    fun processQueueJob() {
        smartSearchProps.contexts.values.forEach { context ->
            try {
                processQueue(context)
            } catch(e: Exception) {
                log.error("Ошибка инкрементального обновления поискового индекса для контекста ${context.contextName}", e)
            }
        }
        if(++processQueueCount >= smartSearchProps.queueCleanupPeriodicity) {
            processQueueCount = 0
            cleanupQueue()
        }
    }

    private fun processQueue(context: SmartSearchContext) {
        val contextLock = try {
            acquireContext(context, smartSearchProps.queueAcquireTimeout)
        } catch (e: TimeoutException) {
            return
        }
        contextLock.use {
            val dbRecords = AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
                entityManager.createNativeQuery("""
                    select *
                    from (${context.sql}) as q
                    where key in (
                        select key
                        from dsp_smartsearch_queue
                        where context_name = ?1
                            and apply_ts is null
                        group by key
                        order by min(create_ts)
                        limit ?2
                    )
                """.trimIndent(), "SmartSearchDbRecord")
                    .setParameter(1, context.contextName)
                    .setParameter(2, smartSearchProps.rebuildIndexDbChunk)
                    .resultList
                    .map { it as SmartSearchDbRecord }
            }
            if(dbRecords.isNotEmpty()) {
                val indexedKeys = context.attributes.values
                    .map { attribute -> indexDbRecords(attribute, dbRecords).toSet() }
                    .reduce { a, b -> a intersect b }
                checkoutQueue(context, indexedKeys)
            }

            val deletingKeys = AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
                entityManager.createNativeQuery("""
                    select *
                    from (
                        select key
                        from dsp_smartsearch_queue
                        where context_name = ?1
                            and apply_ts is null
                        group by key
                        order by min(create_ts)
                        limit ?2
                    ) as q
                    where key not in (
                        select key
                        from (${context.sql}) as q
                    )
                """.trimIndent())
                    .setParameter(1, context.contextName)
                    .setParameter(2, smartSearchProps.rebuildIndexDbChunk)
                    .resultList
                    .map { it as String }
            }
            if(deletingKeys.isNotEmpty()) {
                context.attributes.values
                    .filter(::hasIndex)
                    .map { attribute ->
                        //TODO: Как только Java API для этого созреет, обернуть все это в булки! (ES API это умеет)
                        val indexCoordinates = IndexCoordinates.of(attribute.indexAlias)
                        deletingKeys.forEach { deletingKey ->
                            elasticsearchOps.delete(deletingKey, indexCoordinates)
                        }
                    }
                checkoutQueue(context, deletingKeys)
            }
        }
    }

    private fun checkoutQueue(context: SmartSearchContext, keys: Collection<String>) {
        if(keys.isEmpty()) {
            return
        }
        TransactionTemplate(transactionManager).execute {
            AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
                entityManager.createNativeQuery("""
                    update dsp_smartsearch_queue
                    set apply_ts = now() at time zone 'UTC'
                    where context_name = ?1
                        and key = any (?2)
                        and apply_ts is null
                """.trimIndent())
                    .setParameter(1, context.contextName)
                    .setParameter(2, TypedParameterValue(CustomType(StringArrayType()), keys.toTypedArray()))
                    .executeUpdate()
            }
        }
    }

    private fun cleanupQueue() {
        TransactionTemplate(transactionManager).execute {
            AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
                entityManager.createNativeQuery("""
                        delete from dsp_smartsearch_queue
                        where apply_ts is not null
                    """.trimIndent())
                    .executeUpdate()
            }
        }
    }
}
