package com.groupstp.dsp.domain.entity.container

import com.groupstp.dsp.domain.entity.changerequest.value.EntityChangeRequestValue
import com.groupstp.dsp.service.ContainerTypeService
import org.hibernate.Hibernate
import java.util.*
import javax.persistence.EntityManager

class ContainerTypeChangeRequestValue(
    private val entityManager: EntityManager,
    private val containerTypeService: ContainerTypeService
): EntityChangeRequestValue<ContainerType>() {

    override val badDTOFormatMessage = "Неверный формат обмена для типа контейнера"
    override val instanceNotFoundMessage = "Не найден тип контейнера"

    override fun findById(id: UUID): ContainerType? {
        val graph = entityManager.createEntityGraph(ContainerType::class.java).apply {
            addAttributeNodes("loadTypes")
        }
        return containerTypeService.findById(id, graph)
    }

    override fun toDTO(): Any? {
        return value?.let {
            val value = Hibernate.unproxy(it, ContainerType::class.java)
            mapOf(
                "id" to value.id,
                "code" to value.code,
                "name" to value.name,
                "volume" to value.volume,
                "loadTypes" to value.loadTypes?.map { loadType ->
                    mapOf<String, Any?>(
                        "code" to loadType.code,
                        "name" to loadType.name
                    )
                }
            )
        }
    }

    override fun toString(): String {
        return value?.name ?: "null"
    }
}
