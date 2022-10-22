package com.groupstp.dsp.repository.changerequest

import com.groupstp.dsp.domain.entity.changerequest.ChangeRequest
import java.util.*
import javax.persistence.EntityGraph

interface ChangeRequestCustomRepository {

    /**
     * Найти запрос на изменение по его ID, если ничего не найдено - выдать исключение.
     */
    fun findByIdOrThrow(id: UUID): ChangeRequest

    /**
     * Найти запрос на изменение с заданным графом по его ID, если ничего не найдено - выдать исключение.
     */
    fun findByIdOrThrow(id: UUID, graph: EntityGraph<ChangeRequest>?): ChangeRequest

    /**
     * Найти запросы на изменение с непроведенными изменениями по ID экземпляра.
     */
    fun findNonPropagatedChangeRequestsByInstanceId(entityName: String, instanceId: UUID): List<ChangeRequest>

    /**
     * Найти запросы на добавление с непроведенными изменениями.
     */
    fun findNonPropagatedChangeRequestsByInsertOperation(entityName: String): List<ChangeRequest>

    /**
     * Найти неэкспортированные запросы на изменение.
     */
    fun findNonExported(): List<ChangeRequest>
}
