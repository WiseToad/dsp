package com.groupstp.dsp.repository.changerequest

import com.groupstp.dsp.domain.entity.changerequest.ChangeRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ChangeRequestRepository:
    JpaRepository<ChangeRequest, UUID>,
    ChangeRequestCustomRepository
