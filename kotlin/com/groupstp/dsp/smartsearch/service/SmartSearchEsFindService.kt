package com.groupstp.dsp.smartsearch.service

import com.groupstp.dsp.security.SecurityUtils
import com.groupstp.dsp.smartsearch.config.SmartSearchProps
import com.groupstp.dsp.smartsearch.service.dto.find.SmartSearchInstance
import com.groupstp.dsp.smartsearch.service.dto.find.SmartSearchRealm
import com.groupstp.dsp.smartsearch.service.dto.find.SmartSearchResult
import com.groupstp.dsp.smartsearch.service.dto.find.SmartSearchTag
import com.groupstp.dsp.smartsearch.service.dto.storage.SmartSearchEsDocument
import org.elasticsearch.index.query.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder
import org.springframework.stereotype.Service
import java.util.*
import kotlin.math.min

@Service
class SmartSearchEsFindService(
    private val smartSearchProps: SmartSearchProps,
    private val userService: SmartSearchUserService,
    private val elasticsearchOps: ElasticsearchOperations
) {
    class RankedEsDocument(
        val esDocument: SmartSearchEsDocument,
        val rank: Int
    )

    val rankedEsDocumentComparator = Comparator { a: RankedEsDocument, b: RankedEsDocument ->
        if(a.rank < b.rank) -1
        else if(a.rank > b.rank) 1
        else if(!a.esDocument.isArchive && b.esDocument.isArchive) -1
        else if(a.esDocument.isArchive && !b.esDocument.isArchive) 1
        else 0 // устойчивая сортировка подразумевает сохранение внутри групп ранжирования, выполненного ES
    }

    /**
     * Выполнить поиск экземпляров сущностей по заданным критериям.
     *
     * Оставлено для обратной совместимости. Теперь во всех случаях должен использоваться более общий метод smartFind.
     * В аргументах допускается задавать как сущности, так и поисковые контексты.
     */
    fun findInstances(
        realms: List<SmartSearchRealm>,
        isArchive: Boolean?,
        tags: List<SmartSearchTag>?,
        searchValue: String?,
        limit: Int?
    ): List<SmartSearchInstance> {
        return find(realms, isArchive, tags, searchValue, limit, true)
            .map { esDocument ->
                SmartSearchInstance(
                    UUID.fromString(esDocument.key),
                    esDocument.contextName,
                    esDocument.entityName ?: throw AssertionError(),
                    esDocument.attributeName,
                    esDocument.displayValue,
                    esDocument.isArchive
                )
            }
    }

    /**
     * Выполнить обобщенный поиск по заданным критериям.
     *
     * В аргументах допускается задавать только поисковые контексты. Более узкого понятия сущности здесь как такового нет.
     */
    fun smartFind(
        realms: List<SmartSearchRealm>,
        isArchive: Boolean?,
        tags: List<SmartSearchTag>?,
        searchValue: String?,
        limit: Int?
    ): List<SmartSearchResult> {
        return find(realms, isArchive, tags, searchValue, limit, false)
            .map { esDocument ->
                SmartSearchResult(
                    esDocument.key,
                    esDocument.contextName,
                    esDocument.attributeName,
                    esDocument.displayValue,
                    esDocument.isArchive
                )
            }
    }

    fun find(
        realms: List<SmartSearchRealm>,
        isArchive: Boolean?,
        tags: List<SmartSearchTag>?,
        searchValue: String?,
        limit: Int?,
        legacyMode: Boolean
    ): List<SmartSearchEsDocument> {
        val indexAliases = mutableSetOf<String>()
        val ranks = mutableMapOf<String, Int>()

        val queryBuilder = BoolQueryBuilder()

        realms.forEach { realm ->
            realm.attributeNames.forEach { attributeName ->
                if(realm.entityName != null && !legacyMode) {
                    throw IllegalArgumentException("Неподдерживаемый аргумент: entityName")
                }
                val attribute = smartSearchProps.getAttribute(realm.contextName, realm.entityName, attributeName)
                if(attribute.context.entityName == null && legacyMode) {
                    throw IllegalArgumentException("Неподдерживаемый поисковый контекст: ${attribute.contextName}")
                }

                indexAliases.add(attribute.indexAlias)
                ranks.getOrPut(attribute.uniqueName, ranks::size)

                val attributeQueryBuilder = BoolQueryBuilder()
                    .filter(TermQueryBuilder("contextName", attribute.contextName))
                    .filter(TermQueryBuilder("attributeName", attribute.attributeName))

                if(realm.isArchive != null || isArchive != null) {
                    attributeQueryBuilder.filter(TermQueryBuilder("isArchive", realm.isArchive ?: isArchive))
                }

                val realmTags = realm.tags ?: emptyList()
                realmTags.find { tag ->
                    !attribute.context.tags.contains(tag.name)
                }?.let { invalidTag ->
                    throw IllegalArgumentException("Неверный тег для атрибута ${attribute.uniqueName}: ${invalidTag.name}")
                }

                val commonTags = tags?.filter { tag ->
                    attribute.context.tags.contains(tag.name)
                } ?: emptyList()

                val queryTags = realmTags + commonTags
                queryTags.forEach { tag ->
                    attributeQueryBuilder.filter(TermsQueryBuilder("tags.${tag.name}", tag.values))
                }

                // implicit filters by permitted carriers and regions
                if(attribute.context.permittedCarrierTags.isNotEmpty()) {
                    val login = SecurityUtils.getCurrentUserLogin().orElse(null)
                    if(login != "system" && userService.getIsRegoper(login) != true) {
                        val tagValues = userService.getPermittedCarrierIds(login).map(UUID::toString)
                        attribute.context.permittedCarrierTags.forEach { tag ->
                            attributeQueryBuilder.filter(TermsQueryBuilder("tags.$tag", tagValues))
                        }
                    }
                }
                if(attribute.context.permittedRegionTags.isNotEmpty()) {
                    val login = SecurityUtils.getCurrentUserLogin().orElse(null)
                    if(login != "system" && userService.getIsRegoper(login) != true) {
                        val tagValues = userService.getPermittedRegionIds(login).map(UUID::toString)
                        attribute.context.permittedRegionTags.forEach { tag ->
                            attributeQueryBuilder.filter(TermsQueryBuilder("tags.$tag", tagValues))
                        }
                    }
                }

                if(!searchValue.isNullOrEmpty()) {
                    attributeQueryBuilder.minimumShouldMatch(1)
                        .should(MatchQueryBuilder("searchValue", searchValue).operator(Operator.AND))
                    if(legacyMode) {
                        attributeQueryBuilder.should(TermQueryBuilder("key", searchValue))
                    }
                }

                queryBuilder.should(attributeQueryBuilder)
            }
        }

        val searchQuery = NativeSearchQueryBuilder()
            .withQuery(queryBuilder)
            .withPageable(PageRequest.of(0, min(limit ?: Int.MAX_VALUE, smartSearchProps.maxSearchLimit)))
            .build()

        return elasticsearchOps.search(
            searchQuery,
            SmartSearchEsDocument::class.java,
            IndexCoordinates.of(*indexAliases.toTypedArray())
        ).toList()
            .map { searchHit ->
                RankedEsDocument(
                    searchHit.content,
                    ranks.getOrPut("${searchHit.content.contextName}.${searchHit.content.attributeName}", ranks::size)
                )
            }
            .sortedWith(rankedEsDocumentComparator)
            .map(RankedEsDocument::esDocument)
    }
}
