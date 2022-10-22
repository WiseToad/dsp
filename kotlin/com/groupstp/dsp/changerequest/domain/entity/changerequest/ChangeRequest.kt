package com.groupstp.dsp.domain.entity.changerequest

import com.groupstp.dsp.domain.entity.employee.Employee
import com.groupstp.dsp.domain.entity.verification.ContainerAreaVerification
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import java.util.*
import javax.persistence.*

/**
 * Обобщенный запрос на изменение.
 */
@Entity(name = "dsp_ChangeRequest")
@Table(name = "DSP_CHANGE_REQUEST")
@DiscriminatorColumn(name="ENTITY_NAME")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
abstract class ChangeRequest: ChangeRequestElement() {

    // Атрибут, в который входит данный запрос в виде значения
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_ATTRIBUTE_ID")
    open var parentAttribute: ChangeRequestAttribute? = null

    // Источник изменения
    @Column(name = "SOURCE", nullable = false)
    @Enumerated(EnumType.STRING)
    open lateinit var source: ChangeRequestSource

    // Верификация, от которой создан запрос на изменение
    // Обязательно для source == VERIFICATION, для прочих случаев не требуется
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VERIFICATION_ID")
    open var verification: ContainerAreaVerification? = null

    // Вид изменения
    @Column(name = "OPERATION", nullable = false)
    @Enumerated(EnumType.STRING)
    open lateinit var operation: ChangeRequestOperation

    // Имя сущности (read-only, ибо @DiscriminatorColumn - см. выше)
    @Column(name = "ENTITY_NAME", nullable = false, insertable = false, updatable = false)
    open lateinit var entityName: String

    // Ссылка на инстанс нужного типа - см. TypedChangeRequest

    // Список изменяемых атрибутов
    // Для INSERT - обязательный, наиболее полный
    // Для UPDATE - обязательный, может быть частичным
    // Для DELETE - не требуется
    @OneToMany(mappedBy = "changeRequest", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    open var attributes: MutableSet<ChangeRequestAttribute>? = null

    // Причина изменения
    @Column(name = "REASON")
    open var reason: String? = null

    // Время запроса на изменение
    @Column(name = "REQUEST_TS", nullable = false)
    open lateinit var requestTs: Date

    // Инициатор изменения
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REQUESTED_BY_ID")
    open var requestedBy: Employee? = null

    // Время отправки запроса на изменение во внешнюю систему
    @Column(name = "EXPORT_TS")
    open var exportTs: Date? = null
}
