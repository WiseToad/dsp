package com.groupstp.dsp.config

import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration
import org.elasticsearch.client.RestHighLevelClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.RestClients

@Configuration
class ElasticsearchConfig (
    private val elasticsearchProps: ElasticsearchProps
): AbstractElasticsearchConfiguration() {

    private val log = LoggerFactory.getLogger(ElasticsearchConfig::class.java)

    override fun elasticsearchClient(): RestHighLevelClient {

        log.debug("Параметры коннекта к Elasticsearch: " +
            "host=${elasticsearchProps.host} port=${elasticsearchProps.port} ssl=${elasticsearchProps.ssl}")

        val clientConfiguration = ClientConfiguration.builder()
            .connectedTo("${elasticsearchProps.host}:${elasticsearchProps.port}")
            .let { if(elasticsearchProps.ssl) it.usingSsl() else it }
            .withBasicAuth(elasticsearchProps.user, elasticsearchProps.pass)
            .build()

        return RestClients.create(clientConfiguration).rest()
    }
}
