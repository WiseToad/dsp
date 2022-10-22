package com.groupstp.dsp.reporting

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.groupstp.dsp.domain.utils.AppCastUtils
import com.groupstp.dsp.domain.utils.AppIoUtils
import com.groupstp.dsp.domain.utils.multithreading.ProcessLimiter
import com.groupstp.dsp.domain.utils.streamedcache.FileStreamedCache
import com.groupstp.dsp.domain.utils.streamedcache.StreamedCache
import com.groupstp.dsp.reporting.audit.ReportAuditEventType
import com.groupstp.dsp.reporting.audit.ReportAuditRecord
import com.groupstp.dsp.reporting.audit.ReportAuditRecordDTO
import com.groupstp.dsp.reporting.audit.ReportAuditRecordRepo
import com.groupstp.dsp.reporting.convert.FormatConverter
import com.groupstp.dsp.reporting.fetch.DataFetcher
import com.groupstp.dsp.reporting.generate.ReportGenerator
import com.groupstp.dsp.repository.UserRepository
import com.groupstp.dsp.security.SecurityUtils
import com.groupstp.dsp.service.rest.report.ExcelUtil
import org.slf4j.LoggerFactory
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.util.ClassUtils
import org.springframework.util.FileSystemUtils
import java.io.*
import java.nio.file.Files
import java.util.*

/**
 * Сервис отчетной подсистемы.
 */
@Service
class ReportingService(
    private val reportingProperties: ReportingProperties,
    dataFetchers: List<DataFetcher>,
    reportGenerators: List<ReportGenerator>,
    formatConverters: List<FormatConverter>,
    private val excelUtil: ExcelUtil,
    private val auditRecordRepo: ReportAuditRecordRepo,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    //TODO: Реализовать контроль доступа к отчетам (см. ReportConfig.permittedRoles)

    private val dataFetchers = dataFetchers.associateBy { ClassUtils.getUserClass(it.javaClass).simpleName }
    private val reportGenerators = reportGenerators.associateBy { it.templateFormat }
    private val formatConverters = formatConverters.associateBy { Pair(it.inputFormat, it.outputFormat) }

    val reportConfigs = mutableMapOf<String, ReportConfig>().apply {
        reportingProperties.reportConfigs
            .filter(String::isNotEmpty)
            .forEach { config ->
                try {
                    putAll(
                        AppIoUtils.loadResource(config) { resourceStream ->
                            jacksonObjectMapper().readValue(resourceStream,
                                object: TypeReference<List<ReportConfig>>() {})
                        }.associateBy { it.reportName }
                    )
                    log.debug("Загружен список отчетов из $config")
                } catch (e: Exception) {
                    log.error("Ошибка чтения списка отчетов из $config", e)
                }
            }
    }

    // Кэш экземпляров отчетов
    private val reports = mutableMapOf<UUID, Report>()

    // Префикс файлов в файловом кэше
    private val tempPrefix = "disp.report."

    init {
        log.trace("Очистка мусора в файловом кэше отчетов")
        val tempDirName = System.getProperty("java.io.tmpdir")
        val tempFileList = File(tempDirName).list()
        tempFileList?.forEach { fileName ->
            if(fileName.startsWith(tempPrefix)) {
                try {
                    val tempFile = File("$tempDirName${File.separator}$fileName")
                    FileSystemUtils.deleteRecursively(tempFile)
                }
                catch(e: Exception) {
                    log.warn("Ошибка очистки временного файла или директории: $fileName; причина: ${e.message}")
                }
            }
        }
    }

    private class Template (
        val resource: Resource
    ) {
        val name = resource.filename
            ?: throw IllegalArgumentException("Имя файла для шаблона отчета не определено")

        val format = ReportDataFormat.fromFileName(name)
            ?: throw IllegalArgumentException("Неизвестный формат шаблона: $name")
    }

    val limiter = ProcessLimiter(reportingProperties.processLimit)

    /**
     * Подготовить экземпляр отчета по заданным параметрам.
     *
     * Для отчета должен быть определен DataFetcher, превращающий переданные параметры в набор данных, отображаемых
     * затем в отчете.
     *
     * Подготовленный отчет сохраняется в кэше для последующего (множественного) формирования отчета по запросу в
     * окончательном виде в требуемом формате.
     *
     * @param reportName    имя отчета в системе
     * @param reportParams  параметры для формирования отчета
     * @return              описатель экземпляра отчета
     */
    fun fetchReport(reportName: String, reportParams: Map<String, Any?>): Report {
        synchronized(limiter) {
            limiter.limit = reportingProperties.processLimit
        }
        limiter.acquire().use {
            val report = Report(getReportConfig(reportName))
            report.params = reportParams

            log.debug("Запуск выборки данных для отчета ${report.name}, id отчета ${report.id}, пользователь ${report.user}")
            log.debug("Параметры отчета: ${report.params}")
            writeAudit(report, ReportAuditEventType.FETCH_START,
                "Запуск выборки данных для отчета с параметрами: ${report.params}")

            try {
                val dataFetcher = report.config.dataFetcher?.let { dataFetcher ->
                    dataFetchers[dataFetcher]
                } ?: throw IllegalArgumentException("Не определен, либо не найден сборщик данных для отчета: ${report.config.dataFetcher}")

                report.data = dataFetcher.fetch(reportParams)
            } catch(e: Exception) {
                writeAudit(report, ReportAuditEventType.FETCH_ERROR, e.message)
                throw e
            }
            writeAudit(report, ReportAuditEventType.FETCH_END, "Завершена выборка данных для отчета")
            log.debug("Завершена выборка данных для отчета ${report.name}")

            addReport(report)
            return report
        }
    }

    /**
     * Подготовить экземпляр отчета на основе переданных данных.
     *
     * DataFetcher для отчета не требуется, т.к. данные для отображения в отчете уже подготовлены.
     *
     * @param reportName  имя отчета в системе
     * @param reportData  данные для отображения в отчете
     * @return            описатель экземпляра отчета
     */
    fun buildReport(reportName: String, reportData: Map<String, Any?>): Report {
        val report = Report(getReportConfig(reportName))
        report.data = reportData

        addReport(report)
        return report
    }

    private fun getReportConfig(reportName: String): ReportConfig {
        return reportConfigs[reportName]
            ?: throw IllegalArgumentException("Отчет не найден: $reportName")
    }

    private fun addReport(report: Report) {
        synchronized(reports) {
            reports[report.id] = report
        }
    }

    /**
     * Извлечь из кэша описатель экземпляра отчета, с возможным указанием выходного формата данных.
     *
     * @param reportId      id экземпляра отчета
     * @return              описатель экземпляра отчета
     */
    fun getReport(reportId: UUID): Report {
        return synchronized(reports) {
            reports[reportId] ?: throw IllegalArgumentException("Экземпляр отчета не найден: $reportId")
        }.also { report ->
            report.accessTs = Date()
        }
    }

    /**
     * Сформировать экземпляр отчета в выходной поток.
     *
     * В процессе формирования отчета происходит вызов генератора отчета и конвертера формата данных при необходимости.
     *
     * @param report        описатель экземпляра отчета
     * @param reportFormat  требуемый формат данных отчета
     * @param output        выходной поток сформированного отчета
     */
    fun saveReport(report: Report, reportFormat: ReportDataFormat, output: OutputStream) {
        synchronized(limiter) {
            limiter.limit = reportingProperties.processLimit
        }
        limiter.acquire().use {
            log.debug("Запуск экспорта отчета ${report.name} в формат $reportFormat, id отчета ${report.id}")
            writeAudit(report, ReportAuditEventType.EXPORT_START,
                "Запуск экспорта отчета в формат $reportFormat")

            try {
                val isReportEmpty = report.data.isEmpty() || report.data["isEmpty"]?.let(AppCastUtils::toBoolean) == true

                val template = if(isReportEmpty) {
                    getTemplate(reportingProperties.emptyTemplate)
                } else {
                    val templateName = report.config.templateNames[reportFormat.name] ?: report.config.templateName
                        ?: throw IllegalArgumentException("Не задан шаблон для отчета ${report.name}")
                    findTemplate(templateName)
                }

                val reportContent = report.content.getOrPut(template.name) {
                    log.debug("Запуск генерации отчета ${report.name}")
                    generateReport(report, template)
                }

                //TODO: Возможно, переделать на pipelined генерацию/конвертацию (понадобится свой thread pool со всеми вытекающими)

                if(reportFormat == template.format) {
                    AppIoUtils.loadAll(reportContent.getInputStream()) { contentStream ->
                        contentStream.transferTo(output)
                    }
                } else {
                    val formatConverter = formatConverters[Pair(template.format, reportFormat)]
                        ?: throw IllegalArgumentException("Не найден конвертер данных в формат $reportFormat")

                    val convertParams = report.config.convertParams[reportFormat.name] ?: emptyMap()

                    log.debug("Запуск конвертации отчета ${report.name} в формат $reportFormat")
                    AppIoUtils.loadAll(reportContent.getInputStream()) { contentStream ->
                        formatConverter.convert(contentStream, convertParams, output)
                    }
                }
            } catch(e: Exception) {
                writeAudit(report, ReportAuditEventType.EXPORT_ERROR, e.message)
                throw e
            }
            writeAudit(report, ReportAuditEventType.EXPORT_END, "Завершен экспорт отчета в формат $reportFormat")
            log.debug("Завершен экспорт отчета ${report.name} в формат $reportFormat")
        }
    }

    private fun getTemplate(templateName: String): Template {
        val resourceLoader = DefaultResourceLoader()
        val resource = resourceLoader.getResource(templateName)
        if(resource.exists()) {
            return Template(resource)
        }
        throw IllegalArgumentException("Шаблон не найден: $templateName")
    }

    private fun findTemplate(templateName: String): Template {
        //TODO: Сделать кэширование найденных шаблонов
        val resourceLoader = DefaultResourceLoader()
        for(templateLocation in reportingProperties.templateLocations) {
            val resource = resourceLoader.getResource(templateLocation + templateName)
            if(resource.exists()) {
                return Template(resource)
            }
        }
        throw IllegalArgumentException("Шаблон не найден: $templateName")
    }

    private fun generateReport(report: Report, template: Template): StreamedCache {
        val generator = reportGenerators[template.format]
            ?: throw IllegalArgumentException("Генератор отчетов не найден для шаблона: ${template.name}")

        //TODO: Сделать переключение вида кэша через настройку
        //val generatedContent = MemoryStreamedCache()
        val generatedContent = FileStreamedCache(tempPrefix)

        AppIoUtils.saveAll(generatedContent.getOutputStream()) { generatedStream ->
            AppIoUtils.loadAll(template.resource.inputStream) { templateStream ->
                //TODO: Сделать кэширование содержимого шаблона (особенно если шаблоны хранятся на удаленном ресурсе)
                generator.generate(templateStream, report.data, report.config.generateParams, generatedStream)
            }
        }

        return generatedContent
    }

    /**
     * Экспортировать экземпляр отчета в Google Drive.
     *
     * @param report  описатель экземпляра отчета
     */
    fun exportToGoogle(report: Report): String {
        val tempDir = Files.createTempDirectory(tempPrefix)
        val tempFile = File(tempDir.toFile().absolutePath + "/" + report.getFileName(ReportDataFormat.XLSX))
        tempFile.createNewFile()

        try {
            AppIoUtils.saveAll(FileOutputStream(tempFile)) { tempOutput ->
                saveReport(report, ReportDataFormat.XLSX, tempOutput)
            }
            log.debug("Запуск экспорта отчета ${report.name} в Google Drive")
            //TODO: Перенести функционал, вместо того чтобы вызывать его
            val googleDriveUrl = excelUtil.exportToGoogle(tempFile.absolutePath)
            log.trace("Завершен экспорт отчета ${report.name} в Google Drive")
            return googleDriveUrl
        } finally {
            tempFile.delete()
            Files.delete(tempDir)
        }
    }

    /**
     * Экспортировать экземпляр отчета в Yandex.
     *
     * @param report  описатель экземпляра отчета
     */
    fun exportToYandex(report: Report): String {
        val tempDir = Files.createTempDirectory(tempPrefix)
        val tempFile = File(tempDir.toFile().absolutePath + "/" + report.getFileName(ReportDataFormat.XLSX))
        tempFile.createNewFile()
        try {
            AppIoUtils.saveAll(FileOutputStream(tempFile)) { tempOutput ->
                saveReport(report, ReportDataFormat.XLSX, tempOutput)
            }
            log.debug("Запуск экспорта отчета ${report.name} в Yandex disk")
            //TODO: Перенести функционал, вместо того чтобы вызывать его
            val yandexDiskUrl = excelUtil.exportToYandex(tempFile.absolutePath)
            log.trace("Завершен экспорт отчета ${report.name} в Yandex disk")
            return yandexDiskUrl
        } finally {
            tempFile.delete()
            Files.delete(tempDir)
        }
    }

    /**
     * Выполнить конвертацию данных из формата в формат.
     *
     * @param input         входной поток данных для конвертации
     * @param inputFormat   формат входных данных для конвертации
     * @param outputFormat  требуемый формат данных после конвертации
     * @param params        параметры, специфичные для разных типов конвертеров
     * @param output        выходной поток сконвертированных данных
     */
    fun convertFormat(
        input: InputStream,
        inputFormat: ReportDataFormat,
        outputFormat: ReportDataFormat,
        params: Map<String, Any?>,
        output: OutputStream
    ) {
        val formatConverter = formatConverters[Pair(inputFormat, outputFormat)]
            ?: throw IllegalArgumentException("Конвертация из $inputFormat в $outputFormat не поддерживается")
        formatConverter.convert(input, params, output)
    }

    /**
     * Получить системную информацию о содержимом кэша отчетов.
     */
    fun getReportInfo(): List<Map<String, Any?>> {
        synchronized(reports) {
            return reports.values.map { report ->
                mutableMapOf<String, Any?> (
                    "id" to report.id,
                    "createTs" to report.createTs,
                    "accessTs" to report.accessTs,
                    "user" to report.user,
                    "name" to report.name,
                    "title" to report.title
                ).also {
                    if(report.params != null) {
                        it["params"] = report.params
                    }
                }
            }
        }
    }

    /**
     * Удалить отчет из кэша.
     */
    fun purgeReport(reportId: UUID) {
        purgeReports(listOf(reportId))
    }

    /**
     * Очистить весь кэш отчетов.
     */
    fun purgeAllReports() {
        purgeReports()
    }

    private fun purgeReports(reportIds: Iterable<UUID>? = null) {
        synchronized(reports) {
            val toBeRemoved = reportIds?.mapNotNull { reportId -> reports[reportId] } ?: reports.values
            toBeRemoved.forEach { report ->
                report.content.values.forEach(StreamedCache::close)
            }
            if(reportIds != null) {
                reports.keys.removeAll(reportIds)
            } else {
                reports.clear()
            }
        }
    }

    /**
     * Точка входа для рабочего джоба
     */
    fun cleanupCache() {
        synchronized(reports) {
            val allReports = reports.values
            val toBeRemoved = mutableSetOf<Report>()
            // Сбор отчетов, устаревших по времени
            val minAccessTs = Date().time - reportingProperties.reportTtl * 1000L * 60L
            toBeRemoved.addAll(
                allReports.filter { report -> report.accessTs.time < minAccessTs }
            )
            // Сбор отчетов, выпадающих из лимита отчетов на каждого пользователя
            val reportLimitPerUser = reportingProperties.reportLimitPerUser
            if(reportLimitPerUser > 0) {
                allReports.groupBy(Report::user)
                    .values.forEach { userReports ->
                        toBeRemoved.addAll(
                            userReports.sortedBy { report -> report.accessTs }
                                .dropLast(reportLimitPerUser)
                        )
                    }
            }
            // Сбор отчетов, выпадающих из лимита общего количества отчетов в кэше
            val reportLimit = reportingProperties.reportLimit
            if(reportLimit > 0) {
                toBeRemoved.addAll(
                    allReports.sortedBy { report -> report.accessTs }
                        .dropLast(reportLimit)
                )
            }
            // Собственно очистка
            toBeRemoved.forEach { report ->
                report.content.values.forEach(StreamedCache::close)
            }
            if(allReports.removeAll(toBeRemoved)) {
                log.trace("Удалено ${toBeRemoved.size} экземпляров отчетов из кэша")
            }
        }
    }

    private fun writeAudit(report: Report, eventType: ReportAuditEventType, eventDescr: String?) {
        // в самом общем случае userLogin может отличаться от report.user
        val userLogin = SecurityUtils.getCurrentUserLogin().orElse(null)
        val userName = userRepository.getUserNameByLogin(userLogin)

        auditRecordRepo.save(
            ReportAuditRecord().also { auditRecord ->
                auditRecord.reportName = report.name
                auditRecord.reportId = report.id
                auditRecord.eventType = eventType
                auditRecord.eventDescr = eventDescr
                auditRecord.userLogin = userLogin
                auditRecord.userName = userName
            }
        )
    }

    /**
     * Сделать выборку из аудита запуска отчетов.
     */
    fun getReportAudit(reportName: String): List<ReportAuditRecordDTO> {
        return auditRecordRepo.findByReportNameOrderByOrderNumDesc(reportName)
            .map { auditRecord ->
                ReportAuditRecordDTO(
                    auditRecord.reportName,
                    auditRecord.eventTs,
                    auditRecord.eventType,
                    auditRecord.eventDescr,
                    auditRecord.userName ?: auditRecord.userLogin
                )
            }
    }
}
