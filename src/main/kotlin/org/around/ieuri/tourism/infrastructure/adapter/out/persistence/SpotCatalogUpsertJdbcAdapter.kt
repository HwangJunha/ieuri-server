package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.TranslationSource
import org.around.ieuri.tourism.domain.vo.UnresolvedReason
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 번역·미해결 upsert와 표준값 재확정 전용 어댑터.
 *
 * JPA에 이식 가능한 upsert가 없어(`merge` 의미로는 select-then-insert 경합이 생긴다), 그리고
 * 표준값 확정이 원본을 훑는 집계 UPDATE라 QueryDSL로 표현되지 않아 이 어댑터에만 생 SQL이 남는다.
 */
@Component
class SpotCatalogUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) {

    /**
     * 번역 한 건. **덮어쓰기 여부가 출처에 달렸다**(R-4·R-5):
     *  - `OFFICIAL`이 들어오면 기존 값을 덮는다 — TourAPI가 그 언어를 새로 발급한 것이며
     *    공식 표기가 우리 번역보다 우선한다.
     *  - `MACHINE`이 들어오면 기존 값을 남긴다 — 이미 있는 제목을 기계 번역으로 밀어낼 수 없다.
     *
     * 이 분기를 SQL의 `WHERE`로 표현해 두면 호출자가 순서를 신경 쓰지 않아도 된다.
     */
    @Transactional
    fun upsertTranslation(
        catalogId: Long,
        language: DomainLocale,
        title: String,
        address: String?,
        source: TranslationSource,
    ) {
        jdbcTemplate.update(
            UPSERT_TRANSLATION_SQL,
            catalogId, language.name, title, address, source.name, catalogId,
        )
    }

    /**
     * 미해결 기록. 같은 spot을 다시 기록하면 사유·후보만 갱신되고 행이 늘지 않는다 —
     * 배치를 재실행해도 CSV 줄 수가 부풀지 않는다.
     */
    @Transactional
    fun upsertUnresolved(
        spotId: Long,
        reason: UnresolvedReason,
        extractedKey: String?,
        candidateCatalogIds: List<Long>,
    ) {
        jdbcTemplate.update(UPSERT_UNRESOLVED_SQL) { ps ->
            ps.setLong(1, spotId)
            ps.setString(2, reason.name)
            ps.setString(3, extractedKey)
            // BIGINT[]는 드라이버가 만든 java.sql.Array로 넘겨야 한다 — Kotlin 배열을 그대로
            // setObject하면 드라이버가 타입을 못 정해 실패한다.
            ps.setArray(4, ps.connection.createArrayOf("bigint", candidateCatalogIds.toTypedArray()))
        }
    }

    /**
     * 카탈로그의 표준값을 배정된 원본에서 다시 확정한다 — 좌표·썸네일·분류·저작권·축제기간.
     *
     * **다섯 문장으로 나뉜다.** 좌표는 유효성 필터가 나머지와 달라 채택 대상 집합 자체가 다르고,
     * 축제는 다른 테이블에 있으며 "없으면 삭제"라는 반대 동작이 필요하다. 한 문장으로 합치려면
     * 없는 값을 만들어 내야 한다.
     *
     * 배정할 때마다 부르는 이유는 새로 붙은 행이 기존보다 나은 값을 가질 수 있기 때문이다 —
     * KO 좌표가 (0,0)인데 뒤늦게 붙은 EN이 유효한 경우가 실제로 있다(간절곶).
     */
    @Transactional
    fun refreshStandardValues(catalogId: Long) {
        jdbcTemplate.update(REFRESH_COORDINATES_SQL, catalogId, catalogId)
        jdbcTemplate.update(REFRESH_ATTRIBUTES_SQL, catalogId, catalogId)
        // 번역 행의 좌표 사본을 카탈로그와 맞춘다. **이 문장이 없으면 검색이 조용히 낡는다** —
        // 검색은 평면 질의라 `spot_catalog_translation.location`만 보고, 그 값이 비어 있으면
        // ST_DWithin이 거짓이라 그 카탈로그는 검색에서 영영 사라진다.
        jdbcTemplate.update(REFRESH_TRANSLATION_COORDINATES_SQL, catalogId)
        jdbcTemplate.update(UPSERT_FESTIVAL_PERIOD_SQL, catalogId)
        jdbcTemplate.update(DELETE_ORPHAN_FESTIVAL_SQL, catalogId)
    }

    private companion object {
        /**
         * **`WHERE`가 없으면 MACHINE이 OFFICIAL을 덮어 공식 표기를 잃는다.**
         *
         * 갱신은 들어온 값이 `OFFICIAL`일 때만 일어난다. 즉 기존 행이 있는 상태에서 `MACHINE`이
         * 들어오면 **기존 값이 무엇이든 아무것도 하지 않는다** — 이미 제목이 있는 쌍은 번역
         * 대상이 아니므로(R-4) 이 경로 자체가 정상 동작에서는 오지 않는다.
         */
        val UPSERT_TRANSLATION_SQL = """
            INSERT INTO spot_catalog_translation (catalog_id, language, title, address, source, map_x, map_y)
            SELECT ?, ?, ?, ?, ?, c.map_x, c.map_y
            FROM spot_catalog c WHERE c.id = ?
            ON CONFLICT (catalog_id, language) DO UPDATE SET
                title      = EXCLUDED.title,
                address    = EXCLUDED.address,
                source     = EXCLUDED.source,
                map_x      = EXCLUDED.map_x,
                map_y      = EXCLUDED.map_y,
                updated_at = now()
            WHERE EXCLUDED.source = 'OFFICIAL'
        """.trimIndent()

        /**
         * 번역 행의 좌표는 카탈로그 좌표의 **사본이다**(BM25가 조인을 못 받아 복사해 둔 값).
         * 원본이 바뀌면 사본도 따라가야 한다 — 어긋나면 검색이 지도와 다른 위치를 돌려준다.
         */
        val REFRESH_TRANSLATION_COORDINATES_SQL = """
            UPDATE spot_catalog_translation t
            SET map_x = c.map_x, map_y = c.map_y, updated_at = now()
            FROM spot_catalog c
            WHERE c.id = t.catalog_id
              AND t.catalog_id = ?
              AND (t.map_x IS DISTINCT FROM c.map_x OR t.map_y IS DISTINCT FROM c.map_y)
        """.trimIndent()

        val UPSERT_UNRESOLVED_SQL = """
            INSERT INTO spot_catalog_unresolved (spot_id, reason, extracted_key, candidate_catalog_ids)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (spot_id) DO UPDATE SET
                reason                = EXCLUDED.reason,
                extracted_key         = EXCLUDED.extracted_key,
                candidate_catalog_ids = EXCLUDED.candidate_catalog_ids,
                detected_at           = now()
        """.trimIndent()

        /**
         * 채택 순서 KO → EN → JA → ZH. **목록의 순서가 곧 우선순위이며** KO가 원본 언어이고
         * 가장 많으며(56,425행) 번역 기준이기 때문이다.
         *
         * `DISTINCT ON`이 이 순서의 첫 행만 남긴다 — 언어당 여러 행이 걸릴 수 있어
         * `id`로 한 번 더 갈라야 실행마다 같은 행이 뽑힌다.
         */
        const val LANGUAGE_RANK = """
            CASE s.language WHEN 'KO' THEN 0 WHEN 'EN' THEN 1 WHEN 'JA' THEN 2 ELSE 3 END
        """

        /**
         * 좌표만 **유효한 것 중에서** 고른다 — 원본에 (0,0) 135건과 한국 밖 182건이 있어
         * KO 행의 좌표가 무효일 수 있고, 그때는 EN이 구제한다(간절곶).
         *
         * **유효 좌표가 하나도 없으면 NULL로 남는다** — 서브쿼리가 빈 결과라 LEFT JOIN이 NULL을
         * 준다. 그 카탈로그는 지도·반경 조회에서 빠진다. 트리거가 location을 함께 갱신한다.
         */
        val REFRESH_COORDINATES_SQL = """
            UPDATE spot_catalog c
            SET map_x      = picked.map_x,
                map_y      = picked.map_y,
                updated_at = now()
            FROM (SELECT ?::bigint AS catalog_id) target
            LEFT JOIN LATERAL (
                SELECT DISTINCT ON (s.catalog_id) s.map_x, s.map_y
                FROM spot s
                WHERE s.catalog_id = ?
                  AND NOT (s.map_x = 0 AND s.map_y = 0)
                  AND s.map_x BETWEEN 124 AND 132
                  AND s.map_y BETWEEN 33 AND 39
                ORDER BY s.catalog_id, $LANGUAGE_RANK, s.id
            ) picked ON TRUE
            WHERE c.id = target.catalog_id
        """.trimIndent()

        /**
         * 썸네일·분류·저작권은 **유효성 필터 없이** 배정된 행 중 KO 우선으로 고른다 —
         * 좌표와 달리 "무효값"을 판별할 기준이 없다.
         *
         * 분류를 KO 우선으로 두는 이유는 외국어가 TOURIST_SPOT으로 뭉개는 경향이 있기
         * 때문이다(실측 불일치 88건 중 65%).
         */
        val REFRESH_ATTRIBUTES_SQL = """
            UPDATE spot_catalog c
            SET thumbnail_image_url = picked.thumbnail_image_url,
                category            = picked.category,
                copyright           = picked.copyright,
                updated_at          = now()
            FROM (SELECT ?::bigint AS catalog_id) target
            LEFT JOIN LATERAL (
                SELECT DISTINCT ON (s.catalog_id) s.thumbnail_image_url, s.category, s.copyright
                FROM spot s
                WHERE s.catalog_id = ?
                ORDER BY s.catalog_id, $LANGUAGE_RANK, s.id
            ) picked ON TRUE
            WHERE c.id = target.catalog_id
        """.trimIndent()

        /**
         * 축제 기간도 KO 우선이다. 기간은 언어 무관한 장소 자체의 속성이며, 실측상 다국어 축제
         * 60개 중 기간이 다른 것은 3개뿐이다.
         *
         * 원본 `spot_festival`은 V20 이후 `spot_id`가 단위라 그 컬럼으로 잇는다.
         * 축제인 행이 하나도 없으면 이 문장은 0행을 쓴다 — 삭제는 다음 문장이 한다.
         */
        val UPSERT_FESTIVAL_PERIOD_SQL = """
            INSERT INTO spot_catalog_festival (catalog_id, event_start_date, event_end_date)
            SELECT DISTINCT ON (s.catalog_id) s.catalog_id, f.event_start_date, f.event_end_date
            FROM spot s
            JOIN spot_festival f ON f.spot_id = s.id
            WHERE s.catalog_id = ?
            ORDER BY s.catalog_id, $LANGUAGE_RANK, s.id
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
