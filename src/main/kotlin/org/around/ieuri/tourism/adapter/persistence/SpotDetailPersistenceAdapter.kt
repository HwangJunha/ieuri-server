package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.tourism.adapter.persistence.mapper.toSpotDetail
import org.around.ieuri.tourism.adapter.persistence.mapper.toSpotImage
import org.around.ieuri.tourism.adapter.persistence.mapper.toSpotInfoItem
import org.around.ieuri.tourism.adapter.persistence.mapper.toSpotIntro
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailImageTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailInfoTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailIntroTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailTable
import org.around.ieuri.tourism.application.detail.required.SpotDetailRepository
import org.around.ieuri.tourism.application.detail.required.StoredDetail
import org.around.ieuri.tourism.application.detail.required.StoredIntro
import org.around.ieuri.tourism.domain.detail.SpotDetail
import org.around.ieuri.tourism.domain.detail.SpotImage
import org.around.ieuri.tourism.domain.detail.SpotInfoItem
import org.around.ieuri.tourism.domain.detail.SpotIntro
import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement

/**
 * 관광지 상세 저장소 구현. 적재는 raw SQL upsert(`ON CONFLICT`)이고, 조회는 DSL이다.
 * - 모든 키가 `spot_id` 하나다.
 */
@Repository
class SpotDetailPersistenceAdapter(
    private val exposedJdbcTemplate: JdbcTemplate,
) : SpotDetailRepository {

    override fun saveDetail(spotId: Long, detail: SpotDetail) {
        upsertDetail(spotId, detail, bodyAbsent = false)
    }

    /** 본문 부재 적재. NOT NULL 컬럼은 [fallback] 값으로 채우고 overview·homepage·tel은 null로 남긴다. */
    override fun saveDetailAbsence(spotId: Long, fallback: SpotDetail) {
        upsertDetail(spotId, fallback, bodyAbsent = true)
    }

    /**
     * 본문 upsert. [bodyAbsent]만 두 경로가 다르며, 부재였던 항목에 본문이 생기면 false로 되돌아간다.
     * - 항상 `fetched_at`을 갱신해 신선도 창을 새로 시작한다.
     */
    private fun upsertDetail(spotId: Long, detail: SpotDetail, bodyAbsent: Boolean) {
        exposedJdbcTemplate.update(
            """
            INSERT INTO spot_detail (spot_id, title, category, thumbnail_image_url,
                                     overview, address, homepage, tel, copyright, body_absent, fetched_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (spot_id) DO UPDATE SET
                title = EXCLUDED.title, category = EXCLUDED.category,
                thumbnail_image_url = EXCLUDED.thumbnail_image_url, overview = EXCLUDED.overview,
                address = EXCLUDED.address, homepage = EXCLUDED.homepage, tel = EXCLUDED.tel,
                copyright = EXCLUDED.copyright, body_absent = EXCLUDED.body_absent,
                fetched_at = now(), updated_at = now()
            """.trimIndent(),
            spotId, detail.title, detail.category.code, detail.thumbnailImageUrl,
            detail.overview, detail.address, detail.homepage, detail.tel, detail.copyright.name,
            bodyAbsent,
        )
    }

    /** 갤러리 전량 교체. 삽입은 `ON CONFLICT`라 동시 교체가 제약 위반 대신 덮어쓴다. */
    override fun saveGallery(spotId: Long, gallery: List<SpotImage>) {
        exposedJdbcTemplate.update("DELETE FROM spot_detail_image WHERE spot_id = ?", spotId)
        if (gallery.isEmpty()) return
        exposedJdbcTemplate.batchUpdate(
            """
            INSERT INTO spot_detail_image (spot_id, display_order, image_url,
                                           thumbnail_url, caption, copyright, fetched_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (spot_id, display_order) DO UPDATE SET
                image_url = EXCLUDED.image_url, thumbnail_url = EXCLUDED.thumbnail_url,
                caption = EXCLUDED.caption, copyright = EXCLUDED.copyright, fetched_at = now()
            """.trimIndent(),
            object : BatchPreparedStatementSetter {
                override fun getBatchSize() = gallery.size
                override fun setValues(ps: PreparedStatement, i: Int) {
                    val image = gallery[i]
                    ps.setLong(1, spotId)
                    ps.setInt(2, i)
                    ps.setString(3, image.imageUrl)
                    ps.setString(4, image.thumbnailUrl)
                    ps.setString(5, image.caption)
                    ps.setString(6, image.copyright.name)
                }
            },
        )
    }

    /** 운영 정보 upsert. 값이 하나도 없는 경우도 행으로 남겨 미적재와 구분한다. */
    override fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?) {
        exposedJdbcTemplate.update(
            """
            INSERT INTO spot_detail_intro (spot_id, category, business_hours, closed_days,
                                           parking, inquiry_phone, fee, pet_allowed, credit_card_accepted, fetched_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (spot_id, category) DO UPDATE SET
                business_hours = EXCLUDED.business_hours, closed_days = EXCLUDED.closed_days,
                parking = EXCLUDED.parking, inquiry_phone = EXCLUDED.inquiry_phone,
                fee = EXCLUDED.fee, pet_allowed = EXCLUDED.pet_allowed,
                credit_card_accepted = EXCLUDED.credit_card_accepted,
                fetched_at = now(), updated_at = now()
            """.trimIndent(),
            spotId, category.code, intro?.businessHours, intro?.closedDays,
            intro?.parking, intro?.inquiryPhone, intro?.fee, intro?.petAllowed, intro?.creditCardAccepted,
        )
    }

    /** 이용안내 항목 전량 교체. 삭제 후 배치 삽입한다. */
    override fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>) {
        exposedJdbcTemplate.update(
            "DELETE FROM spot_detail_info WHERE spot_id = ? AND category = ?",
            spotId,
            category.code,
        )
        if (items.isEmpty()) return
        exposedJdbcTemplate.batchUpdate(
            """
            INSERT INTO spot_detail_info (spot_id, category, display_order, name, text, fetched_at)
            VALUES (?, ?, ?, ?, ?, now())
            ON CONFLICT (spot_id, category, display_order) DO UPDATE SET
                name = EXCLUDED.name, text = EXCLUDED.text, fetched_at = now()
            """.trimIndent(),
            object : BatchPreparedStatementSetter {
                override fun getBatchSize() = items.size
                override fun setValues(ps: PreparedStatement, i: Int) {
                    val item = items[i]
                    ps.setLong(1, spotId)
                    ps.setString(2, category.code)
                    ps.setInt(3, i)
                    ps.setString(4, item.name)
                    ps.setString(5, item.text)
                }
            },
        )
    }

    /** 적재된 본문. 행이 없으면 미적재(null), 있으면 `bodyAbsent`가 부재 여부를 가른다. */
    override fun findDetail(spotId: Long): StoredDetail? = SpotDetailTable.selectAll()
        .where { SpotDetailTable.spotId eq spotId }
        .limit(1).singleOrNull()
        ?.let {
            StoredDetail(
                detail = it.toSpotDetail(),
                bodyAbsent = it[SpotDetailTable.bodyAbsent],
                fetchedAt = it[SpotDetailTable.fetchedAt].toInstant(),
            )
        }

    /** 적재된 갤러리. 행이 없으면 미적재로 간주해 null이다(이미지 없는 관광지와 구분하지 않는다). */
    override fun findGallery(spotId: Long): List<SpotImage>? = SpotDetailImageTable.selectAll()
        .where { SpotDetailImageTable.spotId eq spotId }
        .orderBy(SpotDetailImageTable.displayOrder, SortOrder.ASC)
        .map { it.toSpotImage() }
        .ifEmpty { null }

    override fun findIntro(spotId: Long, category: SpotCategory): StoredIntro? = SpotDetailIntroTable.selectAll()
        .where { (SpotDetailIntroTable.spotId eq spotId) and (SpotDetailIntroTable.category eq category.code) }
        .limit(1).singleOrNull()
        // 전 컬럼이 null인 행은 "조회했으나 값 없음"이다 — 래퍼는 non-null, 값만 null로 돌려준다.
        ?.let { StoredIntro(it.toSpotIntro().takeUnless { value -> value.isEmpty() }) }

    override fun findInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>? = SpotDetailInfoTable.selectAll()
        .where { (SpotDetailInfoTable.spotId eq spotId) and (SpotDetailInfoTable.category eq category.code) }
        .orderBy(SpotDetailInfoTable.displayOrder, SortOrder.ASC)
        .map { it.toSpotInfoItem() }
        .ifEmpty { null }
}
