package com.groupstp.dsp.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Настройки Elasticsearch.
 *
 * В типовом сценарии маппятся из конфигурационных файлов приложения (application.yml и пр.).
 */
@Configuration
@ConfigurationProperties(prefix="es")
class ElasticsearchProps {

    // Параметры соединения с сервером ES
    var host: String = "localhost"
    var port: Int = 9200
    var user: String = ""
    var pass: String = ""
    var ssl: Boolean = false

    // Пользовательский префикс в именах индексов
    var prefix: String = ""
        get() = field.trimEnd().let {
            if(it.isNotEmpty()) "$it-" else it
        }
}
