package com.groupstp.dsp.service.changerequest

import com.groupstp.dsp.domain.entity.changerequest.ChangeRequest
import com.groupstp.dsp.domain.entity.changerequest.value.ChangeRequestValue
import java.util.*

/**
 * Типизированный сервис обработки запросов на изменение.
 *
 * Является вспомогательным сервисом для обработки частей, специфичных для каждой конкретной сущности (КП, КГ и пр.)
 * Методы данного сервиса используются изнутри сервиса ChangeRequestService и ни для чего другого больше не предназначены.
 */
interface TypedChangeRequestService {

    /**
     * Имя сущности, для которой создана реализация сервиса, например "dsp_ContainerArea".
     */
    val entityName: String

    /**
     * Создать запрос на изменение нужного типа, соответствующего данной сущности.
     *
     * Должна быть создана пустая заготовка, у которой задан только экземпляр изменяемой сущности,
     * если в параметрах вызова был указан его instanceId.
     */
    fun createChangeRequest(instanceId: UUID?): ChangeRequest

    /**
     * Применить решения по изменениям.
     *
     * Предполагается, что перед вызовом данного метода уже соблюдена полнота и согласованность принятых решений
     * по всем изменениям в составе указанного запроса.
     */
    fun applyDecisions(changeRequest: ChangeRequest)

    /**
     * Определить типизированное значение по имени атрибута.
     */
    fun resolveTypedValueByName(attributeName: String): ChangeRequestValue

    /**
     * Сконвертировать ключ экземпляра сущности в DTO.
     */
    fun mapInstanceKeyToDTO(changeRequest: ChangeRequest): Map<String, Any?>?
}
