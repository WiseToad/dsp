package com.groupstp.dsp.config.env

import org.springframework.core.env.PropertySource
import java.util.*

/**
 * Реализация PropertySource<Properties> для возможности использования стандартного Properties совместно с Environment.
 */

class StandardPropertySource(name: String, source: Properties): PropertySource<Properties>(name, source) {

    override fun getProperty(name: String): Any? = source.getProperty(name)
}
