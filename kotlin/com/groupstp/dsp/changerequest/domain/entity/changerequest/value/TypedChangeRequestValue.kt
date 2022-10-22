package com.groupstp.dsp.domain.entity.changerequest.value

/**
 * Типизированное значение атрибута в составе запроса на изменение.
 *
 * Является базовым generic-типом для значений конкретных типов (примитивов, сложных типов, массивов и пр.)
 *
 * Для создания класса на основе данного потребуется как минимум переопределить следующее:
 * - fun fromDb(dbValue: String?)
 * - fun fromDTO(dtoValue: Any?)
 * - fun toDTO(): Any?
 * А также для особо специфичных случаев возможно вот это:
 * - fun toDb(): String?
 * - fun toString(): String
 */
abstract class TypedChangeRequestValue<T>: ChangeRequestValue() {

    var value: T? = null
        set(value) {
            field = value
            store()
        }

    private var updating = false

    private inline fun update(update: () -> Unit) {
        if(!updating) {
            updating = true
            try {
                update()
            }
            finally {
                updating = false
            }
        }
    }

    override fun reload() {
        update {
            attribute?.let { fromDb(it.value) }
        }
    }

    override fun store() {
        update {
            attribute?.let { it.value = toDb() }
        }
    }

    // Сконвертировать из/в формат, пригодный для хранения в БД
    abstract fun fromDb(dbValue: String?)
    open fun toDb(): String? = value?.toString()

    override fun toString() = value.toString()
}
