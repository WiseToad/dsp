package com.groupstp.dsp.domain.entity.changerequest.value

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequest
import com.groupstp.dsp.domain.entity.changerequest.dto.ChangeRequestDTO
import com.groupstp.dsp.domain.utils.AppCastUtils
import com.groupstp.dsp.service.changerequest.ChangeRequestService
import java.text.SimpleDateFormat

/**
 * Значение атрибута в составе запроса на изменение, представляющее собой список дочерних запросов на изменение.
 */
class ChildChangeRequestValue(
    private val changeRequestService: ChangeRequestService
): ChangeRequestValue() {

    private val jsonMapper by lazy {
        jacksonObjectMapper().also {
            it.dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXX")
        }
    }

    var value: MutableSet<ChangeRequest>?
        get() {
            return attribute?.childRequests
        }
        set(value) {
            attribute?.childRequests = value
        }

    override fun reload() = Unit
    override fun store() = Unit

    override fun fromDTO(dtoValue: Any?) {
        value = AppCastUtils.toList(dtoValue)
            ?.map {
                changeRequestService.createChangeRequest(
                    jsonMapper.convertValue(it, ChangeRequestDTO::class.java),
                    attribute
                )
            }?.toMutableSet()
    }

    override fun toDTO(): Any? {
        return value?.map(changeRequestService::mapChangeRequestToDTO)
    }
}
