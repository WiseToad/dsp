package com.groupstp.dsp.service.changerequest

import com.groupstp.dsp.config.env.AppEnvironment
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestDecisionMode
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestOperation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Настройки подсистемы ChangeRequest.
 */
@Component
class ChangeRequestProperties(
    private val env: AppEnvironment
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val decisionModeKeyPrefix = "changeRequest.decisionMode."

    class DecisionModes {
        val operationModes = mutableMapOf<ChangeRequestOperation, ChangeRequestDecisionMode?>()
        val attributeModes = mutableMapOf<String, ChangeRequestDecisionMode?>()
    }

    private var decisionModes = mutableMapOf<String, DecisionModes>()

    init {
        reload()
    }

    /**
     * Перечитать (динамические) настройки.
     */
    final fun reload() {
        decisionModes.clear()
        env.getDynamicPropertyKeys()
            .filter { it.startsWith(decisionModeKeyPrefix) }
            .forEach { key ->
                try {
                    val (entityName, propertyStr) = key.substring(decisionModeKeyPrefix.length).let {
                        it.substringBefore(".") to it.substringAfter(".", "")
                    }
                    when(propertyStr.substringBefore(".")) {
                        "operation" -> {
                            val operationName = propertyStr.substringAfter(".", "")
                            val operation = ChangeRequestOperation.valueOf(operationName)
                            env.getProperty(key)?.let { decisionModeName ->
                                val decisionMode = ChangeRequestDecisionMode.valueOf(decisionModeName)
                                decisionModes
                                    .getOrPut(entityName, ::DecisionModes)
                                    .operationModes[operation] = decisionMode
                            }
                        }
                        "attribute" -> {
                            val attributeName = propertyStr.substringAfter(".", "")
                            env.getProperty(key)?.let { decisionModeName ->
                                val decisionMode = ChangeRequestDecisionMode.valueOf(decisionModeName)
                                decisionModes
                                    .getOrPut(entityName, ::DecisionModes)
                                    .attributeModes[attributeName] = decisionMode
                            }
                        }
                    }
                }
                catch(e: Exception) {
                    log.trace("Ошибка считывания настройки $key", e)
                }
            }
    }

    /**
     * Получить все способы принятия решения, заданные для сущностей.
     */
    fun getDecisionModes(): Map<String, DecisionModes> {
        return decisionModes
    }

    /**
     * Задать способы принятия решения для сущности.
     *
     * Указанные значения мерджатся с уже существующими.
     * Для удаления настройки следует указать для нее null-значение.
     */
    fun setDecisionModes(decisionModes: Map<String, DecisionModes>) {
        decisionModes.forEach { (entityName, decisionModes) ->
            decisionModes.operationModes.forEach { (operation, decisionMode) ->
                setDecisionMode(entityName, operation, decisionMode)
            }
            decisionModes.attributeModes.forEach { (attributeName, decisionMode) ->
                // Установка способа принятия решения для конкретного атрибута всегда подразумевает, что речь идет об изменениях вида UPDATE
                setDecisionMode(entityName, ChangeRequestOperation.UPDATE, attributeName, decisionMode)
            }
        }
    }

    /**
     * Получить способ принятия решения для конкретного вида изменения или атрибута.
     */
    fun getDecisionMode(entityName: String, operation: ChangeRequestOperation, attributeName: String? = null): ChangeRequestDecisionMode {
        val properties = decisionModes[entityName]
            ?: return ChangeRequestDecisionMode.ACCEPT

        return when(operation) {
            ChangeRequestOperation.INSERT,
            ChangeRequestOperation.DELETE -> {
                if(attributeName != null) {
                    throw IllegalArgumentException("Для вида изменения $operation способ принятия решения не может быть определен с точностью до атрибута")
                }
                properties.operationModes[operation]
                    ?: ChangeRequestDecisionMode.ACCEPT
            }
            ChangeRequestOperation.UPDATE -> {
                if(attributeName == null) {
                    throw IllegalArgumentException("Для вида изменения $operation способ принятия решения может быть определен только с указанием конкретного атрибута")
                }
                properties.attributeModes[attributeName]
                    ?: properties.operationModes[operation]
                    ?: ChangeRequestDecisionMode.ACCEPT
            }
        }
    }

    /**
     * Задать способ принятия решения для конкретного вида изменения или атрибута.
     *
     * Если decisionMode указано равным null, настройка удаляется.
     */
    fun setDecisionMode(entityName: String, operation: ChangeRequestOperation, attributeName: String?, decisionMode: ChangeRequestDecisionMode?) {
        when(operation) {
            ChangeRequestOperation.INSERT,
            ChangeRequestOperation.DELETE -> {
                if(attributeName != null) {
                    throw IllegalArgumentException("Для вида изменения $operation способ принятия решения не может быть задан с точностью до атрибута")
                }
                val key = "$decisionModeKeyPrefix$entityName.operation.$operation"
                if(decisionMode != null) {
                    env.setProperty(key, decisionMode)
                    this.decisionModes
                        .getOrPut(entityName, ::DecisionModes)
                        .operationModes[operation] = decisionMode
                } else {
                    env.removeDynamicProperty(key)
                    this.decisionModes
                        .getOrPut(entityName, ::DecisionModes)
                        .operationModes.remove(operation)
                }
            }
            ChangeRequestOperation.UPDATE -> {
                if(attributeName == null) {
                    throw IllegalArgumentException("Для вида изменения $operation способ принятия решения может быть задан только с указанием конкретного атрибута")
                }
                val key = "$decisionModeKeyPrefix$entityName.attribute.$attributeName"
                if(decisionMode != null) {
                    env.setProperty(key, decisionMode)
                    this.decisionModes
                        .getOrPut(entityName, ::DecisionModes)
                        .attributeModes[attributeName] = decisionMode
                } else {
                    env.removeDynamicProperty(key)
                    this.decisionModes
                        .getOrPut(entityName, ::DecisionModes)
                        .attributeModes.remove(attributeName)
                }
            }
        }
    }

    fun setDecisionMode(entityName: String, operation: ChangeRequestOperation, decisionMode: ChangeRequestDecisionMode?) {
        setDecisionMode(entityName, operation, null, decisionMode)
    }
}
