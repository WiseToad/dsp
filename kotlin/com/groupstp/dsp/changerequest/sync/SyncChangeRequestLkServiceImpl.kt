package com.groupstp.dsp.service.sync.lk.changerequest

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.groupstp.dsp.config.KafkaConfig
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequest
import com.groupstp.dsp.domain.entity.changerequest.dto.ChangeRequestDecisionDTO
import com.groupstp.dsp.domain.utils.AppProcessUtils
import com.groupstp.dsp.domain.utils.multithreading.CloseableLock
import com.groupstp.dsp.repository.changerequest.ChangeRequestRepository
import com.groupstp.dsp.service.changerequest.ChangeRequestService
import com.groupstp.dsp.service.kafkalogging.KafkaLoggingService
import com.groupstp.dsp.service.sync.lk.LkSynchServiceConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

@Service
class SyncChangeRequestLkServiceImpl(
    private val kafkaConfig: KafkaConfig,
    private val lkSynchServiceConfig: LkSynchServiceConfig,
    private val changeRequestRepository: ChangeRequestRepository,
    kafkaLoggingService: KafkaLoggingService
): SyncChangeRequestLkService {

    //TODO: Как-нибудь избавиться от этой копипасты в разных сервисах обмена через Кафку

    private val log = LoggerFactory.getLogger(javaClass)
    private val kafkaLog = kafkaLoggingService.getLogger(javaClass.simpleName)

    @Lazy
    @Autowired
    private lateinit var changeRequestService: ChangeRequestService

    private val jsonMapper = jacksonObjectMapper().also {
        it.dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXX")
    }

    inner class Config {
        private val regionCode = lkSynchServiceConfig.regionCode
            ?: throw RuntimeException("Не задана обязательная системная настройка regionCode")

        val producerTopic = "CHANGE_REQUEST_DSP_TO_LK$regionCode"
        val consumerTopic = "CHANGE_REQUEST_LK_TO_DSP$regionCode"

        val kafkaProducer = run {
            val producerProps = kafkaConfig.getProducerProps().also {
                it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
            }
            KafkaProducer<String, String>(producerProps)
        }

        val kafkaConsumer = run {
            val consumerProps = kafkaConfig.getConsumerProps()
            KafkaConsumer<String, String>(consumerProps).also {
                it.subscribe(listOf(consumerTopic))
            }
        }
    }

    private val config =
        try {
            Config()
        }
        catch(e: Exception) {
            log.error("Ошибка инициализации Kafka-обмена с ЛК по запросам на изменение", e)
            null
        }

    val exportLock = ReentrantLock(true)
    val importLock = ReentrantLock(true)

    private fun isSyncEnabled(msg: Logger.(String) -> Unit = Logger::warn): Boolean {
        if(config == null && !lkSynchServiceConfig.changeRequestDryProcessing) {
            log.msg("Обмен с ЛК не инициализирован, задание пропущено")
            return false
        }
        if(!lkSynchServiceConfig.syncEnabled) {
            log.msg("Обмен с ЛК отключен, задание пропущено")
            return false
        }
        if(!lkSynchServiceConfig.changeRequestEnable) {
            log.msg("Обмен с ЛК по запросам на изменение отключен, задание пропущено")
            return false
        }
        return true
    }

    override fun exportChangeRequest(changeRequest: ChangeRequest) {
        if(!isSyncEnabled()) {
            return
        }
        if(!exportLock.tryLock(5, TimeUnit.SECONDS)) {
            throw RuntimeException("Превышен таймаут ожидания экспорта в ЛК запроса на изменение")
        }
        CloseableLock(exportLock).use {
            if(lkSynchServiceConfig.changeRequestDryProcessing) {
                log.warn("Включен режим пробного прогона, фактический экспорт в ЛК запроса на изменение будет пропущен")
            }
            exportChangeRequestInternal(changeRequest)
        }
    }

    private fun exportChangeRequestInternal(changeRequest: ChangeRequest) {
        if(changeRequest.parentAttribute != null) {
            throw RuntimeException("Дочерний запрос на изменение не может быть экспортирован в ЛК непосредственно")
        }

        val changeRequestDTO = changeRequestService.mapChangeRequestToDTO(changeRequest)
        val message = jsonMapper.writeValueAsString(changeRequestDTO)

        if(!lkSynchServiceConfig.changeRequestDryProcessing) {
            val config = config ?: throw AssertionError()
            val record = ProducerRecord<String, String>(config.producerTopic, message)
            config.kafkaProducer.send(record)
            kafkaLog.write(record)

            changeRequest.exportTs = Date()
            changeRequestRepository.save(changeRequest)
        }
    }

    /**
     * Обработчик административного REST API
     */
    override fun exportStuckChangeRequests() {
        if(!isSyncEnabled()) {
            return
        }
        if(!exportLock.tryLock(5, TimeUnit.SECONDS)) {
            throw RuntimeException("Превышен таймаут ожидания экспорта в ЛК подвисших запросов на изменение")
        }
        CloseableLock(exportLock).use {
            log.info("Запуск экспорта в ЛК подвисших запросов на изменение")

            if(lkSynchServiceConfig.changeRequestDryProcessing) {
                log.warn("Включен режим пробного прогона, фактический экспорт в ЛК запросов на изменение будет пропущен")
            }

            val changeRequests = changeRequestRepository.findNonExported()
            log.info("Для экспорта в ЛК отобрано ${changeRequests.size} подвисших запросов на изменение")

            val errorCount = AppProcessUtils.batchProcess(changeRequests,
                log, "Ошибка экспорта в ЛК подвисшего запроса на изменение",
                ::exportChangeRequestInternal
            )
            log.info("Завершен экспорт в ЛК подвисших запросов на изменение, $errorCount ошибок.")
        }
    }

    /**
     * Обработчик джоба
     */
    override fun importDecisionsJob() {
        if(!isSyncEnabled(Logger::trace)) {
            return
        }
        if(!importLock.tryLock(5, TimeUnit.SECONDS)) {
            throw RuntimeException("Превышен таймаут ожидания импорта из ЛК решений по изменениям")
        }
        CloseableLock(importLock).use {
            if(lkSynchServiceConfig.verificationDryProcessing) {
                log.trace("Включен режим пробного прогона, фактический импорт из ЛК решений по изменениям пропущен")
                return
            }

            val config = config ?: throw AssertionError()

            val consumerRecords = config.kafkaConsumer.poll(Duration.ofMillis(1000))
            val errorCount = AppProcessUtils.batchProcess(consumerRecords,
                log, "Ошибка импорта из ЛК решения по изменению"
            ) { record ->
                kafkaLog.write(record)
                val decisionDTOs = jsonMapper.readValue(record.value(),
                    object: TypeReference<List<ChangeRequestDecisionDTO>>() {})
                changeRequestService.setDecisions(decisionDTOs)
            }
            if(errorCount > 0) {
                log.error("Во время импорта из ЛК решений по изменениям произошло $errorCount ошибок")
            }
        }
    }
}
