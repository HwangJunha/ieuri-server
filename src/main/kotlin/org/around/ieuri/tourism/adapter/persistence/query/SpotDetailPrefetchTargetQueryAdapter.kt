package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.tourism.adapter.persistence.mapper.toPrefetchTarget
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailImageTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailInfoTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailIntroTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.detail.required.PrefetchTarget
import org.around.ieuri.tourism.application.detail.required.SpotDetailPart
import org.around.ieuri.tourism.application.detail.required.SpotDetailPrefetchTargetQueryPort
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
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 상세 사전 수집 대상 조회(SPEC-0037) — 그 갈래 자식 표에 행이 없거나 `fetched_at`이 지난 장소.
 * - `NOT EXISTS`다 — 조인하면 여러 행 자식 때문에 중복이 생기고 `limit`이 행 수를 센다.
 */
@Component
class SpotDetailPrefetchTargetQueryAdapter : SpotDetailPrefetchTargetQueryPort {
    override fun findTargets(
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
