package com.groupstp.dsp.domain.entity.changerequest

import com.groupstp.dsp.domain.entity.StandardEntityUUID
import javax.persistence.*

/**
 * Типизированный запрос на изменение.
 *
 * Является базовым generic-типом для запросов на изменение конкретных сущностей (КП, КГ и пр.)
 */
@MappedSuperclass
abstract class TypedChangeRequest<T: StandardEntityUUID>: ChangeRequest() {

    // Изменяемый экземпляр сущности
    // Для INSERT - при создании запроса на изменение не требуется, но должен быть указан после применения одобренных изменений
    // Для UPDATE и DELETE - обязательный
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "INSTANCE_ID")
    open var instance: T? = null
}
