package com.groupstp.dsp.graphql.changerequest

import com.groupstp.dsp.domain.entity.changerequest.ChangeRequest
import com.groupstp.dsp.domain.entity.employee.Employee
import com.groupstp.dsp.domain.entity.employee.Person
import com.groupstp.dsp.service.EmployeeFindService
import com.groupstp.dsp.service.changerequest.ChangeRequestService
import graphql.kickstart.tools.GraphQLResolver
import org.springframework.stereotype.Component
import javax.persistence.EntityManager

@Component
class ChangeRequestResolver(
    private val entityManager: EntityManager,
    private val changeRequestService: ChangeRequestService,
    private val employeeFindService: EmployeeFindService
): GraphQLResolver<ChangeRequest> {

    private val unitUtil by lazy { entityManager.entityManagerFactory.persistenceUnitUtil }

    fun instanceKey(owner: ChangeRequest): Map<String, Any?>? {
        return changeRequestService.mapInstanceKeyToDTO(owner)
    }

    fun requestedBy(owner: ChangeRequest): String? {
        val requestedBy = if (unitUtil.isLoaded(owner.requestedBy)) {
            owner.requestedBy
        } else {
            val graph = entityManager.createEntityGraph(Employee::class.java).apply {
                addSubgraph("person", Person::class.java).apply {
                    addAttributeNodes("fullName")
                }
            }
            employeeFindService.getCurrentEmployee(graph)
        }
        return requestedBy?.person?.fullName
    }
}
