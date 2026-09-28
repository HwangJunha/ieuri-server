package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailUpsertPort
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.PreparedStatement

/**
 * 상세 적재 전용 어댑터. 쓰기가 전부 `INSERT ... ON CONFLICT`라 생 SQL이다.
 * - 트랜잭션은 메서드마다 연다 — 전량 교체의 삭제와 삽입이 함께 커밋된다.
 */
@Component
class SpotDetailUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotDetailUpsertPort {

    @Transactional
    override fun saveDetail(spotId: Long, detail: SpotDetail) {
        upsertDetail(spotId, detail, bodyAbsent = false)
    }

    /** 본문 부재 적재. NOT NULL 컬럼은 [fallback] 값으로 채우고 overview·homepage·tel은 null로 남긴다. */
    @Transactional
    override fun saveDetailAbsence(spotId: Long, fallback: SpotDetail) {
        upsertDetail(spotId, fallback, bodyAbsent = true)
    }

    /**
     * 본문 upsert. [bodyAbsent]만 두 경로가 다르며, 부재였던 항목에 본문이 생기면 false로 되돌아간다.
     * - 항상 `fetched_at`을 갱신해 신선도 창을 새로 시작한다.
     * - private 헬퍼다 — `@Transactional` 메서드끼리 자기호출하면 프록시를 우회한다.
     */
    private fun upsertDetail(spotId: Long, detail: SpotDetail, bodyAbsent: Boolean) {
        jdbcTemplate.update(
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
    @Transactional
    override fun saveGallery(spotId: Long, gallery: List<SpotImage>) {
        jdbcTemplate.update("DELETE FROM spot_detail_image WHERE spot_id = ?", spotId)
        if (gallery.isEmpty()) return
        jdbcTemplate.batchUpdate(
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
    @Transactional
    override fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?) {
        jdbcTemplate.update(
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
    @Transactional
    override fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>) {
        jdbcTemplate.update(
            "DELETE FROM spot_detail_info WHERE spot_id = ? AND category = ?",
            spotId,
            category.code,
        )
        if (items.isEmpty()) return
        jdbcTemplate.batchUpdate(
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
}
