package com.groupstp.dsp.domain.entity.container

import com.groupstp.dsp.domain.entity.changerequest.TypedChangeRequest
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import javax.persistence.*

@Entity(name = "dsp_ContainerGroupChangeRequest")
@DiscriminatorValue("dsp_ContainerGroup")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
class ContainerGroupChangeRequest: TypedChangeRequest<ContainerGroup>()
