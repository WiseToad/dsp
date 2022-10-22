package com.groupstp.dsp.service

import com.groupstp.dsp.domain.entity.changerequest.*
import com.groupstp.dsp.domain.entity.changerequest.value.*
import com.groupstp.dsp.domain.entity.company.CompanyDTO
import com.groupstp.dsp.domain.entity.container.*
import com.groupstp.dsp.domain.entity.geo.GeoData
import com.groupstp.dsp.domain.entity.geo.GeoPoint
import com.groupstp.dsp.domain.entity.vehicle.VehicleSizeChangeRequestValue
import com.groupstp.dsp.domain.exception.MissingAttributeException
import com.groupstp.dsp.domain.exception.UnknownAttributeException
import com.groupstp.dsp.security.SecurityUtils
import com.groupstp.dsp.service.changerequest.ChangeRequestService
import com.groupstp.dsp.service.changerequest.TypedChangeRequestServiceImpl
import com.groupstp.dsp.service.dto.LatLng
import com.groupstp.dsp.service.vehicle.VehicleSizeService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.util.*
import javax.persistence.EntityManager

@Service
class ContainerAreaChangeRequestServiceImpl(
    private val entityManager: EntityManager,
    private val containerAreaFindService: ContainerAreaFindService,
    private val geoDataService: GeoDataService,
    private val addressService: AddressService,
    private val containerAreaCategoryService: ContainerAreaCategoryService,
    private val companyService: CompanyService,
    private val containerAreaTypeService: ContainerAreaTypeService,
    private val containerAreaTariffCalculationMethodService: ContainerAreaTariffCalculationMethodService,
    private val vehicleSizeService: VehicleSizeService,
    private val removalScheduleService: RemovalScheduleService,
    private val contactService: ContactService,
    private val containerAreaService: ContainerAreaService
): TypedChangeRequestServiceImpl<ContainerArea>() {

    override val entityName = "dsp_ContainerArea"

    @Lazy
    @Autowired
    private lateinit var changeRequestService: ChangeRequestService

    private val instanceNotFoundMessage: String = "Не найдена КП из запроса на изменение"
    private val instanceNotSpecifiedMessage: String = "Не задана КП в запросе на изменение"

    override fun createChangeRequest(instanceId: UUID?): ChangeRequest {
        return ContainerAreaChangeRequest().also { changeRequest ->
            changeRequest.instance = instanceId?.let { instanceId ->
                val graph = entityManager.createEntityGraph(ContainerArea::class.java).apply {
                    addAttributeNodes("containerGroups")
                    addSubgraph("geoData", GeoData::class.java).apply {
                        addSubgraph("center", GeoPoint::class.java)
                    }
                }
                containerAreaFindService.findById(instanceId, graph)
                    ?: throw RuntimeException("$instanceNotFoundMessage с ID $instanceId")
            }
        }
    }

    override fun beforeApplyDecisions(changeRequest: ChangeRequest) {
        val requiredAttributes = setOf("address", "longitude", "latitude")

        if(changeRequest.operation == ChangeRequestOperation.INSERT) {
            val presentAttributes = changeRequest.attributes?.map(ChangeRequestAttribute::name)?.toSet() ?: emptySet()
            val missingAttributes = (requiredAttributes subtract presentAttributes)
            if(missingAttributes.isNotEmpty()) {
                throw MissingAttributeException("ContainerArea.${missingAttributes.first()}")
            }
        }

        val invalidValue = changeRequest.attributes?.find {
            (requiredAttributes.contains(it.name) && it.value == null) ||
                when(it.name) {
                    "address" -> (it.typedValue as? StringChangeRequestValue)?.value.isNullOrBlank()
                    else -> false
                }
        }
        if(invalidValue != null) {
            throw RuntimeException("Не задано, либо неверно задано значение обязательного атрибута ContainerArea.${invalidValue.name}")
        }
    }

    override fun insertInstance(changeRequest: ChangeRequest) {
        changeRequest as ContainerAreaChangeRequest

        val instance = ContainerArea()

        instance.lkCode = containerAreaFindService.getNextLkCode()
        instance.geoData = geoDataService.buildGeoPoint(LatLng())

        // Привязка КГ из дочерних запросов на изменение
        instance.containerGroups = changeRequest.attributes
            ?.find { it.name == "containerGroups" }
            ?.childRequests
            ?.mapNotNull { (it as? ContainerGroupChangeRequest)?.instance }
            ?.toMutableSet() ?: mutableSetOf()
        instance.containerGroups.forEach {
            it.containerArea = instance
        }

        setInstanceAttributes(instance, changeRequest)

        changeRequest.instance = containerAreaService.saveAndSetRegionAutomatically(instance)
    }

    override fun updateInstance(changeRequest: ChangeRequest) {
        changeRequest as ContainerAreaChangeRequest

        val instance = changeRequest.instance
            ?: throw RuntimeException(instanceNotSpecifiedMessage)

        setInstanceAttributes(instance, changeRequest)

        changeRequest.instance = containerAreaService.saveAndSetRegionAutomatically(instance)
    }

    override fun deleteInstance(changeRequest: ChangeRequest) {
        changeRequest as ContainerAreaChangeRequest

        val instance = changeRequest.instance
            ?: throw RuntimeException(instanceNotSpecifiedMessage)

        instance.isArchived = true

        instance.deleteTs = Date()
        instance.deletedBy = SecurityUtils.getCurrentUserLogin().orElse("system")

        changeRequest.instance = containerAreaService.saveAndSetRegionAutomatically(instance)
    }

    override fun resolveTypedValueByName(attributeName: String): ChangeRequestValue {
        return when(attributeName) {
            "lkCode" -> StringChangeRequestValue()
            "address" -> StringChangeRequestValue()
            "longitude" -> DoubleChangeRequestValue()
            "latitude" -> DoubleChangeRequestValue()
            "category" -> ContainerAreaCategoryChangeRequestValue(containerAreaCategoryService)
            "ownerCompany" -> StringChangeRequestValue()
            "type" -> ContainerAreaTypeChangeRequestValue(containerAreaTypeService)
            "tariffCalculationMethod" -> ContainerAreaTariffCalculationMethodChangeRequestValue(containerAreaTariffCalculationMethodService)
            "vehicleSize" -> VehicleSizeChangeRequestValue(vehicleSizeService)
            "availabilitySchedule" -> RemovalScheduleChangeRequestValue(entityManager, removalScheduleService)
            "isRemovalInOnlyWorkingDays" -> BooleanChangeRequestValue()
            "photoForbidden" -> BooleanChangeRequestValue()
            "securedTerritory" -> BooleanChangeRequestValue()
            "isKgo" -> BooleanChangeRequestValue()
            "commentForDriver" -> StringChangeRequestValue()
            "contact" -> StringChangeRequestValue()
            "note" -> StringChangeRequestValue()
            "containerGroups" -> ChildChangeRequestValue(changeRequestService)
            else -> throw UnknownAttributeException("ContainerArea.$attributeName")
        }
    }

    override fun setInstanceAttribute(instance: ContainerArea, attribute: ChangeRequestAttribute) {
        when(attribute.name) {
            "lkCode" -> {
                instance.lkCode = (attribute.typedValue as StringChangeRequestValue).value
            }
            "address" -> {
                instance.address = addressService.getOrCreate(
                    (attribute.typedValue as StringChangeRequestValue).value
                )
            }
            "longitude" -> {
                instance.geoData.center.longitude = (attribute.typedValue as DoubleChangeRequestValue).value
            }
            "latitude" -> {
                instance.geoData.center.latitude = (attribute.typedValue as DoubleChangeRequestValue).value
            }
            "category" -> {
                instance.category = (attribute.typedValue as ContainerAreaCategoryChangeRequestValue).value
            }
            "ownerCompany" -> {
                val companyDTO = CompanyDTO().also {
                    it.name = (attribute.typedValue as StringChangeRequestValue).value
                }
                instance.ownerCompany = companyService.create(companyDTO)
            }
            "type" -> {
                instance.type = (attribute.typedValue as ContainerAreaTypeChangeRequestValue).value
            }
            "tariffCalculationMethod" -> {
                instance.tariffCalculationMethod = (attribute.typedValue as ContainerAreaTariffCalculationMethodChangeRequestValue).value
            }
            "vehicleSize" -> {
                instance.vehicleSize = (attribute.typedValue as VehicleSizeChangeRequestValue).value
            }
            "availabilitySchedule" -> {
                instance.availabilitySchedule = (attribute.typedValue as RemovalScheduleChangeRequestValue).value
            }
            "isRemovalInOnlyWorkingDays" -> {
                instance.isRemovalInOnlyWorkingDays = (attribute.typedValue as BooleanChangeRequestValue).value
            }
            "photoForbidden" -> {
                instance.photoForbidden = (attribute.typedValue as BooleanChangeRequestValue).value
            }
            "securedTerritory" -> {
                instance.securedTerritory = (attribute.typedValue as BooleanChangeRequestValue).value
            }
            "isKgo" -> {
                instance.kgo = (attribute.typedValue as BooleanChangeRequestValue).value
            }
            "commentForDriver" -> {
                instance.commentForDriver = (attribute.typedValue as StringChangeRequestValue).value
            }
            "contact" -> {
                instance.contact = contactService.getOrCreate(
                    (attribute.typedValue as StringChangeRequestValue).value
                )
            }
            "note" -> {
                instance.note = (attribute.typedValue as StringChangeRequestValue).value
            }
            "containerGroups" -> {}
            else -> throw UnknownAttributeException("ContainerArea.${attribute.name}")
        }
    }

    override fun mapInstanceKeyToDTO(changeRequest: ChangeRequest): Map<String, Any?>? {
        changeRequest as ContainerAreaChangeRequest

        return changeRequest.instance?.let {
            mapOf(
                "id" to it.id,
                "lkId" to it.lkId,
                "lkCode" to it.lkCode
            )
        }
    }
}
