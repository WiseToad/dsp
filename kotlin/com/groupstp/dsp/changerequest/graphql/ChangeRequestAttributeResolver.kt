package com.groupstp.dsp.graphql.changerequest

import com.groupstp.dsp.domain.entity.changerequest.ChangeRequestAttribute
import graphql.kickstart.tools.GraphQLResolver
import org.springframework.stereotype.Component

@Component
class ChangeRequestAttributeResolver: GraphQLResolver<ChangeRequestAttribute> {

    fun value(owner: ChangeRequestAttribute): Any? {
        return owner.typedValue?.toDTO()
    }

    fun valueString(owner: ChangeRequestAttribute): String {
        return owner.typedValue.toString()
    }
}
