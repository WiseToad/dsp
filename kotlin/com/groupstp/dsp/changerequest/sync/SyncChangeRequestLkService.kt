package com.groupstp.dsp.service.sync.lk.changerequest

import com.groupstp.dsp.domain.entity.changerequest.ChangeRequest

interface SyncChangeRequestLkService {

    fun exportChangeRequest(changeRequest: ChangeRequest)

    fun exportStuckChangeRequests()

    fun importDecisionsJob()
}
