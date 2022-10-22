package com.groupstp.dsp.domain.entity.changerequest.value

import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestAttribute

/**
 * Обобщенное значение атрибута в составе запроса на изменение.
 *
 * Служит для поддержки конвертации (в т.ч. неявной) типизированного значения в/из форматов хранения и обмена.
 */
abstract class ChangeRequestValue {

    var attribute: ChangeRequestAttribute? = null
        set(value) {
            if(value !== field) {
                field?.typedValue = null
                field = value
                field?.typedValue = this
            }
        }

    // Синхронизировать значение со значением в связанном атрибуте
    abstract fun reload()
    abstract fun store()

    // Сконвертировать из/в формат, пригодный для обмена через DTO
    abstract fun fromDTO(dtoValue: Any?)
    abstract fun toDTO(): Any?
}
