package com.groupstp.dsp.domain.utils

import org.slf4j.Logger

object AppProcessUtils {

    @JvmStatic
    fun <T> batchProcess(objects: Iterable<T>, log: Logger, errorMessage: String, process: (T) -> Unit): Int {
        var errorCount = 0
        objects.forEach {
            try {
                process(it)
            }
            catch(e: Exception) {
                if(++errorCount <= 3) {
                    log.error(errorMessage, e)
                }
                if(errorCount == 3) {
                    log.error("Вывод в лог приостановлен из-за слишком большого количества ошибок")
                }
            }
        }
        return errorCount
    }
}
