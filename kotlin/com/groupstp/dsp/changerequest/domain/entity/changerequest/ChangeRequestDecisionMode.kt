package com.groupstp.dsp.domain.entity.changerequest

/**
 * Способ принятия решения по запрошенному изменению.
 */
enum class ChangeRequestDecisionMode {
    ACCEPT,     // Автоматическое принятие изменения
    DENY,       // Автоматическая блокировка изменения
    APPROVE,    // Ручное подтверждение
    LK_APPROVE  // Ручное подтверждение через ЛК
}
