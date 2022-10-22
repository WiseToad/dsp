package com.groupstp.dsp.service

import com.groupstp.dsp.domain.entity.Color
import com.groupstp.dsp.domain.entity.ColorChangeRequestValue
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequest
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestAttribute
import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestOperation
import com.groupstp.dsp.domain.entity.changerequest.value.ChangeRequestValue
import com.groupstp.dsp.domain.entity.changerequest.value.EnumChangeRequestValue
import com.groupstp.dsp.domain.entity.changerequest.value.IntChangeRequestValue
import com.groupstp.dsp.domain.entity.changerequest.value.TypedChangeRequestValue
import com.groupstp.dsp.domain.entity.company.CompanyChangeRequestValue
import com.groupstp.dsp.domain.entity.container.*
import com.groupstp.dsp.domain.entity.enums.ContainerTypeCategoryEnum
import com.groupstp.dsp.domain.exception.UnknownAttributeException
import com.groupstp.dsp.security.SecurityUtils
import com.groupstp.dsp.service.changerequest.TypedChangeRequestServiceImpl
import org.hibernate.Hibernate
import org.springframework.stereotype.Service
import java.util.*
import javax.persistence.EntityManager

@Service
class ContainerGroupChangeRequestServiceImpl(
    private val entityManager: EntityManager,
    private val containerGroupFindService: ContainerGroupFindService,
    private val containerGroupService: ContainerGroupService,
    private val containerTypeService: ContainerTypeService,
    private val materialService: MaterialService,
    private val companyFindService: CompanyFindService,
    private val colorService: ColorService,
    private val removalScheduleService: RemovalScheduleService
): TypedChangeRequestServiceImpl<ContainerGroup>() {

    override val entityName = "dsp_ContainerGroup"

    private val instanceNotFoundMessage: String = "Не найдена КГ из запроса на изменение"
    private val instanceNotSpecifiedMessage: String = "Не задана КГ в запросе на изменение"

    override fun createChangeRequest(instanceId: UUID?): ChangeRequest {
        return ContainerGroupChangeRequest().also { changeRequest ->
            changeRequest.instance = instanceId?.let { instanceId ->
                val graph = entityManager.createEntityGraph(ContainerGroup::class.java).apply {
                    addSubgraph("type", ContainerType::class.java).apply {
                        addAttributeNodes("loadTypes")
                    }
                    addAttributeNodes("material", "carrier", "color", "schedule", "routeDetails")
                }
                containerGroupFindService.findById(instanceId, graph)
                    ?: throw RuntimeException("$instanceNotFoundMessage с ID $instanceId")
            }
        }
    }

    override fun insertInstance(changeRequest: ChangeRequest) {
        changeRequest as ContainerGroupChangeRequest

        var instance = ContainerGroup()

        instance.routeDetails = mutableSetOf()

        // Привязка КП из родительского запроса на изменение
        val containerArea = getContainerArea(changeRequest)
        instance.containerArea = containerArea

        setInstanceAttributes(instance, changeRequest)

        instance = containerGroupService.save(instance)

        changeRequest.instance = instance
        updateContainerArea(containerArea, instance)
    }

    override fun updateInstance(changeRequest: ChangeRequest) {
        changeRequest as ContainerGroupChangeRequest

        var instance = changeRequest.instance
            ?: throw RuntimeException(instanceNotSpecifiedMessage)

        val oldInstance = instance.clone()

        setInstanceAttributes(instance, changeRequest)

        instance = containerGroupService.saveAsCopy(oldInstance, instance)

        changeRequest.instance = instance
        updateContainerArea(getContainerArea(changeRequest), instance)
    }

    override fun deleteInstance(changeRequest: ChangeRequest) {
        changeRequest as ContainerGroupChangeRequest

        var instance = changeRequest.instance
            ?: throw RuntimeException(instanceNotSpecifiedMessage)

        instance.deleteTs = Date()
        instance.deletedBy = SecurityUtils.getCurrentUserLogin().orElse("system")

        instance = containerGroupService.save(instance)

        changeRequest.instance = instance
        updateContainerArea(getContainerArea(changeRequest), instance)
    }

    private fun getContainerArea(changeRequest: ChangeRequest): ContainerArea? {
        val parentRequest = changeRequest.parentAttribute?.changeRequest
        return (parentRequest as? ContainerAreaChangeRequest)?.instance
    }

    private fun updateContainerArea(containerArea: ContainerArea?, instance: ContainerGroup) {
        containerArea?.containerGroups?.let {
            it.remove(instance)
            it.add(instance)
        }
    }

    override fun resolveTypedValueByName(attributeName: String): ChangeRequestValue {
        return when(attributeName) {
            "type" -> ContainerTypeChangeRequestValue(entityManager, containerTypeService)
            "amount" -> IntChangeRequestValue()
            "material" -> MaterialChangeRequestValue(materialService)
            "category" -> EnumChangeRequestValue<ContainerTypeCategoryEnum>()
            "carrier" -> CompanyChangeRequestValue(companyFindService)
            "color" -> ColorChangeRequestValue(colorService)
            "tripsPerDay" -> IntChangeRequestValue()
            "schedule" -> RemovalScheduleChangeRequestValue(entityManager, removalScheduleService)
            else -> throw UnknownAttributeException("ContainerGroup.$attributeName")
        }
    }

    override fun setInstanceAttribute(instance: ContainerGroup, attribute: ChangeRequestAttribute) {
        when(attribute.name) {
            "type" -> {
                instance.type = (attribute.typedValue as ContainerTypeChangeRequestValue).value
            }
            "amount" -> {
                instance.amount = (attribute.typedValue as IntChangeRequestValue).value
            }
            "material" -> {
                instance.material = (attribute.typedValue as MaterialChangeRequestValue).value
            }
            "category" -> {
                @Suppress("UNCHECKED_CAST")
                instance.category = (attribute.typedValue as TypedChangeRequestValue<ContainerTypeCategoryEnum>).value
            }
            "carrier" -> {
                instance.carrier = (attribute.typedValue as CompanyChangeRequestValue).value
            }
            "color" -> {
                instance.color = (attribute.typedValue as ColorChangeRequestValue).value
            }
            "tripsPerDay" -> {
                instance.tripsPerDay = (attribute.typedValue as IntChangeRequestValue).value
            }
            "schedule" -> {
                instance.schedule = (attribute.typedValue as RemovalScheduleChangeRequestValue).value
            }
            else -> throw UnknownAttributeException("ContainerGroup.${attribute.name}")
        }
    }

    override fun mapInstanceKeyToDTO(changeRequest: ChangeRequest): Map<String, Any?>? {
        changeRequest as ContainerGroupChangeRequest

        return changeRequest.instance?.let { instance ->
            mapOf<String, Any?>(
                "id" to instance.id
            ).also { instanceKey ->
                // Все что далее, не относится к натуральному ключу сущности. Однако ЛК-шникам
                // понадобилась эта инфа вне зависимости от того, изменились ли данные атрибуты или нет.
                // Здесь видится единственное место, куда это все можно засунуть и не сломать концепцию:
                if(changeRequest.operation in listOf(ChangeRequestOperation.INSERT, ChangeRequestOperation.UPDATE)) {
                    instanceKey + mapOf(
                        "category" to instance.category,

                        // Копипаста из ContainerTypeChangeRequestValue.toDTO():
                        "type" to instance.type?.let {
                            val value = Hibernate.unproxy(it, ContainerType::class.java)
                            mapOf(
                                "id" to value.id,
                                "code" to value.code,
                                "name" to value.name,
                                "volume" to value.volume,
                                "loadTypes" to value.loadTypes?.map { loadType ->
                                    mapOf<String, Any?>(
                                        "code" to loadType.code,
                                        "name" to loadType.name
                                    )
                                }
                            )
                        },

                        // Копипаста из MaterialChangeRequestValue.toDTO():
                        "material" to instance.material?.let {
                            val value = Hibernate.unproxy(it, Material::class.java)
                            mapOf<String, Any?>(
                                "id" to value.id,
                                "code" to value.code
                            )
                        },

                        // Копипаста из ColorChangeRequestValue.toDTO():
                        "color" to instance.color?.let {
                            val value = Hibernate.unproxy(it, Color::class.java)
                            mapOf<String, Any?>(
                                "id" to value.id,
                                "code" to value.code
                            )
                        }
                    )
                }
            }
        }
    }
}
