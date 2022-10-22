package com.groupstp.dsp.reporting

import com.groupstp.dsp.service.SchedulerService
import org.quartz.JobExecutionContext
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component

@Component
class ReportingCleanupCacheJob(
    private val schedulerService: SchedulerService,
    private val reportingService: ReportingService
): QuartzJobBean() {

    fun init() {
        schedulerService.createJobWithTrigger(javaClass, javaClass.simpleName, "*/15 * * * * ?")
    }

    override fun executeInternal(context: JobExecutionContext) {
        reportingService.cleanupCache()
    }
}
