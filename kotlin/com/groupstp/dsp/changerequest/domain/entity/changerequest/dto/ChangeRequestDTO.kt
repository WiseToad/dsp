package com.groupstp.dsp.domain.entity.changerequest.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestOperation
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestSource
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
class ChangeRequestDTO: ChangeRequestElementDTO() {

    var source: ChangeRequestSource? = null

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var verificationId: UUID? = null

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var verificationRequestId: UUID? = null

    var operation: ChangeRequestOperation? = null

    var entityName: String? = null

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var instanceKey: Map<String, Any?>? = null

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var attributes: List<ChangeRequestAttributeDTO>? = null

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var reason: String? = null

    var requestTs: Date? = null

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var requestedBy: String? = null
}
