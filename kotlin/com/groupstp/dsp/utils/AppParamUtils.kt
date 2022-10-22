package com.groupstp.dsp.domain.utils

import java.lang.IllegalArgumentException

/**
 * Методы для извлечения параметров из обобщенной коллекции с одновременным преобразованием в требуемый тип.
 *
 * Позволяют оформить код в удобочитаемый вид, в котором также обозначена обязательность/необязательность
 * используемых параметров.
 */
object AppParamUtils {

    @JvmStatic
    inline fun <T> optionalParam(params: Map<String, Any?>, paramName: String, cast: (Any?) -> T?): T? {
        return cast(params[paramName])
    }

    @JvmStatic
    inline fun <T> requiredParam(params: Map<String, Any?>, paramName: String, cast: (Any?) -> T?): T {
        val value = optionalParam(params, paramName, cast)
            ?: throw IllegalArgumentException("Не задан обязательный параметр: $paramName")
        if(value is Iterable<*> && !value.iterator().hasNext()) {
            throw IllegalArgumentException("Указан пустой список: $paramName")
        }
        return value
    }
}
