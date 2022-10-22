package com.groupstp.dsp.graphql.changerequest

import com.groupstp.dsp.domain.entity.changerequest.ChangeRequest
import com.groupstp.dsp.domain.entity.changerequest.dto.ChangeRequestDTO
import com.groupstp.dsp.domain.entity.changerequest.dto.ChangeRequestDecisionDTO
import com.groupstp.dsp.security.AuthoritiesConstants
import com.groupstp.dsp.service.changerequest.ChangeRequestService
import graphql.kickstart.tools.GraphQLMutationResolver
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component

@Component
class ChangeRequestMutation(
    private val changeRequestService: ChangeRequestService
) : GraphQLMutationResolver {

    @PreAuthorize(AuthoritiesConstants.PreAuthorize.MASTER_OR_DISPATCHER)
    fun createChangeRequest(changeRequestDTO: ChangeRequestDTO): ChangeRequest {
        return changeRequestService.createChangeRequest(changeRequestDTO)
    }

    @PreAuthorize(AuthoritiesConstants.PreAuthorize.MASTER_OR_DISPATCHER)
    fun setDecisions(decisionDTOs: List<ChangeRequestDecisionDTO>) {
        return changeRequestService.setDecisions(decisionDTOs)
    }
}
