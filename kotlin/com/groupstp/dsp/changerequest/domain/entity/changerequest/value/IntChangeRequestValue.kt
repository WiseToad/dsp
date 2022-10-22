package com.groupstp.dsp.domain.entity.changerequest.value

import com.groupstp.dsp.domain.utils.AppCastUtils

/**
 * Значение атрибута с типом Int в составе запроса на изменение.
 */
class IntChangeRequestValue: TypedChangeRequestValue<Int>() {

    override fun fromDb(dbValue: String?) {
        value = dbValue?.toInt()
    }

    override fun fromDTO(dtoValue: Any?) {
        value = AppCastUtils.toInt(dtoValue)
    }

    override fun toDTO(): Any? = value
}
