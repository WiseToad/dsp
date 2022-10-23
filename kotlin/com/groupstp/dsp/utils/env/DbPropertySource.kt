package com.groupstp.dsp.config.env

import com.groupstp.dsp.repository.ConfigPropertyRepository
import org.springframework.core.env.PropertySource

/**
 * Реализация PropertySource, требуемая для этапа перехода с DbConfigService на AppEnvironment.
 */

class DbPropertySource(name: String, source: ConfigPropertyRepository): PropertySource<ConfigPropertyRepository>(name, source) {

    override fun getProperty(name: String): Any? = source.findByKey(name)?.value
}
