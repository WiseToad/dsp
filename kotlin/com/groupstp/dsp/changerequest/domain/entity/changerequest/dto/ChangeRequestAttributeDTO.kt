package com.groupstp.dsp.domain.entity.changerequest.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class ChangeRequestAttributeDTO: ChangeRequestElementDTO() {

    var name: String? = null

    var value: Any? = null
}
