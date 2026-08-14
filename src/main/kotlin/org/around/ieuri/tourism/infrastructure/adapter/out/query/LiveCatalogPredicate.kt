package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.JPAExpressions
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity

/**
 * **원본이 남아 있는 카탈로그만 조회한다**(SPEC-0019).
 *
 * TourAPI에서 장소가 사라지면 동기화가 그 `spot` 행을 지우지만 카탈로그와 번역은 남는다.
 * 그것을 그대로 내보내면 **목록·지도에는 보이는데 상세를 열면 실패하는 장소**가 된다 —
 * 상세는 `spot`을 거쳐 contentId를 얻기 때문이다.
 *
 * **카탈로그를 지우지 않는 이유는 좋아요다.** `spot_reaction`의 FK가 `ON DELETE CASCADE`라
 * 카탈로그를 지우면 그 장소의 좋아요가 함께 사라진다. 상류가 일시적으로 항목을 빠뜨렸다가
 * 다음날 다시 주는 경우 되돌릴 수 없다. 그래서 데이터는 남기고 조회에서만 감춘다
 * (사용자 결정 2026-08-15). 쌓인 고아 카탈로그의 정리 정책은 미정이다.
 *
 * `idx_spot_catalog_id`(spot의 catalog_id 부분 인덱스)를 타므로 비용은 크지 않다.
 */
object LiveCatalogPredicate {

    /** 생 SQL용. 카탈로그 별칭을 받아 `EXISTS` 절을 만든다. */
    fun sql(catalogAlias: String): String =
        "EXISTS (SELECT 1 FROM spot s_live WHERE s_live.catalog_id = $catalogAlias.id)"

    /** QueryDSL용. 같은 판정을 서브쿼리로 만든다. */
    fun queryDsl(catalog: QSpotCatalogEntity): BooleanExpression {
        val live = QSpotEntity("liveSpot")
        return JPAExpressions.selectOne().from(live).where(live.catalogId.eq(catalog.id)).exists()
    }
}
