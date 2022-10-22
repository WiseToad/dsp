package com.groupstp.dsp.domain.entity.changerequest.value

/**
 * Значение атрибута с типом String в составе запроса на изменение.
 */
class StringChangeRequestValue: TypedChangeRequestValue<String>() {

    override fun fromDb(dbValue: String?) {
        value = dbValue
    }

    override fun fromDTO(dtoValue: Any?) {
        value = dtoValue?.toString()
    }

    override fun toDTO(): Any? = value
}
