package com.groupstp.dsp.domain.entity.container

import com.groupstp.dsp.domain.entity.changerequest.value.EntityChangeRequestValue
import com.groupstp.dsp.service.ContainerAreaTypeService
import org.hibernate.Hibernate
import java.util.*

class ContainerAreaTypeChangeRequestValue(
    private val containerAreaTypeService: ContainerAreaTypeService
): EntityChangeRequestValue<ContainerAreaType>() {

    override val badDTOFormatMessage = "Неверный формат обмена для типа КП"
    override val instanceNotFoundMessage = "Не найден тип КП"

    override fun findById(id: UUID): ContainerAreaType? {
        return containerAreaTypeService.findById(id)
    }

    override fun toDTO(): Any? {
        return value?.let {
            val value = Hibernate.unproxy(it, ContainerAreaType::class.java)
            mapOf<String, Any?>(
                "id" to value.id,
                "code" to value.code
            )
        }
    }

    override fun toString(): String {
        return value?.name ?: "null"
    }
}
