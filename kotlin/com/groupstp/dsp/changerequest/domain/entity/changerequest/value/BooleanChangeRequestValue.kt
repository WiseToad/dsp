package com.groupstp.dsp.domain.entity.changerequest.value

import com.groupstp.dsp.domain.utils.AppCastUtils

/**
 * Значение атрибута с типом Boolean в составе запроса на изменение.
 */
class BooleanChangeRequestValue: TypedChangeRequestValue<Boolean>() {

    override fun fromDb(dbValue: String?) {
        value = dbValue?.toBoolean()
    }

    override fun fromDTO(dtoValue: Any?) {
        value = AppCastUtils.toBoolean(dtoValue)
    }

    override fun toDTO(): Any? = value
}
