package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.application.port.out.persistence.StoredDetail
import org.around.ieuri.tourism.application.port.out.persistence.StoredIntro
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotDetailEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotDetailImageEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotDetailInfoEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotDetailIntroEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toDomain
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 상세 **조회** 포트 구현. 쓰기는 전부 `INSERT ... ON CONFLICT`라 생 SQL이며,
 * `SpotDetailUpsertPort`(→ [SpotDetailUpsertJdbcAdapter])로 갈라져 있다 — 호출자가 두 포트를
 * 함께 주입받는다.
 *
 * 모든 키가 `spot_id` 하나다 — 호출자가 이미 유효한 `spot.id`를 들고 온다.
 */
@Component
class SpotDetailStoreAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotDetailStorePort {

    private val detail = QSpotDetailEntity.spotDetailEntity
    private val image = QSpotDetailImageEntity.spotDetailImageEntity
    private val intro = QSpotDetailIntroEntity.spotDetailIntroEntity
    private val info = QSpotDetailInfoEntity.spotDetailInfoEntity

    // ---------- 본문 ----------

    /**
     * 적재된 본문. [StoredDetail]로 감싸 부재와 미적재를 구분한다 — 행이 없으면 미조회(null),
     * 있으면 `bodyAbsent`가 부재 여부를 가른다.
     */
    @Transactional(readOnly = true)
    override fun findDetail(spotId: Long): StoredDetail? =
        queryFactory
            .selectFrom(detail)
            .where(detail.spotId.eq(spotId))
            .fetchFirst()
            ?.let {
                StoredDetail(
                    detail = it.toDomain(),
                    bodyAbsent = it.bodyAbsent,
                    fetchedAt = it.fetchedAt,
                )
            }

    // ---------- 갤러리 ----------

    /**
     * 적재된 갤러리. 행이 없으면 "미적재"인지 "이미지가 없는 관광지"인지 알 수 없으므로 미적재로
     * 간주해 null을 돌려준다 — 빈 갤러리는 드물고 재조회 비용도 1콜이다.
     */
    @Transactional(readOnly = true)
    override fun findGallery(spotId: Long): List<SpotImage>? =
        queryFactory
            .selectFrom(image)
            .where(image.spotId.eq(spotId))
            .orderBy(image.displayOrder.asc())
            .fetch()
            .map { it.toDomain() }
            .ifEmpty { null }

    // ---------- 운영 정보 ----------

    @Transactional(readOnly = true)
    override fun findIntro(spotId: Long, category: SpotCategory): StoredIntro? =
        queryFactory
            .selectFrom(intro)
            .where(intro.spotId.eq(spotId), intro.category.eq(category.code))
            .fetchFirst()
            // 전 컬럼이 null이면 "조회했으나 값 없음"으로 적재된 행이다 — 래퍼는 non-null,
            // 값만 null로 돌려줘야 다음 조회가 미스로 오판하지 않는다.
            ?.let { StoredIntro(it.toDomain().takeUnless { value -> value.isEmpty() }) }

    // ---------- 이용안내 ----------

    @Transactional(readOnly = true)
    override fun findInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>? =
        queryFactory
            .selectFrom(info)
            .where(info.spotId.eq(spotId), info.category.eq(category.code))
            .orderBy(info.displayOrder.asc())
            .fetch()
            .map { it.toDomain() }
            .ifEmpty { null }
}
