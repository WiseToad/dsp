package com.groupstp.dsp.domain.entity.changerequest.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestDecision
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestDecisionMode
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
abstract class ChangeRequestElementDTO {
    
    var id: UUID? = null

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var decisionMode: ChangeRequestDecisionMode? = null

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var decision: ChangeRequestDecision? = null

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var decisionTs: Date? = null

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var decidedBy: String? = null
}
