package org.around.ieuri.community.infrastructure.adapter.out.persistence

import org.around.ieuri.community.application.port.out.persistence.CatalogLanguageEntry
import org.around.ieuri.community.application.port.out.persistence.SpotCatalogLanguagePort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 카탈로그-언어 사본 적재 전용 raw SQL 어댑터(SPEC-0029 R-15, upsert). */
@Component
class SpotCatalogLanguageJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotCatalogLanguagePort {

    /**
     * 표 전체를 [entries]로 맞춘다(차집합만 움직인다).
     * - 주의: [entries]가 비면 표를 전부 지운다.
     */
    @Transactional
    override fun replaceAll(entries: List<CatalogLanguageEntry>): Int {
        if (entries.isEmpty()) {
            jdbcTemplate.update(DELETE_ALL_SQL)
            return 0
        }

        // 비우고 다시 넣지 않는다 — 중간에 표가 비면 동시 조회가 글을 놓친다.
        // 정본을 임시 테이블에 싣고 차집합만 움직인다.
        jdbcTemplate.execute(CREATE_TEMP_SQL)
        jdbcTemplate.batchUpdate(
            INSERT_TEMP_SQL,
            entries.map { arrayOf<Any>(it.catalogId, it.language.name) },
        )
        jdbcTemplate.update(DELETE_STALE_SQL)
        jdbcTemplate.update(INSERT_NEW_SQL)

        return entries.size
    }

    private companion object {
        /** 정본을 담을 임시 테이블. `ON COMMIT DROP` — 트랜잭션 안에서만 쓸 수 있다. */
        const val CREATE_TEMP_SQL = """
            CREATE TEMP TABLE resolved_catalog_language (
                catalog_id BIGINT NOT NULL,
                language VARCHAR(2) NOT NULL,
                PRIMARY KEY (catalog_id, language)
            ) ON COMMIT DROP
        """

        const val INSERT_TEMP_SQL =
            "INSERT INTO resolved_catalog_language (catalog_id, language) VALUES (?, ?) ON CONFLICT DO NOTHING"

        /** 정본에 없는 사본 행을 지운다. */
        const val DELETE_STALE_SQL = """
            DELETE FROM community.community_spot_catalog_language c
            WHERE NOT EXISTS (
                SELECT 1 FROM resolved_catalog_language r
                WHERE r.catalog_id = c.catalog_id AND r.language = c.language
            )
        """

        /** 사본에 없는 정본 행을 넣는다. */
        const val INSERT_NEW_SQL = """
            INSERT INTO community.community_spot_catalog_language (catalog_id, language)
            SELECT r.catalog_id, r.language FROM resolved_catalog_language r
            ON CONFLICT (catalog_id, language) DO NOTHING
        """

        /** 정본이 비었을 때 표를 통째로 비운다. */
        const val DELETE_ALL_SQL = "DELETE FROM community.community_spot_catalog_language"
    }
}
