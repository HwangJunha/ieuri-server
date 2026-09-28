package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogSplitPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogTranslationEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 카탈로그 분할의 쓰기 구현(SPEC-0029). */
@Component
class SpotCatalogSplitAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotCatalogSplitPort {

    private val spot = QSpotEntity.spotEntity
    private val translation = QSpotCatalogTranslationEntity.spotCatalogTranslationEntity

    /**
     * spot을 새 카탈로그로 옮긴다. 이미 배정된 행을 덮어쓴다(`assign`과 반대).
     * - 벌크 갱신이라 영속성 컨텍스트를 우회한다(같은 트랜잭션에서 다시 읽지 않는다).
     */
    @Transactional
    override fun reassign(spotIds: List<Long>, catalogId: Long) {
        if (spotIds.isEmpty()) return
        queryFactory
            .update(spot)
            .set(spot.catalogId, catalogId)
            .where(spot.id.`in`(spotIds))
            .execute()
    }

    /** 옮겨 간 언어의 번역 행을 지운다. 빈 목록은 무동작이다 — 조건 없는 delete가 되면 번역이 전부 사라진다. */
    @Transactional
    override fun deleteTranslations(catalogId: Long, languages: List<DomainLocale>) {
        if (languages.isEmpty()) return
        queryFactory
            .delete(translation)
            .where(translation.catalogId.eq(catalogId), translation.language.`in`(languages))
            .execute()
    }
}
