package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.application.port.out.persistence.StoredIntro
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.CopyrightType
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
 * 상세 적재 포트 구현(SPEC-0004). 도메인 ↔ 테이블 매핑을 여기서 담당한다.
 *
 * **JPA가 아니라 JdbcTemplate을 쓴다.** 이 테이블들은 애그리거트가 아니라 캐시 계층이고, 쓰기가
 * 전부 upsert(`ON CONFLICT`)라 dirty-checking이 필요 없다. 갤러리·이용안내는 "전량 교체"라
 * 삭제 후 배치 삽입이 가장 단순하다 — JPA로 하면 orphanRemoval·flush 순서를 신경 써야 한다.
 *
 * **각 save는 단일 트랜잭션이다**(AC-44). 종료 시점에 절반만 쓰인 상태가 남지 않아야 하며,
 * 갤러리처럼 "삭제 후 삽입"하는 경우 특히 중요하다 — 삭제만 되고 삽입이 안 되면 데이터가 사라진다.
 *
 * **모든 키가 `spot_id` 하나다**(V20). 자연키(content_id·language)가 자식에서 사라져 조회·적재가
 * 단일 컬럼으로 이뤄지며, 호출자가 이미 유효한 `spot.id`를 들고 오므로 V19의 `SELECT ... FROM spot`
 * 부모 조회 트릭도 필요 없다. 존재하지 않는 id로는 애초에 여기까지 도달하지 않는다.
 */
@Component
class SpotDetailJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotDetailStorePort {

    // ---------- 본문 ----------

    @Transactional(readOnly = true)
    override fun findDetail(spotId: Long): SpotDetail? =
        jdbcTemplate.query(
            """
            SELECT title, category, thumbnail_image_url, overview, address, homepage, tel, copyright
            FROM spot_detail WHERE spot_id = ?
            """.trimIndent(),
            { rs, _ ->
                SpotDetail(
                    title = rs.getString("title"),
                    category = SpotCategory.valueOf(rs.getString("category")),
                    thumbnailImageUrl = rs.getString("thumbnail_image_url"),
                    overview = rs.getString("overview"),
                    address = rs.getString("address"),
                    homepage = rs.getString("homepage"),
                    tel = rs.getString("tel"),
                    copyright = CopyrightType.valueOf(rs.getString("copyright")),
                    // likeCount는 적재 대상이 아니다(AC-38) — 서비스가 실시간 집계로 얹는다.
                )
            },
            spotId,
        ).firstOrNull()

    @Transactional
    override fun saveDetail(spotId: Long, detail: SpotDetail) {
        jdbcTemplate.update(
            """
            INSERT INTO spot_detail (spot_id, title, category, thumbnail_image_url,
                                     overview, address, homepage, tel, copyright, fetched_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (spot_id) DO UPDATE SET
                title = EXCLUDED.title, category = EXCLUDED.category,
                thumbnail_image_url = EXCLUDED.thumbnail_image_url, overview = EXCLUDED.overview,
                address = EXCLUDED.address, homepage = EXCLUDED.homepage, tel = EXCLUDED.tel,
                copyright = EXCLUDED.copyright, fetched_at = now(), updated_at = now()
            """.trimIndent(),
            spotId, detail.title, detail.category.name, detail.thumbnailImageUrl,
            detail.overview, detail.address, detail.homepage, detail.tel, detail.copyright.name,
        )
    }

    // ---------- 갤러리 ----------

    @Transactional(readOnly = true)
    override fun findGallery(spotId: Long): List<SpotImage>? {
        val rows = jdbcTemplate.query(
            """
            SELECT image_url, thumbnail_url, caption, copyright
            FROM spot_detail_image WHERE spot_id = ? ORDER BY display_order
            """.trimIndent(),
            { rs, _ ->
                SpotImage(
                    imageUrl = rs.getString("image_url"),
                    thumbnailUrl = rs.getString("thumbnail_url"),
                    caption = rs.getString("caption"),
                    copyright = CopyrightType.valueOf(rs.getString("copyright")),
                )
            },
            spotId,
        )
        // 행이 없으면 "미적재"인지 "이미지가 없는 관광지"인지 알 수 없다. 후자는 빈 목록을 적재하지
        // 못하므로(행이 없으니), 미적재로 간주해 upstream에 한 번 더 묻는다. 빈 갤러리는 드물고
        // 비용도 1콜이라, 빈 목록 전용 마커 행을 두는 것보다 이 편이 단순하다.
        return rows.ifEmpty { null }
    }

    @Transactional
    override fun saveGallery(spotId: Long, gallery: List<SpotImage>) {
        // 전량 교체. upstream이 이미지를 지우거나 순서를 바꿨을 때 낡은 행이 남지 않는다.
        // 삽입은 ON CONFLICT로 둔다 — 같은 항목을 두 워커가 동시에 교체할 때 유니크 제약 위반으로
        // 실패하는 대신 덮어쓰게 한다(AC-41). 실패해도 자기치유되지만 무의미한 예외 로그를 남긴다.
        jdbcTemplate.update(
            """
            DELETE FROM spot_detail_image WHERE spot_id = ?
            """.trimIndent(),
            spotId,
        )
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

    // ---------- 운영 정보 ----------

    @Transactional(readOnly = true)
    override fun findIntro(spotId: Long, category: SpotCategory): StoredIntro? =
        jdbcTemplate.query(
            """
            SELECT business_hours, closed_days, parking, inquiry_phone, fee, pet_allowed, credit_card_accepted
            FROM spot_detail_intro WHERE spot_id = ? AND category = ?
            """.trimIndent(),
            { rs, _ ->
                val intro = SpotIntro(
                    businessHours = rs.getString("business_hours"),
                    closedDays = rs.getString("closed_days"),
                    parking = rs.getString("parking"),
                    inquiryPhone = rs.getString("inquiry_phone"),
                    fee = rs.getString("fee"),
                    petAllowed = rs.getString("pet_allowed"),
                    creditCardAccepted = rs.getString("credit_card_accepted"),
                )
                // 전 컬럼이 null이면 "조회했으나 값 없음"으로 적재된 행이다 — 래퍼는 non-null,
                // 값만 null로 돌려줘야 다음 조회가 미스로 오판하지 않는다.
                StoredIntro(intro.takeUnless { it.isEmpty() })
            },
            spotId, category.name,
        ).firstOrNull()

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
            spotId, category.name, intro?.businessHours, intro?.closedDays,
            intro?.parking, intro?.inquiryPhone, intro?.fee, intro?.petAllowed, intro?.creditCardAccepted,
        )
    }

    // ---------- 이용안내 ----------

    @Transactional(readOnly = true)
    override fun findInfoItems(
        spotId: Long,
        category: SpotCategory,
    ): List<SpotInfoItem>? {
        val rows = jdbcTemplate.query(
            """
            SELECT name, text FROM spot_detail_info
            WHERE spot_id = ? AND category = ? ORDER BY display_order
            """.trimIndent(),
            { rs, _ -> SpotInfoItem(name = rs.getString("name"), text = rs.getString("text")) },
            spotId, category.name,
        )
        return rows.ifEmpty { null }
    }

    @Transactional
    override fun saveInfoItems(
        spotId: Long,
        category: SpotCategory,
        items: List<SpotInfoItem>,
    ) {
        // 갤러리와 같은 이유로 삭제도 부모 존재를 조건으로 건다(V19).
        jdbcTemplate.update(
            """
            DELETE FROM spot_detail_info WHERE spot_id = ? AND category = ?
            """.trimIndent(),
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
