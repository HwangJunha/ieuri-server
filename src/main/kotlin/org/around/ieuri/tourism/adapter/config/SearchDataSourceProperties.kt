package org.around.ieuri.tourism.adapter.config

import org.springframework.boot.context.properties.ConfigurationProperties
/** 검색 노드 접속 설정. */
@ConfigurationProperties(prefix = "ieuri.search-datasource")
data class SearchDataSourceProperties(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val maxPoolSize: Int = 5,
)
