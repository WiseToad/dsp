package com.groupstp.dsp.service.changerequest

import com.groupstp.dsp.domain.entity.changerequest.ChangeRequest
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestAttribute
import com.groupstp.dsp.domain.entity.changerequest.dto.ChangeRequestDTO
import com.groupstp.dsp.domain.entity.changerequest.dto.ChangeRequestDecisionDTO
import java.util.*

/**
 * Cервис обработки запросов на изменение.
 */
interface ChangeRequestService {

    /**
     * Создать запрос на изменение.
     */
    fun createChangeRequest(
        changeRequestDTO: ChangeRequestDTO,
        parentAttribute: ChangeRequestAttribute? = null
    ): ChangeRequest

    /**
     * Создать запрос на изменение на основе указанной верификации.
     */
    fun createChangeRequestByVerification(verificationId: UUID): ChangeRequest

    /**
     * Зафиксировать принятые решения по изменениям.
     */
    fun setDecisions(decisionDTOs: List<ChangeRequestDecisionDTO>)

    /**
     * Примененить принятые решения по изменению.
     */
    fun applyDecisions(changeRequestId: UUID)

    /**
     * Определить типизированное значение всех атрибутов запроса на изменение по их имени.
     */
    fun resolveTypedValuesByName(changeRequest: ChangeRequest)

    /**
     * Сконвертировать запрос на изменение в DTO.
     */
    fun mapChangeRequestToDTO(changeRequest: ChangeRequest): ChangeRequestDTO

    /**
     * Сконвертировать ключ экземпляра сущности запроса на изменение в DTO.
     */
    fun mapInstanceKeyToDTO(changeRequest: ChangeRequest): Map<String, Any?>?
}
