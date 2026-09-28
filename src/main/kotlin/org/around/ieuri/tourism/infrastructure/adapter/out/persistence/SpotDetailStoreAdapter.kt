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
 * 상세 조회 포트 구현. 쓰기(upsert)는 [SpotDetailUpsertJdbcAdapter]가 따로 맡는다.
 * - 모든 키가 `spot_id` 하나다.
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

    /** 적재된 본문. 행이 없으면 미적재(null), 있으면 `bodyAbsent`가 부재 여부를 가른다. */
    @Transactional(readOnly = true)
    override fun findDetail(spotId: Long): StoredDetail? = queryFactory
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

    /** 적재된 갤러리. 행이 없으면 미적재로 간주해 null이다(이미지 없는 관광지와 구분하지 않는다). */
    @Transactional(readOnly = true)
    override fun findGallery(spotId: Long): List<SpotImage>? = queryFactory
        .selectFrom(image)
        .where(image.spotId.eq(spotId))
        .orderBy(image.displayOrder.asc())
        .fetch()
        .map { it.toDomain() }
        .ifEmpty { null }

    // ---------- 운영 정보 ----------

    @Transactional(readOnly = true)
    override fun findIntro(spotId: Long, category: SpotCategory): StoredIntro? = queryFactory
        .selectFrom(intro)
        .where(intro.spotId.eq(spotId), intro.category.eq(category.code))
        .fetchFirst()
        // 전 컬럼이 null인 행은 "조회했으나 값 없음"이다 — 래퍼는 non-null, 값만 null로 돌려준다.
        ?.let { StoredIntro(it.toDomain().takeUnless { value -> value.isEmpty() }) }

    // ---------- 이용안내 ----------

    @Transactional(readOnly = true)
    override fun findInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>? = queryFactory
        .selectFrom(info)
        .where(info.spotId.eq(spotId), info.category.eq(category.code))
        .orderBy(info.displayOrder.asc())
        .fetch()
        .map { it.toDomain() }
        .ifEmpty { null }
}
