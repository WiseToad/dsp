package com.groupstp.dsp.domain.entity

import com.groupstp.dsp.domain.entity.changerequest.value.EntityChangeRequestValue
import com.groupstp.dsp.service.ColorService
import org.hibernate.Hibernate
import java.util.*

class ColorChangeRequestValue(
    private val colorService: ColorService
): EntityChangeRequestValue<Color>() {

    override val badDTOFormatMessage = "Неверный формат обмена для цвета"
    override val instanceNotFoundMessage = "Не найден цвет"

    override fun findById(id: UUID): Color? {
        return colorService.findById(id)
    }

    override fun toDTO(): Any? {
        return value?.let {
            val value = Hibernate.unproxy(it, Color::class.java)
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
