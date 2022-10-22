package com.groupstp.dsp.reporting

import com.groupstp.dsp.domain.utils.AppRestUtils
import com.groupstp.dsp.reporting.audit.ReportAuditRecordDTO
import com.groupstp.dsp.security.AuthoritiesConstants
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * REST-контроллер отчетной подсистемы.
 *
 * @see ReportingService
 */
@RestController
@RequestMapping("/api/v1/reporting/")
class ReportingController(
    private val reportingService: ReportingService
) {
    @PostMapping("fetchReport")
    fun fetchReport(
        @RequestParam reportName: String,
        @RequestBody  reportParams: Map<String, Any?>?
    ): ResponseEntity<Map<String, Any?>> {
        return AppRestUtils.perform {
            val report = reportingService.fetchReport(reportName, reportParams ?: emptyMap())
            ResponseEntity.ok(mapOf("reportId" to report.id))
        }
    }

    @PostMapping("buildReport")
    fun buildReport(
        @RequestParam reportName: String,
        @RequestBody  reportData: Map<String, Any?>?
    ): ResponseEntity<Map<String, Any?>> {
        return AppRestUtils.perform {
            val report = reportingService.buildReport(reportName, reportData ?: emptyMap())
            ResponseEntity.ok(mapOf("reportId" to report.id))
        }
    }

    //TODO: Убедиться что генерация внутри StreamingResponseBody происходит асинхронно, если нет - внести изменения в конфигурацию

    @GetMapping("download")
    fun download(
            @RequestParam reportId: UUID,
        @RequestParam reportFormat: ReportDataFormat?
    ): ResponseEntity<StreamingResponseBody> {
        return AppRestUtils.perform {
            val report = reportingService.getReport(reportId)
            val format = reportFormat ?: ReportDataFormat.HTML

            val responseBuilder = if(format == ReportDataFormat.HTML) {
                ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
            } else {
                val fileName = URLEncoder.encode(report.getFileName(format), StandardCharsets.UTF_8)
                    .replace("+", "%20")
                ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=utf-8''$fileName")
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
            }

            responseBuilder.body(
                StreamingResponseBody {
                    AppRestUtils.perform {
                        reportingService.saveReport(report, format, it)
                    }
                }
            )
        }
    }

    @PostMapping("exportToGoogle")
    fun exportToGoogle(
        @RequestParam reportId: UUID
    ): ResponseEntity<String> {
        return AppRestUtils.perform {
            val report = reportingService.getReport(reportId)
            val googleDriveUrl = reportingService.exportToGoogle(report)
            ResponseEntity.ok(googleDriveUrl)
        }
    }

    @PostMapping("exportToYandex")
    fun exportToYandex(
        @RequestParam reportId: UUID
    ): ResponseEntity<String> {
        return AppRestUtils.perform {
            val report = reportingService.getReport(reportId)
            val yandexDiskUrl = reportingService.exportToYandex(report)
            ResponseEntity.ok(yandexDiskUrl)
        }
    }

    //TODO: Реализовать endpoint для конвертации из формата в формат

    @GetMapping("getReportInfo")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    fun getReportInfo(): ResponseEntity<List<Map<String, Any?>>> {
        return AppRestUtils.perform {
            ResponseEntity.ok(reportingService.getReportInfo())
        }
    }

    @PostMapping("purgeReport")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    fun purgeReport(
        @RequestParam reportId: UUID
    ): ResponseEntity<Unit> {
        return AppRestUtils.perform {
            ResponseEntity.ok(reportingService.purgeReport(reportId))
        }
    }

    @PostMapping("purgeAllReports")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    fun purgeAllReports(): ResponseEntity<Unit> {
        return AppRestUtils.perform {
            ResponseEntity.ok(reportingService.purgeAllReports())
        }
    }

    @GetMapping("getReportAudit")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    fun getReportAudit(
        @RequestParam reportName: String
    ): ResponseEntity<List<ReportAuditRecordDTO>> {
        return AppRestUtils.perform {
            ResponseEntity.ok(reportingService.getReportAudit(reportName))
        }
    }
}
