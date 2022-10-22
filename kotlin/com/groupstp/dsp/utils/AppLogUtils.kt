package com.groupstp.dsp.domain.utils

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import java.io.Closeable
import java.util.*
import java.util.stream.Collectors

/**
 * Утилиты для поддержки логирования.
 */
object AppLogUtils {

    /**
     * Вернуть стектрейс для вывода в лог в отладочных целях.
     */
    @JvmStatic
    fun getStackTrace() = getStackTrace(null)

    /**
     * Вернуть стектрейс для вывода в лог в отладочных целях,
     * состоящий только из вызовов из указанного пакета и его подпакетов.
     *
     * @param packageName  имя пакета
     */
    @JvmStatic
    fun getStackTrace(packageName: String?): String {
        return "Текущий стек:\n" + Arrays.stream(Thread.currentThread().stackTrace)
            .filter { stackTraceElement -> packageName == null || stackTraceElement.className.startsWith(packageName) }
            .map { stackTraceElement -> "\tat $stackTraceElement" }
            .collect(Collectors.joining("\n"))
    }

    /**
     * Выполнить блок кода с логированием всех выполняемых в нем SQL-запросов.
     *
     * @param code         блок кода
     */
    @JvmStatic
    inline fun <T> execWithSqlLogging(code: () -> T): T =
        execWithSqlLogging(null, code)

    /**
     * Выполнить блок кода с логированием всех выполняемых в нем SQL-запросов,
     * если эффективный уровень логирования у указанного референсного логгера DEBUG или ниже.
     *
     * @param loggerName   имя референсного логгера
     * @param code         блок кода
     */
    @JvmStatic
    inline fun <T> execWithSqlLogging(loggerName: String?, code: () -> T): T =
        execWithSqlLogging(loggerName, Level.DEBUG, code)

    /**
     * Выполнить блок кода с логированием всех выполняемых в нем SQL-запросов,
     * если эффективный уровень логирования у указанного референсного логгера равен или ниже
     * заданного порогового значения.
     *
     * @param loggerName   имя референсного логгера
     * @param level        пороговый уровень логирования
     * @param code         блок кода
     */
    @JvmStatic
    inline fun <T> execWithSqlLogging(loggerName: String?, level: Level?, code: () -> T): T {
        return SqlLoggingHandle(loggerName, level).use {
            code()
        }
    }

    class SqlLoggingHandle(
        loggerName: String?,
        level: Level?
    ): Closeable {

        private val loggerContext = org.slf4j.LoggerFactory.getILoggerFactory() as LoggerContext

        private var sqlLogger: Logger? = null
        private var sqlLoggerLevel: Level? = null
        private var basicBinderLogger: Logger? = null
        private var basicBinderLoggerLevel: Level? = null

        init {
            if (loggerName == null) {
                startLogging()
            } else {
                val loggerLevel = loggerContext.exists(loggerName)?.effectiveLevel
                if (loggerLevel != null && loggerLevel.levelInt <= (level ?: Level.DEBUG).levelInt) {
                    startLogging()
                }
            }
        }

        private fun startLogging() {
            sqlLogger = loggerContext.exists("org.hibernate.SQL")
            sqlLoggerLevel = sqlLogger?.level
            sqlLogger?.level = Level.DEBUG

            basicBinderLogger = loggerContext.exists("org.hibernate.type.descriptor.sql.BasicBinder")
            basicBinderLoggerLevel = basicBinderLogger?.level
            basicBinderLogger?.level = Level.TRACE
        }

        override fun close() {
            sqlLogger?.level = sqlLoggerLevel
            sqlLogger = null

            basicBinderLogger?.level = basicBinderLoggerLevel
            basicBinderLogger = null
        }
    }
}
