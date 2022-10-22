package com.groupstp.dsp.domain.entity.changerequest.value

import com.groupstp.dsp.domain.utils.AppCastUtils

/**
 * Значение атрибута с типом Double в составе запроса на изменение.
 */
class DoubleChangeRequestValue: TypedChangeRequestValue<Double>() {

    override fun fromDb(dbValue: String?) {
        value = dbValue?.toDouble()
    }

    override fun fromDTO(dtoValue: Any?) {
        value = AppCastUtils.toDouble(dtoValue)
    }

    override fun toDTO(): Any? = value
}
