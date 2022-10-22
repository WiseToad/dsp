package com.groupstp.dsp.domain.utils.multithreading

import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate

/**
 * Класс для одиночного и пакетного поиска и создания (если не найдено) экземпляров сущности заданного типа.
 * <p>
 * Поддерживается потокобезопасность (при соблюдениии условия единственности экземпляра данного класса),
 * а также транзакционность при создании сущностей.
 *
 * @param findEntity           функция поиска экземпляра сущности. Должна вернуть <code>null</code> если не найдено.
 * @param createEntity         функция создания и сохранения (например, методом <code>save</code> и пр.) нового экземпляра
 * @param transactionTemplate  шаблон транзакции для создания новых экземпляров сущности
 */
class EntityProvider<E, K> (
    val findEntity: (key: K) -> E?,
    val createEntity: (key: K) -> E,
    val transactionTemplate: TransactionTemplate
){
    private val log = LoggerFactory.getLogger(this.javaClass)

    private val keyLocker = KeyLocker<K>()

    /**
     * Ищет и возвращает экземпляр сущности с заданным ключом.
     * Если не найдено, создает новый экземпляр в контексте транзакции.
     *
     * @param key          ключ требуемого экземпляра сущности
     * @param postProcess  функция пост-обработки. Выполняется в едином потокобезопасном и транзакционном контексте с основным телом
     * @return             требуемый экземпляр сущности
     */
    fun findOrCreate(key: K, postProcess: (E, isJustCreated: Boolean) -> Unit = { _: E, _: Boolean -> }): E
    {
        keyLocker.acquireKey(key).use {
            var entity = findEntity(key)
            if(entity != null) {
                postProcess(entity, false)
            } else {
                transactionTemplate.execute {
                    entity = createEntity(key)
                    postProcess(entity!!, true)
                }
            }
            return entity!!
        }
    }

    /**
     * Ищет и возвращает список экземпляров сущности по заданному списку ключей.
     * Не найденные экземпляры сущности создает в контексте единой транзакции.
     *
     * @param keys         список ключей требуемых экземпляров сущности
     * @param postProcess  функция пост-обработки. Выполняется в едином потокобезопасном и транзакционном контексте с основным телом
     * @return             список требуемых экземпляров сущности
     */
    fun findOrCreate(keys: Iterable<K>, postProcess: (E, isJustCreated: Boolean) -> Unit = { _: E, _: Boolean -> }): List<E>
    {
        val keyLocks = mutableListOf<KeyLocker<K>.LockHandle>()
        val notFoundKeys = mutableListOf<K>()
        val entities = mutableListOf<E>()

        synchronized(this) { // Для предотвращения deadlock-ов с себе подобными
            try {
                log.trace("Блокировка и поиск экземпляров сущности по заданному списку ключей")
                for (key in keys) {
                    keyLocks.add(keyLocker.acquireKey(key))
                    val entity = findEntity(key)
                    if (entity != null) {
                        entities.add(entity)
                        postProcess(entity, false)
                        keyLocks.removeLast().close()
                    } else {
                        notFoundKeys.add(key)
                    }
                }
                log.trace("Найдено: ${entities.size} шт., не найдено: ${notFoundKeys.size} шт.")

                if (notFoundKeys.isNotEmpty()) {
                    log.trace("Старт транзакции и создание новых экземпляров (${notFoundKeys.size} шт.)")
                    transactionTemplate.execute {
                        for (key in notFoundKeys) {
                            val entity = createEntity(key)
                            postProcess(entity, true)
                            entities.add(entity)
                        }
                    }
                }
            } finally {
                log.trace("Разблокировка ключей (${keyLocks.size} шт.)")
                for (keyLock in keyLocks) {
                    keyLock.close()
                }
            }
        }
        return entities
    }
}
