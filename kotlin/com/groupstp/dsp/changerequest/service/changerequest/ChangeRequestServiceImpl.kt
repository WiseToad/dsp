package com.groupstp.dsp.service.changerequest

import com.groupstp.dsp.domain.entity.changerequest.*
import com.groupstp.dsp.domain.entity.changerequest.dto.ChangeRequestAttributeDTO
import com.groupstp.dsp.domain.entity.changerequest.dto.ChangeRequestDTO
import com.groupstp.dsp.domain.entity.changerequest.dto.ChangeRequestDecisionDTO
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestSource
import com.groupstp.dsp.domain.entity.changerequest.value.ChildChangeRequestValue
import com.groupstp.dsp.domain.entity.employee.Employee
import com.groupstp.dsp.domain.entity.employee.Person
import com.groupstp.dsp.domain.entity.verification.ContainerAreaVerificationContainerGroup
import com.groupstp.dsp.domain.exception.MissingArgumentException
import com.groupstp.dsp.domain.utils.AppCastUtils
import com.groupstp.dsp.repository.changerequest.ChangeRequestAttributeRepository
import com.groupstp.dsp.repository.changerequest.ChangeRequestRepository
import com.groupstp.dsp.service.EmployeeFindService
import com.groupstp.dsp.service.access.EmployeeAccessControlService
import com.groupstp.dsp.service.container.changelog.ContainerAreaChangeLogServiceConfig
import com.groupstp.dsp.service.sync.lk.changerequest.SyncChangeRequestLkService
import com.groupstp.dsp.service.verification.ContainerAreaVerificationFindService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*
import javax.persistence.EntityManager

@Service
class ChangeRequestServiceImpl(
    typedChangeRequestServices: List<TypedChangeRequestService>,
    private val changeRequestProperties: ChangeRequestProperties,
    private val containerAreaChangeLogServiceConfig: ContainerAreaChangeLogServiceConfig,
    private val changeRequestRepository: ChangeRequestRepository,
    private val changeRequestAttributeRepository: ChangeRequestAttributeRepository,
    private val employeeAccessControlService: EmployeeAccessControlService,
    private val employeeFindService: EmployeeFindService,
    private val changeRequestLkService: SyncChangeRequestLkService,
    private val entityManager: EntityManager,
    private val containerAreaVerificationFindService: ContainerAreaVerificationFindService
): ChangeRequestService {

    private val log = LoggerFactory.getLogger(javaClass)

    private val typedChangeRequestServices = typedChangeRequestServices.associateBy(TypedChangeRequestService::entityName)

    private class UnknownElementTypeError: AssertionError("Unknown descendant of ChangeRequestElement??")

    private fun getTypedChangeRequestService(entityName: String): TypedChangeRequestService =
        typedChangeRequestServices[entityName]
            ?: throw IllegalArgumentException("Неверная сущность: $entityName")

    override fun createChangeRequest(changeRequestDTO: ChangeRequestDTO, parentAttribute: ChangeRequestAttribute?): ChangeRequest {
        val operation = changeRequestDTO.operation
            ?: throw MissingArgumentException("ChangeRequest.operation")

        val entityName = changeRequestDTO.entityName
            ?: throw MissingArgumentException("ChangeRequest.entityName")

        val typedChangeRequestService = getTypedChangeRequestService(entityName)

        var instanceId: UUID? = null
        when(operation) {
            ChangeRequestOperation.INSERT -> {
                if(changeRequestDTO.instanceKey != null) {
                    log.warn("Аргумент ChangeRequest.instanceKey избыточен для изменения вида INSERT и будет проигнорирован")
                }
            }
            ChangeRequestOperation.UPDATE,
            ChangeRequestOperation.DELETE -> {
                val instanceKey = changeRequestDTO.instanceKey
                    ?: throw MissingArgumentException("ChangeRequest.instanceKey")
                instanceId = AppCastUtils.toUUID(instanceKey["id"])
                    ?: throw MissingArgumentException("ChangeRequest.instanceKey.id")
            }
        }

        var changeRequest = typedChangeRequestService.createChangeRequest(instanceId).also { changeRequest ->
            changeRequest.parentAttribute = parentAttribute

            changeRequest.source = parentAttribute?.changeRequest?.source
                ?: changeRequestDTO.source
                ?: ChangeRequestSource.UI

            if(changeRequest.source == ChangeRequestSource.VERIFICATION) {
                changeRequest.verification = parentAttribute?.changeRequest?.verification
                    ?: run {
                        val verificationId = changeRequestDTO.verificationId
                            ?: throw MissingArgumentException("ChangeRequest.verificationId")
                        containerAreaVerificationFindService.findByIdOrThrowException(verificationId)
                    }
            } else if(changeRequestDTO.verificationId != null) {
                log.warn("Аргумент ChangeRequest.verificationId избыточен если источник изменения не верификация и будет проигнорирован")
            }

            changeRequest.operation = operation
            changeRequest.entityName = entityName

            when(operation) {
                ChangeRequestOperation.DELETE -> {
                    if(!changeRequestDTO.attributes.isNullOrEmpty()) {
                        log.warn("Аргумент ChangeRequest.attributes избыточен для изменения вида DELETE и будет проигнорирован")
                    }
                }
                ChangeRequestOperation.INSERT,
                ChangeRequestOperation.UPDATE -> {
                    changeRequest.attributes = changeRequestDTO.attributes?.map { attributeDTO ->
                        ChangeRequestAttribute().also { attribute ->
                            attribute.changeRequest = changeRequest

                            attribute.name = attributeDTO.name
                                ?: throw MissingArgumentException("ChangeRequestAttribute.name")

                            attribute.typedValue = typedChangeRequestService.resolveTypedValueByName(attribute.name)
                            attribute.typedValue?.fromDTO(attributeDTO.value)

                            (attribute.typedValue as? ChildChangeRequestValue)?.let { childRequest ->
                                // childRequest.value - массив дочерних запросов на изменение
                                if(operation == ChangeRequestOperation.INSERT) {
                                    val hasInvalidOperations = childRequest.value?.any {
                                        it.operation != ChangeRequestOperation.INSERT
                                    }
                                    if(hasInvalidOperations == true) {
                                        throw RuntimeException("Недопустимый код операции в дочернем запросе на изменение")
                                    }
                                }
                            }

                            initDecision(attribute)
                        }
                    }?.toMutableSet()
                }
            }

            changeRequest.reason = changeRequestDTO.reason
            changeRequest.requestTs = Date()
            changeRequest.requestedBy = getCurrentEmployee()

            initDecision(changeRequest)
        }

        if(parentAttribute == null) {
            // сохраняем в БД
            changeRequest = changeRequestRepository.save(changeRequest)
            // применяем к данным
            applyDecisionsForBranch(changeRequest)
            // отправляем в ЛК
            changeRequestLkService.exportChangeRequest(changeRequest)
        }

        return changeRequest
    }

    private fun initDecision(element: ChangeRequestElement) {
        val changeRequest: ChangeRequest
        var attributeName: String? = null
        when(element) {
            is ChangeRequest -> {
                changeRequest = element
                if(changeRequest.operation == ChangeRequestOperation.UPDATE) {
                    return
                }
            }
            is ChangeRequestAttribute -> {
                changeRequest = element.changeRequest
                if(changeRequest.operation != ChangeRequestOperation.UPDATE || element.typedValue is ChildChangeRequestValue) {
                    return
                }
                attributeName = element.name
            }
            else -> throw UnknownElementTypeError()
        }

        element.decisionMode = if(
            changeRequest.source == ChangeRequestSource.VERIFICATION ||
            //TODO: Заимствована настройка autoAcceptEnable из прошлого подхода (ChangeLog), мигрировать ее в новую реализацию (ChangeRequest)
            containerAreaChangeLogServiceConfig.getAutoAcceptEnable() ||
            employeeAccessControlService.workInRegionalOperator()
        ) {
            ChangeRequestDecisionMode.ACCEPT
        } else {
            changeRequestProperties.getDecisionMode(changeRequest.entityName, changeRequest.operation, attributeName)
        }

        // Автоматическое принятие решения
        when(element.decisionMode) {
            ChangeRequestDecisionMode.ACCEPT -> {
                element.decision = ChangeRequestDecision.ACCEPTED
                element.decisionTs = Date()
            }
            ChangeRequestDecisionMode.DENY -> {
                element.decision = ChangeRequestDecision.DENIED
                element.decisionTs = Date()
            }
            else -> {}
        }
    }

    override fun createChangeRequestByVerification(verificationId: UUID): ChangeRequest {
        val graph = containerAreaVerificationFindService.getGraphForCreateChanges()
        val verification = containerAreaVerificationFindService.findByIdOrThrowException(verificationId, graph)

        val area = verification.request?.area

        val changeRequestDTO = ChangeRequestDTO()
        changeRequestDTO.source = ChangeRequestSource.VERIFICATION
        changeRequestDTO.verificationId = verificationId

        changeRequestDTO.entityName = "dsp_ContainerArea"

        if(area == null) {
            changeRequestDTO.operation = ChangeRequestOperation.INSERT
        } else {
            changeRequestDTO.operation = ChangeRequestOperation.UPDATE
            changeRequestDTO.instanceKey = mapOf("id" to area.id)
        }

        val attributeDTOs = mutableListOf<ChangeRequestAttributeDTO>()
        changeRequestDTO.attributes = attributeDTOs

        addVerificationAttributeDTO(
            attributeDTOs, "address",
            verification.addressView,
            area?.address?.view)

        addVerificationAttributeDTO(
            attributeDTOs, "ownerCompany",
            verification.companyName,
            area?.ownerCompany?.fullName)

        addVerificationAttributeDTO(
            attributeDTOs, "contact",
            verification.phone,
            area?.contact?.view)

        addVerificationAttributeDTO(
            attributeDTOs, "category",
            verification.category?.id,
            area?.category?.id)
        {
            mapOf<String, Any?>("id" to it)
        }

        addVerificationAttributeDTO(
            attributeDTOs, "photoForbidden",
            verification.photoForbidden,
            area?.photoForbidden)

        addVerificationAttributeDTO(
            attributeDTOs, "type",
            verification.type?.id,
            area?.type?.id)
        {
            mapOf<String, Any?>("id" to it)
        }

        addVerificationAttributeDTO(
            attributeDTOs, "securedTerritory",
            verification.securedTerritory,
            area?.securedTerritory)

        addVerificationAttributeDTO(
            attributeDTOs, "longitude",
            verification.geoData?.center?.longitude,
            area?.geoData?.center?.longitude)

        addVerificationAttributeDTO(
            attributeDTOs, "latitude",
            verification.geoData?.center?.latitude,
            area?.geoData?.center?.latitude)

        val verificationSourceGroups = verification.containerGroups
            ?.mapNotNull(ContainerAreaVerificationContainerGroup::getSourceGroup)
            ?.toSet() ?: emptySet()

        // Собираем DTO для КГ, которые нужно удалить
        val groupChangeRequestDTOs = area?.containerGroups
            ?.minus(verificationSourceGroups)
            ?.map { deletingGroup ->
                ChangeRequestDTO().also { groupChangeRequestDTO ->
                    groupChangeRequestDTO.entityName = "dsp_ContainerGroup"

                    groupChangeRequestDTO.operation = ChangeRequestOperation.DELETE
                    groupChangeRequestDTO.instanceKey = mapOf("id" to deletingGroup.id)
                }
            }
            ?.toMutableList() ?: mutableListOf()

        // Добираем DTO для КГ, которые нужно добавить, либо изменить
        groupChangeRequestDTOs.addAll(
            verification.containerGroups?.map { verificationGroup ->
                ChangeRequestDTO().also { groupChangeRequestDTO ->
                    groupChangeRequestDTO.entityName = "dsp_ContainerGroup"

                    if(verificationGroup.sourceGroup == null) {
                        groupChangeRequestDTO.operation = ChangeRequestOperation.INSERT
                    } else {
                        groupChangeRequestDTO.operation = ChangeRequestOperation.UPDATE
                        groupChangeRequestDTO.instanceKey = mapOf("id" to verificationGroup.sourceGroup.id)
                    }

                    val groupAttributeDTOs = mutableListOf<ChangeRequestAttributeDTO>()
                    groupChangeRequestDTO.attributes = groupAttributeDTOs

                    addVerificationAttributeDTO(
                        groupAttributeDTOs, "category",
                        verificationGroup.category,
                        verificationGroup.sourceGroup?.category)

                    addVerificationAttributeDTO(
                        groupAttributeDTOs, "carrier",
                        verificationGroup.carrier?.id,
                        verificationGroup.sourceGroup?.carrier?.id)
                    {
                        mapOf<String, Any?>("id" to it)
                    }

                    addVerificationAttributeDTO(
                        groupAttributeDTOs, "material",
                        verificationGroup.material?.id,
                        verificationGroup.sourceGroup?.material?.id)
                    {
                        mapOf<String, Any?>("id" to it)
                    }

                    addVerificationAttributeDTO(
                        groupAttributeDTOs, "type",
                        verificationGroup.type?.id,
                        verificationGroup.sourceGroup?.type?.id)
                    {
                        mapOf<String, Any?>("id" to it)
                    }

                    addVerificationAttributeDTO(
                        groupAttributeDTOs, "amount",
                        verificationGroup.amount,
                        verificationGroup.sourceGroup?.amount)
                }
            } ?: emptyList()
        )

        // Устанавливаем собранные DTO как значение атрибута
        // в виде списка дочерних запросов на изменение (точнее их DTO)
        addVerificationAttributeDTO(
            attributeDTOs, "containerGroups",
            groupChangeRequestDTOs
        )

        return createChangeRequest(changeRequestDTO)
    }

    private fun <T> addVerificationAttributeDTO(
        attributeDTOs: MutableList<ChangeRequestAttributeDTO>,
        attributeName: String,
        verificationValue: T?,
        instanceValue: T?,
        toDTO: (T) -> Any? = { it }
    ) {
        if(verificationValue != null && verificationValue != instanceValue) {
            addVerificationAttributeDTO(attributeDTOs, attributeName, toDTO(verificationValue))
        }
    }

    private fun addVerificationAttributeDTO(
        attributeDTOs: MutableList<ChangeRequestAttributeDTO>,
        attributeName: String,
        verificationValue: Any?
    ) {
        attributeDTOs.add(
            ChangeRequestAttributeDTO().also {
                it.name = attributeName
                it.value = verificationValue
            }
        )
    }

    override fun setDecisions(decisionDTOs: List<ChangeRequestDecisionDTO>) {
        var isFailed = false
        val changeRequests = mutableSetOf<ChangeRequest>()
        decisionDTOs.forEach { decisionDTO ->
            try {
                val elementId = decisionDTO.id
                    ?: throw MissingArgumentException("ChangeRequestDecision.elementId")

                val element: ChangeRequestElement =
                    changeRequestRepository.findById(elementId).orElse(null) ?:
                    changeRequestAttributeRepository.findById(elementId).orElse(null) ?:
                    throw RuntimeException("Не найдено изменение с указанным ID")

                if(element.decision != null) {
                    throw RuntimeException("По изменению ранее уже было зафиксировано решение")
                }
                element.decision = decisionDTO.decision
                    ?: throw MissingArgumentException("ChangeRequestDecision.decision")

                when(element.decisionMode) {
                    ChangeRequestDecisionMode.APPROVE -> {
                        element.decisionTs = Date()
                        element.decidedBy = getCurrentEmployee()?.person?.fullName
                    }

                    ChangeRequestDecisionMode.LK_APPROVE -> {
                        element.decisionTs = decisionDTO.decisionTs
                            ?: throw MissingArgumentException("ChangeRequestDecision.decisionTs")
                        element.decidedBy = decisionDTO.decidedBy
                    }

                    else -> throw RuntimeException("Для изменения не предусмотрен выбор произвольного решения")
                }

                lateinit var changeRequest: ChangeRequest
                when(element) {
                    is ChangeRequest -> {
                        changeRequestRepository.save(element)
                        changeRequest = element
                    }
                    is ChangeRequestAttribute -> {
                        changeRequestAttributeRepository.save(element)
                        changeRequest = element.changeRequest
                    }
                    else -> throw UnknownElementTypeError()
                }
                changeRequests.add(changeRequest)
            }
            catch(e: Exception) {
                log.error("Ошибка фиксации решения по изменению с ID ${decisionDTO.id}")
                isFailed = true
            }
        }
        if(isFailed) {
            throw RuntimeException("Не все решения по изменениям были зафиксированы")
        }

        changeRequests.forEach { changeRequest ->
            if(isAllDecisionsMade(changeRequest)) {
                applyDecisions(changeRequest)
            }
        }
    }

    override fun applyDecisions(changeRequestId: UUID) {
        applyDecisions(changeRequestRepository.findByIdOrThrow(changeRequestId))
    }

    private fun applyDecisions(changeRequest: ChangeRequest) {
        if(!isAllDecisionsMade(changeRequest)) {
            throw RuntimeException("Не по всем составляющим изменения было принято решение там, где оно ожидается")
        }

        val typedChangeRequestService = getTypedChangeRequestService(changeRequest.entityName)
        changeRequest.attributes?.forEach { attribute ->
            if(attribute.typedValue == null) {
                attribute.typedValue = typedChangeRequestService.resolveTypedValueByName(attribute.name)
            }
        }
        typedChangeRequestService.applyDecisions(changeRequest)

        changeRequestRepository.save(changeRequest)
    }

    private fun applyDecisionsForBranch(changeRequest: ChangeRequest) {
        if(isAllDecisionsMade(changeRequest)) {
            applyDecisions(changeRequest)
        }
        changeRequest.attributes?.forEach {
            (it.typedValue as? ChildChangeRequestValue)?.value?.forEach(::applyDecisionsForBranch)
        }
    }

    private fun isAllDecisionsMade(changeRequest: ChangeRequest): Boolean {
        if(changeRequest.decisionMode != null && changeRequest.decision == null) {
            return false
        }
        changeRequest.attributes?.forEach { attribute ->
            if(attribute.decisionMode != null && attribute.decision == null) {
                return false
            }
        }
        return true
    }

    override fun resolveTypedValuesByName(changeRequest: ChangeRequest) {
        val typedChangeRequestService = getTypedChangeRequestService(changeRequest.entityName)
        changeRequest.attributes?.forEach { attribute ->
            if(attribute.typedValue == null) {
                attribute.typedValue = typedChangeRequestService.resolveTypedValueByName(attribute.name)
            }
        }
    }

    override fun mapChangeRequestToDTO(changeRequest: ChangeRequest): ChangeRequestDTO {
        val typedChangeRequestService = getTypedChangeRequestService(changeRequest.entityName)
        return ChangeRequestDTO().also { changeRequestDTO ->
            changeRequestDTO.id = changeRequest.id
            changeRequestDTO.decisionMode = changeRequest.decisionMode
            changeRequestDTO.decision = changeRequest.decision
            changeRequestDTO.decisionTs = changeRequest.decisionTs
            changeRequestDTO.decidedBy = changeRequest.decidedBy
            changeRequestDTO.source = changeRequest.source
            changeRequestDTO.verificationId = changeRequest.verification?.id
            changeRequestDTO.verificationRequestId = changeRequest.verification?.request?.id
            changeRequestDTO.operation = changeRequest.operation
            changeRequestDTO.entityName = changeRequest.entityName
            changeRequestDTO.instanceKey = typedChangeRequestService.mapInstanceKeyToDTO(changeRequest)
            changeRequestDTO.attributes = changeRequest.attributes?.map { attribute ->
                ChangeRequestAttributeDTO().also { attributeDTO ->
                    attributeDTO.id = attribute.id
                    attributeDTO.decisionMode = attribute.decisionMode
                    attributeDTO.decision = attribute.decision
                    attributeDTO.decisionTs = attribute.decisionTs
                    attributeDTO.decidedBy = attribute.decidedBy
                    attributeDTO.name = attribute.name
                    if(attribute.typedValue == null) {
                        attribute.typedValue = typedChangeRequestService.resolveTypedValueByName(attribute.name)
                    }
                    attributeDTO.value = attribute.typedValue?.toDTO()
                }
            }
            changeRequestDTO.reason = changeRequest.reason
            changeRequestDTO.requestTs = changeRequest.requestTs
            changeRequestDTO.requestedBy = changeRequest.requestedBy?.person?.fullName
        }
    }

    override fun mapInstanceKeyToDTO(changeRequest: ChangeRequest): Map<String, Any?>? {
        val typedChangeRequestService = getTypedChangeRequestService(changeRequest.entityName)
        return typedChangeRequestService.mapInstanceKeyToDTO(changeRequest)
    }

    private fun getCurrentEmployee(): Employee? {
        val graph = entityManager.createEntityGraph(Employee::class.java).apply {
            addSubgraph("person", Person::class.java).apply {
                addAttributeNodes("fullName")
            }
        }
        return employeeFindService.getCurrentEmployee(graph)
    }
}
