package org.around.ieuri.community.infrastructure.adapter.out.persistence

import org.around.ieuri.community.application.port.out.persistence.CatalogLanguageEntry
import org.around.ieuri.community.application.port.out.persistence.SpotCatalogLanguagePort
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그-언어 사본 적재 전용 어댑터(SPEC-0029 R-15).
 *
 * **생 SQL을 쓰는 이유는 upsert다** — JPA에 이식 가능한 upsert가 없고, `merge` 의미로 흉내
 * 내면 select-then-insert 경합이 생긴다(`SpotCatalogUpsertJdbcAdapter`와 같은 판단).
 * `JdbcTemplate`은 그 어댑터들에만 머문다.
 *
 * **행마다 문장을 내지 않는다.** 이벤트 하나가 수만 쌍을 실을 수 있어(배정 배치의 노출 갱신)
 * 왕복 수가 그대로 지연이 된다 — `batchUpdate`가 한 번의 왕복으로 보낸다.
 */
@Component
class SpotCatalogLanguageJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotCatalogLanguagePort {

    /**
     * 표 전체를 [entries]로 맞춘다. 정본이 비면 표를 비운다 — 원본이 정말 비었다는 뜻일 수
     * 있고, 그때 사본만 남으면 사라진 장소의 글이 계속 보인다.
     */
    @Transactional
    override fun replaceAll(entries: List<CatalogLanguageEntry>): Int {
        if (entries.isEmpty()) {
            jdbcTemplate.update(DELETE_ALL_SQL)
            return 0
        }

        // **비우고 다시 넣지 않는다.** 수만 행을 지웠다 넣으면 그것이 전부 복제로 흘러가고,
        // 중간에 표가 비는 순간 동시에 도는 목록 조회가 spot 붙은 글을 통째로 놓친다.
        // 정본을 임시 테이블에 싣고 차집합만 움직이면 실제로 달라진 행만 바뀐다.
        //
        // `NOT IN` 목록이 아니라 임시 테이블인 이유는 정본 크기가 파라미터 상한(65,535)을
        // 넘을 수 있어서다 — 크기와 무관하게 한 가지 방식으로 끝난다.
        jdbcTemplate.execute(CREATE_TEMP_SQL)
        jdbcTemplate.batchUpdate(
            INSERT_TEMP_SQL,
            entries.map { arrayOf<Any>(it.catalogId, it.language.name) },
        )
        jdbcTemplate.update(DELETE_STALE_SQL)
        jdbcTemplate.update(INSERT_NEW_SQL)

        return entries.size
    }

    /**
     * 한 카탈로그를 [languages]에 맞춘다. **지우기가 먼저다** — 넣고 지우면 방금 넣은 행이
     * 삭제 조건에 걸리지 않게 조건을 복잡하게 써야 하고, 순서를 이렇게 두면 두 문장이 서로를
     * 모른다. 같은 트랜잭션이라 중간 상태는 밖에서 보이지 않는다.
     */
    @Transactional
    override fun replaceFor(catalogId: Long, languages: Set<ContentLanguage>) {
        if (languages.isEmpty()) {
            jdbcTemplate.update(DELETE_ALL_OF_CATALOG_SQL, catalogId)
            return
        }
        jdbcTemplate.update(
            "$DELETE_ALL_OF_CATALOG_SQL AND language NOT IN (${languages.joinToString(",") { "?" }})",
            catalogId,
            *languages.map { it.name }.toTypedArray(),
        )
        jdbcTemplate.batchUpdate(
            INSERT_SQL,
            languages.map { arrayOf<Any>(catalogId, it.name) },
        )
    }

    private companion object {
        /**
         * **스키마를 한정해 쓴다** — Flyway 커넥션과 달리 런타임 커넥션에는 search_path가
         * 걸려 있지만, 생 SQL이 그것에 기대면 설정이 바뀔 때 조용히 다른 스키마를 친다.
         */
        const val INSERT_SQL = """
            INSERT INTO community.community_spot_catalog_language (catalog_id, language)
            VALUES (?, ?)
            ON CONFLICT (catalog_id, language) DO NOTHING
        """

        /**
         * 정본을 담을 임시 테이블. `ON COMMIT DROP`이라 트랜잭션이 끝나면 사라지고, 커넥션
         * 풀이 재사용하는 커넥션에 남지 않는다.
         */
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

        /** 사본에 없는 정본 행을 넣는다. 이미 있는 것은 건드리지 않아 복제가 조용하다. */
        const val INSERT_NEW_SQL = """
            INSERT INTO community.community_spot_catalog_language (catalog_id, language)
            SELECT r.catalog_id, r.language FROM resolved_catalog_language r
            ON CONFLICT (catalog_id, language) DO NOTHING
        """

        /** 뒤에 `AND language NOT IN (...)`이 붙을 수 있어 WHERE가 열려 있다. */
        const val DELETE_ALL_OF_CATALOG_SQL = """
            DELETE FROM community.community_spot_catalog_language
            WHERE catalog_id = ?
        """

        /** 뒤에 `WHERE ... NOT IN (...)`이 붙을 수 있어 WHERE 없이 끝난다. */
        const val DELETE_ALL_SQL = "DELETE FROM community.community_spot_catalog_language"

    }
}
