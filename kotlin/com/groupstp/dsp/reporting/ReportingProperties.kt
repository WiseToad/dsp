package com.groupstp.dsp.reporting

import com.groupstp.dsp.config.env.AppEnvironment
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Настройки отчетной подсистемы.
 *
 * В типовом сценарии маппятся из конфигурационных файлов приложения (application.yml и пр.).
 */
@Configuration
@ConfigurationProperties(prefix="reporting")
class ReportingProperties(
    private val env: AppEnvironment
) {
    final val resourceLocation = "classpath:reporting/"

    private val processLimitKey = "reporting.processLimit"
    private val reportTtlKey = "reporting.reportTtl"
    private val reportLimitPerUserKey = "reporting.reportLimitPerUser"
    private val reportLimitKey = "reporting.reportLimit"

    private val useClickHouseForReportGeneratorKey = "reporting.useClickHouseForReportGenerator"

    // Список конфигурационных файлов с описанием видов отчетов в системе
    var reportConfigs: MutableList<String> = mutableListOf(resourceLocation + "config/reports.json")

    // Список путей к шаблонам отчетов в виде URL
    // Типичные схемы: "file:", "classpath:" и пр.
    var templateLocations: MutableList<String> = mutableListOf(resourceLocation + "templates/")

    // Шаблон, применяемый при отсутствии отчетных данных
    var emptyTemplate: String = resourceLocation + "templates/empty.xlsx"

    // Количество одновременно выполняющихся процессов выборки/формирования отчета
    val processLimit: Int
        get() = env.getProperty(processLimitKey, Int::class.java, 0)

    // Время жизни экземпляра отчета в кэше в минутах
    val reportTtl: Int
        get() = env.getProperty(reportTtlKey, Int::class.java, 10)

    // Максимальное количество экземпляров отчетов в кэше на одного пользователя
    val reportLimitPerUser: Int
        get() = env.getProperty(reportLimitPerUserKey, Int::class.java, 3)

    // Максимальное количество экземпляров отчетов в кэше
    val reportLimit: Int
        get() = env.getProperty(reportLimitKey, Int::class.java, 0)

    val useClickHouseForReportGenerator: Int
        get() = env.getProperty(useClickHouseForReportGeneratorKey, Int::class.java, 0)
}
