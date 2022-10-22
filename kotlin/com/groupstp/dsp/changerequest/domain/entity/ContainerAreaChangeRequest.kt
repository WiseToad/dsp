package com.groupstp.dsp.domain.entity.container

import com.groupstp.dsp.domain.entity.changerequest.TypedChangeRequest
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import javax.persistence.*

@Entity(name = "dsp_ContainerAreaChangeRequest")
@DiscriminatorValue("dsp_ContainerArea")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
class ContainerAreaChangeRequest: TypedChangeRequest<ContainerArea>()
