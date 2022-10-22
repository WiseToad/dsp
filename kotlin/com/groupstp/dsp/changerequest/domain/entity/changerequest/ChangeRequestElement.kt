package com.groupstp.dsp.domain.entity.changerequest

import com.groupstp.dsp.domain.entity.StandardEntityUUID
import java.util.*
import javax.persistence.*

/**
 * Изменение, по которому может быть принято независимое решение.
 *
 * Является базовым типом как для запроса на изменение, так и для атрибута в составе запроса на изменение
 * (т.к. решение по каждому атрибуту при изменениях вида UPDATE принимается независимо от прочих атрибутов).
 */
@MappedSuperclass
abstract class ChangeRequestElement: StandardEntityUUID() {

    // Способ принятия решения по изменению
    // Обязательный для запросов на изменение вида INSERT и DELETE, а также для атрибутов в изменениях вида UPDATE
    // Однако не требуется для атрибутов, у которых значением является массив дочерних запросов на изменение
    @Column(name = "DECISION_MODE")
    @Enumerated(EnumType.STRING)
    open var decisionMode: ChangeRequestDecisionMode? = null

    // Принятое решение по изменению
    @Column(name = "DECISION")
    @Enumerated(EnumType.STRING)
    open var decision: ChangeRequestDecision? = null

    // Время принятия решения по изменению
    @Column(name = "DECISION_TS")
    open var decisionTs: Date? = null

    // Лицо, принявшее решение по изменению
    @Column(name = "DECIDED_BY")
    open var decidedBy: String? = null

    // Время применения изменения
    @Column(name = "APPLY_TS")
    open var applyTs: Date? = null
}
