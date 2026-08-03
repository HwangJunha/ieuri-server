package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.command.SyncSpotCommand
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant

/**
 * 관광지 목록 적재 포트 구현(SPEC-0004). Command ↔ 테이블 매핑을 여기서 담당한다
 * (별도 Mapper 클래스 없음, 프로젝트 관례).
 *
 * **네이티브 `INSERT ... ON CONFLICT` + JDBC 배치를 쓴다.** JPA에는 이식 가능한 upsert가 없고, 대안인
 * "자연키로 조회 후 있으면 dirty-checking / 없으면 persist"(spot_reaction 관례)는 배치의 페이지당
 * 수백~천 건 규모에서 건당 SELECT를 발생시켜 맞지 않는다. 단건 재평가와 대량 동기화는 쓰기 성격이
 * 다르므로 관례를 그대로 옮기지 않는다. 한 페이지를 한 번의 `executeBatch`로 보내 왕복 횟수를 줄인다.
 *
 * 충돌 시 **전체 컬럼을 무조건 덮어쓴다**(설계 문서 §2.4) — 해시 비교로 변경을 감지하면 낡은 저작권
 * 분류가 무기한 남는 위험이 있기 때문이다(런타임 계약 위험 5번).
 */
@Component
class SpotJpaAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotStorePort {

    @Transactional
    override fun upsertAll(spots: List<SyncSpotCommand>, syncedAt: Instant): Int {
        if (spots.isEmpty()) return 0
        val syncedTimestamp = Timestamp.from(syncedAt)
        val setter = ParameterizedPreparedStatementSetter<SyncSpotCommand> { ps: PreparedStatement, spot ->
            ps.setString(1, spot.contentId)
            ps.setString(2, spot.language.name)
            ps.setString(3, spot.title)
            ps.setString(4, spot.address)
            ps.setString(5, spot.thumbnailImageUrl)
            ps.setDouble(6, spot.coordinates.longitude)
            ps.setDouble(7, spot.coordinates.latitude)
            ps.setString(8, spot.category.name)
            ps.setInt(9, spot.contentTypeId)
            ps.setString(10, spot.copyright.name)
            ps.setTimestamp(11, syncedTimestamp)
        }
        // batchUpdate(sql, batchArgs, batchSize, pss)는 청크별 결과를 배열의 배열로 돌려준다.
        return jdbcTemplate.batchUpdate(UPSERT_SQL, spots, spots.size, setter)
            .sumOf { chunk -> chunk.count { it != 0 } }
    }

    /**
     * 사라진 항목 삭제(R-5). **임시 테이블을 쓰지 않는다.**
     *
     * 이전 구현은 생존 집합을 `CREATE TEMP TABLE ... ON COMMIT DROP`에 넣고 anti-join으로 지웠다.
     * 세션 로컬이라 인스턴스 간 충돌은 없지만, **같은 세션(=커넥션)이 이 메서드를 두 번 타면 테이블이
     * 이미 존재해 실패한다** — 언어별로 4번 호출되는 구조에서 커넥션이 재사용되면 그대로 걸리고,
     * 애플리케이션을 여러 개로 확장하면 경합 가능성이 더 커진다. DDL을 배치 경로에서 없애는 편이
     * 단순하고 안전하다.
     *
     * **대신 삭제 대상을 먼저 확정한 뒤 청크로 나눠 지운다.** `NOT IN (6만 건)`을 그대로 보내면
     * 드라이버 파라미터 한도(65535)를 넘고 플래너도 감당하지 못하므로, 순서를 뒤집는다:
     *
     *  1. 그 언어의 **현존 contentId 전체**를 읽는다(언어당 최대 6만 건 ≈ 6MB, 이미 같은 규모의
     *     생존 집합을 메모리에 들고 있으므로 새로운 부담이 아니다).
     *  2. 메모리에서 생존 집합을 빼 **삭제 대상만** 남긴다.
     *  3. 그 대상을 [DELETE_CHUNK_SIZE]씩 나눠 `IN (...)`으로 지운다.
     *
     * **여집합을 SQL이 아니라 메모리에서 계산하는 것이 핵심이다.** `NOT IN`을 청크로 쪼개면 각 청크가
     * "그 청크에 없는 전부"를 지워 첫 청크에서 데이터가 날아간다 — 여집합은 분할 가능한 연산이
     * 아니다. 반대로 삭제 대상 목록은 그냥 목록이라 얼마든지 나눌 수 있다.
     */
    @Transactional
    override fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int {
        // 빈 집합으로 호출되면 전량 삭제가 된다. 게이트가 막아야 하지만, 여기서도 한 번 더 막는다 —
        // 이 한 줄이 없으면 게이트 버그가 곧 데이터 전량 손실이다(AC-14).
        require(survivingContentIds.isNotEmpty()) { "생존 집합이 비어 있으면 정리하지 않는다." }

        val doomed = jdbcTemplate.queryForList(
            "SELECT content_id FROM spot WHERE language = ?",
            String::class.java,
            language.name,
        ).filterNotNull().filterNot { it in survivingContentIds }

        return deleteByContentIdsInChunks("spot", language, doomed)
    }

    /**
     * [contentIds]를 [DELETE_CHUNK_SIZE]씩 나눠 삭제하고 총 삭제 행 수를 돌려준다.
     *
     * 한 트랜잭션 안에서 여러 번 `DELETE`를 치므로 부분 삭제로 끝나지 않는다 — 중간에 실패하면
     * 전체가 롤백된다. 청크는 **파라미터 한도를 넘지 않기 위한 분할이지 커밋 경계가 아니다.**
     */
    private fun deleteByContentIdsInChunks(
        table: String,
        language: DomainLocale,
        contentIds: List<String>,
    ): Int {
        if (contentIds.isEmpty()) return 0
        return contentIds.chunked(DELETE_CHUNK_SIZE).sumOf { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            jdbcTemplate.update(
                "DELETE FROM $table WHERE language = ? AND content_id IN ($placeholders)",
                language.name, *chunk.toTypedArray(),
            )
        }
    }

    @Transactional(readOnly = true)
    override fun countByLanguage(language: DomainLocale): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM spot WHERE language = ?",
            Long::class.java,
            language.name,
        ) ?: 0L

    private companion object {
        /**
         * 삭제 `IN` 절 하나에 넣을 최대 id 수. JDBC 드라이버 파라미터 한도(65535)에 여유를 두고,
         * 플래너가 부담 없이 다루는 크기로 잡는다. 커밋 경계가 아니라 **분할 단위일 뿐이다.**
         */
        const val DELETE_CHUNK_SIZE = 1_000

        /**
         * `updated_at`은 **실제로 값이 바뀐 경우에만** 올린다(CASE 절). 무조건 갱신하면 매일 전 행이
         * 변경된 것처럼 보여 "실제로 바뀐 것"을 구분할 수 없어진다. `last_synced_at`은 반대로 항상
         * 갱신해야 정리 단계(R-5)가 "이번 실행이 이 행을 봤다"를 판정할 수 있다.
         */
        val UPSERT_SQL = """
            INSERT INTO spot (
                content_id, language, title, address, thumbnail_image_url,
                map_x, map_y, category, content_type_id, copyright, last_synced_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (content_id, language) DO UPDATE SET
                title               = EXCLUDED.title,
                address             = EXCLUDED.address,
                thumbnail_image_url = EXCLUDED.thumbnail_image_url,
                map_x               = EXCLUDED.map_x,
                map_y               = EXCLUDED.map_y,
                category            = EXCLUDED.category,
                content_type_id     = EXCLUDED.content_type_id,
                copyright           = EXCLUDED.copyright,
                last_synced_at      = EXCLUDED.last_synced_at,
                updated_at          = CASE
                    WHEN spot.title               IS DISTINCT FROM EXCLUDED.title
                      OR spot.address             IS DISTINCT FROM EXCLUDED.address
                      OR spot.thumbnail_image_url IS DISTINCT FROM EXCLUDED.thumbnail_image_url
                      OR spot.map_x               IS DISTINCT FROM EXCLUDED.map_x
                      OR spot.map_y               IS DISTINCT FROM EXCLUDED.map_y
                      OR spot.category            IS DISTINCT FROM EXCLUDED.category
                      OR spot.content_type_id     IS DISTINCT FROM EXCLUDED.content_type_id
                      OR spot.copyright           IS DISTINCT FROM EXCLUDED.copyright
                    THEN now()
                    ELSE spot.updated_at
                END
        """.trimIndent()
    }
}
