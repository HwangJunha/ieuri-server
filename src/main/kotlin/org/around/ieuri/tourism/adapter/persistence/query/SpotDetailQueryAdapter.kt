package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.adapter.persistence.mapper.toPrefetchTarget
import org.around.ieuri.tourism.adapter.persistence.mapper.toSpotDetail
import org.around.ieuri.tourism.adapter.persistence.mapper.toSpotImage
import org.around.ieuri.tourism.adapter.persistence.mapper.toSpotInfoItem
import org.around.ieuri.tourism.adapter.persistence.mapper.toSpotIntro
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailImageTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailInfoTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailIntroTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.spot.provided.dto.PrefetchTarget
import org.around.ieuri.tourism.application.spot.provided.dto.SpotDetailPart
import org.around.ieuri.tourism.application.spot.required.KnownIntro
import org.around.ieuri.tourism.application.spot.required.SpotDetailQueryPort
import org.around.ieuri.tourism.application.spot.required.StoredDetail
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** 적재된 상세 읽기(SPEC-0004 R-14)와 사전 수집 대상 조회(SPEC-0037). 모든 키가 `spot_id`다. */
@Component
class SpotDetailQueryAdapter : SpotDetailQueryPort {

    /** 행이 없으면 미적재(null), 있으면 `bodyAbsent`가 부재 여부를 가른다. */
    override fun findDetail(spotId: Long): StoredDetail? = SpotDetailTable.selectAll()
        .where { SpotDetailTable.spotId eq spotId }
        .limit(1).singleOrNull()
        ?.let { StoredDetail(detail = it.toSpotDetail(), bodyAbsent = it[SpotDetailTable.bodyAbsent]) }

    /** 행이 없으면 미적재로 간주해 null이다(이미지 없는 관광지와 구분하지 않는다). */
    override fun findGallery(spotId: Long): List<SpotImage>? = SpotDetailImageTable.selectAll()
        .where { SpotDetailImageTable.spotId eq spotId }
        .orderBy(SpotDetailImageTable.displayOrder, SortOrder.ASC)
        .map { it.toSpotImage() }
        .ifEmpty { null }

    /** 전 컬럼이 null인 행은 "조회했으나 값 없음"이다 — 래퍼는 non-null, 값만 null로 돌려준다. */
    override fun findIntro(spotId: Long, category: SpotCategory): KnownIntro? = SpotDetailIntroTable.selectAll()
        .where { (SpotDetailIntroTable.spotId eq spotId) and (SpotDetailIntroTable.category eq category.code) }
        .limit(1).singleOrNull()
        ?.let { KnownIntro(it.toSpotIntro().takeUnless { value -> value.isEmpty() }) }

    override fun findInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>? = SpotDetailInfoTable.selectAll()
        .where { (SpotDetailInfoTable.spotId eq spotId) and (SpotDetailInfoTable.category eq category.code) }
        .orderBy(SpotDetailInfoTable.displayOrder, SortOrder.ASC)
        .map { it.toSpotInfoItem() }
        .ifEmpty { null }

    /** 그 갈래 표에 행이 없거나 `fetched_at`이 지난 원본. `NOT EXISTS`다 — 조인하면 여러 행 자식 때문에 `limit`이 행 수를 센다. */
    override fun findPrefetchTargets(
        part: SpotDetailPart,
        afterSpotId: Long,
        limit: Int,
        staleBefore: Instant,
    ): List<PrefetchTarget> = SpotTable
        .select(
            SpotTable.id,
            SpotTable.contentId,
            SpotTable.language,
            SpotTable.category,
            SpotTable.title,
            SpotTable.thumbnailImageUrl,
            SpotTable.copyright,
            SpotTable.address,
        )
        .where { (SpotTable.id greater afterSpotId) and needsFetch(part, staleBefore) }
        .orderBy(SpotTable.id, SortOrder.ASC)
        .limit(limit)
        .map { it.toPrefetchTarget() }

    /** 아직 확보하지 못했거나 신선도가 지난 장소. 확보 실패와 미확보를 구분하지 않는다. */
    private fun needsFetch(part: SpotDetailPart, staleBefore: Instant): Op<Boolean> = when (part) {
        SpotDetailPart.BODY -> notFresh(SpotDetailTable, SpotDetailTable.spotId, SpotDetailTable.fetchedAt, staleBefore)
        SpotDetailPart.GALLERY -> notFresh(SpotDetailImageTable, SpotDetailImageTable.spotId, SpotDetailImageTable.fetchedAt, staleBefore)
        SpotDetailPart.INTRO -> notFresh(SpotDetailIntroTable, SpotDetailIntroTable.spotId, SpotDetailIntroTable.fetchedAt, staleBefore)
        SpotDetailPart.INFO -> notFresh(SpotDetailInfoTable, SpotDetailInfoTable.spotId, SpotDetailInfoTable.fetchedAt, staleBefore)
    }

    private fun notFresh(table: Table, spotId: Column<Long>, fetchedAt: Column<OffsetDateTime>, staleBefore: Instant): Op<Boolean> = notExists(
        table.select(spotId).where { (spotId eq SpotTable.id) and (fetchedAt greaterEq OffsetDateTime.ofInstant(staleBefore, ZoneOffset.UTC)) },
    )
}
