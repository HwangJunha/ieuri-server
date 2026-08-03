package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.application.port.out.persistence.StoredIntro
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
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
 * **삽입은 `VALUES`가 아니라 `SELECT ... FROM spot`이다**(V19). 자식이 부모의 surrogate id를
 * 참조하게 되면서, 적재 시점에 그 id를 가져와야 하기 때문이다. 부수 효과로 **부모 `spot` 행이 없으면
 * 0행이 삽입되어 조용히 건너뛴다** — FK 위반으로 조회 요청 전체를 실패시키지 않는다. 상세는 조회된
 * 것만 누적되는 캐시이므로, 부모가 아직 적재되지 않은 항목은 다음 조회 때 다시 시도되면 그만이다.
 */
@Component
class SpotDetailJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotDetailStorePort {

    // ---------- 본문 ----------

    @Transactional(readOnly = true)
    override fun findDetail(contentId: String, locale: DomainLocale): SpotDetail? =
        jdbcTemplate.query(
            """
            SELECT title, category, thumbnail_image_url, overview, address, homepage, tel, copyright
            FROM spot_detail WHERE content_id = ? AND language = ?
            """.trimIndent(),
            { rs, _ ->
                SpotDetail(
                    id = contentId,
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
            contentId, locale.name,
        ).firstOrNull()

    @Transactional
    override fun saveDetail(contentId: String, locale: DomainLocale, detail: SpotDetail) {
        jdbcTemplate.update(
            """
            INSERT INTO spot_detail (spot_id, content_id, language, title, category, thumbnail_image_url,
                                     overview, address, homepage, tel, copyright, fetched_at)
            SELECT s.id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now()
            FROM spot s WHERE s.content_id = ? AND s.language = ?
            ON CONFLICT (content_id, language) DO UPDATE SET
                title = EXCLUDED.title, category = EXCLUDED.category,
                thumbnail_image_url = EXCLUDED.thumbnail_image_url, overview = EXCLUDED.overview,
                address = EXCLUDED.address, homepage = EXCLUDED.homepage, tel = EXCLUDED.tel,
                copyright = EXCLUDED.copyright, fetched_at = now(), updated_at = now()
            """.trimIndent(),
            contentId, locale.name, detail.title, detail.category.name, detail.thumbnailImageUrl,
            detail.overview, detail.address, detail.homepage, detail.tel, detail.copyright.name,
            contentId, locale.name,
        )
    }

    // ---------- 갤러리 ----------

    @Transactional(readOnly = true)
    override fun findGallery(contentId: String, locale: DomainLocale): List<SpotImage>? {
        val rows = jdbcTemplate.query(
            """
            SELECT image_url, thumbnail_url, caption, copyright
            FROM spot_detail_image WHERE content_id = ? AND language = ? ORDER BY display_order
            """.trimIndent(),
            { rs, _ ->
                SpotImage(
                    imageUrl = rs.getString("image_url"),
                    thumbnailUrl = rs.getString("thumbnail_url"),
                    caption = rs.getString("caption"),
                    copyright = CopyrightType.valueOf(rs.getString("copyright")),
                )
            },
            contentId, locale.name,
        )
        // 행이 없으면 "미적재"인지 "이미지가 없는 관광지"인지 알 수 없다. 후자는 빈 목록을 적재하지
        // 못하므로(행이 없으니), 미적재로 간주해 upstream에 한 번 더 묻는다. 빈 갤러리는 드물고
        // 비용도 1콜이라, 빈 목록 전용 마커 행을 두는 것보다 이 편이 단순하다.
        return rows.ifEmpty { null }
    }

    @Transactional
    override fun saveGallery(contentId: String, locale: DomainLocale, gallery: List<SpotImage>) {
        // 전량 교체. upstream이 이미지를 지우거나 순서를 바꿨을 때 낡은 행이 남지 않는다.
        // 삽입은 ON CONFLICT로 둔다 — 같은 항목을 두 워커가 동시에 교체할 때 유니크 제약 위반으로
        // 실패하는 대신 덮어쓰게 한다(AC-41). 실패해도 자기치유되지만 무의미한 예외 로그를 남긴다.
        // **삭제도 부모 존재를 조건으로 건다**(V19). 아래 삽입이 `SELECT ... FROM spot`이라 부모가 없으면
        // 0행이 되는데, 삭제만 무조건 실행하면 기존 갤러리가 지워지고 아무것도 채워지지 않는다.
        // V19 이전에는 FK 위반이 같은 트랜잭션을 롤백시켜 이 상태가 생길 수 없었다 — 둘이 함께
        // 건너뛰어야 그 성질이 유지된다.
        jdbcTemplate.update(
            """
            DELETE FROM spot_detail_image
            WHERE content_id = ? AND language = ?
              AND EXISTS (SELECT 1 FROM spot s WHERE s.content_id = ? AND s.language = ?)
            """.trimIndent(),
            contentId, locale.name, contentId, locale.name,
        )
        if (gallery.isEmpty()) return
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO spot_detail_image (spot_id, content_id, language, display_order, image_url,
                                           thumbnail_url, caption, copyright, fetched_at)
            SELECT s.id, ?, ?, ?, ?, ?, ?, ?, now()
            FROM spot s WHERE s.content_id = ? AND s.language = ?
            ON CONFLICT (content_id, language, display_order) DO UPDATE SET
                image_url = EXCLUDED.image_url, thumbnail_url = EXCLUDED.thumbnail_url,
                caption = EXCLUDED.caption, copyright = EXCLUDED.copyright, fetched_at = now()
            """.trimIndent(),
            object : BatchPreparedStatementSetter {
                override fun getBatchSize() = gallery.size
                override fun setValues(ps: PreparedStatement, i: Int) {
                    val image = gallery[i]
                    ps.setString(1, contentId)
                    ps.setString(2, locale.name)
                    ps.setInt(3, i)
                    ps.setString(4, image.imageUrl)
                    ps.setString(5, image.thumbnailUrl)
                    ps.setString(6, image.caption)
                    ps.setString(7, image.copyright.name)
                    // 부모 조회용(V19).
                    ps.setString(8, contentId)
                    ps.setString(9, locale.name)
                }
            },
        )
    }

    // ---------- 운영 정보 ----------

    @Transactional(readOnly = true)
    override fun findIntro(contentId: String, locale: DomainLocale, category: SpotCategory): StoredIntro? =
        jdbcTemplate.query(
            """
            SELECT business_hours, closed_days, parking, inquiry_phone, fee, pet_allowed, credit_card_accepted
            FROM spot_detail_intro WHERE content_id = ? AND language = ? AND category = ?
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
            contentId, locale.name, category.name,
        ).firstOrNull()

    @Transactional
    override fun saveIntro(contentId: String, locale: DomainLocale, category: SpotCategory, intro: SpotIntro?) {
        jdbcTemplate.update(
            """
            INSERT INTO spot_detail_intro (spot_id, content_id, language, category, business_hours, closed_days,
                                           parking, inquiry_phone, fee, pet_allowed, credit_card_accepted, fetched_at)
            SELECT s.id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now()
            FROM spot s WHERE s.content_id = ? AND s.language = ?
            ON CONFLICT (content_id, language, category) DO UPDATE SET
                business_hours = EXCLUDED.business_hours, closed_days = EXCLUDED.closed_days,
                parking = EXCLUDED.parking, inquiry_phone = EXCLUDED.inquiry_phone,
                fee = EXCLUDED.fee, pet_allowed = EXCLUDED.pet_allowed,
                credit_card_accepted = EXCLUDED.credit_card_accepted,
                fetched_at = now(), updated_at = now()
            """.trimIndent(),
            contentId, locale.name, category.name, intro?.businessHours, intro?.closedDays,
            intro?.parking, intro?.inquiryPhone, intro?.fee, intro?.petAllowed, intro?.creditCardAccepted,
            contentId, locale.name,
        )
    }

    // ---------- 이용안내 ----------

    @Transactional(readOnly = true)
    override fun findInfoItems(
        contentId: String,
        locale: DomainLocale,
        category: SpotCategory,
    ): List<SpotInfoItem>? {
        val rows = jdbcTemplate.query(
            """
            SELECT name, text FROM spot_detail_info
            WHERE content_id = ? AND language = ? AND category = ? ORDER BY display_order
            """.trimIndent(),
            { rs, _ -> SpotInfoItem(name = rs.getString("name"), text = rs.getString("text")) },
            contentId, locale.name, category.name,
        )
        return rows.ifEmpty { null }
    }

    @Transactional
    override fun saveInfoItems(
        contentId: String,
        locale: DomainLocale,
        category: SpotCategory,
        items: List<SpotInfoItem>,
    ) {
        // 갤러리와 같은 이유로 삭제도 부모 존재를 조건으로 건다(V19).
        jdbcTemplate.update(
            """
            DELETE FROM spot_detail_info
            WHERE content_id = ? AND language = ? AND category = ?
              AND EXISTS (SELECT 1 FROM spot s WHERE s.content_id = ? AND s.language = ?)
            """.trimIndent(),
            contentId, locale.name, category.name, contentId, locale.name,
        )
        if (items.isEmpty()) return
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO spot_detail_info (spot_id, content_id, language, category, display_order, name, text, fetched_at)
            SELECT s.id, ?, ?, ?, ?, ?, ?, now()
            FROM spot s WHERE s.content_id = ? AND s.language = ?
            ON CONFLICT (content_id, language, category, display_order) DO UPDATE SET
                name = EXCLUDED.name, text = EXCLUDED.text, fetched_at = now()
            """.trimIndent(),
            object : BatchPreparedStatementSetter {
                override fun getBatchSize() = items.size
                override fun setValues(ps: PreparedStatement, i: Int) {
                    val item = items[i]
                    ps.setString(1, contentId)
                    ps.setString(2, locale.name)
                    ps.setString(3, category.name)
                    ps.setInt(4, i)
                    ps.setString(5, item.name)
                    ps.setString(6, item.text)
                    // 부모 조회용(V19).
                    ps.setString(7, contentId)
                    ps.setString(8, locale.name)
                }
            },
        )
    }
}
