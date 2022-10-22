package com.groupstp.dsp.domain.entity.vehicle

import com.groupstp.dsp.domain.entity.changerequest.value.EntityChangeRequestValue
import com.groupstp.dsp.service.vehicle.VehicleSizeService
import org.hibernate.Hibernate
import java.util.*

class VehicleSizeChangeRequestValue(
    private val vehicleSizeService: VehicleSizeService
): EntityChangeRequestValue<VehicleSize>() {

    override val badDTOFormatMessage = "Неверный формат обмена для габарита ТС"
    override val instanceNotFoundMessage = "Не найден габарит ТС"

    override fun findById(id: UUID): VehicleSize? {
        return vehicleSizeService.findById(id)
    }

    override fun toDTO(): Any? {
        return value?.let {
            val value = Hibernate.unproxy(it, VehicleSize::class.java)
            mapOf<String, Any?>(
                "id" to value.id,
                "code" to value.code
            )
        }
    }

    override fun toString(): String {
        return value?.name ?: "null"
    }
}
