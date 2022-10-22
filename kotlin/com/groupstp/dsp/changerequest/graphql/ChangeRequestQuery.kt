package com.groupstp.dsp.graphql.changerequest

import com.groupstp.dsp.domain.entity.changerequest.ChangeRequest
import com.groupstp.dsp.repository.changerequest.ChangeRequestRepository
import com.groupstp.dsp.security.AuthoritiesConstants
import graphql.kickstart.tools.GraphQLQueryResolver
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import java.util.*

@Component
class ChangeRequestQuery(
    private val changeRequestRepository: ChangeRequestRepository
): GraphQLQueryResolver {

    @PreAuthorize(AuthoritiesConstants.PreAuthorize.MASTER_OR_DISPATCHER)
    fun findChangeRequestById(id: UUID): ChangeRequest? {
        return changeRequestRepository.findById(id).orElse(null)
    }
    @PreAuthorize(AuthoritiesConstants.PreAuthorize.MASTER_OR_DISPATCHER)
    fun findNonPropagatedChangeRequestsByInstanceId(entityName: String, instanceId: UUID): List<ChangeRequest> {
        return changeRequestRepository.findNonPropagatedChangeRequestsByInstanceId(entityName, instanceId)
    }

    @PreAuthorize(AuthoritiesConstants.PreAuthorize.MASTER_OR_DISPATCHER)
    fun findNonPropagatedChangeRequestsByInsertOperation(entityName: String): List<ChangeRequest> {
        return changeRequestRepository.findNonPropagatedChangeRequestsByInsertOperation(entityName)
    }
}
