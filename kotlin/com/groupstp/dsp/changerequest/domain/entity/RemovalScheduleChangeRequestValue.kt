package com.groupstp.dsp.domain.entity.container

import com.groupstp.dsp.domain.entity.changerequest.value.EntityChangeRequestValue
import com.groupstp.dsp.service.RemovalScheduleService
import org.hibernate.Hibernate
import java.util.*
import javax.persistence.EntityManager

class RemovalScheduleChangeRequestValue(
    private val entityManager: EntityManager,
    private val removalScheduleService: RemovalScheduleService
): EntityChangeRequestValue<RemovalSchedule>() {

    override val badDTOFormatMessage = "Неверный формат обмена для графика вывоза"
    override val instanceNotFoundMessage = "Не найден график вывоза"

    override fun findById(id: UUID): RemovalSchedule? {
        val graph = entityManager.createEntityGraph(RemovalSchedule::class.java).apply {
            addSubgraph("records", RemovalScheduleRecord::class.java).apply {
                addAttributeNodes("interval")
            }
        }
        return removalScheduleService.findById(id, graph)
    }

    override fun toDTO(): Any? {
        return value?.let {
            val value = Hibernate.unproxy(it, RemovalSchedule::class.java)
            mapOf<String, Any?>(
                "id" to value.id,
                "name" to value.name,
                "startDate" to value.startDate,
                "endDate" to value.endDate,
                "records" to value.records.map { scheduleRecord ->
                    mapOf(
                        "id" to scheduleRecord.id,
                        "timeFrom" to scheduleRecord.timeFrom,
                        "timeTo" to scheduleRecord.timeTo,
                        "interval" to scheduleRecord.interval?.let { interval ->
                            mapOf<String, Any?>(
                                "name" to interval.name,
                                "orderInMonth" to interval.orderInMonth,
                                "type" to interval.type,
                                "value" to interval.value
                            )
                        }
                    )
                }
            )
        }
    }

    override fun toString(): String {
        return value?.name ?: "null"
    }
}
