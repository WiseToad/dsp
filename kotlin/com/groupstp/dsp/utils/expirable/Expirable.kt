package com.groupstp.dsp.domain.utils.expirable

import java.util.*

/**
 * Что-л., имеющее срок действия, по истечении которого требуется обновление значения.
 */
class Expirable<T> (
    private val ttl: Int, // time to live in seconds
    private val valueProvider: () -> T
) {
    enum class State {
        TRANSIENT, // значение еще ни разу не запрашивалось
        VALID,     // значение закэшировано и еще не устарело
        EXPIRED    // значение устарело
    }

    /**
     * Текущее состояние значения.
     */
    val state: State
        get() = updateDate?.let { updateDate ->
            if((Date().time - updateDate.time) < ttl * 1000) {
                State.VALID
            } else {
                State.EXPIRED
            }
        } ?: State.TRANSIENT

    /**
     * Значение.
     *
     * Если значение ни разу не запрашивалось, либо устарело, оно обновляется через вызов лямбды, заданной
     * в аргументах конструктора. В противном случае возвращается сохраненное ранее (закэшированное) значение.
     */
    val value: T
        get() = if(state == State.VALID) {
            @Suppress("UNCHECKED_CAST")
            cachedValue as T
        } else {
            valueProvider().also { value ->
                cachedValue = value
                updateDate = Date()
            }
        }

    private var cachedValue: T? = null
    private var updateDate: Date? = null
}
