package com.groupstp.dsp.smartsearch.job

import com.groupstp.dsp.service.SchedulerService
import com.groupstp.dsp.smartsearch.service.SmartSearchEsIndexService
import org.quartz.JobExecutionContext
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component

@Component
class SmartSearchRebuildJob(
    private val schedulerService: SchedulerService,
    private val indexService: SmartSearchEsIndexService
): QuartzJobBean() {

    fun createJob() {
        schedulerService.createJobWithTrigger(javaClass, javaClass.simpleName, "0 30 1 * * ?")
    }

    fun removeJob() {
        schedulerService.deleteJobDetail(javaClass.simpleName, "DEFAULT")
    }

    override fun executeInternal(context: JobExecutionContext) {
        indexService.rebuildAllIndexesJob()
    }
}
