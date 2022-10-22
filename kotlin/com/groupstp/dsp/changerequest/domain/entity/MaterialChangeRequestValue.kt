package com.groupstp.dsp.domain.entity.container

import com.groupstp.dsp.domain.entity.changerequest.value.EntityChangeRequestValue
import com.groupstp.dsp.service.MaterialService
import org.hibernate.Hibernate
import java.util.*

class MaterialChangeRequestValue(
    private val materialService: MaterialService
): EntityChangeRequestValue<Material>() {

    //TODO: Изменить формулировки с "сущность/экземпляр сущности Material" на человеческие
    override val badDTOFormatMessage = "Неверный формат обмена для сущности Material"
    override val instanceNotFoundMessage = "Не найден экземпляр сущности Material"

    override fun findById(id: UUID): Material? {
        return materialService.findById(id)
    }

    override fun toDTO(): Any? {
        return value?.let {
            val value = Hibernate.unproxy(it, Material::class.java)
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
