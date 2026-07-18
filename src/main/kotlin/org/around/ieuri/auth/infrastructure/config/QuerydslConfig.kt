package org.around.ieuri.auth.infrastructure.config

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** QueryDSL 읽기 프로젝션용 JPAQueryFactory 빈. 읽기 어댑터(out/query)가 주입받아 사용한다. */
@Configuration
class QuerydslConfig {

    @Bean
    fun jpaQueryFactory(entityManager: EntityManager): JPAQueryFactory = JPAQueryFactory(entityManager)
}
