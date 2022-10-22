package com.groupstp.dsp.domain.utils.multithreading

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.locks.ReentrantLock

/**
 * Класс для поддержки многопоточной синхронизации отдельных однородных ключей (id и пр.)
 */
class KeyLocker<K> {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private data class Lock<K>(val key: K) {
        val mutex = ReentrantLock(true)
        var queueLength = 0 // Намеренно добавлено и используется вместо ReentrantLock.queueLength
    }

    private val locks = mutableMapOf<K, Lock<K>>()

    /**
     * Описатель блокировки.
     * <p>
     * Позволяет построить паттерн <code>try-with-resources</code>, т.к. реализует интерфейс <code>Closeable</code>.
     */
    inner class LockHandle(key: K) : Closeable {
        private val lock: Lock<K>

        init {
            log.trace("Старт блокировки ключа: ${key}")
            synchronized(locks) {
                lock = locks.computeIfAbsent(key) { Lock(key) }
                lock.queueLength++
            }
            log.trace("Попытка блокировки ключа: ${key}")
            lock.mutex.lock()
            log.trace("Блокировка получена: ${key}")
        }

        override fun close() {
            log.trace("Попытка снятия блокировки ключа: ${lock.key}")
            lock.mutex.unlock()
            log.trace("Блокировка снята: ${lock.key}")
            synchronized(locks) {
                if (--lock.queueLength <= 0) {
                    locks.remove(lock.key)
                }
            }
            log.trace("Завершение снятия блокировки ключа: ${lock.key}")
        }
    }

    /**
     * Блокирует заданный ключ.
     * <p>
     * Если ключ заблокирован в другом потоке, текущий поток приостанавливается до снятия блокировки.
     *
     * @param key  ключ, котоый предполагается заблокировать
     * @return     описатель блокировки. Снятие блокировки выполняется через метод <code>close</code> данного описателя,
     *             либо путем реализации паттерна <code>try-with-resources</code>.
     */
    fun acquireKey(key: K): LockHandle {
        return LockHandle(key)
    }
}
