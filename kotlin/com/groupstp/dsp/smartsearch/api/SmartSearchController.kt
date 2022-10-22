package com.groupstp.dsp.smartsearch.api

import com.groupstp.dsp.domain.utils.AppRestUtils
import com.groupstp.dsp.security.AuthoritiesConstants
import com.groupstp.dsp.smartsearch.service.SmartSearchEsIndexService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/smartsearch/")
class SmartSearchController(
    private val indexService: SmartSearchEsIndexService
) {

    @PostMapping("rebuildAllIndexes")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    fun rebuildAllIndexes(): ResponseEntity<Unit> {
        return AppRestUtils.perform {
            indexService.rebuildAllIndexes()
            ResponseEntity.ok().build()
        }
    }

    @PostMapping("rebuildIndexes")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    fun rebuildIndexes(
        @RequestParam contextName: String?,
        @RequestParam entityName: String?
    ): ResponseEntity<Unit> {
        return AppRestUtils.perform {
            indexService.rebuildIndexes(contextName, entityName)
            ResponseEntity.ok().build()
        }
    }
}
