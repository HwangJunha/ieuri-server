package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogUpsertPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 upsert 전용 어댑터(SPEC-0019). JPA에 이식 가능한 upsert가 없어 이 어댑터에만
 * 생 SQL이 남는다.
 */
@Component
class SpotCatalogUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotCatalogUpsertPort {

    /**
     * 번역 한 건을 쓴다. **이미 있으면 아무것도 하지 않는다** — 먼저 들어온 값이 TourAPI
     * 원문이고 그것이 우리 번역보다 우선한다(R-4).
     *
     * 좌표는 카탈로그에서 복사한다. 검색이 평면 질의라 이 사본만 보므로, 비면 그 카탈로그는
     * 검색에서 사라진다.
     */
    @Transactional
    override fun upsertTranslation(
        catalogId: Long,
        language: DomainLocale,
        title: String,
        address: String?,
    ) {
        jdbcTemplate.update(
            UPSERT_TRANSLATION_SQL,
            catalogId, language.name, title, address, catalogId,
        )
    }

    /**
     * 축제 기간을 확정한다.
     *
     * **두 문장은 한 트랜잭션 안에 있어야 한다** — 삭제가 upsert보다 먼저 커밋되면 방금 쓴
     * 기간이 지워진다.
     */
    @Transactional
    override fun refreshFestivalPeriod(catalogId: Long) {
        jdbcTemplate.update(UPSERT_FESTIVAL_PERIOD_SQL, catalogId)
        jdbcTemplate.update(DELETE_ORPHAN_FESTIVAL_SQL, catalogId)
    }

    private companion object {
        /**
         * 좌표·분류를 카탈로그에서 가져오므로 `catalog_id`가 두 번 들어간다 — 값 자리와
         * SELECT의 WHERE 자리다.
         *
         * **사본 컬럼(map_x·map_y·category)은 충돌 시에도 갱신한다.** 제목·주소와 달리 이들은
         * 카탈로그가 소유한 값의 복제본이라, 낡은 채로 두면 검색이 지도와 다른 위치를
         * 돌려주거나 분류 필터가 어긋난다. 제목·주소는 그대로 둔다 — 먼저 들어온 TourAPI
         * 원문이 우선한다(R-4).
         *
         * `map_x`·`map_y`를 갱신하면 트리거(V52)가 `location`도 함께 고친다 — SET 목록에
         * 없지만 낡은 채로 남지 않는다.
         */
        val UPSERT_TRANSLATION_SQL = """
            INSERT INTO spot_catalog_translation (catalog_id, language, title, address, map_x, map_y, category, visible)
            SELECT ?, ?, ?, ?, c.map_x, c.map_y, c.category, TRUE
            FROM spot_catalog c WHERE c.id = ?
            ON CONFLICT (catalog_id, language) DO UPDATE SET
                map_x      = EXCLUDED.map_x,
                map_y      = EXCLUDED.map_y,
                category   = EXCLUDED.category,
                -- **행이 다시 생겼다는 것은 그 언어의 원본이 돌아왔다는 뜻이다.** 감춰져
                -- 있었다면 여기서 되살린다 — 노출 갱신을 기다리지 않는다.
                visible    = TRUE,
                updated_at = now()
            WHERE spot_catalog_translation.map_x    IS DISTINCT FROM EXCLUDED.map_x
               OR spot_catalog_translation.map_y    IS DISTINCT FROM EXCLUDED.map_y
               OR spot_catalog_translation.category IS DISTINCT FROM EXCLUDED.category
               OR NOT spot_catalog_translation.visible
        """.trimIndent()

        /**
         * 축제 기간은 KO 우선이다. 기간은 언어 무관한 장소 자체의 속성이며, 실측상 다국어 축제
         * 60개 중 기간이 다른 것은 3개뿐이다.
         *
         * 원본 `spot_festival`은 `spot_id`가 단위라 그 컬럼으로 잇는다. 축제인 행이 하나도
         * 없으면 이 문장은 0행을 쓴다 — 삭제는 다음 문장이 한다.
         */
        val UPSERT_FESTIVAL_PERIOD_SQL = """
            INSERT INTO spot_catalog_festival (catalog_id, event_start_date, event_end_date)
            SELECT DISTINCT ON (s.catalog_id) s.catalog_id, f.event_start_date, f.event_end_date
            FROM spot s
            JOIN spot_festival f ON f.spot_id = s.id
            WHERE s.catalog_id = ?
            ORDER BY s.catalog_id,
                     CASE s.language WHEN 'KO' THEN 0 WHEN 'EN' THEN 1 WHEN 'JA' THEN 2 ELSE 3 END,
                     s.id
            ON CONFLICT (catalog_id) DO UPDATE SET
                event_start_date = EXCLUDED.event_start_date,
                event_end_date   = EXCLUDED.event_end_date,
                updated_at       = now()
        """.trimIndent()

        /**
         * 축제 행이 사라진 카탈로그의 기간을 지운다 — 상류 정리로 원본이 없어지면 축제가
         * 아니게 된다.
         *
         * **위의 upsert 다음에 와야 한다.** 순서가 뒤집히면 방금 쓴 기간을 지운다.
         */
        val DELETE_ORPHAN_FESTIVAL_SQL = """
            DELETE FROM spot_catalog_festival cf
            WHERE cf.catalog_id = ?
              AND NOT EXISTS (
                  SELECT 1
                  FROM spot s
                  JOIN spot_festival f ON f.spot_id = s.id
                  WHERE s.catalog_id = cf.catalog_id
              )
        """.trimIndent()
    }
}
