package com.groupstp.dsp.domain.utils

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Методы для оформления кода REST-контроллеров.
 */
object AppRestUtils {

    /**
     * Обертка для вызова методов сервисов-исполнителей из REST-контролера.
     *
     * Для трансляции типовых исключений, произошедших в сервисе-исполнителе, в требуемый HTTP-статус
     */
    @JvmStatic
    inline fun <T> perform(perform: () -> T): T {
        try {
            return perform()
        }
        catch(e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
    }
}
