package com.groupstp.dsp.domain.utils

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

/**
 * Методы для преобразования базовых типов данных между собой (не только из/в наиболее используемый везде String).
 *
 * Вероятные сценарии применения: система параметризированных отчетов, REST-сервисы с произвольными параметрами,
 * сервисы для сохранения/извлечения произвольных настроек и пр. - там, где набор и тип входных данных заранее
 * не определен, либо слабо определен на этапе кодирования.
 *
 * При ошибках преобразования могут выдаваться исключения. Если это нежелательно, можно обернуть вызов метода
 * преобразования в предлагаемый здесь же метод safeCast. Например, вместо val date = toDate(value) можно сделать так:
 * val date = safeCast(value, AppCastUtils::toDate), либо:
 * val date = safeCast(value, AppCastUtils::toDate) { log.warn("...", it) }
 */
object AppCastUtils {

    const val isoDatePattern = "yyyy-MM-dd"
    const val isoDateTimePattern = "yyyy-MM-dd'T'HH:mm:ss"
    const val isoDateTimeTzPattern = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    @JvmStatic
    inline fun <T> safeCast(value: Any?, cast: (Any?) -> T?, castError: (e: Throwable) -> Unit): T? {
        return try {
            cast(value)
        } catch (e: Exception) {
            castError(e)
            null
        }
    }

    @JvmStatic
    inline fun <T> safeCast(value: Any?, cast: (Any?) -> T?): T? = safeCast(value, cast) {}

    @JvmStatic
    fun toString(value: Any?): String? {
        return cast(value, Any::toString)
    }

    @JvmStatic
    fun toBoolean(value: Any?): Boolean? {
        return cast(value) {
            when (it) {
                //TODO: В Kotlin 1.5 можно будет воспользоваться toBooleanStrict
                is String -> when (it.trim().lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> throwCastException()
                }
                is Int -> it != 0
                is Long -> it != 0L
                is Double -> it != 0.0
                is BigDecimal -> it.compareTo(BigDecimal.ZERO) != 0
                else -> throwCastException()
            }
        }
    }

    @JvmStatic
    fun toInt(value: Any?): Int? {
        return cast(value) {
            when (it) {
                is String -> it.toInt()
                is Boolean -> if(it) 1 else 0
                is Number -> it.toInt()
                else -> throwCastException()
            }
        }
    }

    @JvmStatic
    fun toLong(value: Any?): Long? {
        return cast(value) {
            when (it) {
                is String -> it.toLong()
                is Boolean -> if(it) 1L else 0L
                is Number -> it.toLong()
                else -> throwCastException()
            }
        }
    }

    @JvmStatic
    fun toDouble(value: Any?): Double? {
        return cast(value) {
            when (it) {
                is String -> it.toDouble()
                is Number -> it.toDouble()
                else -> throwCastException()
            }
        }
    }

    @JvmStatic
    fun toBigDecimal(value: Any?): BigDecimal? {
        return cast(value) {
            when (it) {
                is String -> BigDecimal(it)
                is Int -> BigDecimal(it)
                is Long -> BigDecimal(it)
                is Double -> BigDecimal(it)
                else -> throwCastException()
            }
        }
    }

    @JvmStatic
    fun toDate(value: Any?, pattern: String = isoDatePattern): Date? {
        return cast(value) {
            when (it) {
                is String -> SimpleDateFormat(pattern).parse(it)
                else -> throwCastException()
            }
        }
    }

    @JvmStatic
    fun toDateTime(value: Any?) = toDate(value, isoDateTimePattern)

    @JvmStatic
    fun toDateTimeTz(value: Any?) = toDate(value, isoDateTimeTzPattern)

    @JvmStatic
    fun toUUID(value: Any?): UUID? {
        return cast(value) {
            when (it) {
                is String -> UUID.fromString(it)
                else -> throwCastException()
            }
        }
    }

    // Преобразовать в обобщенный List
    // Тип данных элементов списка можно затем подогнать при помощи потокового метода map и пр.
    @JvmStatic
    fun toList(value: Any?): List<*>? {
        return when (value) {
            null -> null
            is Iterable<*> -> value.toList()
            else -> listOf(value)
        }
    }

    //TODO: Избавиться в пользу метода toList(Any?): List<*>
    @JvmStatic
    fun <T> toList(value: Any?, castItem: (Any?) -> T?): List<T?>? {
        return when (value) {
            null -> null
            is Iterable<*> -> value.map(castItem)
            else -> listOf(castItem(value))
        }
    }

    //TODO: Возможно удобный метод, но он несколько выбивается из общей идеологии модуля - вынести его отдельно?
    @JvmStatic
    fun <T> toList(value: String?, delimiter: String, castItem: (String) -> T): List<T> {
        return when (value) {
            null -> emptyList()
            else -> value.split(delimiter).map { it.trim() }.map { castItem(it) }
        }
    }

    // Преобразовать в обобщенный Map
    // Типы данных ключа и значения можно затем подогнать при помощи потоковых методов mapKeys, mapValues и пр.
    @JvmStatic
    fun toMap(value: Any?): Map<*, *>? {
        return value?.let {
            (it as? Map<*, *>) ?: throwCastException()
        }
    }

    private inline fun <reified T> cast(value: Any?, cast: (Any) -> T): T? {
        return when (value) {
            null -> null
            is T -> value
            else -> cast(value)
        }
    }

    private fun throwCastException(): Nothing =
        throw IllegalArgumentException("Ошибка преобразования типа данных")
}
