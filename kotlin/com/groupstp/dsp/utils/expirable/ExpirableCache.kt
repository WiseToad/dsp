package com.groupstp.dsp.domain.utils.expirable

/**
 * Thread-safe список (кэш) значений, имеющих срок действия.
 */
class ExpirableCache<K, T> (
    private val ttl: Int, // time to live in seconds
    private val valueProvider: (K) -> T
) {
    private class Entry<T> (
        val expirable: Expirable<T>
    ){
        var lockCount = 0
    }

    private val cache = mutableMapOf<K, Entry<T>>()

    /**
     * Получить значение по заданному ключу.
     *
     * Если значение ни разу не запрашивалось, либо устарело, оно обновляется через вызов лямбды, заданной
     * в аргументах конструктора. В противном случае возвращается сохраненное ранее (закэшированное) значение.
     *
     * @param key  ключ значения
     * @return     значение
     */
    fun value(key: K): T {
        val entry = synchronized(cache) {
            cache.getOrPut(key) {
                Entry (
                    Expirable(ttl) {
                        valueProvider(key)
                    }
                )
            }.apply {
                lockCount++
            }
        }
        synchronized(entry) {
            entry.lockCount--
            return entry.expirable.value
        }
    }

    /**
     * Очистить устаревшие записи.
     */
    fun purgeExpired() {
        synchronized(cache) {
            cache.values.removeIf { entry ->
                synchronized(entry) {
                    entry.lockCount == 0 && entry.expirable.state == Expirable.State.EXPIRED
                }
            }
        }
    }
}
