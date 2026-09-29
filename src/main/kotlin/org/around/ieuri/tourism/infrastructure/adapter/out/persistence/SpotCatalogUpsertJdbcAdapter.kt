package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogUpsertPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 카탈로그 upsert 전용 어댑터(SPEC-0019). JPA에 이식 가능한 upsert가 없어 생 SQL이다. */
@Component
class SpotCatalogUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotCatalogUpsertPort {

    /**
     * 번역 한 건을 쓴다. 제목·주소는 이미 있으면 덮지 않는다 — 먼저 들어온 TourAPI 원문이 우선한다(R-4).
     * - 좌표·분류는 카탈로그에서 복사한다. 비면 그 카탈로그는 검색에서 사라진다.
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
            catalogId,
            language.name,
            title,
            address,
            catalogId,
        )
    }

    /** 축제 기간을 확정한다. 두 문장은 한 트랜잭션이어야 한다 — 삭제가 먼저 커밋되면 방금 쓴 기간이 지워진다. */
    @Transactional
    override fun refreshFestivalPeriod(catalogId: Long) {
        jdbcTemplate.update(UPSERT_FESTIVAL_PERIOD_SQL, catalogId)
        jdbcTemplate.update(DELETE_ORPHAN_FESTIVAL_SQL, catalogId)
    }

    private companion object {
        /**
         * 번역 upsert. `catalog_id`가 두 번(값 자리·SELECT의 WHERE) 들어간다.
         * - 사본 컬럼(map_x·map_y·category)은 충돌 시에도 갱신하고, 제목·주소는 그대로 둔다(R-4).
         * - `map_x`·`map_y`를 갱신하면 트리거(V52)가 `location`도 고친다.
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
         * 축제 기간 upsert(KO 우선). 원본 `spot_festival`과는 `spot_id`로 잇는다.
         * - 축제 행이 없으면 0행을 쓴다 — 삭제는 다음 문장이 한다.
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

        /** 축제 행이 사라진 카탈로그의 기간을 지운다. 위의 upsert 다음에 와야 한다 — 뒤집히면 방금 쓴 기간을 지운다. */
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
