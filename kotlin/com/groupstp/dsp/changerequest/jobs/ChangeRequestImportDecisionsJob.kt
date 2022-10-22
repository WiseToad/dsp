package com.groupstp.dsp.schedules.jobs.changerequest

import com.groupstp.dsp.service.SchedulerService
import com.groupstp.dsp.service.sync.lk.changerequest.SyncChangeRequestLkService
import org.quartz.JobExecutionContext
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component

@Component
class ChangeRequestImportDecisionsJob(
    private val schedulerService: SchedulerService,
    private val syncChangeRequestLkService: SyncChangeRequestLkService
): QuartzJobBean() {

    fun init() {
        schedulerService.createJobWithTrigger(javaClass, javaClass.simpleName, "*/15 * * * * ?")
    }

    override fun executeInternal(context: JobExecutionContext) {
        syncChangeRequestLkService.importDecisionsJob()
    }
}

