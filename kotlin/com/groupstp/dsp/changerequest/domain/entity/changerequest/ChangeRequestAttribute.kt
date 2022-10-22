package com.groupstp.dsp.domain.entity.changerequest

import com.groupstp.dsp.domain.entity.changerequest.value.ChangeRequestValue
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import javax.persistence.*

/**
 * Атрибут в составе запроса на изменение.
 */
@Entity(name = "dsp_ChangeRequestAttribute")
@Table(name = "DSP_CHANGE_REQUEST_ATTRIBUTE")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
class ChangeRequestAttribute: ChangeRequestElement() {

    // Запрос на изменение, в состав которого входит данный атрибут
    @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    @JoinColumn(name = "CHANGE_REQUEST_ID", nullable = false)
    lateinit var changeRequest: ChangeRequest

    // Имя атрибута
    @Column(name = "NAME", nullable = false)
    lateinit var name: String

    // Значение атрибута
    @Column(name = "VALUE")
    var value: String? = null
        set(value) {
            field = value
            typedValue?.reload()
        }

    // Типизированное значение атрибута
    // Является отображением значений value (с автоматическим преобразованием в нужный тип), либо childRequests
    @Transient
    var typedValue: ChangeRequestValue? = null
        set(value) {
            if(value !== field) {
                field?.attribute = null
                field = value
                field?.attribute = this
                field?.reload()
            }
        }

    // Дочерние запросы на изменение, которые входят в данный атрибут в виде его значений
    @OneToMany(mappedBy = "parentAttribute", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var childRequests: MutableSet<ChangeRequest>? = null
}
