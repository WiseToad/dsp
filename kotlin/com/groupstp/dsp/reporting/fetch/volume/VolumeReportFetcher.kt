package com.groupstp.dsp.reporting.fetch.volume

import ch.qos.logback.classic.Level
import com.groupstp.dsp.domain.utils.AppCastUtils
import com.groupstp.dsp.domain.utils.AppLogUtils
import com.groupstp.dsp.domain.utils.AppParamUtils
import com.groupstp.dsp.domain.entity.company.Company
import com.groupstp.dsp.hibernate.UUIDArrayType
import com.groupstp.dsp.reporting.fetch.DataFetcher
import com.groupstp.dsp.service.CompanyFindService
import com.groupstp.dsp.service.appconfig.AppConfigService
import org.hibernate.jpa.TypedParameterValue
import org.hibernate.type.CustomType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.text.SimpleDateFormat
import java.util.*
import javax.persistence.EntityManager

@Component
class VolumeReportFetcher(
    private val companyFindService: CompanyFindService,
    private val entityManager: EntityManager,
    private val transactionManager: PlatformTransactionManager,
    private val appConfigService: AppConfigService
): DataFetcher {

    private val log = LoggerFactory.getLogger(javaClass)

    enum class ValueType {
        AMOUNT,
        VOLUME
    }

    class VolumeColumn (
        val header: String
    ) {
        val volumes = mutableListOf<Double?>()
    }

    override fun fetch(params: Map<String, Any?>): Map<String, Any?> {

        log.trace("fetch: enter")

        // Предполагается, что на вход поступают даты в таймзоне приложения, а не в UTC
        // TODO: Чтобы обозначить, что даты на входе интерпретируются как заданные в таймзоне приложения, а не в UTC,
        //       желательно входные параметры переименовать с инфиксом AppTz (как это сделано для внутренних переменных)
        val dateAppTzFrom = AppParamUtils.requiredParam(params, "dateFrom", AppCastUtils::toDate)
        val dateAppTzTo = AppParamUtils.requiredParam(params, "dateTo", AppCastUtils::toDate)

        if((dateAppTzTo.time - dateAppTzFrom.time) >= (35L * 24L * 60L * 60L * 1000L)) {
            throw IllegalArgumentException("Отчетный период превышает 35 дней")
        }

        val carrierIds = AppParamUtils.optionalParam(params, "carrierIds") {
            AppCastUtils.toList(it, AppCastUtils::toUUID)?.filterNotNull()?.distinct()
        }

        val regionIds = AppParamUtils.optionalParam(params, "regionIds") {
            AppCastUtils.toList(it, AppCastUtils::toUUID)?.filterNotNull()?.distinct()
        }

        val valueType = AppParamUtils.optionalParam(params, "valueType", AppCastUtils::toString)
            ?.let(ValueType::valueOf)
            ?: ValueType.AMOUNT

        if(carrierIds.isNullOrEmpty() && regionIds.isNullOrEmpty()) {
            throw IllegalArgumentException("Должен быть задан хотя бы один перевозчик либо район")
        }

        log.trace("fetch: retrieving carrierNames")
        val carrierNames = if(carrierIds != null) {
            val carriers = companyFindService.findCarriersByIdsWithArchived(carrierIds)
            if(carriers.size != carrierIds.size) {
                throw IllegalArgumentException("В заданном списке перевозчиков как минимум один неверен")
            }
            carriers.joinToString(", ", transform = Company::getFullName)
        } else {
            null
        }

        log.trace("fetch: retrieving childRegionIds")
        //TODO: Оформить это куда-нить в библиотеку - оптимизированное получение списка дочерних участков
        val childRegionIds = if(regionIds == null) {
            null
        } else if(regionIds.isEmpty()) {
            emptyList()
        } else {
            AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
                entityManager.createNamedQuery("RegionRepository.childRegions")
                    .setParameter("regionIds", regionIds)
                    .resultList.mapNotNull(AppCastUtils::toUUID)
            }
        }

        log.trace("fetch: retrieving dbRows")
        val dbRows = AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
            entityManager.createNamedQuery("VolumeReport.fetch", VolumeDTO::class.java)
                .setParameter("dateAppTzFrom", dateAppTzFrom)
                .setParameter("dateAppTzTo", dateAppTzTo)
                .setParameter("carrierIds", TypedParameterValue(
                    CustomType(UUIDArrayType()),
                    carrierIds?.toTypedArray()?.ifEmpty { null }
                ))
                .setParameter("regionIds", TypedParameterValue(
                    CustomType(UUIDArrayType()),
                    childRegionIds?.toTypedArray()?.ifEmpty { null }
                ))
                .resultList
        }

        val cal = Calendar.getInstance()

        log.trace("fetch: creating volumeColumns")
        val volumeColumns = mutableListOf<VolumeColumn>()
        cal.time = dateAppTzFrom
        while(cal.time <= dateAppTzTo) {
            volumeColumns.add(VolumeColumn(cal.get(Calendar.DAY_OF_MONTH).toString()))
            cal.add(Calendar.DATE, 1)
        }

        log.trace("fetch: populating volumeColumns")
        dbRows.forEachIndexed { index, dbRow ->
            cal.time = dateAppTzFrom
            var columnIndex = 0
            if(dbRow.removalDateAppTzTruncs != null) {
                dbRow.removalDateAppTzTruncs.forEachIndexed { arrayIndex, removalDateAppTzTrunc ->
                    if(removalDateAppTzTrunc != null) {
                        while(cal.time < removalDateAppTzTrunc) {
                            volumeColumns[columnIndex++].volumes.add(null)
                            cal.add(Calendar.DATE, 1)
                        }
                        volumeColumns[columnIndex++].volumes.add(
                            when(valueType) {
                                ValueType.AMOUNT -> dbRow.factAmounts?.get(arrayIndex)?.toDouble()
                                ValueType.VOLUME -> dbRow.factVolumes?.get(arrayIndex)
                            }
                        )
                        cal.add(Calendar.DATE, 1)
                    }
                }
            }
            while(cal.time <= dateAppTzTo) {
                volumeColumns[columnIndex++].volumes.add(null)
                cal.add(Calendar.DATE, 1)
            }
            dbRow.volumeTotal = when(valueType) {
                ValueType.AMOUNT -> dbRow.factAmountTotal?.toDouble()
                ValueType.VOLUME -> dbRow.factVolumeTotal
            }
            dbRow.rowNum = index + 1
        }

        val printDateFormat = SimpleDateFormat("dd.MM.yyyy")

        return mapOf(
            "title" to when(valueType) {
                ValueType.AMOUNT -> "Отчет по числу вывезенных контейнеров"
                ValueType.VOLUME -> "Отчет по объему вывезенных контейнеров"
            },
            "dateFrom" to printDateFormat.format(dateAppTzFrom),
            "dateTo" to printDateFormat.format(dateAppTzTo),
            "carrierNames" to carrierNames,
            "containerGroups" to dbRows,
            "volumeColumns" to volumeColumns,
            "totalHeader" to when(valueType) {
                ValueType.AMOUNT -> "Итого контейнеров"
                ValueType.VOLUME -> "Итого, м3"
            },
            "isEmpty" to dbRows.isEmpty()
        ).also {
            log.trace("fetch: return")
        }
    }

    fun precalc() {

        log.debug("Запуск предрасчета данных для отчета по объемам")

        val transactionTemplate = TransactionTemplate(transactionManager)

        log.trace("precalc: executing precalcContainerGroupDim")
        transactionTemplate.execute {
            AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
                entityManager.createNamedQuery("VolumeReport.precalcContainerGroupDim")
                    .executeUpdate()
            }
        }

        log.trace("precalc: executing precalcContainerGroupRemovalFct")
        transactionTemplate.execute {
            AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
                entityManager.createNamedQuery("VolumeReport.precalcContainerGroupRemovalFct")
                    .setParameter("appTz", appConfigService.getAppTimeZoneId().id)
                    .executeUpdate()
            }
        }

        log.debug("Завершен предрасчет данных для отчета по объемам.")
    }
}
