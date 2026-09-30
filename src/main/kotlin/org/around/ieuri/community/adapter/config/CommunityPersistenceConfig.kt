package org.around.ieuri.community.adapter.config

import org.around.ieuri.common.persistence.ExposedTables
import org.around.ieuri.community.adapter.persistence.table.communityTables
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CommunityPersistenceConfig {

    /** 기동 시 스키마 검증 대상(SPEC-0039 D-35). */
    @Bean
    fun communityExposedTables(): ExposedTables = ExposedTables(communityTables)
}
