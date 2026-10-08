package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.tourism.adapter.persistence.mapper.toFestivalPeriod
import org.around.ieuri.tourism.adapter.persistence.mapper.writeTo
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailImageTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailInfoTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailIntroTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotFestivalTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.spot.required.SpotRepository
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import org.around.ieuri.tourism.domain.spot.Spot
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotFestival
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IsDistinctFromOp
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.case
import org.jetbrains.exposed.v1.core.compoundOr
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.insertValue
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 원본 관광지 저장소 구현. 적재는 자연키 upsert, 정리는 메모리에서 계산한 여집합의 삭제다.
 * - 여집합을 SQL `NOT IN`을 청크로 쪼개 만들면 첫 청크가 데이터를 날린다 — 그래서 메모리에서 계산한다.
 * - 상세 키는 전부 `spot_id`다.
 */
@Repository
class SpotPersistenceAdapter : SpotRepository {

    /** 한 페이지를 한 번의 JDBC 배치로 보낸다. 모든 행이 쓰인다(삽입 또는 갱신). */
    override fun upsertSpots(spots: List<Spot>, syncedAt: Instant): Int {
        if (spots.isEmpty()) return 0
        val syncedTimestamp = syncedAt.atOffset(ZoneOffset.UTC)
        SpotTable.batchUpsert(
            spots,
            SpotTable.contentId,
            SpotTable.language,
            shouldReturnGeneratedValues = false,
            onUpdate = {
                it[SpotTable.title] = insertValue(SpotTable.title)
                it[SpotTable.address] = insertValue(SpotTable.address)
                it[SpotTable.thumbnailImageUrl] = insertValue(SpotTable.thumbnailImageUrl)
                it[SpotTable.mapX] = insertValue(SpotTable.mapX)
                it[SpotTable.mapY] = insertValue(SpotTable.mapY)
                it[SpotTable.category] = insertValue(SpotTable.category)
                it[SpotTable.contentTypeId] = insertValue(SpotTable.contentTypeId)
                it[SpotTable.copyright] = insertValue(SpotTable.copyright)
                it[SpotTable.lastSyncedAt] = insertValue(SpotTable.lastSyncedAt)
                it[SpotTable.updatedAt] = touchedWhenChanged(SpotTable.updatedAt, SPOT_VALUE_COLUMNS)
            },
        ) { spot ->
            spot.writeTo(this)
            this[SpotTable.lastSyncedAt] = syncedTimestamp
        }
        return spots.size
    }

    override fun deleteMissingSpots(language: DomainLocale, survivingContentIds: Set<String>): Int {
        // 빈 집합이면 전량 삭제된다 — 도메인 게이트와 별개로 여기서도 막는다.
        require(survivingContentIds.isNotEmpty()) { "생존 집합이 비어 있으면 정리하지 않는다." }

        val doomed = SpotTable.select(SpotTable.contentId)
            .where { SpotTable.language eq language.name }
            .map { it[SpotTable.contentId] }
            .filterNot { it in survivingContentIds }
        return deleteSpotsInChunks(language, doomed)
    }

    /** 부모 원본의 id를 자연키로 먼저 찾는다. 부모가 없는 항목은 건너뛴다(FK 위반 대신). */
    override fun upsertFestivals(festivals: List<SpotFestival>, syncedAt: Instant): Int {
        if (festivals.isEmpty()) return 0
        val spotIds = festivals.groupBy { it.language }.flatMap { (language, group) ->
            SpotTable.select(SpotTable.id, SpotTable.contentId)
                .where { (SpotTable.language eq language.name) and (SpotTable.contentId inList group.map { it.contentId }) }
                .map { (it[SpotTable.contentId] to language) to it[SpotTable.id] }
        }.toMap()
        val parented = festivals.mapNotNull { festival -> spotIds[festival.contentId to festival.language]?.let { it to festival } }
        if (parented.isEmpty()) return 0

        val syncedTimestamp = syncedAt.atOffset(ZoneOffset.UTC)
        SpotFestivalTable.batchUpsert(
            parented,
            SpotFestivalTable.spotId,
            shouldReturnGeneratedValues = false,
            onUpdate = {
                it[SpotFestivalTable.eventStartDate] = insertValue(SpotFestivalTable.eventStartDate)
                it[SpotFestivalTable.eventEndDate] = insertValue(SpotFestivalTable.eventEndDate)
                it[SpotFestivalTable.progressType] = insertValue(SpotFestivalTable.progressType)
                it[SpotFestivalTable.festivalType] = insertValue(SpotFestivalTable.festivalType)
                it[SpotFestivalTable.lastSyncedAt] = insertValue(SpotFestivalTable.lastSyncedAt)
                it[SpotFestivalTable.updatedAt] = touchedWhenChanged(SpotFestivalTable.updatedAt, FESTIVAL_VALUE_COLUMNS)
            },
        ) { (spotId, festival) ->
            festival.writeTo(this, spotId)
            this[SpotFestivalTable.lastSyncedAt] = syncedTimestamp
        }
        return parented.size
    }

    override fun deleteSpots(language: DomainLocale, contentIds: Set<String>): Int = deleteSpotsInChunks(language, contentIds.toList())

    /** 대상은 `spot_festival`이 아니라 축제 카테고리 원본 전체다 — 축제 API는 진행 중인 것만 주므로 "이번 수집에 없다 = 끝났다"다. */
    override fun deleteMissingFestivals(language: DomainLocale, survivingContentIds: Set<String>): Int {
        require(survivingContentIds.isNotEmpty()) { "생존 집합이 비어 있으면 정리하지 않는다." }

        val doomed = SpotTable.select(SpotTable.contentId)
            .where { (SpotTable.language eq language.name) and (SpotTable.category eq SpotCategory.FESTIVAL_EVENT.code) }
            .map { it[SpotTable.contentId] }
            .filterNot { it in survivingContentIds }
        return deleteSpotsInChunks(language, doomed)
    }

    override fun findFestivalPeriods(language: DomainLocale): Map<String, FestivalPeriod> = SpotFestivalTable
        .join(SpotTable, JoinType.INNER, SpotFestivalTable.spotId, SpotTable.id)
        .select(SpotTable.contentId, SpotFestivalTable.eventStartDate, SpotFestivalTable.eventEndDate)
        .where { SpotTable.language eq language.name }
        .associate { it[SpotTable.contentId] to it.toFestivalPeriod() }

    override fun saveDetail(spotId: Long, detail: SpotDetail) = upsertDetail(spotId, detail, bodyAbsent = false)

    override fun saveDetailAbsence(spotId: Long, absent: SpotDetail) = upsertDetail(spotId, absent, bodyAbsent = true)

    /** 갤러리 전량 교체. 넣기는 키 upsert다 — 같은 원본의 교체가 동시에 오면 유일 제약 위반 대신 덮어쓴다. */
    override fun saveGallery(spotId: Long, gallery: List<SpotImage>) {
        SpotDetailImageTable.deleteWhere { SpotDetailImageTable.spotId eq spotId }
        SpotDetailImageTable.batchUpsert(
            gallery.withIndex(),
            SpotDetailImageTable.spotId,
            SpotDetailImageTable.displayOrder,
            shouldReturnGeneratedValues = false,
        ) { (order, image) -> image.writeTo(this, spotId, order) }
    }

    /** 값이 하나도 없어도 행을 남겨 미적재와 구분한다. 확보 시각을 새로 시작한다. */
    override fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?) {
        SpotDetailIntroTable.upsert(
            SpotDetailIntroTable.spotId,
            SpotDetailIntroTable.category,
            onUpdate = {
                it[SpotDetailIntroTable.businessHours] = insertValue(SpotDetailIntroTable.businessHours)
                it[SpotDetailIntroTable.closedDays] = insertValue(SpotDetailIntroTable.closedDays)
                it[SpotDetailIntroTable.parking] = insertValue(SpotDetailIntroTable.parking)
                it[SpotDetailIntroTable.inquiryPhone] = insertValue(SpotDetailIntroTable.inquiryPhone)
                it[SpotDetailIntroTable.fee] = insertValue(SpotDetailIntroTable.fee)
                it[SpotDetailIntroTable.petAllowed] = insertValue(SpotDetailIntroTable.petAllowed)
                it[SpotDetailIntroTable.creditCardAccepted] = insertValue(SpotDetailIntroTable.creditCardAccepted)
                it[SpotDetailIntroTable.fetchedAt] = CurrentTimestampWithTimeZone
                it[SpotDetailIntroTable.updatedAt] = CurrentTimestampWithTimeZone
            },
        ) { intro.writeTo(it, spotId, category) }
    }

    /** 이용안내 전량 교체. 넣기는 키 upsert다(갤러리와 같은 이유). */
    override fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>) {
        SpotDetailInfoTable.deleteWhere { (SpotDetailInfoTable.spotId eq spotId) and (SpotDetailInfoTable.category eq category.code) }
        SpotDetailInfoTable.batchUpsert(
            items.withIndex(),
            SpotDetailInfoTable.spotId,
            SpotDetailInfoTable.category,
            SpotDetailInfoTable.displayOrder,
            shouldReturnGeneratedValues = false,
        ) { (order, item) -> item.writeTo(this, spotId, category, order) }
    }

    /** 부재였던 행에 본문이 생기면 `body_absent`가 false로 돌아간다. 항상 확보 시각을 새로 시작한다. */
    private fun upsertDetail(spotId: Long, detail: SpotDetail, bodyAbsent: Boolean) {
        SpotDetailTable.upsert(
            SpotDetailTable.spotId,
            onUpdate = {
                it[SpotDetailTable.title] = insertValue(SpotDetailTable.title)
                it[SpotDetailTable.category] = insertValue(SpotDetailTable.category)
                it[SpotDetailTable.thumbnailImageUrl] = insertValue(SpotDetailTable.thumbnailImageUrl)
                it[SpotDetailTable.overview] = insertValue(SpotDetailTable.overview)
                it[SpotDetailTable.address] = insertValue(SpotDetailTable.address)
                it[SpotDetailTable.homepage] = insertValue(SpotDetailTable.homepage)
                it[SpotDetailTable.tel] = insertValue(SpotDetailTable.tel)
                it[SpotDetailTable.copyright] = insertValue(SpotDetailTable.copyright)
                it[SpotDetailTable.bodyAbsent] = insertValue(SpotDetailTable.bodyAbsent)
                it[SpotDetailTable.fetchedAt] = CurrentTimestampWithTimeZone
                it[SpotDetailTable.updatedAt] = CurrentTimestampWithTimeZone
            },
        ) { detail.writeTo(it, spotId, bodyAbsent) }
    }

    /**
     * 원본 행을 [DELETE_CHUNK_SIZE]씩 나눠 지우고 총 삭제 행 수를 돌려준다.
     * - 청크는 파라미터 한도 때문의 분할이지 커밋 경계가 아니다 — 중간 실패 시 전체가 롤백된다.
     */
    private fun deleteSpotsInChunks(language: DomainLocale, contentIds: List<String>): Int = contentIds.chunked(DELETE_CHUNK_SIZE).sumOf { chunk ->
        SpotTable.deleteWhere { (SpotTable.language eq language.name) and (SpotTable.contentId inList chunk) }
    }

    private companion object {
        /** 삭제 `IN` 절 하나에 넣을 최대 id 수(드라이버 파라미터 한도). */
        const val DELETE_CHUNK_SIZE = 1_000

        /** 수집이 덮어쓰는 원본 값. 이 중 하나가 바뀌어야 `updated_at`이 오른다. */
        val SPOT_VALUE_COLUMNS = listOf(
            SpotTable.title,
            SpotTable.address,
            SpotTable.thumbnailImageUrl,
            SpotTable.mapX,
            SpotTable.mapY,
            SpotTable.category,
            SpotTable.contentTypeId,
            SpotTable.copyright,
        )

        val FESTIVAL_VALUE_COLUMNS = listOf(
            SpotFestivalTable.eventStartDate,
            SpotFestivalTable.eventEndDate,
            SpotFestivalTable.progressType,
            SpotFestivalTable.festivalType,
        )
    }
}

/** upsert의 `DO UPDATE`에서 [columns] 중 하나라도 바뀔 때만 `now()`, 아니면 기존 [updatedAt] — 같은 값으로 다시 적재해도 "바뀐 시각"이 움직이지 않는다. */
private fun touchedWhenChanged(updatedAt: Column<OffsetDateTime>, columns: List<Column<*>>): Expression<OffsetDateTime> = case()
    .When(columns.map { IsDistinctFromOp(it, insertValue(it)) }.compoundOr(), CurrentTimestampWithTimeZone)
    .Else(updatedAt)
