package com.groupstp.dsp.domain.entity.container

import com.groupstp.dsp.domain.entity.changerequest.value.EntityChangeRequestValue
import com.groupstp.dsp.service.ContainerAreaTariffCalculationMethodService
import org.hibernate.Hibernate
import java.util.*

class ContainerAreaTariffCalculationMethodChangeRequestValue(
    private val containerAreaTariffCalculationMethodService: ContainerAreaTariffCalculationMethodService
): EntityChangeRequestValue<ContainerAreaTariffCalculationMethod>() {

    override val badDTOFormatMessage = "Неверный формат обмена для расчета тарифа"
    override val instanceNotFoundMessage = "Не найден расчет тарифа"

    override fun findById(id: UUID): ContainerAreaTariffCalculationMethod? {
        return containerAreaTariffCalculationMethodService.findById(id)
    }

    override fun toDTO(): Any? {
        return value?.let {
            val value = Hibernate.unproxy(it, ContainerAreaTariffCalculationMethod::class.java)
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
