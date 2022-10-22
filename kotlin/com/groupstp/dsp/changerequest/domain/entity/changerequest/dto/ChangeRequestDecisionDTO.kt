package com.groupstp.dsp.domain.entity.changerequest.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestDecision
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
class ChangeRequestDecisionDTO {
    var id: UUID? = null

    var decision: ChangeRequestDecision? = null

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var decisionTs: Date? = null

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var decidedBy: String? = null
}
