package com.groupstp.dsp.domain.entity.company

import com.groupstp.dsp.domain.entity.changerequest.value.EntityChangeRequestValue
import com.groupstp.dsp.service.CompanyFindService
import org.hibernate.Hibernate
import java.util.*

class CompanyChangeRequestValue(
    private val companyFindService: CompanyFindService
): EntityChangeRequestValue<Company>() {

    override val badDTOFormatMessage = "Неверный формат обмена для организации"
    override val instanceNotFoundMessage = "Не найдена организация"

    override fun findById(id: UUID): Company? {
        return companyFindService.findById(id)
    }

    override fun toDTO(): Any? {
        return value?.let {
            val value = Hibernate.unproxy(it, Company::class.java)
            mapOf<String, Any?>(
                "id" to value.id,
                "inn" to value.inn,
                "name" to value.fullName
            )
        }
    }

    override fun toString(): String {
        return value?.fullName ?: "null"
    }
}
