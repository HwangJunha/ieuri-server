package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement

/**
 * 상세 적재 전용 어댑터. 쓰기가 전부 `INSERT ... ON CONFLICT`라 이 어댑터에만 생 SQL이 남는다.
 *
 * 트랜잭션은 호출자([SpotDetailStoreAdapter])가 연다 — "삭제 후 삽입"이 한 트랜잭션에 묶여야
 * 삭제만 되고 삽입이 안 되는 상태가 생기지 않는다. 여기 다시 애노테이션을 붙이지 않는다.
 */
@Component
class SpotDetailUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) {

    /**
     * 본문 upsert. [bodyAbsent]가 두 경로의 유일한 차이다 — 부재였던 항목에 나중에 본문이 생기면
     * 이 플래그가 false로 되돌아가며 상태가 전환된다.
     *
     * 부재 행이 이미 있어도 `fetched_at`을 갱신하므로 다음 신선도 창이 새로 시작된다.
     */
    fun upsertDetail(spotId: Long, detail: SpotDetail, bodyAbsent: Boolean) {
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
            spotId, detail.title, detail.category.name, detail.thumbnailImageUrl,
            detail.overview, detail.address, detail.homepage, detail.tel, detail.copyright.name,
            bodyAbsent,
        )
    }

    /**
     * 갤러리 전량 교체. upstream이 이미지를 지우거나 순서를 바꿨을 때 낡은 행이 남지 않는다.
     *
     * 삽입을 `ON CONFLICT`로 두는 이유는 같은 항목을 두 워커가 동시에 교체할 때 유니크 제약 위반으로
     * 실패하는 대신 덮어쓰게 하기 위해서다.
     */
    fun replaceGallery(spotId: Long, gallery: List<SpotImage>) {
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
    fun upsertIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?) {
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
            spotId, category.name, intro?.businessHours, intro?.closedDays,
            intro?.parking, intro?.inquiryPhone, intro?.fee, intro?.petAllowed, intro?.creditCardAccepted,
        )
    }

    /** 이용안내 항목 전량 교체. 갤러리와 같은 이유로 삭제 후 배치 삽입한다. */
    fun replaceInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>) {
        jdbcTemplate.update(
            "DELETE FROM spot_detail_info WHERE spot_id = ? AND category = ?",
            spotId, category.name,
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
                    ps.setString(2, category.name)
                    ps.setInt(3, i)
                    ps.setString(4, item.name)
                    ps.setString(5, item.text)
                }
            },
        )
    }
}
