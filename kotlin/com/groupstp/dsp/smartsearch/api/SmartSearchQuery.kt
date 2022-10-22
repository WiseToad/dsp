package com.groupstp.dsp.smartsearch.api

import com.groupstp.dsp.security.AuthoritiesConstants
import com.groupstp.dsp.smartsearch.service.dto.find.SmartSearchInstance
import com.groupstp.dsp.smartsearch.service.dto.find.SmartSearchRealm
import com.groupstp.dsp.smartsearch.service.dto.find.SmartSearchTag
import com.groupstp.dsp.smartsearch.service.SmartSearchEsFindService
import com.groupstp.dsp.smartsearch.service.dto.find.SmartSearchResult
import graphql.kickstart.tools.GraphQLQueryResolver
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component

@Component
class SmartSearchQuery(
    private val findService: SmartSearchEsFindService
): GraphQLQueryResolver {

    @PreAuthorize(AuthoritiesConstants.PreAuthorize.READER)
    fun findInstances(realms: List<SmartSearchRealm>,
                      isArchive: Boolean?,
                      tags: List<SmartSearchTag>?,
                      searchValue: String?,
                      limit: Int?
    ): List<SmartSearchInstance> {
        return findService.findInstances(realms, isArchive, tags, searchValue, limit)
    }

    @PreAuthorize(AuthoritiesConstants.PreAuthorize.READER)
    fun smartFind(realms: List<SmartSearchRealm>,
                  isArchive: Boolean?,
                  tags: List<SmartSearchTag>?,
                  searchValue: String?,
                  limit: Int?
    ): List<SmartSearchResult> {
        return findService.smartFind(realms, isArchive, tags, searchValue, limit)
    }
}
