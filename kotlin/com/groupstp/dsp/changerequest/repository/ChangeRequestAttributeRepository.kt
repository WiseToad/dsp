package com.groupstp.dsp.repository.changerequest

import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestAttribute
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ChangeRequestAttributeRepository: JpaRepository<ChangeRequestAttribute, UUID>
