package com.groupstp.dsp.domain.entity.changerequest.value

/**
 * Значение атрибута с типом Enum в составе запроса на изменение.
 */
class EnumChangeRequestValue<T: Enum<T>>(
    private val valueOf: (String) -> T
): TypedChangeRequestValue<T>() {

    override fun fromDb(dbValue: String?) {
        value = dbValue?.let { valueOf(it) }
    }

    override fun fromDTO(dtoValue: Any?) {
        value = dtoValue?.toString()?.let { valueOf(it) }
    }

    override fun toDTO(): Any? = value?.toString()
}

inline fun <reified T: Enum<T>> EnumChangeRequestValue() =
    EnumChangeRequestValue { enumValueOf<T>(it) }
