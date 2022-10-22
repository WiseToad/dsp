package com.groupstp.dsp.service.changerequest

import com.groupstp.dsp.domain.entity.StandardEntityUUID
import com.groupstp.dsp.domain.entity.changerequest.*
import java.util.*

/**
 * Реализация типизированного сервиса обработки запросов на изменение.
 *
 * Является базовым generic-типом для реализаций сервисов конкретных сущностей в модели данных.
 *
 * Для создания класса на основе данного потребуется как минимум переопределить следующее:
 * - val entityName: String
 * - fun createChangeRequest(instanceId: UUID?): ChangeRequest
 * - fun insertInstance(changeRequest: ChangeRequest)
 * - fun updateInstance(changeRequest: ChangeRequest)
 * - fun deleteInstance(changeRequest: ChangeRequest)
 * - fun resolveTypedValueByName(attributeName: String): ChangeRequestValue
 * - fun setInstanceAttribute(instance: ContainerArea, attribute: ChangeRequestAttribute)
 * - fun mapInstanceKeyToDTO(changeRequest: ChangeRequest): Map<String, Any?>?
 * И возможно вот это:
 * - fun beforeApplyDecisions(changeRequest: ChangeRequest)
 */
abstract class TypedChangeRequestServiceImpl<T: StandardEntityUUID>: TypedChangeRequestService {

    override fun applyDecisions(changeRequest: ChangeRequest) {
        if(changeRequest.applyTs == null) {
            beforeApplyDecisions(changeRequest)

            if(changeRequest.operation == ChangeRequestOperation.UPDATE
                || changeRequest.decision == ChangeRequestDecision.ACCEPTED
            ) {
                when(changeRequest.operation) {
                    ChangeRequestOperation.INSERT -> insertInstance(changeRequest)
                    ChangeRequestOperation.UPDATE -> updateInstance(changeRequest)
                    ChangeRequestOperation.DELETE -> deleteInstance(changeRequest)
                }
            }
            changeRequest.applyTs = Date()
        }
    }

    open fun beforeApplyDecisions(changeRequest: ChangeRequest) = Unit

    abstract fun insertInstance(changeRequest: ChangeRequest)

    abstract fun updateInstance(changeRequest: ChangeRequest)

    abstract fun deleteInstance(changeRequest: ChangeRequest)

    protected fun setInstanceAttributes(instance: T, changeRequest: ChangeRequest) {
        changeRequest.attributes?.forEach { attribute ->
            if(attribute.applyTs == null) {
                if(changeRequest.operation == ChangeRequestOperation.INSERT
                    || attribute.decision == ChangeRequestDecision.ACCEPTED
                ) {
                    setInstanceAttribute(instance, attribute)
                }
                attribute.applyTs = Date()
            }
        }
    }

    abstract fun setInstanceAttribute(instance: T, attribute: ChangeRequestAttribute)
}
