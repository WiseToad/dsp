package com.groupstp.dsp.domain.entity.changerequest.value

import com.groupstp.dsp.domain.entity.StandardEntityUUID
import com.groupstp.dsp.domain.utils.AppCastUtils
import java.util.*

/**
 * Значение атрибута с типом сущности в составе запроса на изменение.
 *
 * Является базовым generic-типом для значений конкретных сущностей в модели данных.
 *
 * Для создания класса на основе данного потребуется как минимум переопределить следующее:
 * - fun findById(id: UUID): T?
 * А также желательно вот это:
 * - val badDTOFormatMessage: String
 * - val instanceNotFoundMessage: String
 * И возможно вот это (если в дополнение к id требуется передавать еще какие-то атрибуты через DTO):
 * - fun toDTO(): Any?
 */
abstract class EntityChangeRequestValue<T: StandardEntityUUID>: TypedChangeRequestValue<T>() {

    protected open val badDTOFormatMessage: String = "Неверный формат обмена для сущности"
    protected open val instanceNotFoundMessage: String = "Не найден экземпляр сущности"

    protected abstract fun findById(id: UUID): T?

    private fun fromId(id: UUID?) {
        value = id?.let {
            findById(it) ?: throw RuntimeException("$instanceNotFoundMessage с ID $it")
        }
    }

    override fun fromDb(dbValue: String?) {
        fromId(dbValue?.let(UUID::fromString))
    }

    override fun toDb(): String? {
        return value?.id?.toString()
    }

    override fun fromDTO(dtoValue: Any?) {
        val id = dtoValue?.let {
            AppCastUtils.toUUID((it as? Map<*, *>)?.get("id"))
                ?: throw RuntimeException(badDTOFormatMessage)
        }
        fromId(id)
    }

    override fun toDTO(): Any? {
        return value?.let {
            mapOf<String, Any?>(
                "id" to it.id
            )
        }
    }
}
