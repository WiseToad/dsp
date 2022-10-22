package com.groupstp.dsp.domain.entity.container

import com.groupstp.dsp.domain.entity.changerequest.value.EntityChangeRequestValue
import com.groupstp.dsp.service.ContainerAreaCategoryService
import org.hibernate.Hibernate
import java.util.*

class ContainerAreaCategoryChangeRequestValue(
    private val containerAreaCategoryService: ContainerAreaCategoryService
): EntityChangeRequestValue<ContainerAreaCategory>() {

    override val badDTOFormatMessage = "Неверный формат обмена для категории КП"
    override val instanceNotFoundMessage = "Не найдена категория КП"

    override fun findById(id: UUID): ContainerAreaCategory? {
        return containerAreaCategoryService.findById(id)
    }

    override fun toDTO(): Any? {
        return value?.let {
            val value = Hibernate.unproxy(it, ContainerAreaCategory::class.java)
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
