package com.groupstp.dsp.domain.utils.streamedcache

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/**
 * Абстракция для массива байт, (временно) хранящихся где-л., в который можно писать и читать через потоки ввода/вывода.
 * Предполагается использование по схеме: один раз записал (getOutputStream) - несколько раз считал (getInputStream).
 * Закрытие потоков после их получения методами get...Stream и по завершению использования - ответственность клиента.
 * Также не следует забывать про закрытие самого экземпляра StreamedCache после полного окончания работы с ним.
 */
interface StreamedCache: Closeable {
    fun getOutputStream(): OutputStream
    fun getInputStream(): InputStream
}
