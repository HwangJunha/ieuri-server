package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.TranslationSource
import org.around.ieuri.tourism.domain.vo.UnresolvedReason
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 번역·미해결 upsert 전용 어댑터. JPA에 이식 가능한 upsert가 없어 이 어댑터에만
 * 생 SQL이 남는다(`merge` 의미로는 select-then-insert 경합이 생긴다).
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
    fun upsertTranslation(catalogId: Long, language: DomainLocale, title: String, source: TranslationSource) {
        jdbcTemplate.update(
            UPSERT_TRANSLATION_SQL,
            catalogId, language.name, title, source.name,
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
        candidateSpotIds: List<Long>,
    ) {
        jdbcTemplate.update(UPSERT_UNRESOLVED_SQL) { ps ->
            ps.setLong(1, spotId)
            ps.setString(2, reason.name)
            ps.setString(3, extractedKey)
            // BIGINT[]는 드라이버가 만든 java.sql.Array로 넘겨야 한다 — Kotlin 배열을 그대로
            // setObject하면 드라이버가 타입을 못 정해 실패한다.
            ps.setArray(4, ps.connection.createArrayOf("bigint", candidateSpotIds.toTypedArray()))
        }
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
            INSERT INTO spot_catalog_translation (catalog_id, language, title, source)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (catalog_id, language) DO UPDATE SET
                title      = EXCLUDED.title,
                source     = EXCLUDED.source,
                updated_at = now()
            WHERE EXCLUDED.source = 'OFFICIAL'
        """.trimIndent()

        val UPSERT_UNRESOLVED_SQL = """
            INSERT INTO spot_catalog_unresolved (spot_id, reason, extracted_key, candidate_spot_ids)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (spot_id) DO UPDATE SET
                reason             = EXCLUDED.reason,
                extracted_key      = EXCLUDED.extracted_key,
                candidate_spot_ids = EXCLUDED.candidate_spot_ids,
                detected_at        = now()
        """.trimIndent()
    }
}
