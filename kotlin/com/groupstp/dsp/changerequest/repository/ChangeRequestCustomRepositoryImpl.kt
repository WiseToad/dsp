package com.groupstp.dsp.repository.changerequest

import com.groupstp.dsp.config.GraphType
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequest
import java.util.*
import javax.persistence.EntityGraph
import javax.persistence.EntityManager
import kotlin.NoSuchElementException

class ChangeRequestCustomRepositoryImpl(
    private val entityManager: EntityManager
): ChangeRequestCustomRepository {

    override fun findByIdOrThrow(id: UUID): ChangeRequest {
        return findByIdOrThrow(id, null)
    }

    override fun findByIdOrThrow(id: UUID, graph: EntityGraph<ChangeRequest>?): ChangeRequest {
        return entityManager.createQuery("""
            select cr
            from dsp_ChangeRequest as cr
            where cr.id = :id
        """.trimIndent(), ChangeRequest::class.java)
            .setParameter("id", id)
            .also {
                if(graph != null) it.setHint(GraphType.LOAD, graph)
            }
            .resultList.firstOrNull()
            ?: throw NoSuchElementException("Не найден запрос на изменение с ID $id")
    }

    override fun findNonPropagatedChangeRequestsByInstanceId(entityName: String, instanceId: UUID): List<ChangeRequest> {
        return entityManager.createNamedQuery(
            "ChangeRequestCustomRepositoryImpl.findNonPropagated",
            ChangeRequest::class.java
        ).setParameter("entityName", entityName)
            .setParameter("instanceId", instanceId)
            .resultList
    }

    override fun findNonPropagatedChangeRequestsByInsertOperation(entityName: String): List<ChangeRequest> {
        return entityManager.createNamedQuery(
            "ChangeRequestCustomRepositoryImpl.findNonPropagated",
            ChangeRequest::class.java
        ).setParameter("entityName", entityName)
            //.setParameter("instanceId", TypedParameterValue(PostgresUUIDType(), null))
            .setParameter("instanceId", null as UUID?)
            .resultList
    }

    override fun findNonExported(): List<ChangeRequest> {
        return entityManager.createQuery("""
            select cr
            from dsp_ChangeRequest as cr
            where cr.parentAttribute is null
                and cr.exportTs is null
        """.trimIndent(), ChangeRequest::class.java)
            .resultList
    }
}
